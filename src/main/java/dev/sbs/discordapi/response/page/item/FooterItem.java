package dev.sbs.discordapi.response.page.item;

import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.embed.Footer;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.util.StringUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public final class FooterItem implements Item {

    private final @NotNull SelectMenu.Option option;
    private final boolean editable;
    private final @NotNull Optional<String> text;
    private final @NotNull Optional<String> iconUrl;
    private final @NotNull Optional<Instant> timestamp;

    @Override
    public @NotNull FooterItem applyVariables(@NotNull ConcurrentMap<String, Object> variables) {
        return this.mutate()
            .withText(this.getText().map(value -> StringUtil.format(value, variables)))
            .withIconUrl(this.getIconUrl().map(value -> StringUtil.format(value, variables)))
            .build();
    }

    public @NotNull Footer asFooter() {
        return Footer.builder()
            .withText(this.getText())
            .withIconUrl(this.getIconUrl())
            .withTimestamp(this.getTimestamp())
            .build();
    }

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    public static @NotNull Builder from(@NotNull FooterItem item) {
        return builder()
            .withOption(item.getOption())
            .isEditable(item.isEditable())
            .withText(item.getText())
            .withIconUrl(item.getIconUrl())
            .withTimestamp(item.getTimestamp());
    }

    @Override
    public @NotNull Type getType() {
        return Type.FOOTER;
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private final SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        private boolean editable;
        private Optional<String> text = Optional.empty();
        private Optional<String> iconUrl = Optional.empty();
        private Optional<Instant> timestamp = Optional.empty();

        /**
         * Sets the {@link Item} as editable.
         */
        public Builder isEditable() {
            return this.isEditable(true);
        }

        /**
         * Set the editable state of the {@link Item}.
         *
         * @param editable The value of the author item.
         */
        public Builder isEditable(boolean editable) {
            this.editable = editable;
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
         * @param objects The objects used to format the label.
         * @see SelectMenu.Option#getLabel()
         */
        public Builder withLabel(@PrintFormat @NotNull String label, @Nullable Object... objects) {
            this.optionBuilder.withLabel(label, objects);
            return this;
        }

        public Builder withOption(@NotNull SelectMenu.Option option) {
            return this.withIdentifier(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .withLabel(option.getLabel());
        }

        /**
         * Sets the icon url of the {@link FooterItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public Builder withIconUrl(@Nullable String iconUrl) {
            return this.withIconUrl(Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the icon url of the {@link FooterItem}.
         *
         * @param iconUrl The selected value of the menu item.
         * @param objects The objects used to format the icon url.
         */
        public Builder withIconUrl(@PrintFormat @Nullable String iconUrl, @Nullable Object... objects) {
            return this.withIconUrl(StringUtil.formatNullable(iconUrl, objects));
        }

        /**
         * Sets the icon url of the {@link FooterItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public Builder withIconUrl(@NotNull Optional<String> iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        /**
         * Sets the text of the {@link FooterItem}.
         *
         * @param text The text of the menu item.
         */
        public Builder withText(@Nullable String text) {
            return this.withText(Optional.ofNullable(text));
        }

        /**
         * Sets the text of the {@link FooterItem}.
         *
         * @param text The text of the footer item.
         * @param args The objects used to format the name.
         */
        public Builder withText(@PrintFormat @Nullable String text, @Nullable Object... args) {
            return this.withText(StringUtil.formatNullable(text, args));
        }

        /**
         * Sets the text of the {@link FooterItem}.
         *
         * @param text The text of the menu item.
         */
        public Builder withText(@NotNull Optional<String> text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the timestamp of the {@link FooterItem}.
         *
         * @param timestamp The timestamp
         */
        public Builder withTimestamp(@Nullable Instant timestamp) {
            return this.withTimestamp(Optional.ofNullable(timestamp));
        }

        /**
         * Sets the timestamp of the {@link FooterItem}.
         *
         * @param timestamp The timestamp
         */
        public Builder withTimestamp(@NotNull Optional<Instant> timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public @NotNull FooterItem build() {
            return new FooterItem(
                this.optionBuilder.build(),
                this.editable,
                this.text,
                this.iconUrl,
                this.timestamp
            );
        }

    }

}