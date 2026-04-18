package dev.sbs.discordapi.response.page.editor.field;

import dev.sbs.discordapi.response.Emoji;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * A labelled option within a {@link FieldKind.Choice} editable field.
 *
 * <p>
 * Records render as select menu options when a choice field is edited. The {@link #value()}
 * is the domain value returned to the save handler when the user picks this choice.
 *
 * @param label the user-facing label
 * @param value the underlying domain value
 * @param description the optional secondary description shown below the label
 * @param emoji the optional emoji rendered next to the label
 * @param <V> the domain value type
 */
public record Choice<V>(
    @NotNull String label,
    @NotNull V value,
    @NotNull Optional<String> description,
    @NotNull Optional<Emoji> emoji
) {

    /**
     * Creates a choice with only a label and value.
     *
     * @param label the user-facing label
     * @param value the underlying domain value
     * @param <V> the domain value type
     * @return a new choice with no description or emoji
     */
    public static <V> @NotNull Choice<V> of(@NotNull String label, @NotNull V value) {
        return new Choice<>(label, value, Optional.empty(), Optional.empty());
    }

}
