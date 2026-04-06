package dev.sbs.discordapi.handler.shard;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.util.DiscordReference;
import dev.simplified.reflection.Reflection;
import discord4j.common.close.CloseStatus;
import discord4j.core.DiscordClient;
import discord4j.core.shard.GatewayBootstrap;
import discord4j.gateway.GatewayClient;
import discord4j.rest.util.Multimap;
import discord4j.rest.util.RouteUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Wrapper around a single Discord gateway {@link GatewayClient} that
 * exposes connection state, latency, and lifecycle operations (start,
 * stop, restart) for an individual shard.
 *
 * @see ShardHandler
 */
@Getter
public class Shard extends DiscordReference {

    /** Underlying Discord4J gateway client for this shard. */
    private final @NotNull GatewayClient gatewayClient;

    /**
     * Constructs a new {@code Shard} wrapping the given gateway client.
     *
     * @param discordBot the bot this shard belongs to
     * @param gatewayClient the gateway client representing this shard
     */
    public Shard(@NotNull DiscordBot discordBot, @NotNull GatewayClient gatewayClient) {
        super(discordBot);
        this.gatewayClient = gatewayClient;
    }

    /**
     * Returns the underlying Discord REST client.
     *
     * @return the discord client
     */
    private @NotNull DiscordClient getClient() {
        return this.getDiscordBot().getClient();
    }

    /**
     * Checks whether this shard is currently connected to the Discord gateway.
     *
     * @return {@code true} if the shard is connected
     */
    public boolean isConnected() {
        return this.getGatewayClient().isConnected().blockOptional().orElse(false);
    }

    /**
     * Returns the most recent heartbeat round-trip latency for this shard.
     *
     * @return the gateway response time
     */
    public @NotNull Duration getResponseTime() {
        return this.getGatewayClient().getResponseTime();
    }

    /**
     * Stops and then restarts this shard's gateway connection.
     *
     * @return a mono that completes when the shard has reconnected
     */
    public @NotNull Mono<Void> restart() {
        return this.stop().then(this.start());
    }

    /**
     * Starts this shard's gateway connection if it is not already connected.
     *
     * @return a mono that completes when the connection is established, or
     *         immediately if the shard is already connected
     */
    @SuppressWarnings("unchecked")
    public @NotNull Mono<Void> start() {
        return !this.isConnected() ? this.getClient()
            .getGatewayService()
            .getGateway()
            .flatMap(gatewayData -> this.getGatewayClient().execute(
                RouteUtils.expandQuery(
                    gatewayData.url(),
                    new Reflection<>(GatewayBootstrap.class).invokeMethod(Multimap.class, this.getClient().gateway())
                )
            )) : Mono.empty();
    }

    /**
     * Stops this shard's gateway connection, allowing a future resume.
     *
     * @return a mono emitting the close status, or empty if already disconnected
     */
    public @NotNull Mono<CloseStatus> stop() {
        return this.stop(true);
    }

    /**
     * Stops this shard's gateway connection.
     *
     * @param allowResume {@code true} to permit the gateway to resume
     *                    the session on next connect, {@code false} to
     *                    force a full re-identify
     * @return a mono emitting the close status, or empty if already disconnected
     */
    public @NotNull Mono<CloseStatus> stop(boolean allowResume) {
        return this.isConnected() ? this.getGatewayClient().close(allowResume) : Mono.empty();
    }

}
