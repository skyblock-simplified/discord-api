package dev.sbs.discordapi.component.layout;

import dev.sbs.discordapi.component.scope.ContainerComponent;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import discord4j.core.object.component.ICanBeUsedInContainerComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable layout component that groups {@link ContainerComponent ContainerComponents}
 * with an optional accent color and spoiler flag.
 *
 * <p>
 * Instances are created via the {@link Builder} obtained from {@link #builder()}, or
 * duplicated for modification via {@link #mutate()}.
 *
 * @see ContainerComponent
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Container implements LayoutComponent {

    /** The optional accent color applied to the container border. */
    private final @NotNull Optional<Color> accent;

    /** The child components held by this container. */
    private final @NotNull ConcurrentList<ContainerComponent> components;

    /** Whether the container content is hidden behind a spoiler. */
    private final boolean spoiler;

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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Container container = (Container) o;

        return Objects.equals(this.getAccent(), container.getAccent())
            && Objects.equals(this.getComponents(), container.getComponents())
            && this.isSpoiler() == container.isSpoiler();
    }

    /**
     * Creates a pre-filled builder from the given instance.
     *
     * @param container the container to copy values from
     * @return a pre-filled builder
     */
    public static @NotNull Builder from(@NotNull Container container) {
        return builder()
            .withAccent(container.getAccent())
            .withComponents(container.getComponents())
            .isSpoiler(container.isSpoiler());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.Container getD4jComponent() {
        return discord4j.core.object.component.Container.of(
            this.getAccent()
                .map(Color::getRGB)
                .map(discord4j.rest.util.Color::of)
                .orElse(null),
            this.isSpoiler(),
            this.getComponents()
                .stream()
                .map(ContainerComponent::getD4jComponent)
                .map(ICanBeUsedInContainerComponent.class::cast)
                .collect(Concurrent.toList())
        );
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.CONTAINER;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getAccent(), this.getComponents(), this.isSpoiler());
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     *
     * @return a pre-filled builder
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /** A builder for constructing {@link Container} instances. */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private Optional<Color> accent = Optional.empty();
        @BuildFlag(notEmpty = true)
        private final ConcurrentList<ContainerComponent> components = Concurrent.newList();
        private boolean spoiler = false;

        /**
         * Sets the spoiler flag to {@code true}.
         *
         * @return this builder
         */
        public Builder isSpoiler() {
            return this.isSpoiler(true);
        }

        /**
         * Sets whether the container content is hidden behind a spoiler.
         *
         * @param spoiler {@code true} to mark the container as a spoiler
         * @return this builder
         */
        public Builder isSpoiler(boolean spoiler) {
            this.spoiler = spoiler;
            return this;
        }

        /**
         * Sets the accent color of the {@link Container}.
         *
         * @param accent the accent color, or {@code null} to clear
         * @return this builder
         */
        public Builder withAccent(@Nullable Color accent) {
            return this.withAccent(Optional.ofNullable(accent));
        }

        /**
         * Sets the accent color of the {@link Container}.
         *
         * @param accent the accent color wrapped in an {@link Optional}
         * @return this builder
         */
        public Builder withAccent(@NotNull Optional<Color> accent) {
            this.accent = accent;
            return this;
        }

        /**
         * Adds {@link ContainerComponent ContainerComponents} to the {@link Container}.
         *
         * @param components the container components to add
         * @return this builder
         */
        public Builder withComponents(@NotNull ContainerComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Adds {@link ContainerComponent ContainerComponents} to the {@link Container}.
         *
         * @param components the container components to add
         * @return this builder
         */
        public Builder withComponents(@NotNull Iterable<ContainerComponent> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Builds a new {@link Container} from the configured fields.
         *
         * @return a new container
         */
        public @NotNull Container build() {
            Reflection.validateFlags(this);

            return new Container(
                this.accent,
                this.components.toUnmodifiable(),
                this.spoiler
            );
        }

    }

}
