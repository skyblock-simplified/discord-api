package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.CheckboxGroup;
import dev.sbs.discordapi.context.component.CheckboxGroupContext;
import dev.sbs.discordapi.handler.response.ResponseFollowup;
import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.Concurrent;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Listener for {@link CheckboxGroup} component interactions, updating the group's
 * selected values and constructing a {@link CheckboxGroupContext} for the handler.
 */
public final class CheckboxGroupListener extends ComponentListener<ComponentInteractionEvent, CheckboxGroupContext, CheckboxGroup> {

    /**
     * Constructs a new {@code CheckboxGroupListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public CheckboxGroupListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull CheckboxGroupContext getContext(@NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull CheckboxGroup component, @NotNull Optional<ResponseFollowup> followup) {
        List<String> values = event.getInteraction().getCommandInteraction()
            .flatMap(ci -> ci.getValues())
            .orElse(Concurrent.newList());

        component.updateSelected(values);

        return CheckboxGroupContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

}
