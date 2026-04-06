package dev.sbs.discordapi.handler.response;

import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.User;
import discord4j.discordjson.Id;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;

/**
 * Cached entry for a primary {@link Response} message, tracking its
 * lifecycle state (busy, deferred, last interaction time), associated
 * {@link ResponseFollowup} messages, and per-user active {@link Modal} dialogs.
 *
 * <p>
 * A cached response is considered {@linkplain #isActive() active} while
 * it is busy or its time-to-live has not elapsed since the last
 * interaction. Inactive responses are eligible for removal from the
 * {@link ResponseHandler}.
 *
 * @see ResponseEntry
 * @see ResponseFollowup
 * @see ResponseHandler
 */
@Getter
public final class CachedResponse implements ResponseEntry {

    /** Discord channel snowflake where the response message resides. */
    private final @NotNull Snowflake channelId;

    /** Discord user snowflake of the user who owns this response. */
    private final @NotNull Snowflake userId;

    /** Discord message snowflake of the response message. */
    private final @NotNull Snowflake messageId;

    /** Current (possibly updated) response state. */
    private @NotNull Response response;

    /** Snapshot of the response state as last sent to Discord. */
    private @NotNull Response currentResponse;

    /** Followup messages associated with this cached response. */
    private final @NotNull ConcurrentList<ResponseFollowup> followups = Concurrent.newList();

    /** Map of user snowflakes to their currently active modal dialogs. */
    private final @NotNull ConcurrentMap<Snowflake, Modal> activeModals = Concurrent.newMap();

    /** Epoch millisecond timestamp of the most recent user interaction. */
    private long lastInteract = System.currentTimeMillis();

    /** Whether this response is currently being processed. */
    private boolean busy;

    /** Whether this response's reply has been deferred. */
    private boolean deferred;

    /**
     * Constructs a new {@code CachedResponse} in the busy, non-deferred state.
     *
     * @param channelId the Discord channel snowflake
     * @param userId the user snowflake
     * @param messageId the message snowflake
     * @param response the response to cache
     */
    public CachedResponse(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        this.channelId = channelId;
        this.userId = userId;
        this.messageId = messageId;
        this.response = response;
        this.currentResponse = response;
        this.busy = true;
        this.deferred = false;
    }

    /**
     * Creates a new {@link ResponseFollowup} entry, adds it to this cached response,
     * and returns it.
     *
     * @param identifier the unique string identifier for the followup
     * @param channelId the Discord channel snowflake
     * @param userId the user snowflake
     * @param messageId the Discord message snowflake of the followup
     * @param response the followup response
     * @return a mono emitting the newly created followup
     */
    public Mono<ResponseFollowup> addFollowup(@NotNull String identifier, @NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        ResponseFollowup responseFollowup = new ResponseFollowup(identifier, channelId, userId, messageId, response);
        this.followups.add(responseFollowup);
        return Mono.just(responseFollowup);
    }

    /**
     * Checks whether a followup with the given identifier exists.
     *
     * @param identifier the followup identifier to search for
     * @return {@code true} if a matching followup exists
     */
    public boolean containsFollowup(@NotNull String identifier) {
        return this.getFollowups()
            .stream()
            .anyMatch(followup -> followup.getIdentifier().equals(identifier));
    }

    /**
     * Checks whether a followup with the given message snowflake exists.
     *
     * @param messageId the Discord message snowflake to search for
     * @return {@code true} if a matching followup exists
     */
    public boolean containsFollowup(@NotNull Snowflake messageId) {
        return this.getFollowups()
            .stream()
            .anyMatch(followup -> followup.getMessageId().equals(messageId));
    }

    /**
     * Finds the first followup matching the given identifier.
     *
     * @param identifier the followup identifier to search for
     * @return an optional containing the matching followup, or empty if
     *         none exists
     */
    public Optional<ResponseFollowup> findFollowup(@NotNull String identifier) {
        return this.getFollowups().findFirst(ResponseFollowup::getIdentifier, identifier);
    }

    /**
     * Finds the first followup matching the given message snowflake.
     *
     * @param messageId the Discord message snowflake to search for
     * @return an optional containing the matching followup, or empty if
     *         none exists
     */
    public Optional<ResponseFollowup> findFollowup(@NotNull Snowflake messageId) {
        return this.getFollowups().findFirst(ResponseFollowup::getMessageId, messageId);
    }

    /**
     * Removes the followup with the given identifier, if it exists.
     *
     * @param identifier the followup identifier to remove
     */
    public void removeFollowup(@NotNull String identifier) {
        this.followups.removeIf(followup -> followup.getIdentifier().equals(identifier));
    }

    /**
     * Removes the active modal dialog for the given user.
     *
     * @param user the user whose modal should be cleared
     */
    public void clearModal(@NotNull User user) {
        this.activeModals.remove(user.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CachedResponse entry = (CachedResponse) o;

        return Objects.equals(this.getChannelId(), entry.getChannelId())
            && Objects.equals(this.getUserId(), entry.getUserId())
            && Objects.equals(this.getMessageId(), entry.getMessageId())
            && Objects.equals(this.getResponse(), entry.getResponse())
            && Objects.equals(this.getCurrentResponse(), entry.getCurrentResponse())
            && this.getLastInteract() == entry.getLastInteract()
            && this.isBusy() == entry.isBusy()
            && this.isDeferred() == entry.isDeferred()
            && Objects.equals(this.getFollowups(), entry.getFollowups())
            && Objects.equals(this.getActiveModals(), entry.getActiveModals());
    }

    /**
     * Returns the active modal dialog for the given user, if one exists.
     *
     * @param user the user to look up
     * @return an optional containing the user's active modal, or empty if
     *         no modal is active
     */
    public @NotNull Optional<Modal> getUserModal(@NotNull User user) {
        return Optional.ofNullable(this.activeModals.getOrDefault(user.getId(), null));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getChannelId(), this.getUserId(), this.getMessageId(), this.getResponse(), this.getCurrentResponse(), this.getFollowups(), this.getActiveModals(), this.getLastInteract(), this.isBusy(), this.isDeferred());
    }

    /**
     * Checks whether this cached response or any of its followups match
     * the given message and user snowflakes.
     *
     * @param messageId the Discord message snowflake to match
     * @param userId the user snowflake to match
     * @return {@code true} if both the user matches and the message matches
     *         either this response or a followup
     */
    public boolean matchesMessage(@NotNull Snowflake messageId, @NotNull Snowflake userId) {
        return this.getUserId().equals(userId) && (this.getMessageId().equals(messageId) || this.containsFollowup(messageId));
    }

    /**
     * Checks whether this cached response or any of its followups match
     * the given message snowflake and user ID.
     *
     * @param messageId the Discord message snowflake to match
     * @param userId the raw user ID to match
     * @return {@code true} if both the user matches and the message matches
     *         either this response or a followup
     */
    public boolean matchesMessage(@NotNull Snowflake messageId, @NotNull Id userId) {
        return this.getUserId().asLong() == userId.asLong() && (this.getMessageId().equals(messageId) || this.containsFollowup(messageId));
    }

    /**
     * Checks whether this response is currently busy or has not yet
     * exceeded its time-to-live since the last interaction.
     *
     * @return {@code true} if the response is busy or not yet expired
     * @see #getLastInteract()
     * @see Response#getTimeToLive()
     */
    public boolean isActive() {
        return this.isBusy() || System.currentTimeMillis() < this.getLastInteract() + (this.getResponse().getTimeToLive() * 1000L);
    }

    @Override
    public boolean isFollowup() {
        return false;
    }

    /**
     * Returns the inverse of {@link #isActive()}.
     *
     * @return {@code true} if this response is no longer active
     */
    public boolean notActive() {
        return !this.isActive();
    }

    /**
     * Marks this response as busy, preventing it from being removed
     * from the {@link ResponseHandler}.
     */
    public void setBusy() {
        this.busy = true;
    }

    /**
     * Marks this response as deferred, indicating that the initial reply
     * acknowledgment has been sent to Discord.
     */
    public void setDeferred() {
        this.deferred = true;
    }

    /**
     * Associates a modal dialog with the given user for this cached response.
     *
     * @param user the user presenting the modal
     * @param modal the modal dialog to associate
     */
    public void setUserModal(@NotNull User user, @NotNull Modal modal) {
        this.activeModals.put(user.getId(), modal);
    }

    /**
     * Records the current time as the last interaction, synchronizes the
     * rendered snapshot, and clears the busy and deferred flags so the
     * response becomes eligible for expiration.
     *
     * @return a mono emitting this cached response after the update
     */
    public Mono<CachedResponse> updateLastInteract() {
        return Mono.fromRunnable(() -> {
            this.currentResponse = this.response;
            this.response.setNoCacheUpdateRequired();
            this.lastInteract = System.currentTimeMillis();
            this.busy = false;
            this.deferred = false;
        });
    }

    /**
     * Replaces this entry's response with the given updated response.
     *
     * @param response the new response state
     * @return a mono emitting this cached response
     */
    public Mono<CachedResponse> updateResponse(@NotNull Response response) {
        this.response = response;
        return Mono.just(this);
    }

}
