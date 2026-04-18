package dev.sbs.discordapi.response.page.editor.field;

import org.jetbrains.annotations.NotNull;

/**
 * A value transition recorded when a field's edit is applied.
 *
 * <p>
 * Passed to a live-save handler along with the current domain value, so the handler
 * may distinguish which field changed and what the previous value was.
 *
 * @param fieldId the identifier of the edited {@link EditableField}
 * @param oldValue the prior value before the edit
 * @param newValue the value submitted by the user
 * @param <V> the field value type
 */
public record FieldEdit<V>(
    @NotNull String fieldId,
    @NotNull V oldValue,
    @NotNull V newValue
) { }
