package dev.sbs.discordapi.response.page.item.field;

import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.page.item.Item;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.util.StringUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public final class ToggleItem implements FieldItem<Boolean> {

    private final @NotNull SelectMenu.Option option;
    private final boolean editable;
    private final boolean inline;
    private final @NotNull Optional<Boolean> value;

    @Override
    public @NotNull ToggleItem applyVariables(@NotNull ConcurrentMap<String, Object> variables) {
        return this;
    }

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    public static @NotNull Builder from(@NotNull ToggleItem item) {
        return builder()
            .withOption(item.getOption())
            .isEditable(item.isEditable())
            .isEnabled(item.getValue().orElse(false));
    }

    @Override
    public @NotNull String getRenderValue() {
        return String.valueOf(this.getValue().orElse(false));
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private final SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        private boolean editable;
        private boolean inline;
        private boolean enabled;

        /**
         * Sets the {@link Item} as editable.
         */
        public Builder isEditable() {
            return this.isEditable(true);
        }

        /**
         * Set the editable state of the {@link Item}.
         *
         * @param editable The editable state of the item.
         */
        public Builder isEditable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * Sets the {@link Item} as inline.
         */
        public Builder isInline() {
            return this.isInline(true);
        }

        /**
         * Set the inline state of the {@link Item}.
         *
         * @param editable The inline state of the item.
         */
        public Builder isInline(boolean editable) {
            this.inline = editable;
            return this;
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @param args The objects used to format the description.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link SelectMenu.Option}.
         *
         * @param description The description to use.
         * @see SelectMenu.Option#getDescription()
         */
        public Builder withDescription(@NotNull Optional<String> description) {
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
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link SelectMenu.Option}.
         *
         * @param emoji The emoji to use.
         * @see SelectMenu.Option#getEmoji()
         * @see Field#getName()
         */
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.optionBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Overrides the default identifier of the {@link SelectMenu.Option}.
         *
         * @param identifier The identifier to use.
         * @see SelectMenu.Option#getValue()
         */
        public Builder withIdentifier(@NotNull String identifier) {
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
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.optionBuilder.withValue(identifier, args);
            return this;
        }

        /**
         * Sets the label of the {@link SelectMenu.Option}.
         *
         * @param label The label of the field item.
         * @see SelectMenu.Option#getLabel()
         */
        public Builder withLabel(@NotNull String label) {
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
        public Builder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.optionBuilder.withLabel(label, args);
            return this;
        }

        public Builder withOption(@NotNull SelectMenu.Option option) {
            return this.withIdentifier(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel());
        }

        /**
         * Disables the value of the {@link ToggleItem}.
         */
        public Builder isDisabled() {
            return this.isDisabled(true);
        }

        /**
         * Sets the status of the {@link ToggleItem}.
         *
         * @param disabled The value of the menu item.
         */
        public Builder isDisabled(boolean disabled) {
            this.enabled = !disabled;
            return this;
        }

        /**
         * Enables the value of the {@link ToggleItem}.
         */
        public Builder isEnabled() {
            return this.isEnabled(true);
        }

        /**
         * Sets the status of the {@link ToggleItem}.
         *
         * @param enabled The value of the menu item.
         */
        public Builder isEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public @NotNull ToggleItem build() {
            return new ToggleItem(
                this.optionBuilder.build(),
                this.editable,
                this.inline,
                Optional.of(this.enabled)
            );
        }

    }

}