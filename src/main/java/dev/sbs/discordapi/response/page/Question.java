package dev.sbs.discordapi.response.page;

import dev.sbs.discordapi.component.TextDisplay;
import dev.sbs.discordapi.component.layout.Section;
import dev.sbs.discordapi.response.page.item.field.FieldItem;
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

import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Question<T> {

    private final @NotNull String identifier;
    private final @NotNull FieldItem<T> fieldItem;
    private final @NotNull String title;
    private final @NotNull Optional<String> description;
    private final boolean required;

    public static <T> @NotNull Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Question<?> question = (Question<?>) o;

        return Objects.equals(this.getIdentifier(), question.getIdentifier())
            && Objects.equals(this.getFieldItem(), question.getFieldItem())
            && Objects.equals(this.getTitle(), question.getTitle())
            && Objects.equals(this.getDescription(), question.getDescription())
            && this.isRequired() == question.isRequired();
    }

    public static <T> @NotNull Builder<T> from(@NotNull Question<T> question) {
        return new Builder<T>()
            .withIdentifier(question.getIdentifier())
            .withFieldItem(question.getFieldItem())
            .withTitle(question.getTitle())
            .withDescription(question.getDescription())
            .isRequired(question.isRequired());
    }

    public @NotNull Optional<T> getAnswer() {
        return this.getFieldItem().getValue();
    }

    public @NotNull Section getComponent() {
        Section.Builder section = Section.builder();
        StringJoiner questionText = new StringJoiner("\n");
        questionText.add(String.format("### %s%s", this.getTitle(), (this.isRequired() ? " *" : "")));
        this.getDescription().ifPresent(questionText::add);
        section.withComponents(TextDisplay.of(questionText.toString()));

        // TODO: Check if FieldItem is going to be a SelectMenu
        // TODO: If Button/Model, build it, cache it and add to accessory

        return section.build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.getFieldItem(), this.getTitle(), this.getDescription(), this.isRequired());
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T> {

        @BuildFlag(nonNull = true)
        private String identifier;
        @BuildFlag(nonNull = true)
        private FieldItem<T> fieldItem;
        @BuildFlag(nonNull = true)
        private String title;
        private Optional<String> description = Optional.empty();
        private boolean required = false;

        /**
         * Marks the question as required.
         *
         * @return the updated builder instance
         */
        public Builder<T> isRequired() {
            return this.isRequired(true);
        }

        /**
         * Sets whether the field is required for the question.
         *
         * @param required a boolean indicating whether the field is required
         * @return the updated builder instance
         */
        public Builder<T> isRequired(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets the field item for the builder if the provided field item is not singular.
         *
         * @param fieldItem the field item to set, must not be null
         * @return the updated builder instance
         */
        public Builder<T> withFieldItem(@NotNull FieldItem<T> fieldItem) {
            if (!fieldItem.isSingular())
                this.fieldItem = fieldItem;

            return this;
        }

        /**
         * Sets the identifier for the builder.
         *
         * @param identifier the identifier to set, must not be null
         * @return the updated builder instance
         */
        public Builder<T> withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the identifier for the builder using a formatted string. The identifier is constructed
         * using the provided format and optional arguments.
         *
         * @param identifier the identifier to set, formatted as a string, must not be null
         * @param args the optional arguments to include in the formatted identifier, can be null
         * @return the updated builder instance
         */
        public Builder<T> withIdentifier(@NotNull @PrintFormat String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Sets the optional description for the builder. If the provided description is null,
         * it will be wrapped as an empty {@link Optional}.
         *
         * @param description the description to set, may be null
         * @return the updated builder instance
         */
        public Builder<T> withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description for the builder with a formatted string. If the provided description
         * is null, it will be handled as an empty {@link Optional}.
         *
         * @param description the description to set, formatted as a string, can be null
         * @param args the optional arguments to include in the formatted description, can be null
         * @return the updated builder instance
         */
        public Builder<T> withDescription(@Nullable @PrintFormat String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the optional description for the builder.
         *
         * @param description the optional description to set, must not be null
         * @return the updated builder instance
         */
        public Builder<T> withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the title for the builder.
         *
         * @param title the title to set, must not be null
         * @return the updated builder instance
         */
        public Builder<T> withTitle(@NotNull String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the title for the builder with a formatted string.
         *
         * @param title the title to set, formatted as a string, must not be null
         * @param args the optional arguments to include in the formatted title, may be null
         * @return the updated builder instance
         */
        public Builder<T> withTitle(@NotNull @PrintFormat String title, @Nullable Object... args) {
            this.title = String.format(title, args);
            return this;
        }

        public @NotNull Question<T> build() {
            Reflection.validateFlags(this);

            return new Question<>(
                this.identifier,
                this.fieldItem,
                this.title,
                this.description,
                this.required
            );
        }

    }

}
