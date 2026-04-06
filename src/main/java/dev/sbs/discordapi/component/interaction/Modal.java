package dev.sbs.discordapi.component.interaction;

import dev.sbs.discordapi.command.exception.InputException;
import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.capability.EventInteractable;
import dev.sbs.discordapi.component.capability.ModalUpdatable;
import dev.sbs.discordapi.component.capability.UserInteractable;
import dev.sbs.discordapi.component.layout.Label;
import dev.sbs.discordapi.component.scope.ActionComponent;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.component.scope.TopLevelModalComponent;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.sbs.discordapi.context.component.ModalContext;
import dev.sbs.discordapi.context.scope.ComponentContext;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.spec.InteractionPresentModalSpec;
import discord4j.discordjson.possible.Possible;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * An immutable modal dialog presented to a user as a pop-up form.
 *
 * <p>
 * Modals contain {@link TopLevelModalComponent} instances - typically {@link Label Labels}
 * wrapping {@link TextInput} or {@link SelectMenu} components. When submitted, the computed
 * {@link #getInteraction()} validates {@link TextInput} values against their validators and
 * dispatches to {@link TextInput.SearchType} handlers before falling back to the modal-level
 * interaction handler.
 *
 * <p>
 * Unlike {@link Button} and {@link SelectMenu}, a modal is not a {@link Component Component}
 * itself; it is presented via {@link #getD4jPresentSpec()} rather than embedded in a message
 * layout.
 *
 * <p>
 * Instances are created via {@link #builder()} and can be copied for modification
 * via {@link #mutate()}.
 *
 * @see TopLevelModalComponent
 * @see TextInput
 * @see Label
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Modal implements EventInteractable<ModalContext>, UserInteractable {

    private static final @NotNull Function<ModalContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;

    /** The unique identifier for this modal. */
    private final @NotNull String identifier;

    /** The optional title displayed at the top of the modal. */
    private final @NotNull Optional<String> title;

    /** The top-level modal components contained in this modal. */
    private final @NotNull ConcurrentList<TopLevelModalComponent> components;

    /** The interaction handler invoked when this modal is submitted. */
    private final @NotNull Function<ModalContext, Mono<Void>> interaction;

    /**
     * Creates a new builder with a random identifier.
     *
     * @return a new {@link Builder} instance
     */
    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Modal modal = (Modal) o;

        return Objects.equals(this.getIdentifier(), modal.getIdentifier())
            && Objects.equals(this.getTitle(), modal.getTitle())
            && Objects.equals(this.getComponents(), modal.getComponents())
            && Objects.equals(this.interaction, modal.interaction);
    }

    /**
     * Creates a pre-filled builder from the given modal.
     *
     * @param modal the modal to copy fields from
     * @return a pre-filled {@link Builder} instance
     */
    public static @NotNull Builder from(@NotNull Modal modal) {
        return new Builder()
            .withIdentifier(modal.getIdentifier())
            .withTitle(modal.getTitle())
            .withComponents(modal.getComponents())
            .onInteract(modal.interaction);
    }

    /**
     * Finds the first {@link ActionComponent} of the given type whose extracted property
     * matches the specified value, searching through all layout components in this modal.
     *
     * @param tClass the action component subtype to search for
     * @param function the property extractor applied to each candidate
     * @param value the value to match against the extracted property
     * @param <S> the property type
     * @param <T> the action component subtype
     * @return an {@link Optional} containing the matching component, or empty if none is found
     */
    public <S, T extends ActionComponent> @NotNull Optional<T> findComponent(@NotNull Class<T> tClass, @NotNull Function<T, S> function, S value) {
        return this.getComponents()
            .stream()
            .filter(LayoutComponent.class::isInstance)
            .map(LayoutComponent.class::cast)
            .map(layout -> layout.findComponent(tClass, function, value))
            .flatMap(Optional::stream)
            .findFirst();
    }

    /**
     * Converts this modal to a Discord4J presentation specification.
     *
     * @return the Discord4J modal presentation spec
     */
    public @NotNull InteractionPresentModalSpec getD4jPresentSpec() {
        return InteractionPresentModalSpec.builder()
            .customId(this.getIdentifier())
            .title(this.getTitle().map(Possible::of).orElse(Possible.absent()))
            .components(
                this.getComponents()
                    .stream()
                    .map(Component::getD4jComponent)
                    .map(discord4j.core.object.component.TopLevelModalComponent.class::cast)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Validates each {@link TextInput} value against its validator, dispatches to
     * {@link TextInput.SearchType} handlers for search-enabled inputs, and falls back
     * to the modal-level interaction handler for remaining submissions.
     */
    @Override
    public @NotNull Function<ModalContext, Mono<Void>> getInteraction() {
        return modalContext -> Flux.fromIterable(modalContext.getComponent().getComponents())
            .filter(Label.class::isInstance)
            .map(Label.class::cast)
            .map(Label::getComponent)
            .next()
            //.flatMap(Mono::justOrEmpty)
            .map(TextInput.class::cast)
            .filter(textInput -> textInput.getValue().isPresent())
            .flatMap(textInput -> {
                boolean validInput = textInput.getValue()
                    .map(value -> textInput.getValidator().test(value))
                    .orElse(true);

                if (!validInput) {
                    return modalContext.getDiscordBot().getExceptionHandler().handleException(
                        ExceptionContext.of(
                            modalContext.getDiscordBot(),
                            modalContext,
                            new InputException(textInput.getValue()),
                            "Modal Interaction Exception"
                        )
                    );
                }

                return Mono.just(textInput);
            })
            // Search Checks Top-Most
            .filter(textInput -> textInput.getSearchType() != TextInput.SearchType.NONE)
            //.next()
            .switchIfEmpty(this.interaction.apply(modalContext).then(Mono.empty()))
            .flatMap(textInput -> textInput.getSearchType().getInteraction().apply(modalContext, textInput));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.getTitle(), this.getComponents(), this.interaction);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDeferEdit() {
        return false;
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     *
     * @return a pre-filled {@link Builder} instance
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /**
     * A builder for constructing {@link Modal} instances.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {

        @BuildFlag(nonNull = true)
        private String identifier;
        @BuildFlag(notEmpty = true)
        private Optional<String> title = Optional.empty();
        @BuildFlag(notEmpty = true)
        private final ConcurrentList<TopLevelModalComponent> components = Concurrent.newList();
        private Optional<Function<ModalContext, Mono<Void>>> interaction = Optional.empty();

        /**
         * Clears all components from the {@link Modal}.
         */
        public Builder clearComponents() {
            this.components.clear();
            return this;
        }

        /**
         * Sets the interaction handler invoked when the {@link Modal} is submitted.
         *
         * @param interaction the interaction function, or {@code null} for the default no-op handler
         */
        public Builder onInteract(@Nullable Function<ModalContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction handler invoked when the {@link Modal} is submitted.
         *
         * @param interaction the optional interaction function
         */
        public Builder onInteract(@NotNull Optional<Function<ModalContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Updates component values from a modal submit event.
         * <p>
         * Iterates through the event's submitted components and delegates to each
         * matching {@link ModalUpdatable} component's
         * {@link ModalUpdatable#updateFromData updateFromModalData} method.
         *
         * @param event the modal submit interaction event
         */
        public Builder updateComponents(@NotNull ModalSubmitInteractionEvent event) {
            event.getComponents(discord4j.core.object.component.LayoutComponent.class)
                .stream()
                .map(discord4j.core.object.component.LayoutComponent::getChildren)
                .flatMap(List::stream)
                .forEach(d4jComponent -> this.components.stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .filter(label -> label.getComponent().getIdentifier().equals(d4jComponent.getData().customId().get()))
                    .findFirst()
                    .ifPresent(label -> label.getComponent().updateFromData(d4jComponent.getData()))
                );

            return this;
        }

        /**
         * Adds {@link TopLevelModalComponent components} to the {@link Modal}.
         *
         * @param components variable number of components to add
         */
        @SuppressWarnings("all")
        public Builder withComponents(@NotNull TopLevelModalComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Adds {@link TopLevelModalComponent components} to the {@link Modal}.
         *
         * @param components collection of components to add
         */
        public Builder withComponents(@NotNull Iterable<TopLevelModalComponent> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Sets the identifier of the {@link Modal}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the title displayed at the top of the {@link Modal}.
         *
         * @param title the title text, or {@code null} to clear
         */
        public Builder withTitle(@Nullable String title) {
            return this.withTitle(Optional.ofNullable(title));
        }

        /**
         * Sets the title displayed at the top of the {@link Modal}.
         *
         * @param title the optional title text
         */
        public Builder withTitle(@NotNull Optional<String> title) {
            this.title = title;
            return this;
        }

        /**
         * Builds a new {@link Modal} from the configured fields.
         *
         * @return a new {@link Modal} instance
         */
        public @NotNull Modal build() {
            Reflection.validateFlags(this);

            return new Modal(
                this.identifier,
                this.title,
                this.components,
                this.interaction.orElse(NOOP_HANDLER)
            );
        }

    }

}
