package dev.sbs.discordapi.response.handler;

import dev.sbs.discordapi.component.capability.UserInteractable;
import dev.sbs.discordapi.component.interaction.RadioGroup;
import dev.sbs.discordapi.response.page.item.Item;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.query.SortOrder;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings({ "unchecked", "rawtypes" })
public class Sorter<T> implements BiFunction<ConcurrentList<T>, Boolean, ConcurrentList<T>>, UserInteractable {

    private final @NotNull String identifier;
    private final @NotNull String label;
    private final @NotNull Optional<String> description;
    private final boolean enabled;
    private final @NotNull ConcurrentMap<Comparator<? extends T>, SortOrder> comparators;
    private final @NotNull SortOrder order;

    @Override
    public @NotNull ConcurrentList<T> apply(@NotNull ConcurrentList<T> list, @NotNull Boolean reversed) {
        ConcurrentList<T> copy = Concurrent.newList(list).sorted((o1, o2) -> {
            Iterator<Map.Entry<Comparator<? extends T>, SortOrder>> iterator = this.getComparators().iterator();
            Map.Entry<Comparator<? extends T>, SortOrder> entry = iterator.next();
            Comparator comparator = entry.getKey();

            if (entry.getValue() == SortOrder.DESCENDING)
                comparator = comparator.reversed();

            while (iterator.hasNext()) {
                entry = iterator.next();
                comparator = comparator.thenComparing(entry.getKey());

                if (entry.getValue() == SortOrder.DESCENDING)
                    comparator = comparator.reversed();
            }

            return this.getOrder() == SortOrder.ASCENDING ? comparator.compare(o1, o2) : comparator.compare(o2, o1);
        });

        // Reverse Results
        if (reversed)
            copy = copy.reversed();

        return copy;
    }

    /**
     * Builds a {@link RadioGroup.Option} from this sorter's fields.
     *
     * @return the built radio option
     */
    public @NotNull RadioGroup.Option buildOption() {
        RadioGroup.Option.Builder optionBuilder = RadioGroup.Option.builder()
            .withLabel(this.getLabel())
            .withValue(this.getIdentifier());

        this.getDescription().ifPresent(optionBuilder::withDescription);
        return optionBuilder.build();
    }

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Sorter<?> sorter = (Sorter<?>) o;

        return Objects.equals(this.getIdentifier(), sorter.getIdentifier())
            && Objects.equals(this.getLabel(), sorter.getLabel())
            && Objects.equals(this.getDescription(), sorter.getDescription())
            && this.isEnabled() == sorter.isEnabled()
            && Objects.equals(this.getComparators(), sorter.getComparators())
            && Objects.equals(this.getOrder(), sorter.getOrder());
    }

    public static <T> @NotNull Builder<T> from(@NotNull Sorter<T> sorter) {
        return new Builder<T>()
            .withIdentifier(sorter.getIdentifier())
            .withLabel(sorter.getLabel())
            .withDescription(sorter.getDescription())
            .isEnabled(sorter.isEnabled())
            .withComparators(sorter.getComparators())
            .withOrder(sorter.getOrder());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.getLabel(), this.getDescription(), this.isEnabled(), this.getComparators(), this.getOrder());
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T> {

        private String identifier = UUID.randomUUID().toString();
        @BuildFlag(notEmpty = true)
        private Optional<String> label = Optional.empty();
        private Optional<String> description = Optional.empty();
        private boolean enabled = false;
        @BuildFlag(nonNull = true)
        private final ConcurrentMap<Comparator<? extends T>, SortOrder> comparators = Concurrent.newMap();
        @BuildFlag(nonNull = true)
        private SortOrder order = SortOrder.DESCENDING;

        /**
         * Sets this sorter as enabled.
         */
        public Builder<T> isEnabled() {
            return this.isEnabled(true);
        }

        /**
         * Sets this sorter as enabled if given true.
         *
         * @param value true to enable
         */
        public Builder<T> isEnabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /**
         * Sets this sorter as disabled.
         */
        public Builder<T> isDisabled() {
            return this.isDisabled(true);
        }

        /**
         * Sets this sorter as disabled if given true.
         *
         * @param value true to disable
         */
        public Builder<T> isDisabled(boolean value) {
            this.enabled = !value;
            return this;
        }

        /**
         * Add custom comparators for the {@link Item FieldItems}.
         *
         * @param comparators a variable amount of comparators
         */
        @SafeVarargs
        public final Builder<T> withComparators(@NotNull Comparator<? extends T>... comparators) {
            return this.withComparators(Arrays.asList(comparators));
        }

        /**
         * Add custom comparators for the {@link Item FieldItems}.
         *
         * @param order how the comparators are sorted
         * @param comparators a variable amount of comparators
         */
        @SafeVarargs
        public final Builder<T> withComparators(@NotNull SortOrder order, @NotNull Comparator<? extends T>... comparators) {
            return this.withComparators(order, Arrays.asList(comparators));
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param comparators a variable amount of comparators
         */
        public Builder<T> withComparators(@NotNull Iterable<Comparator<? extends T>> comparators) {
            return this.withComparators(SortOrder.DESCENDING, comparators);
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param order how the comparators are sorted
         * @param comparators a variable amount of comparators
         */
        public Builder<T> withComparators(@NotNull SortOrder order, @NotNull Iterable<Comparator<? extends T>> comparators) {
            comparators.forEach(comparator -> this.comparators.put(comparator, order));
            return this;
        }

        private Builder<T> withComparators(@NotNull ConcurrentMap<Comparator<? extends T>, SortOrder> comparators) {
            this.comparators.putAll(comparators);
            return this;
        }

        /**
         * Sets the description of the {@link Sorter}.
         *
         * @param description the description to use
         */
        public Builder<T> withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link Sorter}.
         *
         * @param description the description to use
         * @param args the objects used to format the description
         */
        public Builder<T> withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link Sorter}.
         *
         * @param description the description to use
         */
        public Builder<T> withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param functions a variable amount of sort functions
         */
        @SafeVarargs
        public final Builder<T> withFunctions(@NotNull Function<T, ? extends Comparable>... functions) {
            return this.withFunctions(SortOrder.DESCENDING, functions);
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param functions a variable amount of sort functions
         * @param order how the comparators are sorted
         */
        @SafeVarargs
        public final Builder<T> withFunctions(@NotNull SortOrder order, @NotNull Function<T, ? extends Comparable>... functions) {
            return this.withFunctions(order, Arrays.asList(functions));
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param functions a collection of sort functions
         */
        public Builder<T> withFunctions(@NotNull Iterable<Function<T, ? extends Comparable>> functions) {
            return this.withFunctions(SortOrder.DESCENDING, functions);
        }

        /**
         * Add custom sort functions for the {@link Item FieldItems}.
         *
         * @param functions a collection of sort functions
         * @param order how the comparators are sorted
         */
        public Builder<T> withFunctions(@NotNull SortOrder order, @NotNull Iterable<Function<T, ? extends Comparable>> functions) {
            functions.forEach(function -> this.comparators.put(Comparator.comparing(function), order));
            return this;
        }

        /**
         * Sets the identifier of the {@link Sorter}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder<T> withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the label of the {@link Sorter}.
         *
         * @param label the label of the sorter
         */
        public Builder<T> withLabel(@NotNull String label) {
            this.label = Optional.of(label);
            return this;
        }

        /**
         * Sets the label of the {@link Sorter}.
         *
         * @param label the label of the sorter
         * @param args the objects used to format the label
         */
        public Builder<T> withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.label = Optional.of(String.format(label, args));
            return this;
        }

        /**
         * Sets the sort order for the {@link Item PageItems}.
         * <br><br>
         * Descending - Highest to Lowest (Default)<br>
         * Ascending - Lowest to Highest
         *
         * @param order the order to sort the items in
         */
        public Builder<T> withOrder(@NotNull SortOrder order) {
            this.order = order;
            return this;
        }

        public @NotNull Sorter<T> build() {
            Reflection.validateFlags(this);

            return new Sorter<>(
                this.identifier,
                this.label.orElseThrow(),
                this.description,
                this.enabled,
                this.comparators.toUnmodifiableMap(),
                this.order
            );
        }

    }

}
