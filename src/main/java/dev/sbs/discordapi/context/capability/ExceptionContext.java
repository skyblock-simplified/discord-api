package dev.sbs.discordapi.context.capability;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.scope.CommandContext;
import dev.sbs.discordapi.handler.exception.ExceptionHandler;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.event.domain.guild.GuildDeleteEvent;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.event.domain.message.ReactionEvent;
import discord4j.core.event.domain.message.ReactionUserEmojiEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Capability interface for contexts that carry exception information, decorating an
 * existing {@link EventContext} with error metadata used by the {@link ExceptionHandler}
 * to report and log errors that occur during event processing.
 *
 * <p>
 * All {@link EventContext} methods delegate to the wrapped {@link #getEventContext()
 * eventContext}, while {@link #getException()} and {@link #getTitle()} provide the
 * error-specific metadata.
 *
 * @param <T> the Discord4J {@link Event} type of the wrapped context
 * @see ExceptionHandler
 */
public interface ExceptionContext<T extends Event> extends EventContext<T> {

    /** {@inheritDoc} */
    @Override
    default Mono<MessageChannel> getChannel() {
        return this.getEventContext().getChannel();
    }

    /** {@inheritDoc} */
    default @NotNull Snowflake getChannelId() {
        return this.getEventContext().getChannelId();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull T getEvent() {
        return this.getEventContext().getEvent();
    }

    /** The original {@link EventContext} that was being processed when the exception occurred. */
    @NotNull EventContext<T> getEventContext();

    /** The {@link Throwable} that was caught during event processing. */
    @NotNull Throwable getException();

    /** {@inheritDoc} */
    @Override
    default Mono<Guild> getGuild() {
        return this.getEventContext().getGuild();
    }

    /** {@inheritDoc} */
    @Override
    default Optional<Snowflake> getGuildId() {
        return this.getEventContext().getGuildId();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull User getInteractUser() {
        return this.getEventContext().getInteractUser();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull Snowflake getInteractUserId() {
        return this.getEventContext().getInteractUserId();
    }

    /** {@inheritDoc} */
    @Override
    default Mono<Void> reply(@NotNull Response response) {
        return this.getEventContext().reply(response);
    }

    /** A human-readable title describing the category of the exception. */
    @NotNull String getTitle();

    /**
     * Creates a new {@link ExceptionContext} wrapping a {@link CommandContext} with the
     * default title {@code "Command Exception"}.
     *
     * @param discordBot the bot instance
     * @param context the command context in which the exception occurred
     * @param throwable the caught exception
     * @return a new exception context
     */
    static @NotNull ExceptionContext<?> of(@NotNull DiscordBot discordBot, @NotNull CommandContext<?> context, @NotNull Throwable throwable) {
        return of(
            discordBot,
            context,
            throwable,
            "Command Exception"
        );
    }

    /**
     * Creates a new {@link ExceptionContext} wrapping a raw Discord4J {@link Event}
     * that was not yet bound to an {@link EventContext}. Extracts user, channel, and
     * guild information on a best-effort basis from the raw event.
     *
     * @param <T> the Discord4J event type
     * @param discordBot the bot instance
     * @param event the raw event in which the exception occurred
     * @param throwable the caught exception
     * @param title a human-readable title for the exception category
     * @return a new exception context
     */
    static <T extends Event> @NotNull ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull T event, @NotNull Throwable throwable, @NotNull String title) {
        return of(discordBot, RawEventContext.of(discordBot, event), throwable, title);
    }

    /**
     * Creates a new {@link ExceptionContext} wrapping the given {@link EventContext} with
     * the specified title.
     *
     * @param <T> the Discord4J event type
     * @param discordBot the bot instance
     * @param context the event context in which the exception occurred
     * @param throwable the caught exception
     * @param title a human-readable title for the exception category
     * @return a new exception context
     */
    static <T extends Event> @NotNull ExceptionContext<T> of(@NotNull DiscordBot discordBot, @NotNull EventContext<T> context, @NotNull Throwable throwable, @NotNull String title) {
        return new Impl<>(discordBot, context, throwable, title);
    }

    /**
     * Default implementation of {@link ExceptionContext} that stores the bot instance,
     * wrapped event context, exception, and title.
     *
     * @param <T> the Discord4J event type
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl<T extends Event> implements ExceptionContext<T> {

        /**
         * The bot instance that received this event.
         */
        private final @NotNull DiscordBot discordBot;

        /**
         * The original event context that was being processed when the exception occurred.
         */
        private final @NotNull EventContext<T> eventContext;

        /**
         * A randomly generated response identifier for this exception context.
         */
        private final @NotNull UUID responseId = UUID.randomUUID();

        /**
         * The caught exception.
         */
        private final @NotNull Throwable exception;

        /**
         * A human-readable title for the exception category.
         */
        private final @NotNull String title;

    }

    /**
     * Synthesizes an {@link EventContext} from a raw Discord4J {@link Event} on a
     * best-effort basis when no listener has yet constructed a real context. Used by
     * the {@link #of(DiscordBot, Event, Throwable, String)} factory to give
     * {@link Impl} a non-self-referential wrapped context, so the delegating defaults
     * on {@link ExceptionContext} resolve cleanly without recursion.
     *
     * <p>
     * Extracts channel, guild, and user metadata via pattern matching on the event
     * type. Unrecognized events fall back to a dummy channel snowflake, an empty
     * guild, and the bot's own self user.
     *
     * @param <T> the Discord4J event type
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    final class RawEventContext<T extends Event> implements EventContext<T> {

        /**
         * The bot instance that received this event.
         */
        private final @NotNull DiscordBot discordBot;

        /**
         * The underlying Discord4J event.
         */
        private final @NotNull T event;

        /**
         * A randomly generated response identifier for this synthesized context.
         */
        private final @NotNull UUID responseId = UUID.randomUUID();

        /** {@inheritDoc} */
        @Override
        public @NotNull Mono<MessageChannel> getChannel() {
            return this.getDiscordBot()
                .getGateway()
                .getChannelById(this.getChannelId())
                .ofType(MessageChannel.class);
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull Snowflake getChannelId() {
            return switch (this.event) {
                case InteractionCreateEvent ice -> ice.getInteraction().getChannelId();
                case MessageCreateEvent mce -> mce.getMessage().getChannelId();
                case MessageDeleteEvent mde -> mde.getChannelId();
                case ReactionUserEmojiEvent rue -> rue.getChannelId();
                default -> Snowflake.of(0L);
            };
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull Mono<Guild> getGuild() {
            return Mono.justOrEmpty(this.getGuildId()).flatMap(id -> this.discordBot.getGateway().getGuildById(id));
        }

        /** {@inheritDoc} */
        @Override
        public Optional<Snowflake> getGuildId() {
            return switch (this.event) {
                case InteractionCreateEvent ice -> ice.getInteraction().getGuildId();
                case MessageCreateEvent mce -> mce.getGuildId();
                case MessageDeleteEvent mde -> mde.getGuildId();
                case ReactionEvent re -> re.getGuildId();
                case GuildCreateEvent gce -> Optional.of(gce.getGuild().getId());
                case GuildDeleteEvent gde -> Optional.of(gde.getGuildId());
                default -> Optional.empty();
            };
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull User getInteractUser() {
            switch (this.getEvent()) {
                case InteractionCreateEvent ice -> {
                    return ice.getInteraction().getUser();
                }
                case MessageCreateEvent mce -> {
                    Optional<User> author = mce.getMessage().getAuthor();
                    if (author.isPresent())
                        return author.get();
                }
                case MessageDeleteEvent mde -> {
                    Optional<User> author = mde.getMessage().flatMap(Message::getAuthor);
                    if (author.isPresent())
                        return author.get();
                }
                case ReactionUserEmojiEvent rue -> {
                    Optional<User> user = rue.getUser().blockOptional();
                    if (user.isPresent())
                        return user.get();
                }
                default -> { }
            }

            return new User(this.getDiscordBot().getGateway(), this.getDiscordBot().getSelf());
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull Snowflake getInteractUserId() {
            return this.getInteractUser().getId();
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull Mono<Void> reply(@NotNull Response response) {
            return this.getChannel()
                .flatMap(response::getD4jCreateMono)
                .then();
        }

        /**
         * Creates a new {@code RawEventContext} for the given bot and raw Discord4J event.
         *
         * @param <T> the Discord4J event type
         * @param discordBot the bot instance
         * @param event the raw Discord4J event
         * @return a new synthesized context
         */
        static <T extends Event> @NotNull RawEventContext<T> of(@NotNull DiscordBot discordBot, @NotNull T event) {
            return new RawEventContext<>(discordBot, event);
        }

    }

}
