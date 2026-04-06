package dev.sbs.discordapi.component.layout;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.component.scope.LabelComponent;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.component.scope.TopLevelModalComponent;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import discord4j.discordjson.json.ComponentData;
import discord4j.discordjson.possible.Possible;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An immutable layout that wraps a {@link LabelComponent} with a title and optional
 * description.
 *
 * <p>
 * Used in modals to attach descriptive labels to interactive components such as
 * {@link TextInput TextInputs} and {@link SelectMenu SelectMenus}.
 *
 * <p>
 * Instances are created via the {@link Builder} obtained from {@link #builder()}, or
 * duplicated for modification via {@link #mutate()}.
 *
 * @see LabelComponent
 * @see Modal
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Label implements LayoutComponent, TopLevelModalComponent {

    /** The title text displayed above the wrapped component. */
    private final @NotNull String title;

    /** The optional description text displayed below the title. */
    private final @NotNull Optional<String> description;

    /** The wrapped label component. */
    private final @NotNull LabelComponent component;

    /**
     * Creates a new builder.
     *
     * @return a new builder
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Label label = (Label) o;

        return Objects.equals(this.getTitle(), label.getTitle())
            && Objects.equals(this.getDescription(), label.getDescription())
            && Objects.equals(this.getComponent(), label.getComponent());
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Includes the wrapped {@link #getComponent() component} in addition to this label itself.
     */
    @Override
    public @NotNull Stream<Component> flattenComponents() {
        return Stream.concat(
            LayoutComponent.super.flattenComponents(),
            this.getComponent().flattenComponents()
        );
    }

    @Override
    public @NotNull ConcurrentList<LabelComponent> getComponents() {
        return Concurrent.newUnmodifiableList(this.getComponent());
    }

    /**
     * Creates a pre-filled builder from the given instance.
     *
     * @param label the label to copy values from
     * @return a pre-filled builder
     */
    public static @NotNull Builder from(@NotNull Label label) {
        return builder()
            .withTitle(label.getTitle())
            .withDescription(label.getDescription())
            .withComponent(label.getComponent());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.Label getD4jComponent() {
        return (discord4j.core.object.component.Label) discord4j.core.object.component.Label.fromData(
            ComponentData.builder()
                .type(Component.Type.LABEL.getValue())
                .label(Possible.of(this.getTitle()).map(Optional::of))
                .description(Possible.of(this.getDescription()))
                .component(this.getComponent().getD4jComponent().getData())
                .build()
        );
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.LABEL;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getTitle(), this.getDescription(), this.getComponent());
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     *
     * @return a pre-filled builder
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /** A builder for constructing {@link Label} instances. */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        @Range(from = 1, to = 45)
        @BuildFlag(notEmpty = true, limit = 45)
        private String title;
        @BuildFlag(limit = 100)
        private Optional<String> description = Optional.empty();
        @BuildFlag(notEmpty = true)
        private Optional<LabelComponent> component = Optional.empty();

        /**
         * Sets the {@link LabelComponent} for the {@link Label}.
         *
         * <p>
         * This field is required and must be provided before building.
         *
         * @param component the label component to wrap
         * @return this builder
         */
        public Builder withComponent(@NotNull LabelComponent component) {
            this.component = Optional.of(component);
            return this;
        }

        /**
         * Sets the description text of the {@link Label} using a format string.
         *
         * @param description the format string for the description, or {@code null} to clear
         * @param args the arguments referenced by the format string
         * @return this builder
         */
        public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description text of the {@link Label}.
         *
         * @param description the description text, or {@code null} to clear
         * @return this builder
         */
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description text of the {@link Label}.
         *
         * @param description the description text wrapped in an {@link Optional}
         * @return this builder
         */
        public Builder withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the title text of the {@link Label} using a format string.
         *
         * @param title the format string for the title
         * @param args the arguments referenced by the format string
         * @return this builder
         */
        public Builder withTitle(@PrintFormat @NotNull String title, @Nullable Object... args) {
            return this.withTitle(String.format(title, args));
        }

        /**
         * Sets the title text of the {@link Label}.
         *
         * @param title the title text
         * @return this builder
         */
        public Builder withTitle(@NotNull String title) {
            this.title = title;
            return this;
        }

        /**
         * Builds a new {@link Label} from the configured fields.
         *
         * @return a new label
         */
        public @NotNull Label build() {
            Reflection.validateFlags(this);

            return new Label(
                this.title,
                this.description,
                this.component.orElseThrow()
            );
        }

    }

}
