package dev.sbs.discordapi.component.layout;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.media.Thumbnail;
import dev.sbs.discordapi.component.scope.AccessoryComponent;
import dev.sbs.discordapi.component.scope.ContainerComponent;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.component.scope.SectionComponent;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import discord4j.core.object.component.ICanBeUsedInSectionComponent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An immutable layout component that combines {@link SectionComponent SectionComponents} with
 * a required {@link AccessoryComponent} such as a {@link Thumbnail} or {@link Button}.
 *
 * <p>
 * Instances are created via the {@link Builder} obtained from {@link #builder()}, or
 * duplicated for modification via {@link #mutate()}.
 *
 * <p>
 * {@link #flattenComponents()} includes the accessory in the flattened stream alongside
 * the section's child components.
 *
 * @see SectionComponent
 * @see AccessoryComponent
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Section implements LayoutComponent, ContainerComponent {

    /** The accessory component displayed alongside this section's content. */
    private final @NotNull AccessoryComponent accessory;

    /** The section components held by this section. */
    private final @NotNull ConcurrentList<SectionComponent> components;

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

        Section section = (Section) o;

        return Objects.equals(this.getAccessory(), section.getAccessory())
            && Objects.equals(this.getComponents(), section.getComponents());
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Includes the {@link #getAccessory() accessory} in addition to the child components
     * and this section itself.
     */
    @Override
    public @NotNull Stream<Component> flattenComponents() {
        return Stream.concat(
            LayoutComponent.super.flattenComponents(),
            Stream.of(this.getAccessory())
        );
    }

    /**
     * Creates a pre-filled builder from the given instance.
     *
     * @param section the section to copy values from
     * @return a pre-filled builder
     */
    public static @NotNull Builder from(@NotNull Section section) {
        return builder()
            .withAccessory(section.getAccessory())
            .withComponents(section.getComponents());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.Section getD4jComponent() {
        return discord4j.core.object.component.Section.of(
            this.getAccessory().getD4jComponent(),
            this.getComponents()
                .stream()
                .map(SectionComponent::getD4jComponent)
                .map(ICanBeUsedInSectionComponent.class::cast)
                .collect(Concurrent.toList())
        );
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Component.Type getType() {
        return Component.Type.SECTION;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getAccessory(), this.getComponents());
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     *
     * @return a pre-filled builder
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /** A builder for constructing {@link Section} instances. */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        @BuildFlag(notEmpty = true)
        private Optional<AccessoryComponent> accessory = Optional.empty();
        private final ConcurrentList<SectionComponent> components = Concurrent.newList();

        /**
         * Sets the {@link AccessoryComponent} for the {@link Section}.
         *
         * <p>
         * This field is required and must be provided before building.
         *
         * @param accessory the accessory component to display alongside the section content
         * @return this builder
         */
        public Builder withAccessory(@NotNull AccessoryComponent accessory) {
            this.accessory = Optional.of(accessory);
            return this;
        }

        /**
         * Adds {@link SectionComponent SectionComponents} to the {@link Section}.
         *
         * @param components the section components to add
         * @return this builder
         */
        public Builder withComponents(@NotNull SectionComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Adds {@link SectionComponent SectionComponents} to the {@link Section}.
         *
         * @param components the section components to add
         * @return this builder
         */
        public Builder withComponents(@NotNull Iterable<SectionComponent> components) {
            components.forEach(this.components::add);
            return this;
        }

        /**
         * Builds a new {@link Section} from the configured fields.
         *
         * @return a new section
         */
        public @NotNull Section build() {
            Reflection.validateFlags(this);

            return new Section(
                this.accessory.orElseThrow(),
                this.components.toUnmodifiableList()
            );
        }

    }

}
