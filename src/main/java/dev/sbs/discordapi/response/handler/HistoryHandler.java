package dev.sbs.discordapi.response.handler;

import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.page.Paging;
import dev.sbs.discordapi.response.page.Subpages;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.util.NumberUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A page handling wrapper that manages both sibling and child navigation
 * using a stack-based history model.
 *
 * <p>
 * Child navigation is available when the current page implements {@link Subpages},
 * determined at runtime. When pages do not support subpages, only sibling
 * (next/previous) navigation is available.
 *
 * @param <P> page type for history
 * @param <I> identifier type for searching
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class HistoryHandler<P, I> implements OutputHandler<P>, Paging<P> {

    private final @NotNull ConcurrentList<P> items;
    private final @NotNull Optional<BiFunction<P, I, Boolean>> matcher;
    private final @NotNull Optional<Function<P, I>> transformer;
    @Setter private boolean cacheUpdateRequired;
    @Getter(AccessLevel.NONE)
    private final @NotNull ConcurrentList<P> history = Concurrent.newList();

    public static <P, I> @NotNull Builder<P, I> builder() {
        return new Builder<>();
    }

    public @NotNull P editCurrentPage(@NotNull Function<P, P> page) {
        P newPage = page.apply(this.getCurrentPage());
        this.history.set(this.history.size() - 1, newPage);
        this.setCacheUpdateRequired();
        return newPage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HistoryHandler<?, ?> that = (HistoryHandler<?, ?>) o;

        return this.isCacheUpdateRequired() == that.isCacheUpdateRequired()
            && Objects.equals(this.getItems(), that.getItems())
            && Objects.equals(this.getMatcher(), that.getMatcher())
            && Objects.equals(this.getTransformer(), that.getTransformer())
            && Objects.equals(this.getHistory(), that.getHistory());
    }

    /**
     * Gets a {@link P page} from the provided identifier.
     *
     * @param identifier the identifier to find
     */
    public @NotNull Optional<P> getPage(I identifier) {
        return this.getItems()
            .stream()
            .filter(page -> this.getMatcher().map(m -> m.apply(page, identifier)).orElse(false))
            .findFirst();
    }

    @Override
    public int getCurrentIndex() {
        return this.getSiblings().indexOf(this.getCurrentPage());
    }

    public @NotNull P getCurrentPage() {
        return this.history.findLast().orElseThrow(); // Will Always Exist
    }

    public @NotNull Optional<P> getPreviousPage() {
        return Optional.ofNullable(this.history.size() > 1 ? this.history.get(this.history.size() - 2) : null);
    }

    public @NotNull ConcurrentList<P> getHistory() {
        return this.history.toUnmodifiableList();
    }

    public @NotNull ConcurrentList<I> getIdentifierHistory() {
        return this.history.stream()
            .map(page -> this.getTransformer().map(t -> t.apply(page)))
            .flatMap(Optional::stream)
            .collect(Concurrent.toList());
    }

    /**
     * Gets a subpage from the current page using the given identifier.
     *
     * @param identifier the subpage option value
     */
    @SuppressWarnings("unchecked")
    public @NotNull Optional<P> getSubPage(@NotNull I identifier) {
        P current = this.getCurrentPage();

        if (current instanceof Subpages<?> subpages) {
            return ((ConcurrentList<P>) subpages.getPages())
                .stream()
                .filter(page -> this.getMatcher().map(m -> m.apply(page, identifier)).orElse(false))
                .findFirst();
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private @NotNull ConcurrentList<P> getSiblings() {
        if (this.history.size() > 1) {
            P parent = this.history.get(this.history.size() - 2);

            if (parent instanceof Subpages<?> subpages)
                return (ConcurrentList<P>) subpages.getPages();
        }

        return this.items;
    }

    @Override
    public int getTotalPages() {
        return this.getSiblings().size();
    }

    /**
     * Whether next/previous sibling navigation is meaningful.
     */
    public boolean hasSiblingNavigation() {
        return true;
    }

    /**
     * Whether child page traversal is available for the current page.
     */
    @SuppressWarnings("unchecked")
    public boolean hasChildNavigation() {
        P current = this.getCurrentPage();
        return current instanceof Subpages<?> subpages && ((ConcurrentList<P>) subpages.getPages()).notEmpty();
    }

    /**
     * Changes the current page to a top-level page.
     *
     * @param page the page value
     */
    @Override
    public void gotoPage(@NotNull P page) {
        this.history.clear();
        this.history.add(page);
        this.setCacheUpdateRequired();
    }

    /**
     * Changes the current page to a top-level page by index.
     *
     * @param index the page index
     */
    public void gotoPage(int index) {
        this.gotoPage(this.items.get(NumberUtil.ensureRange(index, 0, this.items.size() - 1)));
    }

    @Override
    public void gotoNextPage() {
        ConcurrentList<P> siblings = this.getSiblings();
        int index = siblings.indexOf(this.getCurrentPage());
        int next = NumberUtil.ensureRange(index + 1, 0, siblings.size() - 1);
        this.history.set(this.history.size() - 1, siblings.get(next));
        this.setCacheUpdateRequired();
    }

    @Override
    public void gotoPreviousPage() {
        ConcurrentList<P> siblings = this.getSiblings();
        int index = siblings.indexOf(this.getCurrentPage());
        int previous = NumberUtil.ensureRange(index - 1, 0, siblings.size() - 1);
        this.history.set(this.history.size() - 1, siblings.get(previous));
        this.setCacheUpdateRequired();
    }

    /**
     * Navigates to the parent page by popping the history stack.
     */
    public void gotoParentPage() {
        if (this.hasPageHistory())
            this.history.removeLast();

        this.setCacheUpdateRequired();
    }

    /**
     * Navigates into a subpage of the current page using the given identifier.
     *
     * @param identifier the subpage option value
     */
    public void gotoSubPage(@NotNull I identifier) {
        this.history.add(this.getSubPage(identifier).orElseThrow(() -> new DiscordException("Unable to locate subpage identified by '%s'.", identifier)));
        this.setCacheUpdateRequired();
    }

    /**
     * Changes the current page to a top-level page using the given identifier.
     *
     * @param identifier the page option value
     */
    public void gotoTopLevelPage(@NotNull I identifier) {
        this.gotoPage(this.getPage(identifier).orElseThrow(() -> new DiscordException("Unable to locate page identified by '%s'.", identifier)));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getItems(), this.getMatcher(), this.getTransformer(), this.isCacheUpdateRequired(), this.getHistory());
    }

    @Override
    public boolean hasPageHistory() {
        return this.history.size() > 1;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<P, I> {

        private final ConcurrentList<P> pages = Concurrent.newList();
        private Optional<Function<P, I>> transformer = Optional.empty();
        private Optional<BiFunction<P, I, Boolean>> matcher = Optional.empty();

        public Builder<P, I> withMatcher(@Nullable BiFunction<P, I, Boolean> matcher) {
            return this.withMatcher(Optional.ofNullable(matcher));
        }

        public Builder<P, I> withMatcher(@NotNull Optional<BiFunction<P, I, Boolean>> matcher) {
            this.matcher = matcher;
            return this;
        }

        public Builder<P, I> withTransformer(@Nullable Function<P, I> transformer) {
            return this.withTransformer(Optional.ofNullable(transformer));
        }

        public Builder<P, I> withTransformer(@NotNull Optional<Function<P, I>> transformer) {
            this.transformer = transformer;
            return this;
        }

        /**
         * Add pages to the {@link HistoryHandler}.
         *
         * @param pages variable number of pages to add
         */
        @SafeVarargs
        public final Builder<P, I> withPages(@NotNull P... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add pages to the {@link HistoryHandler}.
         *
         * @param pages collection of pages to add
         */
        public Builder<P, I> withPages(@NotNull Iterable<P> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        public @NotNull HistoryHandler<P, I> build() {
            return new HistoryHandler<>(
                this.pages.toUnmodifiableList(),
                this.matcher,
                this.transformer
            );
        }

    }

}
