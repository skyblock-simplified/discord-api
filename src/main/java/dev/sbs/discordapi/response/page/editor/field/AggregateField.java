package dev.sbs.discordapi.response.page.editor.field;

import dev.sbs.discordapi.response.page.editor.EditorPage;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import reactor.core.publisher.Mono;

/**
 * An {@link EditableField} used by {@link EditorPage.Aggregate} to edit a live domain
 * object with save-on-change semantics.
 *
 * <p>
 * The {@link #liveSaver} is invoked when the user submits a new value for this field
 * and returns a {@link Mono} producing the updated domain object. The immutable-return
 * contract means implementations should treat the input as a snapshot.
 *
 * @param identifier the stable field identifier
 * @param label the user-facing label
 * @param description the optional secondary description
 * @param kind the editing semantics
 * @param required whether the user must supply a value
 * @param obfuscated whether the rendered display masks the value
 * @param readOnly whether the field has no edit button
 * @param renderer the value-to-display renderer
 * @param getter the domain-to-value accessor
 * @param liveSaver the save handler returning the updated domain object
 * @param <T> the domain type
 * @param <V> the field value type
 */
public record AggregateField<T, V>(
    @NotNull String identifier,
    @NotNull String label,
    @NotNull Optional<String> description,
    @NotNull FieldKind<V> kind,
    boolean required,
    boolean obfuscated,
    boolean readOnly,
    @NotNull Function<V, String> renderer,
    @NotNull Function<T, V> getter,
    @NotNull BiFunction<T, FieldEdit<V>, Mono<T>> liveSaver
) implements EditableField<T, V> {

    /**
     * Creates a new builder for an aggregate field.
     *
     * @param <T> the domain type
     * @param <V> the field value type
     * @return a new builder
     */
    public static <T, V> @NotNull Builder<T, V> builder() {
        return new Builder<>();
    }

    /** A builder for {@link AggregateField} instances. */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder<T, V> {

        @BuildFlag(nonNull = true)
        private String identifier;
        @BuildFlag(nonNull = true)
        private String label;
        private Optional<String> description = Optional.empty();
        @BuildFlag(nonNull = true)
        private FieldKind<V> kind;
        private boolean required;
        private boolean obfuscated;
        private boolean readOnly;
        @BuildFlag(nonNull = true)
        private Function<V, String> renderer = value -> value == null ? "" : value.toString();
        @BuildFlag(nonNull = true)
        private Function<T, V> getter;
        @BuildFlag(nonNull = true)
        private BiFunction<T, FieldEdit<V>, Mono<T>> liveSaver;

        /**
         * Sets the stable field identifier.
         *
         * @param identifier the field identifier
         * @return this builder
         */
        public Builder<T, V> withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the user-facing label.
         *
         * @param label the field label
         * @return this builder
         */
        public Builder<T, V> withLabel(@NotNull String label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the optional secondary description.
         *
         * @param description the description text
         * @return this builder
         */
        public Builder<T, V> withDescription(@NotNull String description) {
            this.description = Optional.of(description);
            return this;
        }

        /**
         * Sets the editing kind.
         *
         * @param kind the field kind
         * @return this builder
         */
        public Builder<T, V> withKind(@NotNull FieldKind<V> kind) {
            this.kind = kind;
            return this;
        }

        /**
         * Marks the field as required.
         *
         * @return this builder
         */
        public Builder<T, V> isRequired() {
            this.required = true;
            return this;
        }

        /**
         * Marks the field display as obfuscated.
         *
         * @return this builder
         */
        public Builder<T, V> isObfuscated() {
            this.obfuscated = true;
            return this;
        }

        /**
         * Marks the field as read-only.
         *
         * @return this builder
         */
        public Builder<T, V> isReadOnly() {
            this.readOnly = true;
            return this;
        }

        /**
         * Sets the value-to-display renderer.
         *
         * @param renderer the renderer function
         * @return this builder
         */
        public Builder<T, V> withRenderer(@NotNull Function<V, String> renderer) {
            this.renderer = renderer;
            return this;
        }

        /**
         * Sets the accessor producing the current value from the domain object.
         *
         * @param getter the accessor
         * @return this builder
         */
        public Builder<T, V> withGetter(@NotNull Function<T, V> getter) {
            this.getter = getter;
            return this;
        }

        /**
         * Sets the live-save handler invoked when the user submits a new value.
         *
         * @param liveSaver the save handler
         * @return this builder
         */
        public Builder<T, V> withLiveSaver(@NotNull BiFunction<T, FieldEdit<V>, Mono<T>> liveSaver) {
            this.liveSaver = liveSaver;
            return this;
        }

        /**
         * Builds a new {@link AggregateField} from the configured fields.
         *
         * @return a new aggregate field
         */
        public @NotNull AggregateField<T, V> build() {
            Reflection.validateFlags(this);

            return new AggregateField<>(
                this.identifier,
                this.label,
                this.description,
                this.kind,
                this.required,
                this.obfuscated,
                this.readOnly,
                this.renderer,
                this.getter,
                this.liveSaver
            );
        }

    }

}
