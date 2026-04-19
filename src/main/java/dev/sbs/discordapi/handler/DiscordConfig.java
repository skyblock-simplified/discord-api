package dev.sbs.discordapi.handler;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.event.BotEvent;
import dev.sbs.discordapi.listener.BotEventListener;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.persistence.JpaConfig;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.reflection.info.ResourceInfo;
import dev.simplified.util.Logging;
import dev.simplified.yaml.annotation.Flag;
import discord4j.core.event.domain.Event;
import discord4j.core.object.presence.ClientPresence;
import discord4j.core.shard.MemberRequestFilter;
import discord4j.gateway.ShardInfo;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.util.AllowedMentions;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

@Getter
@AllArgsConstructor
@SuppressWarnings("rawtypes")
public final class DiscordConfig {

    @Flag(secure = true)
    private final @NotNull String token;
    private final long mainGuildId;
    private final @NotNull Optional<Long> logChannelId;
    @Flag(secure = true)
    private final @NotNull Optional<String> sentryDsn;
    private final @NotNull Optional<JpaConfig> jpaConfig;
    private final ConcurrentSet<Class<? extends DiscordListener>> listeners;
    private final ConcurrentSet<Class<? extends BotEventListener>> botEventListeners;
    private final ConcurrentSet<Class<DiscordCommand>> commands;
    private final ConcurrentSet<ResourceInfo> emojis;
    private final @NotNull AllowedMentions allowedMentions;
    private final @NotNull IntentSet intents;
    @Getter(AccessLevel.NONE)
    private final @NotNull Function<ShardInfo, ClientPresence> clientPresence;
    private final @NotNull MemberRequestFilter memberRequestFilter;
    private final @NotNull Logging.Level logLevel;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public @NotNull ClientPresence getClientPresence(@NotNull ShardInfo shardInfo) {
        return this.clientPresence.apply(shardInfo);
    }

    public static class Builder {

        // Settings
        @BuildFlag(nonNull = true)
        private Optional<String> token = Optional.empty();
        @BuildFlag(nonNull = true)
        private Optional<Long> mainGuildId = Optional.empty();
        private Optional<Long> logChannelId = Optional.empty();
        @Flag(secure = true)
        private Optional<String> sentryDsn = Optional.empty();
        private Optional<JpaConfig> jpaConfig = Optional.empty();

        // Collections
        private ConcurrentSet<Class<? extends DiscordListener>> listeners = Concurrent.newSet();
        private ConcurrentSet<Class<? extends BotEventListener>> botEventListeners = Concurrent.newSet();
        private ConcurrentSet<Class<DiscordCommand>> commands = Concurrent.newSet();
        private ConcurrentSet<ResourceInfo> emojis = Concurrent.newSet();
        @BuildFlag(nonNull = true)
        private AllowedMentions allowedMentions = AllowedMentions.builder().build();
        @BuildFlag(nonNull = true)
        private IntentSet intents = IntentSet.nonPrivileged();
        @BuildFlag(nonNull = true)
        private Function<ShardInfo, ClientPresence> clientPresence = __ -> ClientPresence.online();
        @BuildFlag(nonNull = true)
        private MemberRequestFilter memberRequestFilter = MemberRequestFilter.all();
        @BuildFlag(nonNull = true)
        private Logging.Level logLevel = Logging.Level.WARN;

        public Builder withAllowedMentions(@NotNull AllowedMentions allowedMentions) {
            this.allowedMentions = allowedMentions;
            return this;
        }

        public Builder withClientPresence(@NotNull ClientPresence clientPresence) {
            return this.withClientPresence(__ -> clientPresence);
        }

        public Builder withClientPresence(@NotNull Function<ShardInfo, ClientPresence> clientPresence) {
            this.clientPresence = clientPresence;
            return this;
        }

        public Builder withCommands(@NotNull String packagePath) {
            this.commands.addAll(
                Reflection.getResources()
                    .filterPackage(packagePath)
                    .getTypesOf(DiscordCommand.class)
            );
            return this;
        }

        public Builder withCommands(@NotNull Class<DiscordCommand>... commands) {
            this.commands.addAll(commands);
            return this;
        }

        public Builder withCommands(@NotNull Iterable<Class<DiscordCommand>> commands) {
            commands.forEach(this.commands::add);
            return this;
        }

        public Builder withEmojis(@NotNull ResourceInfo... emojis) {
            this.emojis.addAll(emojis);
            return this;
        }

        public Builder withEmojis(@NotNull Iterable<ResourceInfo> emojis) {
            emojis.forEach(this.emojis::add);
            return this;
        }

        public Builder withLogChannelId(long debugChannelId) {
            return this.withLogChannelId(Optional.of(debugChannelId));
        }

        public Builder withLogChannelId(Optional<Long> debugChannelId) {
            this.logChannelId = debugChannelId;
            return this;
        }

        public Builder withSentryDsn(@NotNull String sentryDsn) {
            return this.withSentryDsn(Optional.of(sentryDsn));
        }

        public Builder withSentryDsn(@NotNull Optional<String> sentryDsn) {
            this.sentryDsn = sentryDsn;
            return this;
        }

        public Builder withJpaConfig(@Nullable JpaConfig jpaConfig) {
            return this.withJpaConfig(Optional.ofNullable(jpaConfig));
        }

        public Builder withJpaConfig(@NotNull Optional<JpaConfig> jpaConfig) {
            this.jpaConfig = jpaConfig;
            return this;
        }

        public Builder withDisabledIntents(@NotNull IntentSet disabledIntents) {
            this.intents = IntentSet.all().andNot(disabledIntents);
            return this;
        }

        public Builder withEnabledIntents(@NotNull IntentSet enabledIntents) {
            this.intents = enabledIntents;
            return this;
        }

        public Builder withListeners(@NotNull String packagePath) {
            this.listeners.addAll(
                Reflection.getResources()
                    .filterPackage(packagePath)
                    .getSubtypesOf(DiscordListener.class)
            );
            this.botEventListeners.addAll(
                Reflection.getResources()
                    .filterPackage(packagePath)
                    .getSubtypesOf(BotEventListener.class)
            );
            return this;
        }

        public Builder withListeners(@NotNull Class<? extends DiscordListener<? extends Event>>... listeners) {
            this.listeners.addAll(listeners);
            return this;
        }

        public Builder withListeners(@NotNull Iterable<Class<? extends DiscordListener<? extends Event>>> listeners) {
            listeners.forEach(this.listeners::add);
            return this;
        }

        public Builder withBotEventListeners(@NotNull Class<? extends BotEventListener<? extends BotEvent>>... botEventListeners) {
            this.botEventListeners.addAll(botEventListeners);
            return this;
        }

        public Builder withBotEventListeners(@NotNull Iterable<Class<? extends BotEventListener<? extends BotEvent>>> botEventListeners) {
            botEventListeners.forEach(this.botEventListeners::add);
            return this;
        }

        public Builder withLogLevel(@NotNull Logging.Level logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder withMainGuildId(long mainGuildId) {
            return this.withMainGuildId(Optional.of(mainGuildId));
        }

        public Builder withMainGuildId(@NotNull Optional<Long> mainGuildId) {
            this.mainGuildId = mainGuildId;
            return this;
        }

        public Builder withMemberRequestFilter(@NotNull MemberRequestFilter memberRequestFilter) {
            this.memberRequestFilter = memberRequestFilter;
            return this;
        }

        public Builder withToken(@NotNull String token) {
            return this.withToken(Optional.of(token));
        }

        public Builder withToken(@NotNull Optional<String> token) {
            this.token = token;
            return this;
        }

        public @NotNull DiscordConfig build() {
            Reflection.validateFlags(this);

            return new DiscordConfig(
                this.token.orElseThrow(),
                this.mainGuildId.orElseThrow(),
                this.logChannelId,
                this.sentryDsn,
                this.jpaConfig,
                this.listeners.toUnmodifiable(),
                this.botEventListeners.toUnmodifiable(),
                this.commands.toUnmodifiable(),
                this.emojis.toUnmodifiable(),
                this.allowedMentions,
                this.intents,
                this.clientPresence,
                this.memberRequestFilter,
                this.logLevel
            );
        }

    }

}
