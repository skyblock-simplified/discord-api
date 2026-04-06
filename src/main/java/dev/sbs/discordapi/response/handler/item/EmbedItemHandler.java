package dev.sbs.discordapi.response.handler.item;

import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.handler.Filter;
import dev.sbs.discordapi.response.handler.FilterHandler;
import dev.sbs.discordapi.response.handler.Search;
import dev.sbs.discordapi.response.handler.SearchHandler;
import dev.sbs.discordapi.response.handler.SortHandler;
import dev.sbs.discordapi.response.handler.Sorter;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.item.Item;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
import dev.sbs.discordapi.response.page.item.field.StringItem;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.StreamUtil;
import dev.simplified.collection.function.TriFunction;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.collection.tuple.triple.Triple;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.NumberUtil;
import dev.simplified.util.StringUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An {@link ItemHandler} implementation that renders items as embed {@link Field Fields}.
 *
 * <p>
 * Supports static items, a configurable {@link ItemHandler.FieldStyle}, and a transformer that
 * converts each item into a {@link FieldItem} for embed rendering.
 *
 * @param <T> the item type
 * @see ItemHandler
 * @see FieldItem
 */
@Getter
@RequiredArgsConstructor
public final class EmbedItemHandler<T> implements ItemHandler<T> {

    private final @NotNull ConcurrentList<T> items;
    private final @NotNull ConcurrentList<Item> staticItems;
    private final @NotNull ConcurrentMap<String, Object> variables;
    private final @NotNull ItemHandler.FieldStyle fieldStyle;
    private final @NotNull TriFunction<T, Long, Long, FieldItem<?>> transformer;
    private final @NotNull Optional<String> listTitle;
    private final boolean editorEnabled;
    private final int amountPerPage;

    // Handlers
    private final @NotNull SortHandler<T> sortHandler;
    private final @NotNull FilterHandler<T> filterHandler;
    private final @NotNull SearchHandler<T> searchHandler;

    // Caching
    private int currentIndex = 1;
    private boolean cacheUpdateRequired = true;
    private ConcurrentList<T> cachedFilteredItems = Concurrent.newUnmodifiableList();
    private ConcurrentList<FieldItem<?>> cachedFieldItems = Concurrent.newUnmodifiableList();
    private ConcurrentList<Item> cachedStaticItems = Concurrent.newUnmodifiableList();

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EmbedItemHandler<?> that = (EmbedItemHandler<?>) o;

        return Objects.equals(this.getItems(), that.getItems())
            && Objects.equals(this.getStaticItems(), that.getStaticItems())
            && Objects.equals(this.getVariables(), that.getVariables())
            && Objects.equals(this.getFieldStyle(), that.getFieldStyle())
            && Objects.equals(this.getTransformer(), that.getTransformer())
            && Objects.equals(this.getListTitle(), that.getListTitle())
            && this.isEditorEnabled() == that.isEditorEnabled()
            && this.getAmountPerPage() == that.getAmountPerPage()
            && Objects.equals(this.getSortHandler(), that.getSortHandler())
            && Objects.equals(this.getFilterHandler(), that.getFilterHandler())
            && Objects.equals(this.getSearchHandler(), that.getSearchHandler())
            && this.isCacheUpdateRequired() == that.isCacheUpdateRequired()
            && this.getCurrentIndex() == that.getCurrentIndex()
            && Objects.equals(this.getCachedFilteredItems(), that.getCachedFilteredItems())
            && Objects.equals(this.getCachedFieldItems(), that.getCachedFieldItems())
            && Objects.equals(this.getCachedStaticItems(), that.getCachedStaticItems());
    }

    public static <T> @NotNull Builder<T> from(@NotNull EmbedItemHandler<T> itemHandler) {
        return new Builder<T>()
            .withItems(itemHandler.getItems())
            .withStaticItems(itemHandler.getStaticItems())
            .withVariables(itemHandler.getVariables())
            .withFieldStyle(itemHandler.getFieldStyle())
            .withTransformer(itemHandler.getTransformer())
            .withListTitle(itemHandler.getListTitle())
            .isEditorEnabled(itemHandler.isEditorEnabled())
            .withAmountPerPage(itemHandler.getAmountPerPage())
            .withSorters(itemHandler.getSortHandler().getItems())
            .withFilters(itemHandler.getFilterHandler().getItems())
            .withSearch(itemHandler.getSearchHandler().getItems());
    }

    @Override
    public @NotNull ConcurrentList<Item> getCachedStaticItems() {
        if (this.isCacheUpdateRequired()) {
            this.cachedStaticItems = this.staticItems.stream()
                .map(item -> item.applyVariables(this.getVariables()))
                .collect(Concurrent.toUnmodifiableList());
        }

        return this.cachedStaticItems;
    }

    public @NotNull ConcurrentList<FieldItem<?>> getCachedFieldItems() {
        if (this.isCacheUpdateRequired()) {
            // Load Filtered Items
            ConcurrentList<T> filteredItems = this.getFilteredItems();
            ConcurrentList<FieldItem<?>> filteredFieldItems = filteredItems.indexedStream()
                .collapseToSingle(this.getTransformer())
                .filter(Objects::nonNull)
                .collect(Concurrent.toUnmodifiableList());

            // Custom Search
            this.getSearchHandler()
                .getPending()
                .flatMap(search -> filteredItems.indexedStream()
                    .filter((item, index, size) -> search.getPredicates()
                        .stream()
                        .anyMatch(predicate -> predicate.test(item, search.getLastMatch().orElseThrow()))
                    )
                    .map(Triple::getMiddle)
                    .findFirst()
                )
                .filter(index -> index > -1)
                .map(index -> Math.ceil((double) index / this.getAmountPerPage()))
                .map(Double::intValue)
                .map(index -> NumberUtil.ensureRange(index, 1, filteredFieldItems.size()))
                .ifPresent(index -> this.currentIndex = index); // Do not call this.gotoItemPage(index)

            // Cache Sublist
            int startIndex = (this.getCurrentIndex() - 1) * this.getAmountPerPage();
            int endIndex = Math.min(startIndex + this.getAmountPerPage(), filteredFieldItems.size());
            this.cachedFieldItems = filteredFieldItems.subList(startIndex, endIndex);

            // Cache Variables
            this.variables.put("FILTERED_SIZE", filteredFieldItems.size());
            this.variables.put("CACHED_SIZE", this.cachedFieldItems.size());
            this.variables.put("START_INDEX", startIndex);
            this.variables.put("END_INDEX", endIndex);
        }

        return this.cachedFieldItems;
    }

    private @NotNull ConcurrentList<T> getFilteredItems() {
        if (this.isCacheUpdateRequired()) {
            this.cachedFilteredItems = this.getSortHandler()
                .getCurrent()
                .map(sorter -> sorter.apply(this.getItems(), this.getSortHandler().isReversed()))
                .orElse(this.getItems())
                .indexedStream()
                .filter((t, index, size) -> this.getFilterHandler()
                    .getItems()
                    .stream()
                    .allMatch(filter -> filter.test(t, index, size))
                )
                .map(Triple::getLeft)
                .collect(Concurrent.toUnmodifiableList());

            this.variables.put("TOTAL_SIZE", this.getItems().size());
        }

        return this.cachedFilteredItems;
    }

    public @NotNull ConcurrentList<Field> getRenderFields() {
        return switch (this.getFieldStyle()) {
            case DEFAULT -> this.getCachedFieldItems()
                .stream()
                .map(FieldItem::getRenderField)
                .collect(Concurrent.toUnmodifiableList());
            case FIELD, FIELD_INLINE -> this.getCachedFieldItems()
                .stream()
                .map(fieldItem -> fieldItem.getRenderField(this.getFieldStyle().isInline()))
                .collect(Concurrent.toUnmodifiableList());
            case LIST -> Concurrent.newUnmodifiableList(
                Field.builder()
                    .withName(this.getListTitle())
                    .withValue(
                        this.getCachedFieldItems()
                            .stream()
                            .map(FieldItem::getRenderValue)
                            .collect(StreamUtil.toStringBuilder(true))
                            .toString()
                    )
                    .isInline(this.getFieldStyle().isInline())
                    .build()
            );
        };
    }

    @Override
    public @NotNull Integer getCurrentPage() {
        return this.getCurrentIndex();
    }

    @Override
    public int getTotalPages() {
        return NumberUtil.roundUp((double) this.getFilteredItems().size() / this.getAmountPerPage(), 1);
    }

    @Override
    public void gotoPage(@NotNull Integer index) {
        this.currentIndex = NumberUtil.ensureRange(index, 1, this.getFilteredItems().size());
        this.setCacheUpdateRequired();
    }

    @Override
    public void gotoFirstItemPage() {
        this.gotoPage(1);
    }

    @Override
    public void gotoLastItemPage() {
        this.gotoPage(this.getTotalPages());
    }

    @Override
    public void gotoNextPage() {
        this.gotoPage(this.currentIndex + 1);
    }

    @Override
    public void gotoPreviousPage() {
        this.gotoPage(this.currentIndex - 1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getItems(), this.getStaticItems(), this.getVariables(), this.getFieldStyle(), this.getTransformer(), this.getListTitle(), this.isEditorEnabled(), this.getAmountPerPage(), this.getSortHandler(), this.getFilterHandler(), this.getSearchHandler(), this.isCacheUpdateRequired(), this.getCurrentIndex(), this.getCachedFilteredItems(), this.getCachedFieldItems(), this.getCachedStaticItems());
    }

    @Override
    public boolean hasNextItemPage() {
        return this.currentIndex < this.getTotalPages();
    }

    @Override
    public boolean hasPreviousItemPage() {
        return this.currentIndex > 1;
    }

    @Override
    public boolean isCacheUpdateRequired() {
        return this.cacheUpdateRequired ||
            this.getSortHandler().isCacheUpdateRequired() ||
            this.getFilterHandler().isCacheUpdateRequired() ||
            this.getSearchHandler().isCacheUpdateRequired();
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    @Override
    public void setCacheUpdateRequired(boolean cacheUpdateRequired) {
        this.cacheUpdateRequired = cacheUpdateRequired;
        this.getSortHandler().setCacheUpdateRequired(cacheUpdateRequired);
        this.getFilterHandler().setCacheUpdateRequired(cacheUpdateRequired);
        this.getSearchHandler().setCacheUpdateRequired(cacheUpdateRequired);
    }

    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Builder<T> {

        private final ConcurrentList<T> items = Concurrent.newList();
        private final ConcurrentList<Item> staticItems = Concurrent.newList();
        private final ConcurrentList<Sorter<T>> sorters = Concurrent.newList();
        private final ConcurrentList<Filter<T>> filters = Concurrent.newList();
        private final ConcurrentList<Search<T>> searchers = Concurrent.newList();
        private final ConcurrentMap<String, Object> variables = Concurrent.newMap();
        @BuildFlag(nonNull = true)
        private ItemHandler.FieldStyle fieldStyle = ItemHandler.FieldStyle.DEFAULT;
        @BuildFlag(nonNull = true)
        private TriFunction<T, Long, Long, FieldItem<?>> transformer = (t, index, size) -> StringItem.builder().build();
        private Optional<String> listTitle = Optional.empty();
        private boolean editorEnabled = false;
        private int amountPerPage = 12;

        /**
         * Clear all items from the {@link EmbedItemHandler}.
         */
        public Builder<T> clearItems() {
            this.items.clear();
            return this;
        }

        /**
         * Clear all static items from the {@link EmbedItemHandler}.
         */
        public Builder<T> clearStaticItems() {
            this.staticItems.clear();
            return this;
        }

        /**
         * Enables the editor.
         */
        public Builder<T> isEditorEnabled() {
            return this.isEditorEnabled(true);
        }

        /**
         * Sets if the editor is enabled.
         *
         * @param value True to enable editor.
         */
        public Builder<T> isEditorEnabled(boolean value) {
            this.editorEnabled = value;
            return this;
        }

        /**
         * Sets the amount of {@link Item PageItems} to render per {@link Page}.
         * <br><br>
         * Defaults to 12.<br>
         * Minimum required is 1.<br>
         * Maximum allowed is 24.
         *
         * @param amountPerPage Number of items to render.
         */
        public Builder<T> withAmountPerPage(int amountPerPage) {
            this.amountPerPage = NumberUtil.ensureRange(amountPerPage, 1, 24);
            return this;
        }

        /**
         * Adds filters used to render {@link FieldItem RenderItems}.
         *
         * @param filters Collection of filters to apply to {@link T}.
         */
        public Builder<T> withFilters(@NotNull Filter<T>... filters) {
            return this.withFilters(Arrays.asList(filters));
        }

        /**
         * Adds filters used to render {@link FieldItem RenderItems}.
         *
         * @param filters Collection of filters to apply to {@link T}.
         */
        public Builder<T> withFilters(@NotNull Iterable<Filter<T>> filters) {
            filters.forEach(this.filters::add);
            return this;
        }

        /**
         * Sets the {@link ItemHandler.FieldStyle} used to render {@link Field Fields}.
         */
        public Builder<T> withFieldStyle(@NotNull ItemHandler.FieldStyle fieldStyle) {
            this.fieldStyle = fieldStyle;
            return this;
        }

        /**
         * Add {@link T Items} to the {@link Page} item list.
         *
         * @param items Variable number of items to add.
         */
        public Builder<T> withItems(@NotNull T... items) {
            return this.withItems(Arrays.asList(items));
        }

        /**
         * Add {@link T Items} to the {@link Page} item list.
         *
         * @param items Collection of items to add.
         */
        public Builder<T> withItems(@NotNull Iterable<T> items) {
            items.forEach(this.items::add);
            return this;
        }

        public Builder<T> withListTitle(@Nullable String title) {
            return this.withListTitle(Optional.ofNullable(title));
        }

        public Builder<T> withListTitle(@Nullable @PrintFormat String title, @Nullable Object... args) {
            return this.withListTitle(StringUtil.formatNullable(title, args));
        }

        public Builder<T> withListTitle(@NotNull Optional<String> title) {
            this.listTitle = title;
            return this;
        }

        /**
         * Add custom search options for the {@link Item FieldItems}.
         *
         * @param search A variable amount of searchers.
         */
        public Builder<T> withSearch(@NotNull Search<T>... search) {
            return this.withSearch(Arrays.asList(search));
        }

        /**
         * Add custom search options for the {@link Item FieldItems}.
         *
         * @param search A variable amount of searchers.
         */
        public Builder<T> withSearch(@NotNull Iterable<Search<T>> search) {
            search.forEach(this.searchers::add);
            return this;
        }

        /**
         * Add custom sort filter for the {@link Item FieldItems}.
         *
         * @param sorters A variable amount of filters.
         */
        public Builder<T> withSorters(@NotNull Sorter<T>... sorters) {
            return this.withSorters(Arrays.asList(sorters));
        }

        /**
         * Add custom sort filters for the {@link Item FieldItems}.
         *
         * @param sorters A variable amount of filters.
         */
        public Builder<T> withSorters(@NotNull Iterable<Sorter<T>> sorters) {
            sorters.forEach(this.sorters::add);
            return this;
        }

        /**
         * Add {@link Item Items} to the {@link Page} rendered item list.
         * <br><br>
         * These items will always be rendered.
         *
         * @param items Variable number of items to add.
         */
        public Builder<T> withStaticItems(@NotNull Item... items) {
            return this.withStaticItems(Arrays.asList(items));
        }

        /**
         * Add {@link Item Items} to the {@link Page} rendered item list.
         * <br><br>
         * These items will always be rendered.
         *
         * @param items Collection of items to add.
         */
        public Builder<T> withStaticItems(@NotNull Iterable<Item> items) {
            items.forEach(item -> {
                if (!item.isSingular() || this.staticItems.stream().noneMatch(citem -> citem.getType() == item.getType()))
                    this.staticItems.add(item);
            });

            return this;
        }

        /**
         * Sets the transformer used to convert {@link T} to a {@link FieldItem}.
         *
         * @param transformer How to render {@link T} as a FieldItem.
         */
        public Builder<T> withTransformer(@NotNull TriFunction<T, Long, Long, FieldItem<?>> transformer) {
            this.transformer = transformer;
            return this;
        }

        /**
         * Add a variable to be evaluated when building the dynamic {@link Embed}.
         *
         * @param key The variable name.
         * @param value The variable value.
         */
        public Builder<T> withVariable(@NotNull String key, @NotNull Object value) {
            return this.withVariables(Pair.of(key, value));
        }

        /**
         * Add variables to be evaluated when building the dynamic {@link Embed}.
         *
         * @param variables The variables to be accessible.
         */
        public Builder<T> withVariables(@NotNull Pair<String, Object>... variables) {
            return this.withVariables(Arrays.asList(variables));
        }

        /**
         * Add variables to be evaluated when building the dynamic {@link Embed}.
         *
         * @param variables The variables to be accessible.
         */
        public Builder<T> withVariables(@NotNull Iterable<Pair<String, Object>> variables) {
            variables.forEach(this.variables::put);
            return this;
        }

        /**
         * Add variables to be evaluated when building the dynamic {@link Embed}.
         *
         * @param variables The variables to be accessible.
         */
        public Builder<T> withVariables(@NotNull Map<String, Object> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public @NotNull EmbedItemHandler<T> build() {
            Reflection.validateFlags(this);
            this.variables.put("SIZE", this.items.size());

            return new EmbedItemHandler<>(
                this.items.toUnmodifiableList(),
                this.staticItems.toUnmodifiableList(),
                this.variables,
                this.fieldStyle,
                this.transformer,
                this.listTitle,
                this.editorEnabled,
                this.amountPerPage,
                new SortHandler<>(this.sorters),
                new FilterHandler<>(this.filters),
                new SearchHandler<>(this.searchers)
            );
        }

    }

}
