package dev.sbs.discordapi.response.page.editor.field;

import dev.sbs.discordapi.component.interaction.TextInput;
import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A sealed algebraic type describing the editing semantics of an editable field.
 *
 * <p>
 * Each variant maps to a modal layout produced by the {@code FieldModalFactory} - except
 * {@link Choice} with more than 25 options, which escalates to an in-page select menu flow
 * because Discord modals cannot host paginated option lists.
 *
 * @param <V> the field value type
 */
public sealed interface FieldKind<V>
    permits FieldKind.Text, FieldKind.Numeric, FieldKind.Bool, FieldKind.Choice {

    /**
     * A free-text field rendered in a modal as a {@link TextInput}.
     *
     * @param style the text input style
     * @param minLength the minimum accepted character count
     * @param maxLength the maximum accepted character count
     * @param validator the predicate applied to the submitted string
     * @param placeholder the optional placeholder shown when empty
     */
    record Text(
        @NotNull TextInput.Style style,
        int minLength,
        int maxLength,
        @NotNull Predicate<String> validator,
        @NotNull Optional<String> placeholder
    ) implements FieldKind<String> { }

    /**
     * A numeric field parsed from a modal text input.
     *
     * @param type the boxed numeric class
     * @param min the optional inclusive minimum
     * @param max the optional inclusive maximum
     * @param parser the text-to-number parser producing an empty optional on parse failure
     * @param formatter the number-to-text formatter used when pre-filling the modal
     * @param <N> the numeric value type
     */
    record Numeric<N extends Number & Comparable<N>>(
        @NotNull Class<N> type,
        @NotNull Optional<N> min,
        @NotNull Optional<N> max,
        @NotNull Function<String, Optional<N>> parser,
        @NotNull Function<N, String> formatter
    ) implements FieldKind<N> { }

    /**
     * A boolean field rendered as a yes/no radio group in a modal.
     */
    record Bool() implements FieldKind<Boolean> { }

    /**
     * A bounded-choice field. With 25 or fewer options, renders as a select menu inside a modal;
     * with more than 25, edit-click mutates the page into an in-page select-menu paging session.
     *
     * @param choices the available {@link dev.sbs.discordapi.response.page.editor.field.Choice Choices}
     * @param nullable whether the user may clear the selection, returning null to the save handler
     * @param <V> the domain value type
     */
    record Choice<V>(
        @NotNull ConcurrentList<dev.sbs.discordapi.response.page.editor.field.Choice<V>> choices,
        boolean nullable
    ) implements FieldKind<V> { }

}
