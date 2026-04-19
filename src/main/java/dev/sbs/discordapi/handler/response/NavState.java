package dev.sbs.discordapi.handler.response;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.persistence.type.GsonType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Optional;

/**
 * Serializable snapshot of a {@link dev.sbs.discordapi.response.Response Response}'s
 * mutable navigation state, persisted alongside each persistent response row
 * so that the current page, item page, and history stack survive bot
 * restarts.
 *
 * <p>
 * Only the mutable navigation coordinates are stored here - the response's
 * structural content (embeds, components, page definitions) is reconstructed
 * on hydration by invoking the associated {@link dev.sbs.discordapi.response.PersistentResponse
 * PersistentResponse} builder method. This minimises database writes and
 * avoids serializing un-serializable interaction lambdas.
 *
 * <p>
 * Stored in the {@code nav_state_json} column of the persistent response
 * table, serialized via Gson. Marked with {@link GsonType} so the persistence
 * library auto-registers a {@code GsonJsonType<NavState>} when this class
 * appears as a field type on a {@link dev.simplified.persistence.JpaModel}.
 */
@Getter
@GsonType
@RequiredArgsConstructor
public final class NavState implements Serializable {

    /** Identifier of the page currently displayed, if any. */
    private final @NotNull Optional<String> currentPageId;

    /** Zero-based index into the current page's paginated item list. */
    private final int currentItemPage;

    /** Ordered history of visited page identifiers for back-navigation. */
    private final @NotNull ConcurrentList<String> pageHistory;

    /** Returns an empty navigation state used as the default for new responses. */
    public static @NotNull NavState empty() {
        return new NavState(Optional.empty(), 0, Concurrent.newList());
    }

    /** Returns a mutable copy of this state for incremental updates. */
    public @NotNull NavState withCurrentPageId(@NotNull String pageId) {
        ConcurrentList<String> history = Concurrent.newList(this.pageHistory);
        return new NavState(Optional.of(pageId), this.currentItemPage, history);
    }

    /** Returns a copy with the given item page index. */
    public @NotNull NavState withCurrentItemPage(int itemPage) {
        ConcurrentList<String> history = Concurrent.newList(this.pageHistory);
        return new NavState(this.currentPageId, itemPage, history);
    }

}
