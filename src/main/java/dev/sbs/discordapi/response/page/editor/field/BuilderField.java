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

/**
 * An {@link EditableField} used by {@link EditorPage.Builder} to accumulate state inside
 * a caller-supplied builder seed until the user submits the page.
 *
 * <p>
 * The {@link #applier} is invoked synchronously when the user submits a new value and returns
 * an updated builder seed. Unlike {@link AggregateField} there is no asynchronous persistence
 * step - the value is held in the seed until the page-level submit flow materializes the final
 * domain object.
 *
 * @param identifier the stable field identifier
 * @param label the user-facing label
 * @param description the optional secondary description
 * @param kind the editing semantics
 * @param required whether the user must supply a value
 * @param obfuscated whether the rendered display masks the value
 * @param readOnly whether the field has no edit button
 * @param renderer the value-to-display renderer
 * @param getter the seed-to-value accessor
 * @param applier the seed-to-updated-seed applier
 * @param <T> the builder seed type
 * @param <V> the field value type
 */
public record BuilderField<T, V>(
    @NotNull String identifier,
    @NotNull String label,
    @NotNull Optional<String> description,
    @NotNull FieldKind<V> kind,
    boolean required,
    boolean obfuscated,
    boolean readOnly,
    @NotNull Function<V, String> renderer,
    @NotNull Function<T, V> getter,
    @NotNull BiFunction<T, V, T> applier
) implements EditableField<T, V> {

    /**
     * Creates a new builder for a builder-mode field.
     *
     * @param <T> the builder seed type
     * @param <V> the field value type
     * @return a new builder
     */
    public static <T, V> @NotNull Builder<T, V> builder() {
        return new Builder<>();
    }

    /** A builder for {@link BuilderField} instances. */
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
        private BiFunction<T, V, T> applier;

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
         * Sets the accessor producing the current value from the builder seed.
         *
         * @param getter the accessor
         * @return this builder
         */
        public Builder<T, V> withGetter(@NotNull Function<T, V> getter) {
            this.getter = getter;
            return this;
        }

        /**
         * Sets the applier producing an updated builder seed from a submitted value.
         *
         * @param applier the applier
         * @return this builder
         */
        public Builder<T, V> withApplier(@NotNull BiFunction<T, V, T> applier) {
            this.applier = applier;
            return this;
        }

        /**
         * Builds a new {@link BuilderField} from the configured fields.
         *
         * @return a new builder field
         */
        public @NotNull BuilderField<T, V> build() {
            Reflection.validateFlags(this);

            return new BuilderField<>(
                this.identifier,
                this.label,
                this.description,
                this.kind,
                this.required,
                this.obfuscated,
                this.readOnly,
                this.renderer,
                this.getter,
                this.applier
            );
        }

    }

}
