package dev.sbs.discordapi.util;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.response.Emoji;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.linked.ConcurrentLinkedMap;
import dev.simplified.util.StringUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.discordjson.json.ApplicationCommandInteractionData;
import discord4j.discordjson.json.ApplicationCommandInteractionOptionData;
import discord4j.discordjson.json.ApplicationTeamMemberData;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract base class for any component that requires access to the {@link DiscordBot}
 * instance, providing common utilities for annotation lookup, emoji resolution,
 * permission checking, and owner/developer identification.
 *
 * <p>
 * Subclasses include {@link DiscordCommand}, listeners, and handlers.
 */
public abstract class DiscordReference {

    /**
     * The bot instance this reference belongs to.
     */
    @Getter(AccessLevel.PROTECTED)
    private final @NotNull DiscordBot discordBot;

    /**
     * The logger for this reference instance.
     */
    @Getter private final @NotNull Logger log;

    /**
     * Returns the bot instance cast to the specified subclass.
     *
     * @param <B> the bot subclass type
     * @param botType the bot subclass to cast to
     * @return the bot instance as the specified type
     */
    protected final <B extends DiscordBot> @NotNull B getDiscordBot(@NotNull Class<B> botType) {
        return botType.cast(this.discordBot);
    }

    /**
     * Constructs a new reference bound to the given bot instance.
     *
     * @param discordBot the bot instance
     */
    protected DiscordReference(@NotNull DiscordBot discordBot) {
        this.discordBot = discordBot;
        this.log = LogManager.getLogger(this);
    }

    /**
     * Returns the annotation of the given type if it is present on the specified class.
     *
     * @param aClass the annotation type to look for
     * @param tClass the class to inspect
     * @param <T> the annotation type
     * @return the annotation instance, or empty if not present
     */
    protected final @NotNull <T extends Annotation> Optional<T> getAnnotation(@NotNull Class<T> aClass, @NotNull Class<?> tClass) {
        return tClass.isAnnotationPresent(aClass) ? Optional.of(tClass.getAnnotation(aClass)) : java.util.Optional.empty();
    }

    /**
     * Renders a progress bar of the given color for the specified percentage.
     *
     * @param progressBar the color variant to render
     * @param percentage the completion percentage (0-100)
     * @return an unmodifiable list of five emojis representing the progress bar
     */
    protected final @NotNull ConcurrentList<Emoji> buildProgressBar(@NotNull ProgressBar progressBar, double percentage) {
        return progressBar.render(percentage, this.getDiscordBot().getEmojiHandler().getEmojis());
    }

    /**
     * Looks up a custom emoji by name from the bot's emoji handler (case-insensitive).
     *
     * @param name the emoji name to search for
     * @return the matching emoji, or empty if not found
     */
    protected final @NotNull Optional<Emoji> getEmoji(@NotNull String name) {
        return this.getDiscordBot()
            .getEmojiHandler()
            .getEmojis()
            .matchFirst(emoji -> emoji.getName().equalsIgnoreCase(name));
    }

    /**
     * Checks whether the given interaction data matches the specified command's
     * {@link Structure} name, parent, and group hierarchy.
     *
     * @param command the command to match against
     * @param commandData the interaction data from Discord
     * @return {@code true} if the interaction data corresponds to the command
     */
    protected final boolean matchesInteractionData(@NotNull DiscordCommand<?> command, @NotNull ApplicationCommandInteractionData commandData) {
        if (commandData.name().isAbsent())
            return false;

        String compareName = commandData.name().get();

        if (StringUtil.isNotEmpty(command.getStructure().parent().name())) {
            if (commandData.options().isAbsent() || commandData.options().get().isEmpty())
                return false;

            List<ApplicationCommandInteractionOptionData> options = commandData.options().get();
            ApplicationCommandInteractionOptionData option = options.get(0);

            if (!compareName.equals(command.getStructure().parent().name()))
                return false;

            if (options.get(0).type() > 2)
                return false;

            if (StringUtil.isNotEmpty(command.getStructure().group().name())) {
                if (!option.name().equals(command.getStructure().group().name()))
                    return false;

                if (option.options().isAbsent() || option.options().get().isEmpty())
                    return false;

                options = option.options().get();
                option = options.get(0);
            }

            compareName = option.name();
        }

        return compareName.equals(command.getStructure().name());
    }

    // --- Permissions ---

    /**
     * Returns a map of guild-level permissions to their resolved boolean state for a user.
     *
     * @param userId the user to check permissions for
     * @param guild the guild, or empty for no guild context
     * @param permissions the permissions to check
     * @return a map of each permission to {@code true} if granted, {@code false} otherwise
     */
    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(@NotNull Snowflake userId, @NotNull Optional<Guild> guild, @NotNull Permission... permissions) {
        return this.getGuildPermissionMap(userId, guild, Arrays.asList(permissions));
    }

    /**
     * Returns a map of guild-level permissions to their resolved boolean state for a user.
     *
     * @param userId the user to check permissions for
     * @param optionalGuild the guild, or empty for no guild context
     * @param permissions the permissions to check
     * @return a map of each permission to {@code true} if granted, {@code false} otherwise
     */
    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getGuildPermissionMap(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Iterable<Permission> permissions) {
        return optionalGuild.map(guild -> guild.getMemberById(userId))
            .orElse(Mono.empty())
            .flatMap(Member::getBasePermissions)
            .map(permissionSet -> getPermissionMap(permissionSet, permissions))
            .blockOptional()
            .orElse(Concurrent.newLinkedMap());
    }

    /**
     * Returns a map of channel-level effective permissions to their resolved boolean state for a user.
     *
     * @param userId the user to check permissions for
     * @param channel a mono emitting the guild channel
     * @param permissions the permissions to check
     * @return a map of each permission to {@code true} if granted, {@code false} otherwise
     */
    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Permission... permissions) {
        return this.getChannelPermissionMap(userId, channel, Arrays.asList(permissions));
    }

    /**
     * Returns a map of channel-level effective permissions to their resolved boolean state for a user.
     *
     * @param userId the user to check permissions for
     * @param channel a mono emitting the guild channel
     * @param permissions the permissions to check
     * @return a map of each permission to {@code true} if granted, {@code false} otherwise
     */
    protected final @NotNull ConcurrentLinkedMap<Permission, Boolean> getChannelPermissionMap(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Iterable<Permission> permissions) {
        return channel.flatMap(chl -> chl.getEffectivePermissions(userId))
            .map(permissionSet -> getPermissionMap(permissionSet, permissions))
            .blockOptional()
            .orElse(Concurrent.newLinkedMap());
    }

    /**
     * Builds a permission-to-boolean map by checking each requested permission against
     * the given permission set.
     *
     * @param permissionSet the resolved permission set to check against
     * @param permissions the permissions to look up
     * @return a map of each permission to {@code true} if contained in the set
     */
    private static @NotNull ConcurrentLinkedMap<Permission, Boolean> getPermissionMap(@NotNull PermissionSet permissionSet, @NotNull Iterable<Permission> permissions) {
        // Set Default Permissions
        ConcurrentLinkedMap<Permission, Boolean> permissionMap = Concurrent.newLinkedMap();
        permissions.forEach(permission -> permissionMap.put(permission, false));

        // Check Permissions
        for (Permission permission : permissions)
            permissionMap.put(permission, permissionSet.contains(permission));

        return permissionMap;
    }

    /**
     * Checks whether the user has all of the specified channel-level permissions.
     *
     * @param userId the user to check
     * @param channel a mono emitting the guild channel
     * @param permissions the permissions to verify
     * @return {@code true} if all permissions are granted
     */
    protected final boolean hasChannelPermissions(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Permission... permissions) {
        return this.hasChannelPermissions(userId, channel, Concurrent.newList(permissions));
    }

    /**
     * Checks whether the user has all of the specified channel-level permissions.
     *
     * @param userId the user to check
     * @param channel a mono emitting the guild channel
     * @param permissions the permissions to verify
     * @return {@code true} if all permissions are granted
     */
    protected final boolean hasChannelPermissions(@NotNull Snowflake userId, @NotNull Mono<GuildChannel> channel, @NotNull Iterable<Permission> permissions) {
        return this.getChannelPermissionMap(userId, channel, permissions).stream().allMatch(Map.Entry::getValue);
    }

    /**
     * Checks whether the user has all of the specified guild-level permissions.
     *
     * @param userId the user to check
     * @param optionalGuild the guild, or empty for no guild context
     * @param permissions the permissions to verify
     * @return {@code true} if all permissions are granted
     */
    protected final boolean hasGuildPermissions(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Permission... permissions) {
        return this.hasGuildPermissions(userId, optionalGuild, Arrays.asList(permissions));
    }

    /**
     * Checks whether the user has all of the specified guild-level permissions.
     *
     * @param userId the user to check
     * @param optionalGuild the guild, or empty for no guild context
     * @param permissions the permissions to verify
     * @return {@code true} if all permissions are granted
     */
    protected final boolean hasGuildPermissions(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild, @NotNull Iterable<Permission> permissions) {
        return this.getGuildPermissionMap(userId, optionalGuild, permissions).stream().allMatch(Map.Entry::getValue);
    }

    /**
     * Checks whether the given user is a member of the application's owner team,
     * or is the application owner if no team is configured.
     *
     * @param userId the user to check
     * @return {@code true} if the user is a developer
     */
    protected final boolean isDeveloper(@NotNull Snowflake userId) {
        return this.getDiscordBot()
            .getClient()
            .getApplicationInfo()
            .cache()
            .blockOptional()
            .map(applicationInfoData -> applicationInfoData.team()
                .map(applicationTeamData -> {
                    for (ApplicationTeamMemberData teamMemberData : applicationTeamData.members()) {
                        if (teamMemberData.user().id().asLong() == userId.asLong())
                            return true;
                    }

                    return false;
                })
                .orElse(applicationInfoData.owner().map(userData -> userData.id().asLong() == userId.asLong()).toOptional().orElse(false))
            )
            .orElse(false);
    }

    /**
     * Checks whether the given user is the owner of the specified guild.
     *
     * @param userId the user to check
     * @param guild the guild to check ownership of, or {@code null}
     * @return {@code true} if the user is the guild owner
     */
    protected final boolean isGuildOwner(@NotNull Snowflake userId, @Nullable Guild guild) {
        return this.isGuildOwner(userId, Optional.ofNullable(guild));
    }

    /**
     * Checks whether the given user is the owner of the specified guild.
     *
     * @param userId the user to check
     * @param optionalGuild the guild, or empty
     * @return {@code true} if the user is the guild owner
     */
    protected final boolean isGuildOwner(@NotNull Snowflake userId, @NotNull Optional<Guild> optionalGuild) {
        return optionalGuild.map(guild -> guild.getOwnerId().equals(userId)).orElse(false);
    }

}
