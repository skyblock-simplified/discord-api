package dev.sbs.discordapi.component.layout;

import dev.sbs.discordapi.component.scope.ActionComponent;
import dev.sbs.discordapi.component.scope.ContainerComponent;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable layout component that arranges {@link ActionComponent ActionComponents} in a
 * horizontal row.
 *
 * <p>
 * Instances are created via the {@link #of(ActionComponent...)} or
 * {@link #of(Iterable)} factory methods. The resulting row holds an unmodifiable
 * list of action components.
 *
 * @see ActionComponent
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ActionRow implements ContainerComponent, LayoutComponent {

    /** The action components arranged in this row. */
    private final @NotNull ConcurrentList<ActionComponent> components;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ActionRow actionRow = (ActionRow) o;

        return Objects.equals(this.getComponents(), actionRow.getComponents());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.ActionRow getD4jComponent() {
        return discord4j.core.object.component.ActionRow.of(
            this.getComponents()
                .stream()
                .map(ActionComponent::getD4jComponent)
                .collect(Concurrent.toList())
        );
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.ACTION_ROW;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getComponents());
    }

    /**
     * Creates an {@link ActionRow} containing the given action components.
     *
     * @param components the action components to include
     * @return a new action row
     */
    public static @NotNull ActionRow of(@NotNull ActionComponent... components) {
        return of(Arrays.asList(components));
    }

    /**
     * Creates an {@link ActionRow} containing the action components from the given iterable.
     *
     * @param components the action components to include
     * @return a new action row
     */
    public static @NotNull ActionRow of(@NotNull Iterable<? extends ActionComponent> components) {
        ConcurrentList<ActionComponent> componentList = Concurrent.newList();
        components.forEach(componentList::add);
        return new ActionRow(componentList.toUnmodifiableList());
    }

}
