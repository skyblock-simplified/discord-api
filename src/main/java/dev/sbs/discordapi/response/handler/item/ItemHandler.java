package dev.sbs.discordapi.response.handler.item;

import dev.sbs.discordapi.response.handler.FilterHandler;
import dev.sbs.discordapi.response.handler.OutputHandler;
import dev.sbs.discordapi.response.handler.SearchHandler;
import dev.sbs.discordapi.response.handler.SortHandler;
import dev.sbs.discordapi.response.page.Paging;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Manages a paginated collection of items with sort, filter, and search capabilities.
 *
 * <p>
 * Provides navigation through pages of items and exposes handlers for sorting, filtering,
 * and searching. The default implementation is {@link EmbedItemHandler}, which renders
 * items as embed fields.
 *
 * @param <T> the item type
 * @see EmbedItemHandler
 * @see OutputHandler
 * @see Paging
 */
public interface ItemHandler<T> extends OutputHandler<T>, Paging<Integer> {

    /**
     * Creates a new component item handler builder.
     *
     * @param <T> the item type
     * @return a new {@link ComponentItemHandler.Builder}
     */
    static <T> @NotNull ComponentItemHandler.Builder<T> component() {
        return ComponentItemHandler.builder();
    }

    /**
     * Creates a new embed item handler builder.
     *
     * @param <T> the item type
     * @return a new {@link EmbedItemHandler.Builder}
     */
    static <T> @NotNull EmbedItemHandler.Builder<T> embed() {
        return EmbedItemHandler.builder();
    }

    /** Navigates to the first item page. */
    void gotoFirstItemPage();

    /** Navigates to the last item page. */
    void gotoLastItemPage();

    /** Whether there is a next item page. */
    boolean hasNextItemPage();

    /** Whether there is a previous item page. */
    boolean hasPreviousItemPage();

    /** The sort handler managing item ordering. */
    @NotNull SortHandler<T> getSortHandler();

    /** The filter handler managing item filtering. */
    @NotNull FilterHandler<T> getFilterHandler();

    /** The search handler managing item search. */
    @NotNull SearchHandler<T> getSearchHandler();

    /** The cached list of items after filtering and sorting. */
    @NotNull ConcurrentList<T> getCachedFilteredItems();

    /**
     * The cached static items with variables applied from the current pagination state.
     *
     * <p>
     * Static items are re-processed on each cache refresh so that template
     * placeholders (e.g. {@code {FILTERED_SIZE}}, {@code {START_INDEX}}) reflect the
     * current pagination variables from {@link #getVariables()}.
     *
     * @return the variable-processed static items
     */
    @NotNull ConcurrentList<?> getCachedStaticItems();

    /** The mutable variable map used for template evaluation. */
    @NotNull ConcurrentMap<String, Object> getVariables();

    /** The number of items displayed per page. */
    int getAmountPerPage();

    /** Whether the editor mode is enabled. */
    boolean isEditorEnabled();

    /**
     * Rendering style for embed-based item display.
     *
     * @see EmbedItemHandler
     */
    @Getter
    @RequiredArgsConstructor
    enum FieldStyle {

        /**
         * Displays each item as a single field without overriding inline state.
         */
        DEFAULT(false),
        /**
         * Displays each item as a single field, overriding inline state to non-inline.
         */
        FIELD(false),
        /**
         * Displays each item as a single inline field.
         */
        FIELD_INLINE(true),
        /**
         * Displays all items as a single list field.
         */
        LIST(false);

        /** Whether fields rendered in this style are inline. */
        private final boolean inline;

    }

}
