package dev.sbs.discordapi.response.handler;

import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;

/**
 * Contract for a handler that exposes a list of output items and a cache-invalidation flag.
 *
 * @param <T> the item type
 */
public interface OutputHandler<T> {

    /** The source items managed by this handler. */
    @NotNull ConcurrentList<T> getItems();

    /** Whether the cached output needs to be rebuilt. */
    boolean isCacheUpdateRequired();

    /** Whether the item list is empty. */
    default boolean isEmpty() {
        return this.getItems().isEmpty();
    }

    /** Whether the item list is non-empty. */
    default boolean notEmpty() {
        return this.getItems().notEmpty();
    }

    /** Marks the cache as requiring a rebuild. */
    default void setCacheUpdateRequired() {
        this.setCacheUpdateRequired(true);
    }

    /**
     * Sets whether the cache requires a rebuild.
     *
     * @param cacheUpdateRequired {@code true} to mark the cache as stale
     */
    void setCacheUpdateRequired(boolean cacheUpdateRequired);

}
