package dev.sbs.discordapi.listener.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.command.AutoCompleteContext;
import dev.sbs.discordapi.context.command.SlashCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.simplified.collection.Concurrent;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Listener for slash command autocomplete interactions, resolving the focused
 * {@link Parameter} and responding with suggestion choices from its autocomplete handler.
 */
public final class AutoCompleteListener extends DiscordListener<ChatInputAutoCompleteEvent> {

    /**
     * Constructs a new {@code AutoCompleteListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public AutoCompleteListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    @SuppressWarnings("all")
    public Publisher<Void> apply(@NotNull ChatInputAutoCompleteEvent event) {
        return Mono.just(event.getInteraction())
            .flatMap(interaction -> Mono.justOrEmpty(interaction.getData().data().toOptional()))
            .flatMapMany(commandData -> Flux.fromIterable(this.getDiscordBot().getCommandHandler().getCommandsById(event.getCommandId().asLong()))
                .filter(command -> this.matchesInteractionData(command, commandData))
            )
            .single()
            .map(command -> (DiscordCommand<SlashCommandContext>) command)
            .flatMap(slashCommand -> event.respondWithSuggestions(
                slashCommand.getParameters()
                    .findFirst(Parameter::getName, event.getFocusedOption().getName())
                    .map(parameter -> parameter.getAutoComplete()
                        .apply(AutoCompleteContext.of(
                            this.getDiscordBot(),
                            event,
                            slashCommand.getStructure(),
                            new Argument(
                                event.getInteraction(),
                                parameter,
                                event.getFocusedOption().getValue().orElseThrow()
                            )
                        ))
                        .stream()
                        .map(entry -> ApplicationCommandOptionChoiceData.builder()
                            .name(entry.getKey())
                            .value(entry.getValue())
                            .build()
                        )
                        .map(ApplicationCommandOptionChoiceData.class::cast)
                        .collect(Concurrent.toList())
                    )
                    .orElse(Concurrent.newList())
            ));
    }

}
