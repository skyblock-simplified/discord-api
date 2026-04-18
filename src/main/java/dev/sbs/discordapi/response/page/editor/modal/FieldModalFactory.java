package dev.sbs.discordapi.response.page.editor.modal;

import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.component.interaction.RadioGroup;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.component.layout.Label;
import dev.sbs.discordapi.response.page.editor.field.Choice;
import dev.sbs.discordapi.response.page.editor.field.EditableField;
import dev.sbs.discordapi.response.page.editor.field.FieldKind;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Builds the {@link Modal} presented when the user clicks Edit on an {@link EditableField}.
 *
 * <p>
 * For {@link FieldKind.Choice} fields with more than 25 options, this factory returns an
 * empty optional - the caller must escalate to an in-page select menu flow because Discord
 * modals cannot paginate options.
 */
public final class FieldModalFactory {

    private FieldModalFactory() {
        throw new UnsupportedOperationException("FieldModalFactory is a static utility");
    }

    /**
     * Produces the modal for editing the given field if one is supported.
     *
     * @param field the field being edited
     * @param currentValue the current field value rendered as pre-fill
     * @param <T> the domain or seed type
     * @param <V> the field value type
     * @return the modal wrapped in an optional, or empty for Choice fields exceeding 25 options
     */
    public static <T, V> @NotNull Optional<Modal> forField(@NotNull EditableField<T, V> field, @NotNull Optional<V> currentValue) {
        FieldKind<V> kind = field.kind();

        return switch (kind) {
            case FieldKind.Text text -> Optional.of(buildTextModal(field, text, currentValue));
            case FieldKind.Numeric<?> numeric -> Optional.of(buildNumericModal(field, numeric, currentValue));
            case FieldKind.Bool bool -> Optional.of(buildBoolModal(field, currentValue));
            case FieldKind.Choice<?> choice -> buildChoiceModal(field, choice, currentValue);
        };
    }

    private static <T, V> @NotNull Modal buildTextModal(@NotNull EditableField<T, V> field, @NotNull FieldKind.Text text, @NotNull Optional<V> currentValue) {
        TextInput.Builder textBuilder = TextInput.builder()
            .withStyle(text.style())
            .withMinLength(text.minLength())
            .withMaxLength(text.maxLength())
            .withPlaceholder(text.placeholder())
            .withValidator(text.validator())
            .isRequired(field.required());

        currentValue.map(v -> (String) v).ifPresent(textBuilder::withValue);

        return Modal.builder()
            .withTitle(field.label())
            .withComponents(Label.builder().withTitle(field.label()).withComponent(textBuilder.build()).build())
            .build();
    }

    private static <T, V, N extends Number & Comparable<N>> @NotNull Modal buildNumericModal(@NotNull EditableField<T, V> field, @NotNull FieldKind.Numeric<N> numeric, @NotNull Optional<V> currentValue) {
        TextInput.Builder textBuilder = TextInput.builder()
            .withStyle(TextInput.Style.SHORT)
            .isRequired(field.required())
            .withValidator(input -> numeric.parser().apply(input).isPresent());

        // Narrowing cast - the field kind guarantees value type matches N
        @SuppressWarnings("unchecked")
        Optional<N> typedValue = (Optional<N>) currentValue;
        typedValue.map(numeric.formatter()).ifPresent(textBuilder::withValue);

        return Modal.builder()
            .withTitle(field.label())
            .withComponents(Label.builder().withTitle(field.label()).withComponent(textBuilder.build()).build())
            .build();
    }

    private static <T, V> @NotNull Modal buildBoolModal(@NotNull EditableField<T, V> field, @NotNull Optional<V> currentValue) {
        RadioGroup.Builder radio = RadioGroup.builder()
            .withOptions(
                RadioGroup.Option.builder().withLabel("Yes").withValue("true").build(),
                RadioGroup.Option.builder().withLabel("No").withValue("false").build()
            );

        RadioGroup group = radio.build();
        currentValue.map(v -> ((Boolean) v) ? "true" : "false").ifPresent(group::updateSelected);

        return Modal.builder()
            .withTitle(field.label())
            .withComponents(Label.builder().withTitle(field.label()).withComponent(group).build())
            .build();
    }

    private static <T, V, C> @NotNull Optional<Modal> buildChoiceModal(@NotNull EditableField<T, V> field, @NotNull FieldKind.Choice<C> choice, @NotNull Optional<V> currentValue) {
        if (choice.choices().size() > SelectMenu.Option.MAX_ALLOWED)
            return Optional.empty();

        SelectMenu.Builder select = SelectMenu.builder()
            .withPlaceholder("Pick a value");

        for (Choice<C> entry : choice.choices()) {
            SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder()
                .withLabel(entry.label())
                .withValue(entry.label());

            entry.description().ifPresent(optionBuilder::withDescription);
            entry.emoji().ifPresent(optionBuilder::withEmoji);
            select = select.withOptions(optionBuilder.build());
        }

        return Optional.of(
            Modal.builder()
                .withTitle(field.label())
                .withComponents(Label.builder().withTitle(field.label()).withComponent(select.build()).build())
                .build()
        );
    }

}
