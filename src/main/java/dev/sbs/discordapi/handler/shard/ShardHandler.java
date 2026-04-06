package dev.sbs.discordapi.handler.shard;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.util.DiscordReference;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Guild;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Manager for Discord gateway shards, providing lookup by shard ID or
 * guild, enumeration of all active shards, and access to the total shard
 * count.
 *
 * @see Shard
 */
public final class ShardHandler extends DiscordReference {

    /**
     * Constructs a new {@code ShardHandler} with the given bot instance.
     *
     * @param discordBot the bot this handler belongs to
     */
    public ShardHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /**
     * Returns the shard with the given ID, if it exists.
     *
     * @param shardId the zero-based shard index
     * @return an optional containing the shard, or empty if no shard exists
     *         for the given ID
     */
    public @NotNull Optional<Shard> getShard(int shardId) {
        return this.getShard(this.getDiscordBot().getGateway(), shardId);
    }

    /**
     * Returns the total number of shards configured for this bot's gateway
     * connection.
     *
     * @return the shard count
     */
    public int getShardCount() {
        return this.getDiscordBot().getGateway().getGatewayClientGroup().getShardCount();
    }

    /**
     * Returns the shard responsible for the given guild.
     *
     * @param guild the guild to look up
     * @return an optional containing the shard, or empty if no matching
     *         shard exists
     */
    public @NotNull Optional<Shard> getShardOfGuild(@NotNull Guild guild) {
        return this.getShardOfGuild(guild.getId());
    }

    /**
     * Returns the shard responsible for the given guild ID.
     *
     * @param guildId the guild snowflake to look up
     * @return an optional containing the shard, or empty if no matching
     *         shard exists
     */
    public @NotNull Optional<Shard> getShardOfGuild(@NotNull Snowflake guildId) {
        return this.getShard(
            this.getDiscordBot().getGateway(),
            this.getDiscordBot().getGateway().getGatewayClientGroup().computeShardIndex(guildId)
        );
    }

    /**
     * Returns all active shards for this bot's gateway connection.
     *
     * @return a list of all available shards
     */
    public @NotNull ConcurrentList<Shard> getShards() {
        return IntStream.range(0, this.getShardCount() + 1)
            .mapToObj(shardId -> this.getShard(this.getDiscordBot().getGateway(), shardId))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Concurrent.toList());
    }

    /**
     * Resolves a shard by ID from the given gateway client.
     *
     * @param gatewayDiscordClient the gateway client to query
     * @param shardId the zero-based shard index
     * @return an optional containing the shard, or empty if no gateway
     *         client exists for the given ID
     */
    private @NotNull Optional<Shard> getShard(@NotNull GatewayDiscordClient gatewayDiscordClient, int shardId) {
        return gatewayDiscordClient.getGatewayClient(shardId).map(gatewayClient -> new Shard(this.getDiscordBot(), gatewayClient));
    }

}
