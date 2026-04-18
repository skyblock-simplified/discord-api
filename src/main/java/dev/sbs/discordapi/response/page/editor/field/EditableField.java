package dev.sbs.discordapi.response.page.editor.field;

import dev.sbs.discordapi.response.page.editor.EditorPage;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

/**
 * A descriptor for a tagged field within an {@link EditorPage}, carrying identity,
 * rendering, and flag state common to both editing modes.
 *
 * <p>
 * Use {@link AggregateField} for live-save editing over an existing domain object, and
 * {@link BuilderField} for on-submit assembly against a caller-supplied builder seed.
 *
 * @param <T> the domain object or builder seed type
 * @param <V> the field value type
 * @see AggregateField
 * @see BuilderField
 */
public sealed interface EditableField<T, V> permits AggregateField, BuilderField {

    /** The stable identifier used in component custom ids. */
    @NotNull String identifier();

    /** The user-facing label rendered next to the field value. */
    @NotNull String label();

    /** The optional secondary description rendered below the label. */
    @NotNull Optional<String> description();

    /** The {@link FieldKind} describing the editing semantics. */
    @NotNull FieldKind<V> kind();

    /** Whether the user must supply a value when editing the field. */
    boolean required();

    /** Whether the rendered display masks the value (modals pre-fill the real value). */
    boolean obfuscated();

    /** Whether the field is read-only - no edit button is rendered. */
    boolean readOnly();

    /** The renderer converting the raw value into the display text. */
    @NotNull Function<V, String> renderer();

}
