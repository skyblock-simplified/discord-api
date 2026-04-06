package dev.sbs.discordapi.response.page.item.field;

import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.page.item.Item;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Getter
@RequiredArgsConstructor
public final class ModelItem<T> implements FieldItem<T> {

    private final @NotNull SelectMenu.Option option;
    private final boolean editable;
    private final boolean inline;
    private final @NotNull Optional<T> value;
    private final @NotNull Class<T> modelClass;
    private final @NotNull ConcurrentList<T> options;
    private final @NotNull Optional<Function<T, String>> nameFunction;
    private final @NotNull Function<T, String> valueFunction;

    @Override
    public @NotNull ModelItem<T> applyVariables(@NotNull ConcurrentMap<String, Object> variables) {
        return this;
    }

    public static <T> @NotNull Builder<T> builder(@NotNull Class<T> modelClass) {
        return new Builder<>(modelClass).withIdentifier(UUID.randomUUID().toString());
    }

    public static <T> @NotNull Builder<T> from(@NotNull ModelItem<T> item) {
        return builder(item.getModelClass())
            .withOption(item.getOption())
            .isEditable(item.isEditable())
            .withValue(item.getValue())
            .withOptions(item.getOptions())
            .withNameFunction(item.getNameFunction())
            .withValueFunction(item.getValueFunction());
    }

    @Override
    public @NotNull String getRenderName() {
        return this.getValue()
            .flatMap(model -> this.getNameFunction().map(nameFunction -> nameFunction.apply(model)))
            .orElse(this.getOption().getLabel());
    }

    @Override
    public @NotNull String getRenderValue() {
        return this.getValue()
            .map(this.getValueFunction())
            .orElse("***null***");
    }

    public @NotNull Builder<T> mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<T> {

        private final SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        private boolean editable;
        private boolean inline;
        private final Class<T> modelClass;
        private final ConcurrentList<T> options = Concurrent.newList();
        private Optional<T> value = Optional.empty();
        private Optional<Function<T, String>> nameFunction = Optional.empty();
        @BuildFlag(nonNull = true)
        private Function<T, String> valueFunction;

        /**
         * Sets the {@link Item} as editable.
         */
        public Builder<T> isEditable() {
            return this.isEditable(true);
        }

        /**
         * Set the editable state of the {@link Item}.
         *
         * @param editable The value of the author item.
         */
        public Builder<T> isEditable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * Sets the {@link Item} as inline.
         */
        public Builder<T> isInline() {
            return this.isInline(true);
        }

        /**
         * Set the inline state of the {@link Item}.
         *
         * @param editable The inline state of the item.
         */
        public Builder<T> isInline(boolean editable) {
            this.inline = editable;
            return this;
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder<T> withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @param args The objects used to format the description.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder<T> withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder<T> withDescription(@NotNull Optional<String> description) {
            this.optionBuilder.withDescription(description);
            return this;
        }

        /**
         * Sets the emoji of the {@link SelectMenu.Option}.
         *
         * @param emoji The emoji to use.
         * @see SelectMenu.Option#getEmoji()
         * @see Field#getName()
         */
        public Builder<T> withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link SelectMenu.Option}.
         *
         * @param emoji The emoji to use.
         * @see SelectMenu.Option#getEmoji()
         * @see Field#getName()
         */
        public Builder<T> withEmoji(@NotNull Optional<Emoji> emoji) {
            this.optionBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link SelectMenu.Option}.
         *
         * @param identifier The identifier to use.
         * @see SelectMenu.Option#getValue()
         */
        public Builder<T> withIdentifier(@NotNull String identifier) {
            this.optionBuilder.withValue(identifier);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link SelectMenu.Option}.
         *
         * @param identifier The identifier to use.
         * @param args The objects used to format the value.
         * @see SelectMenu.Option#getValue()
         */
        public Builder<T> withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.optionBuilder.withValue(identifier, args);
            return this;
        }

        /**
         * Sets the label of the {@link SelectMenu.Option}.
         *
         * @param label The label of the field item.
         * @see SelectMenu.Option#getLabel()
         */
        public Builder<T> withLabel(@NotNull String label) {
            this.optionBuilder.withLabel(label);
            return this;
        }

        /**
         * Sets the label of the {@link SelectMenu.Option}.
         *
         * @param label The label of the field item.
         * @param args The objects used to format the label.
         * @see SelectMenu.Option#getLabel()
         */
        public Builder<T> withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.optionBuilder.withLabel(label, args);
            return this;
        }

        public Builder<T> withOption(@NotNull SelectMenu.Option option) {
            return this.withIdentifier(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel());
        }

        /**
         * Sets the displayed name of the {@link ModelItem} value.
         *
         * @param nameFunction The display name of the menu item value.
         */
        public Builder<T> withNameFunction(@Nullable Function<T, String> nameFunction) {
            return this.withNameFunction(Optional.ofNullable(nameFunction));
        }

        /**
         * Optionally sets the displayed name of the {@link ModelItem} value.
         *
         * @param nameFunction The display name of the menu item value.
         */
        public Builder<T> withNameFunction(@NotNull Optional<Function<T, String>> nameFunction) {
            this.nameFunction = nameFunction;
            return this;
        }

        /**
         * The options available for selection {@link ModelItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull T... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * The options available for selection {@link ModelItem}.
         *
         * @param options The options available for selection.
         */
        public Builder<T> withOptions(@NotNull Iterable<T> options) {
            this.options.clear();
            options.forEach(this.options::add);
            return this;
        }

        /**
         * Sets the selected value of the {@link ModelItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@Nullable T selected) {
            return this.withValue(Optional.ofNullable(selected));
        }

        /**
         * Sets the selected value of the {@link ModelItem}.
         *
         * @param selected The selected value of the menu item.
         */
        public Builder<T> withValue(@NotNull Optional<T> selected) {
            this.value = selected;
            return this;
        }

        /**
         * Sets the value of the {@link ModelItem}.
         *
         * @param valueFunction The value of the menu item.
         */
        public Builder<T> withValueFunction(@NotNull Function<T, String> valueFunction) {
            this.valueFunction = valueFunction;
            return this;
        }

        public @NotNull ModelItem<T> build() {
            Reflection.validateFlags(this);

            return new ModelItem<>(
                this.optionBuilder.build(),
                this.editable,
                this.inline,
                this.value,
                this.modelClass,
                this.options,
                this.nameFunction,
                this.valueFunction
            );
        }

    }

}