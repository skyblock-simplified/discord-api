package dev.sbs.discordapi.context.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.scope.CommandContext;
import dev.simplified.collection.ConcurrentList;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Context for slash command ({@code /command}) interactions, extending {@link CommandContext}
 * with access to resolved command arguments and the command {@link Structure}.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a {@link ChatInputInteractionEvent}
 * is dispatched.
 *
 * @see Argument
 * @see Parameter
 */
public interface SlashCommandContext extends CommandContext<ChatInputInteractionEvent> {

    /**
     * Finds the resolved argument matching the given parameter name.
     *
     * @param name the parameter name to search for
     * @return an optional containing the matching argument, or empty if not found
     */
    default @NotNull Optional<Argument> getArgument(@NotNull String name) {
        return this.getArguments().findFirst(argument -> argument.getParameter().getName(), name);
    }

    /** The list of resolved arguments provided by the user for this command invocation. */
    @NotNull ConcurrentList<Argument> getArguments();

    /**
     * {@inheritDoc}
     */
    @Override
    @NotNull Structure getStructure();

    /**
     * Creates a new {@code SlashCommandContext} for the given event and resolved arguments.
     *
     * @param discordBot the bot instance
     * @param event the chat input interaction event
     * @param structure the command structure metadata
     * @param arguments the resolved command arguments
     * @return a new slash command context
     */
    static @NotNull SlashCommandContext of(@NotNull DiscordBot discordBot, @NotNull ChatInputInteractionEvent event, @NotNull Structure structure, @NotNull ConcurrentList<Argument> arguments) {
        return new Impl(discordBot, event, structure, arguments);
    }

    /**
     * Default implementation of {@link SlashCommandContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements SlashCommandContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying chat input interaction event. */
        private final @NotNull ChatInputInteractionEvent event;

        /** The unique response identifier for this context. */
        private final @NotNull UUID responseId = UUID.randomUUID();

        /** The command structure metadata. */
        private final @NotNull Structure structure;

        /** The resolved command arguments. */
        private final @NotNull ConcurrentList<Argument> arguments;

    }

}