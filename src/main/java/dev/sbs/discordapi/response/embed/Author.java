package dev.sbs.discordapi.response.embed;

import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import discord4j.core.spec.EmbedCreateFields;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public final class Author {

    private final @NotNull String name;
    private final @NotNull Optional<String> url;
    private final @NotNull Optional<String> iconUrl;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Author author = (Author) o;

        return Objects.equals(this.getName(), author.getName())
            && Objects.equals(this.getUrl(), author.getUrl())
            && Objects.equals(this.getIconUrl(), author.getIconUrl());
    }

    public @NotNull EmbedCreateFields.Author getD4jAuthor() {
        return EmbedCreateFields.Author.of(this.getName(), this.getUrl().orElse(null), this.getIconUrl().orElse(null));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getName(), this.getUrl(), this.getIconUrl());
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        @BuildFlag(notEmpty = true, limit = 256)
        private Optional<String> name = Optional.empty();
        private Optional<String> url = Optional.empty();
        private Optional<String> iconUrl = Optional.empty();

        /**
         * Sets the icon url of the {@link Author}.
         *
         * @param iconUrl The icon url of the author.
         */
        public Builder withIconUrl(@Nullable String iconUrl) {
            return this.withIconUrl(Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the icon url of the {@link Author}.
         *
         * @param iconUrl The icon url of the author.
         * @param args The objects to format with.
         */
        public Builder withIconUrl(@PrintFormat @Nullable String iconUrl, @Nullable Object... args) {
            return this.withIconUrl(StringUtil.formatNullable(iconUrl, args));
        }

        /**
         * Sets the icon url of the {@link Author}.
         *
         * @param iconUrl The icon url of the author.
         */
        public Builder withIconUrl(@NotNull Optional<String> iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        /**
         * Sets the name of the {@link Author}.
         *
         * @param name The name of the author.
         */
        public Builder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Sets the name of the {@link Author}.
         *
         * @param name The name of the author.
         * @param args The objects to format with.
         */
        public Builder withName(@PrintFormat @Nullable String name, @Nullable Object... args) {
            return this.withName(StringUtil.formatNullable(name, args));
        }

        /**
         * Sets the name of the {@link Author}.
         *
         * @param name The url of the author.
         */
        public Builder withName(@NotNull Optional<String> name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the url of the {@link Author}.
         *
         * @param url The url of the author.
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the url of the {@link Author}.
         *
         * @param url The url of the author.
         * @param args The objects to format with.
         */
        public Builder withUrl(@PrintFormat @Nullable String url, @Nullable Object... args) {
            return this.withUrl(StringUtil.formatNullable(url, args));
        }

        /**
         * Sets the url of the {@link Author}.
         *
         * @param url The url of the author.
         */
        public Builder withUrl(@NotNull Optional<String> url) {
            this.url = url;
            return this;
        }

        public @NotNull Author build() {
            Reflection.validateFlags(this);

            return new Author(
                this.name.orElseThrow(),
                this.url,
                this.iconUrl
            );
        }

    }

}