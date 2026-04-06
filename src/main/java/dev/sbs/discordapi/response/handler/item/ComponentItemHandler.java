package dev.sbs.discordapi.response.handler.item;

import dev.sbs.discordapi.component.layout.Container;
import dev.sbs.discordapi.component.layout.Section;
import dev.sbs.discordapi.component.scope.ContainerComponent;
import dev.sbs.discordapi.response.handler.Filter;
import dev.sbs.discordapi.response.handler.FilterHandler;
import dev.sbs.discordapi.response.handler.Search;
import dev.sbs.discordapi.response.handler.SearchHandler;
import dev.sbs.discordapi.response.handler.SortHandler;
import dev.sbs.discordapi.response.handler.Sorter;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.function.TriFunction;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.collection.tuple.triple.Triple;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.NumberUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * An {@link ItemHandler} implementation that renders items as {@link Section Sections}
 * inside a {@link Container}.
 *
 * <p>
 * Instead of embed fields, each item is transformed into a {@code Section} via the
 * configured transformer. Static items are rendered as {@link ContainerComponent} instances
 * prepended to the container.
 *
 * @param <T> the item type
 * @see ItemHandler
 * @see Section
 * @see Container
 */
public final class ComponentItemHandler<T> implements ItemHandler<T> {

    private final @NotNull ConcurrentList<T> items;
    private final @NotNull ConcurrentList<ContainerComponent> staticItems;
    private final @NotNull ConcurrentMap<String, Object> variables;
    private final @NotNull TriFunction<T, Long, Long, Section> transformer;
    private final @NotNull BiFunction<ContainerComponent, ConcurrentMap<String, Object>, ContainerComponent> staticItemApplier;
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
    private ConcurrentList<ContainerComponent> cachedStaticItems = Concurrent.newUnmodifiableList();
    private ConcurrentList<Section> cachedSections = Concurrent.newUnmodifiableList();

    private ComponentItemHandler(
        @NotNull ConcurrentList<T> items,
        @NotNull ConcurrentList<ContainerComponent> staticItems,
        @NotNull ConcurrentMap<String, Object> variables,
        @NotNull TriFunction<T, Long, Long, Section> transformer,
        @NotNull BiFunction<ContainerComponent, ConcurrentMap<String, Object>, ContainerComponent> staticItemApplier,
        boolean editorEnabled,
        int amountPerPage,
        @NotNull SortHandler<T> sortHandler,
        @NotNull FilterHandler<T> filterHandler,
        @NotNull SearchHandler<T> searchHandler
    ) {
        this.items = items;
        this.staticItems = staticItems;
        this.variables = variables;
        this.transformer = transformer;
        this.staticItemApplier = staticItemApplier;
        this.editorEnabled = editorEnabled;
        this.amountPerPage = amountPerPage;
        this.sortHandler = sortHandler;
        this.filterHandler = filterHandler;
        this.searchHandler = searchHandler;
    }

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ComponentItemHandler<?> that = (ComponentItemHandler<?>) o;

        return Objects.equals(this.getItems(), that.getItems())
            && Objects.equals(this.getVariables(), that.getVariables())
            && this.isEditorEnabled() == that.isEditorEnabled()
            && this.getAmountPerPage() == that.getAmountPerPage()
            && Objects.equals(this.getSortHandler(), that.getSortHandler())
            && Objects.equals(this.getFilterHandler(), that.getFilterHandler())
            && Objects.equals(this.getSearchHandler(), that.getSearchHandler())
            && this.isCacheUpdateRequired() == that.isCacheUpdateRequired()
            && this.getCurrentIndex() == that.getCurrentIndex();
    }

    public static <T> @NotNull Builder<T> from(@NotNull ComponentItemHandler<T> handler) {
        return new Builder<T>()
            .withItems(handler.getItems())
            .withStaticItems(handler.staticItems)
            .withVariables(handler.getVariables())
            .withTransformer(handler.getTransformer())
            .withStaticItemApplier(handler.staticItemApplier)
            .isEditorEnabled(handler.isEditorEnabled())
            .withAmountPerPage(handler.getAmountPerPage())
            .withSorters(handler.getSortHandler().getItems())
            .withFilters(handler.getFilterHandler().getItems())
            .withSearch(handler.getSearchHandler().getItems());
    }

    /** The transformer used to convert items into sections. */
    public @NotNull TriFunction<T, Long, Long, Section> getTransformer() {
        return this.transformer;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Applies the configured {@code staticItemApplier} to each raw static component on
     * cache refresh, allowing template placeholders in component text to reflect the
     * current pagination variables.
     */
    @Override
    public @NotNull ConcurrentList<ContainerComponent> getCachedStaticItems() {
        if (this.isCacheUpdateRequired()) {
            this.cachedStaticItems = this.staticItems.stream()
                .map(item -> this.staticItemApplier.apply(item, this.getVariables()))
                .collect(Concurrent.toUnmodifiableList());
        }

        return this.cachedStaticItems;
    }

    /**
     * Returns the cached sections for the current page, rebuilding the cache if needed.
     *
     * @return the cached sections
     */
    public @NotNull ConcurrentList<Section> getCachedSections() {
        if (this.isCacheUpdateRequired()) {
            ConcurrentList<T> filteredItems = this.getFilteredItems();
            ConcurrentList<Section> filteredSections = filteredItems.indexedStream()
                .collapseToSingle(this.transformer)
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
                .map(index -> NumberUtil.ensureRange(index, 1, filteredSections.size()))
                .ifPresent(index -> this.currentIndex = index);

            int startIndex = (this.getCurrentIndex() - 1) * this.getAmountPerPage();
            int endIndex = Math.min(startIndex + this.getAmountPerPage(), filteredSections.size());
            this.cachedSections = filteredSections.subList(startIndex, endIndex);

            this.variables.put("FILTERED_SIZE", filteredSections.size());
            this.variables.put("CACHED_SIZE", this.cachedSections.size());
            this.variables.put("START_INDEX", startIndex);
            this.variables.put("END_INDEX", endIndex);
        }

        return this.cachedSections;
    }

    /**
     * Builds a container with static items and the current page's sections.
     *
     * @return the rendered container
     */
    public @NotNull Container getRenderContainer() {
        Container.Builder containerBuilder = Container.builder();

        this.getCachedStaticItems().forEach(containerBuilder::withComponents);
        this.getCachedSections().forEach(containerBuilder::withComponents);

        return containerBuilder.build();
    }

    @Override
    public @NotNull ConcurrentList<T> getItems() {
        return this.items;
    }

    @Override
    public @NotNull ConcurrentMap<String, Object> getVariables() {
        return this.variables;
    }

    @Override
    public int getAmountPerPage() {
        return this.amountPerPage;
    }

    @Override
    public boolean isEditorEnabled() {
        return this.editorEnabled;
    }

    @Override
    public @NotNull SortHandler<T> getSortHandler() {
        return this.sortHandler;
    }

    @Override
    public @NotNull FilterHandler<T> getFilterHandler() {
        return this.filterHandler;
    }

    @Override
    public @NotNull SearchHandler<T> getSearchHandler() {
        return this.searchHandler;
    }

    @Override
    public @NotNull ConcurrentList<T> getCachedFilteredItems() {
        return this.cachedFilteredItems;
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

    @Override
    public int getCurrentIndex() {
        return this.currentIndex;
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
        return Objects.hash(this.getItems(), this.getVariables(), this.isEditorEnabled(), this.getAmountPerPage(), this.getSortHandler(), this.getFilterHandler(), this.getSearchHandler(), this.isCacheUpdateRequired(), this.getCurrentIndex());
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

    public static class Builder<T> {

        private final ConcurrentList<T> items = Concurrent.newList();
        private final ConcurrentList<ContainerComponent> staticItems = Concurrent.newList();
        private final ConcurrentList<Sorter<T>> sorters = Concurrent.newList();
        private final ConcurrentList<Filter<T>> filters = Concurrent.newList();
        private final ConcurrentList<Search<T>> searchers = Concurrent.newList();
        private final ConcurrentMap<String, Object> variables = Concurrent.newMap();
        @BuildFlag(nonNull = true)
        private TriFunction<T, Long, Long, Section> transformer = (t, index, size) -> Section.builder().build();
        @BuildFlag(nonNull = true)
        private BiFunction<ContainerComponent, ConcurrentMap<String, Object>, ContainerComponent> staticItemApplier = (component, vars) -> component;
        private boolean editorEnabled = false;
        private int amountPerPage = 12;

        private Builder() {}

        public Builder<T> clearItems() {
            this.items.clear();
            return this;
        }

        public Builder<T> isEditorEnabled() {
            return this.isEditorEnabled(true);
        }

        public Builder<T> isEditorEnabled(boolean value) {
            this.editorEnabled = value;
            return this;
        }

        public Builder<T> withAmountPerPage(int amountPerPage) {
            this.amountPerPage = NumberUtil.ensureRange(amountPerPage, 1, 24);
            return this;
        }

        public Builder<T> withFilters(@NotNull Filter<T>... filters) {
            return this.withFilters(Arrays.asList(filters));
        }

        public Builder<T> withFilters(@NotNull Iterable<Filter<T>> filters) {
            filters.forEach(this.filters::add);
            return this;
        }

        public Builder<T> withItems(@NotNull T... items) {
            return this.withItems(Arrays.asList(items));
        }

        public Builder<T> withItems(@NotNull Iterable<T> items) {
            items.forEach(this.items::add);
            return this;
        }

        public Builder<T> withSearch(@NotNull Search<T>... search) {
            return this.withSearch(Arrays.asList(search));
        }

        public Builder<T> withSearch(@NotNull Iterable<Search<T>> search) {
            search.forEach(this.searchers::add);
            return this;
        }

        public Builder<T> withSorters(@NotNull Sorter<T>... sorters) {
            return this.withSorters(Arrays.asList(sorters));
        }

        public Builder<T> withSorters(@NotNull Iterable<Sorter<T>> sorters) {
            sorters.forEach(this.sorters::add);
            return this;
        }

        public Builder<T> withStaticItems(@NotNull ContainerComponent... items) {
            return this.withStaticItems(Arrays.asList(items));
        }

        public Builder<T> withStaticItems(@NotNull Iterable<ContainerComponent> items) {
            items.forEach(this.staticItems::add);
            return this;
        }

        /**
         * Sets the transformer used to convert items into sections.
         *
         * @param transformer the section transformer
         */
        public Builder<T> withTransformer(@NotNull TriFunction<T, Long, Long, Section> transformer) {
            this.transformer = transformer;
            return this;
        }

        /**
         * Sets the function used to apply pagination variables to each static item
         * on cache refresh.
         *
         * <p>
         * Defaults to identity (no variable processing). Provide a custom function to
         * substitute template placeholders in component text.
         *
         * @param applier the variable applier function
         */
        public Builder<T> withStaticItemApplier(@NotNull BiFunction<ContainerComponent, ConcurrentMap<String, Object>, ContainerComponent> applier) {
            this.staticItemApplier = applier;
            return this;
        }

        public Builder<T> withVariable(@NotNull String key, @NotNull Object value) {
            return this.withVariables(Pair.of(key, value));
        }

        public Builder<T> withVariables(@NotNull Pair<String, Object>... variables) {
            return this.withVariables(Arrays.asList(variables));
        }

        public Builder<T> withVariables(@NotNull Iterable<Pair<String, Object>> variables) {
            variables.forEach(this.variables::put);
            return this;
        }

        public Builder<T> withVariables(@NotNull Map<String, Object> variables) {
            this.variables.putAll(variables);
            return this;
        }

        public @NotNull ComponentItemHandler<T> build() {
            Reflection.validateFlags(this);
            this.variables.put("SIZE", this.items.size());

            return new ComponentItemHandler<>(
                this.items.toUnmodifiableList(),
                this.staticItems.toUnmodifiableList(),
                this.variables,
                this.transformer,
                this.staticItemApplier,
                this.editorEnabled,
                this.amountPerPage,
                new SortHandler<>(this.sorters),
                new FilterHandler<>(this.filters),
                new SearchHandler<>(this.searchers)
            );
        }

    }

}
