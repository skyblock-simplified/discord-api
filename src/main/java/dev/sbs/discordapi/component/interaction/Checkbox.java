package dev.sbs.discordapi.component.interaction;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.capability.EventInteractable;
import dev.sbs.discordapi.component.capability.Toggleable;
import dev.sbs.discordapi.component.layout.Label;
import dev.sbs.discordapi.component.scope.ActionComponent;
import dev.sbs.discordapi.component.scope.LabelComponent;
import dev.sbs.discordapi.context.component.CheckboxContext;
import dev.sbs.discordapi.context.scope.ComponentContext;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import discord4j.core.object.component.CheckboxAction;
import discord4j.discordjson.json.ComponentData;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * An immutable single toggle checkbox component rendered within a Discord message.
 *
 * <p>
 * A checkbox represents a boolean on/off toggle. The checked state is provided by the
 * Discord interaction event, not stored on the component itself.
 *
 * <p>
 * Instances are created via {@link #builder()} and can be copied for modification
 * via {@link #mutate()}.
 *
 * @see Label
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Checkbox implements ActionComponent, EventInteractable<CheckboxContext>, LabelComponent, Toggleable {

    private static final Function<CheckboxContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;

    /** The unique identifier for this checkbox. */
    private final @NotNull String identifier;

    /** Whether the interaction is automatically deferred as an edit. */
    private final boolean deferEdit;

    /** The interaction handler invoked when this checkbox is toggled. */
    private final @NotNull Function<CheckboxContext, Mono<Void>> interaction;

    /** Whether this checkbox is currently enabled. */
    private boolean enabled;

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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Checkbox that = (Checkbox) o;

        return this.isDeferEdit() == that.isDeferEdit()
            && this.isEnabled() == that.isEnabled()
            && Objects.equals(this.getIdentifier(), that.getIdentifier());
    }

    /**
     * Creates a pre-filled builder from the given checkbox.
     *
     * @param checkbox the checkbox to copy fields from
     * @return a pre-filled {@link Builder} instance
     */
    public static @NotNull Builder from(@NotNull Checkbox checkbox) {
        return new Builder()
            .withIdentifier(checkbox.getIdentifier())
            .setDisabled(checkbox.isEnabled())
            .withDeferEdit(checkbox.isDeferEdit())
            .onInteract(checkbox.getInteraction());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.CheckboxAction getD4jComponent() {
        return CheckboxAction.of(this.getIdentifier());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Component.Type getType() {
        return Component.Type.CHECKBOX;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.isDeferEdit(), this.isEnabled());
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     *
     * @return a pre-filled {@link Builder} instance
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /** {@inheritDoc} */
    @Override
    public void updateFromData(@NotNull ComponentData data) {
        // Checkbox toggle state is event-driven, not stored on the component
    }

    /** {@inheritDoc} */
    @Override
    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    /**
     * A builder for constructing {@link Checkbox} instances.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {

        @BuildFlag(nonNull = true)
        private String identifier;
        private boolean enabled;
        private boolean deferEdit;
        private Optional<Function<CheckboxContext, Mono<Void>>> interaction = Optional.empty();

        /**
         * Sets the interaction handler invoked when the {@link Checkbox} is toggled.
         *
         * @param interaction the interaction function, or {@code null} for the default no-op handler
         */
        public Builder onInteract(@Nullable Function<CheckboxContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction handler invoked when the {@link Checkbox} is toggled.
         *
         * @param interaction the optional interaction function
         */
        public Builder onInteract(@NotNull Optional<Function<CheckboxContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link Checkbox} as disabled.
         */
        public Builder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets whether the {@link Checkbox} is disabled.
         *
         * @param value {@code true} to disable the checkbox
         */
        public Builder setDisabled(boolean value) {
            return this.setEnabled(!value);
        }

        /**
         * Sets the {@link Checkbox} to automatically defer interactions as edits.
         */
        public Builder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether the {@link Checkbox} automatically defers interactions as edits.
         *
         * @param deferEdit {@code true} to defer interactions
         */
        public Builder withDeferEdit(boolean deferEdit) {
            this.deferEdit = deferEdit;
            return this;
        }

        /**
         * Sets the {@link Checkbox} as enabled.
         */
        public Builder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets whether the {@link Checkbox} is enabled.
         *
         * @param value {@code true} to enable the checkbox
         */
        public Builder setEnabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /**
         * Sets the identifier of the {@link Checkbox}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the identifier of the {@link Checkbox} using a format string, overriding the default random UUID.
         *
         * @param identifier the format string for the identifier
         * @param args the format arguments
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Builds a new {@link Checkbox} from the configured fields.
         *
         * @return a new {@link Checkbox} instance
         */
        public @NotNull Checkbox build() {
            Reflection.validateFlags(this);

            return new Checkbox(
                this.identifier,
                this.deferEdit,
                this.interaction.orElse(NOOP_HANDLER),
                this.enabled
            );
        }

    }

}
