package dev.sbs.discordapi.context.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.context.scope.ActionComponentContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.ConcurrentList;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Context for select menu component interactions, extending {@link ActionComponentContext}
 * with access to the interacted {@link SelectMenu} and its raw selected wire values.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a
 * {@link SelectMenuInteractionEvent} is dispatched. The framework calls
 * {@link SelectMenu#updateSelected(java.util.List)} on the component before the handler
 * runs, so callers may access {@link #getSelectedValues()} for the wire-agnostic view or
 * pattern-match {@link #getComponent()} against {@link SelectMenu.StringMenu} or
 * {@link SelectMenu.EntityMenu} for variant-specific state.
 *
 * @see SelectMenu
 * @see OptionContext
 */
public interface SelectMenuContext extends ActionComponentContext {

    /** {@inheritDoc} */
    @Override
    @NotNull SelectMenuInteractionEvent getEvent();

    /** {@inheritDoc} */
    @Override
    @NotNull SelectMenu getComponent();

    /**
     * The raw wire values from the interaction, as delivered by Discord.
     *
     * <p>
     * For a {@link SelectMenu.StringMenu} these are option values; for a
     * {@link SelectMenu.EntityMenu} they are stringified snowflake ids.
     *
     * @return the selected wire values
     */
    default @NotNull ConcurrentList<String> getSelectedValues() {
        return this.getComponent().getSelectedValues();
    }

    /**
     * Creates a new {@code SelectMenuContext} for the given event, response, and select menu.
     *
     * @param discordBot the bot instance
     * @param event the select menu interaction event
     * @param cachedMessage the cached response containing the select menu
     * @param selectMenu the select menu that was interacted with
     * @param followup the associated followup, if any
     * @return a new select menu context
     */
    static SelectMenuContext of(@NotNull DiscordBot discordBot, @NotNull SelectMenuInteractionEvent event, @NotNull Response cachedMessage, SelectMenu selectMenu, @NotNull Optional<CachedResponse> followup) {
        return new Impl(
            discordBot,
            event,
            cachedMessage.getUniqueId(),
            selectMenu,
            followup
        );
    }

    /**
     * Default implementation of {@link SelectMenuContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements SelectMenuContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying select menu interaction event. */
        private final @NotNull SelectMenuInteractionEvent event;

        /** The unique response identifier linking this context to its cached response. */
        private final @NotNull UUID responseId;

        /** The select menu that was interacted with. */
        private final @NotNull SelectMenu component;

        /** The associated followup, if any. */
        private final @NotNull Optional<CachedResponse> followup;

    }

}
