package dev.sbs.discordapi.context.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.CheckboxGroup;
import dev.sbs.discordapi.context.scope.ActionComponentContext;
import dev.sbs.discordapi.handler.response.ResponseFollowup;
import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.ConcurrentList;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Context for checkbox group component interactions, extending {@link ActionComponentContext}
 * with access to the interacted {@link CheckboxGroup}, its selected options, and a convenience
 * method for modifying it via its builder.
 *
 * <p>
 * Instances are created via the {@link #of} factory method when a
 * {@link ComponentInteractionEvent} targeting a checkbox group is dispatched.
 */
public interface CheckboxGroupContext extends ActionComponentContext {

    /** {@inheritDoc} */
    @Override
    @NotNull ComponentInteractionEvent getEvent();

    /** {@inheritDoc} */
    @Override
    @NotNull CheckboxGroup getComponent();

    /** The list of currently selected options from the checkbox group. */
    default @NotNull ConcurrentList<CheckboxGroup.Option> getSelected() {
        return this.getComponent().getSelected();
    }

    /**
     * Modifies the checkbox group by applying a transformation function to its builder,
     * then replaces it in the current response page.
     *
     * @param checkboxGroupBuilder a function that transforms the checkbox group builder
     * @return a mono completing when the checkbox group has been updated in the page
     */
    default Mono<Void> modify(@NotNull Function<CheckboxGroup.Builder, CheckboxGroup.Builder> checkboxGroupBuilder) {
        return this.modify(checkboxGroupBuilder.apply(this.getComponent().mutate()).build());
    }

    /**
     * Creates a new {@code CheckboxGroupContext} for the given event, response, and checkbox group.
     *
     * @param discordBot the bot instance
     * @param event the component interaction event
     * @param response the cached response containing the checkbox group
     * @param checkboxGroup the checkbox group that was interacted with
     * @param followup the associated followup, if any
     * @return a new checkbox group context
     */
    static @NotNull CheckboxGroupContext of(@NotNull DiscordBot discordBot, @NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull CheckboxGroup checkboxGroup, @NotNull Optional<ResponseFollowup> followup) {
        return new Impl(
            discordBot,
            event,
            response.getUniqueId(),
            checkboxGroup,
            followup
        );
    }

    /**
     * Default implementation of {@link CheckboxGroupContext}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PROTECTED)
    class Impl implements CheckboxGroupContext {

        /** The bot instance. */
        private final @NotNull DiscordBot discordBot;

        /** The underlying component interaction event. */
        private final @NotNull ComponentInteractionEvent event;

        /** The unique response identifier linking this context to its cached response. */
        private final @NotNull UUID responseId;

        /** The checkbox group that was interacted with. */
        private final @NotNull CheckboxGroup component;

        /** The associated followup, if any. */
        private final @NotNull Optional<ResponseFollowup> followup;

    }

}
