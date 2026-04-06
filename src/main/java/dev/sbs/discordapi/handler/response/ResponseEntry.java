package dev.sbs.discordapi.handler.response;

import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import discord4j.core.object.reaction.Reaction;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * A cached entry associating a {@link Response} with its Discord channel, user,
 * and message {@link Snowflake} identifiers.
 *
 * <p>
 * Implementations track the last-rendered state ({@link #getCurrentResponse()})
 * alongside the latest state ({@link #getResponse()}) to support dirty-checking
 * via {@link #isModified()}, and provide reactive helpers for updating
 * attachments and reactions on the underlying Discord message.
 *
 * @see CachedResponse
 * @see ResponseFollowup
 */
public interface ResponseEntry {

    /** Discord channel snowflake where the response message resides. */
    @NotNull Snowflake getChannelId();

    /** Discord user snowflake of the user who owns this response. */
    @NotNull Snowflake getUserId();

    /** Discord message snowflake of the response message. */
    @NotNull Snowflake getMessageId();

    /** Current (possibly updated) response state. */
    @NotNull Response getResponse();

    /** Snapshot of the response state as last sent to Discord. */
    @NotNull Response getCurrentResponse();

    /** Whether this entry represents a followup message. */
    boolean isFollowup();

    /**
     * Checks whether the current response state differs from the
     * last-rendered snapshot, or the response has flagged itself
     * as requiring a cache update.
     *
     * @return {@code true} if the response has been modified since last render
     */
    default boolean isModified() {
        return !this.getCurrentResponse().equals(this.getResponse()) || this.getResponse().isCacheUpdateRequired();
    }

    /**
     * Updates the attachment references on this entry's response from
     * the given Discord message.
     *
     * @param message the Discord message containing updated attachments
     * @return a mono that completes when attachments have been updated
     */
    default Mono<CachedResponse> updateAttachments(@NotNull Message message) {
        return Mono.fromRunnable(() -> this.getResponse().updateAttachments(message));
    }

    /**
     * Synchronizes the Discord message's reactions with those defined on
     * the current response page, removing stale reactions and adding
     * missing ones.
     *
     * @param message the Discord message to update reactions on
     * @return a mono emitting this entry when reaction updates complete
     */
    default Mono<ResponseEntry> updateReactions(@NotNull Message message) {
        return Mono.just(message)
            .checkpoint("ResponseHandler#updateReactions Processing")
            .flatMap(msg -> {
                ConcurrentList<Emoji> newReactions = this.getResponse()
                    .getHistoryHandler()
                    .getCurrentPage()
                    .getReactions();

                ConcurrentList<Emoji> currentReactions = msg.getReactions()
                    .stream()
                    .filter(Reaction::selfReacted)
                    .map(Reaction::getEmoji)
                    .map(Emoji::of)
                    .collect(Concurrent.toList());

                Mono<Void> mono = Mono.empty();

                if (currentReactions.stream().anyMatch(messageEmoji -> !newReactions.contains(messageEmoji)))
                    mono = msg.removeAllReactions();

                return mono.then(Mono.when(
                    newReactions.stream()
                        .map(emoji -> msg.addReaction(emoji.getD4jReaction()))
                        .collect(Concurrent.toList())
                ));
            })
            .thenReturn(this);
    }

}
