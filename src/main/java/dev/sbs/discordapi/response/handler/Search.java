package dev.sbs.discordapi.response.handler;

import dev.sbs.discordapi.component.interaction.TextInput;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
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

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Search<T> {

    private final @NotNull TextInput textInput; // TODO: Extract title/description and build label later
    private final @NotNull ConcurrentList<BiPredicate<T, String>> predicates;
    private @NotNull Optional<String> lastMatch = Optional.empty();

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    @SuppressWarnings("unchecked")
    private static <V> @Nullable V castSafely(@NotNull Class<V> type, @NotNull String value) {
        try {
            PropertyEditor propertyEditor = PropertyEditorManager.findEditor(type);
            propertyEditor.setAsText(value);
            return (V) propertyEditor.getValue();
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Search<?> search = (Search<?>) o;

        return Objects.equals(this.getTextInput(), search.getTextInput())
            && Objects.equals(this.getPredicates(), search.getPredicates());
    }

    public static <T> @NotNull Builder<T> from(@NotNull Search<T> searcher) {
        return new Builder<T>()
            .withPlaceholder(searcher.getTextInput().getPlaceholder())
            .withPredicates(searcher.getPredicates());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getTextInput(), this.getPredicates());
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    public void updateLastMatch(@NotNull TextInput textInput) {
        this.lastMatch = textInput.getValue();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T> {

        @BuildFlag(nonNull = true)
        private TextInput.Builder textInputBuilder = TextInput.builder().withSearchType(TextInput.SearchType.CUSTOM);
        @BuildFlag(nonNull = true)
        private final ConcurrentList<BiPredicate<T, String>> predicates = Concurrent.newList();

        /**
         * Add custom search predicates for items.
         *
         * @param predicates A variable amount of predicates.
         */
        public Builder<T> withPredicates(@NotNull BiPredicate<T, String>... predicates) {
            return this.withPredicates(Arrays.asList(predicates));
        }

        /**
         * Add custom search predicates for items.
         *
         * @param predicates A variable amount of predicates.
         */
        public Builder<T> withPredicates(@NotNull Iterable<BiPredicate<T, String>> predicates) {
            predicates.forEach(this.predicates::add);
            return this;
        }

        /**
         * Add custom search predicates for items.
         *
         * @param predicates A variable amount of predicates.
         */
        public <V extends Serializable> Builder<T> withPredicates(@NotNull Class<V> type, @NotNull BiPredicate<T, V>... predicates) {
            return this.withPredicates(type, Arrays.asList(predicates));
        }

        /**
         * Add custom search predicates for items.
         *
         * @param predicates A variable amount of predicates.
         */
        public <V extends Serializable> Builder<T> withPredicates(@NotNull Class<V> type, @NotNull Iterable<BiPredicate<T, V>> predicates) {
            predicates.forEach(predicate -> this.predicates.add((t, value) -> predicate.test(t, Search.castSafely(type, value))));
            return this;
        }

        /**
         * Sets the placeholder of the {@link Sorter}.
         *
         * @param placeholder The placeholder to use.
         */
        public Builder<T> withPlaceholder(@Nullable String placeholder) {
            return this.withPlaceholder(Optional.ofNullable(placeholder));
        }

        /**
         * Sets the placeholder of the {@link Sorter}.
         *
         * @param placeholder The placeholder to use.
         * @param args The objects used to format the placeholder.
         */
        public Builder<T> withPlaceholder(@PrintFormat @Nullable String placeholder, @Nullable Object... args) {
            return this.withPlaceholder(StringUtil.formatNullable(placeholder, args));
        }

        /**
         * Sets the placeholder of the {@link Sorter}.
         *
         * @param placeholder The placeholder to use.
         */
        public Builder<T> withPlaceholder(@NotNull Optional<String> placeholder) {
            this.textInputBuilder.withPlaceholder(placeholder);
            return this;
        }

        public @NotNull Search<T> build() {
            Reflection.validateFlags(this);

            return new Search<>(
                this.textInputBuilder.withValue(Optional.empty()).build(),
                this.predicates.toUnmodifiable()
            );
        }

    }

}
