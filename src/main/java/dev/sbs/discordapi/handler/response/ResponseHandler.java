package dev.sbs.discordapi.handler.response;

import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.ConcurrentList;
import discord4j.common.util.Snowflake;
import org.jetbrains.annotations.NotNull;

/**
 * Thread-safe registry of active {@link CachedResponse} instances backed
 * by a {@link ConcurrentList}.
 *
 * <p>
 * Each cached entry associates a {@link Response} with its Discord
 * channel, user, and message {@link Snowflake} identifiers so that
 * component interactions and scheduled updates can locate and modify
 * the correct response.
 */
public class ResponseHandler extends ConcurrentList<CachedResponse> {

    /**
     * Creates a new {@link CachedResponse}, adds it to this handler, and
     * returns it.
     *
     * @param channelId the Discord channel snowflake of the response message
     * @param userId the snowflake of the user who owns the response
     * @param messageId the Discord message snowflake of the response
     * @param response the response to cache
     * @return the newly created cached response entry
     */
    public CachedResponse createAndGet(@NotNull Snowflake channelId, @NotNull Snowflake userId, @NotNull Snowflake messageId, @NotNull Response response) {
        CachedResponse entry = new CachedResponse(channelId, userId, messageId, response);
        this.add(entry);
        return entry;
    }

}
