package dev.sbs.discordapi.response.page.item;

import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.embed.Author;
import dev.sbs.discordapi.response.embed.Field;
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
public final class AuthorItem implements Item {

    private final @NotNull SelectMenu.Option option;
    private final boolean editable;
    private final @NotNull Optional<String> name;
    private final @NotNull Optional<String> iconUrl;
    private final @NotNull Optional<String> url;

    @Override
    public @NotNull AuthorItem applyVariables(@NotNull ConcurrentMap<String, Object> variables) {
        return this.mutate()
            .withName(this.getName().map(value -> StringUtil.format(value, variables)))
            .withIconUrl(this.getIconUrl().map(value -> StringUtil.format(value, variables)))
            .withUrl(this.getUrl().map(value -> StringUtil.format(value, variables)))
            .build();
    }

    public @NotNull Author asAuthor() {
        return Author.builder()
            .withName(this.getName())
            .withUrl(this.getUrl())
            .withIconUrl(this.getIconUrl())
            .build();
    }

    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    public static @NotNull Builder from(@NotNull AuthorItem item) {
        return builder()
            .withOption(item.getOption())
            .isEditable(item.isEditable())
            .withName(item.getName())
            .withIconUrl(item.getIconUrl())
            .withUrl(item.getUrl());
    }

    @Override
    public @NotNull Type getType() {
        return Type.AUTHOR;
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private final SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        private boolean editable;
        private Optional<String> name = Optional.empty();
        private Optional<String> iconUrl = Optional.empty();
        private Optional<String> url = Optional.empty();

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
         * Sets the icon url of the {@link AuthorItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public Builder withIconUrl(@Nullable String iconUrl) {
            return this.withIconUrl(Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the icon url of the {@link AuthorItem}.
         *
         * @param iconUrl The selected value of the menu item.
         */
        public Builder withIconUrl(@NotNull Optional<String> iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        /**
         * Sets the name of the {@link AuthorItem}.
         *
         * @param name The selected value of the menu item.
         * @param args The objects used to format the label.
         */
        public Builder withName(@PrintFormat @Nullable String name, @Nullable Object... args) {
            return this.withName(StringUtil.formatNullable(name, args));
        }


        /**
         * Sets the name of the {@link AuthorItem}.
         *
         * @param name The selected value of the menu item.
         */
        public Builder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Sets the name of the {@link AuthorItem}.
         *
         * @param name The selected value of the menu item.
         */
        public Builder withName(@NotNull Optional<String> name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the url of the {@link AuthorItem}.
         *
         * @param url The selected value of the menu item.
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the url of the {@link AuthorItem}.
         *
         * @param url The selected value of the menu item.
         */
        public Builder withUrl(@NotNull Optional<String> url) {
            this.url = url;
            return this;
        }

        public @NotNull AuthorItem build() {
            return new AuthorItem(
                this.optionBuilder.build(),
                this.editable,
                this.name,
                this.iconUrl,
                this.url
            );
        }

    }

}