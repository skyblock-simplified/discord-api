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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public final class Footer {

    private final @NotNull Optional<String> text;
    private final @NotNull Optional<String> iconUrl;
    private final @NotNull Optional<Instant> timestamp;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Footer footer = (Footer) o;

        return Objects.equals(this.getText(), footer.getText())
            && Objects.equals(this.getIconUrl(), footer.getIconUrl())
            && Objects.equals(this.getTimestamp(), footer.getTimestamp());
    }

    public static @NotNull Builder from(@NotNull Footer footer) {
        return builder()
            .withText(footer.getText())
            .withIconUrl(footer.getIconUrl())
            .withTimestamp(footer.getTimestamp());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getText(), this.getIconUrl(), this.getTimestamp());
    }

    public @NotNull EmbedCreateFields.Footer getD4jFooter() {
        return EmbedCreateFields.Footer.of(this.getText().orElse(""), this.getIconUrl().orElse(null));
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        @BuildFlag(notEmpty = true, group = "footer", limit = 2048)
        private Optional<String> text = Optional.empty();
        @BuildFlag(notEmpty = true, group = "footer")
        private Optional<String> iconUrl = Optional.empty();
        @BuildFlag(notEmpty = true, group = "footer")
        private Optional<Instant> timestamp = Optional.empty();

        /**
         * Sets the icon url of the {@link Footer}.
         *
         * @param iconUrl The icon url of the footer.
         */
        public Builder withIconUrl(@Nullable String iconUrl) {
            return this.withIconUrl(Optional.ofNullable(iconUrl));
        }

        /**
         * Sets the icon url of the {@link Footer}.
         *
         * @param iconUrl The icon url of the footer.
         * @param args The objects to format with.
         */
        public Builder withIconUrl(@PrintFormat @Nullable String iconUrl, @Nullable Object... args) {
            return this.withIconUrl(StringUtil.formatNullable(iconUrl, args));
        }

        /**
         * Sets the icon url of the {@link Footer}.
         *
         * @param iconUrl The icon url of the footer.
         */
        public Builder withIconUrl(@NotNull Optional<String> iconUrl) {
            this.iconUrl = iconUrl;
            return this;
        }

        /**
         * Sets the text of the {@link Footer}.
         *
         * @param text The text of the footer.
         */
        public Builder withText(@Nullable String text) {
            return this.withText(Optional.ofNullable(text));
        }

        /**
         * Sets the text of the {@link Footer}.
         *
         * @param text The text of the footer.
         * @param args The objects to format with.
         */
        public Builder withText(@PrintFormat @Nullable String text, @Nullable Object... args) {
            return this.withText(StringUtil.formatNullable(text, args));
        }

        /**
         * Sets the text of the {@link Footer}.
         *
         * @param text The text of the footer.
         */
        public Builder withText(@NotNull Optional<String> text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the timestamp to be shown in the {@link Footer}.
         *
         * @param timestamp Timestamp of the embed.
         */
        public Builder withTimestamp(@Nullable Instant timestamp) {
            return this.withTimestamp(Optional.ofNullable(timestamp));
        }

        /**
         * Sets the timestamp to be shown in the {@link Footer}.
         *
         * @param timestamp Timestamp of the embed.
         */
        public Builder withTimestamp(@NotNull Optional<Instant> timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public @NotNull Footer build() {
            Reflection.validateFlags(this);

            return new Footer(
                this.text,
                this.iconUrl,
                this.timestamp
            );
        }

    }

}