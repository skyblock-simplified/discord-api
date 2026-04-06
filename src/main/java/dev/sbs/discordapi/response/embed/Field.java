package dev.sbs.discordapi.response.embed;

import dev.sbs.discordapi.response.Emoji;
import dev.simplified.collection.tuple.triple.Triple;
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
public final class Field {

    public static final int MAX_ALLOWED = 25;
    private final @NotNull Optional<String> name;
    private final @NotNull Optional<String> value;
    private final boolean inline;
    private final @NotNull Optional<Emoji> emoji;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull Field empty() {
        return empty(false);
    }

    public static @NotNull Field empty(boolean inline) {
        return builder().isInline(inline).build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Field field = (Field) o;

        return Objects.equals(this.getName(), field.getName())
            && Objects.equals(this.getValue(), field.getValue())
            && this.isInline() == field.isInline()
            && Objects.equals(this.getEmoji(), field.getEmoji());
    }

    public static @NotNull Builder from(@NotNull Field field) {
        return builder()
            .withName(field.getName())
            .withValue(field.getValue())
            .isInline(field.isInline())
            .withEmoji(field.getEmoji());
    }

    public static @NotNull Builder from(@NotNull Triple<String, String, Boolean> triple) {
        return builder()
            .withName(triple.getLeft())
            .withValue(triple.getMiddle())
            .isInline(triple.getRight());
    }

    public @NotNull EmbedCreateFields.Field getD4jField() {
        return EmbedCreateFields.Field.of(
            String.format(
                "%s%s",
                this.getEmoji().map(Emoji::asSpacedFormat).orElse(""),
                this.getName().orElse(" ")
            ),
            this.getValue().orElse(" "),
            this.isInline()
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getName(), this.getValue(), this.isInline(), this.getEmoji());
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private Optional<Emoji> emoji = Optional.empty();
        @BuildFlag(limit = 256)
        private Optional<String> name = Optional.empty();
        @BuildFlag(limit = 1024)
        private Optional<String> value = Optional.empty();
        private boolean inline = false;

        /**
         * Sets the {@link Field} to render inline.
         */
        public Builder isInline() {
            return this.isInline(true);
        }

        /**
         * Sets whether the {@link Field} should render inline.
         *
         * @param inline True to render field inline.
         */
        public Builder isInline(boolean inline) {
            this.inline = inline;
            return this;
        }

        /**
         * Sets the emoji of the {@link Field}.
         *
         * @param emoji The emoji of the field.
         */
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji of the {@link Field}.
         *
         * @param emoji The emoji of the field.
         */
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.emoji = emoji;
            return this;
        }

        /**
         * Sets the name of the {@link Field}.
         *
         * @param name The name of the field.
         */
        public Builder withName(@Nullable String name) {
            return this.withName(Optional.ofNullable(name));
        }

        /**
         * Formats the name of the {@link Field} with the given objects.
         *
         * @param name The name of the field.
         * @param args Objects used to format the name.
         */
        public Builder withName(@PrintFormat @Nullable String name, @Nullable Object... args) {
            return this.withName(StringUtil.formatNullable(name, args));
        }

        /**
         * Sets the name of the {@link Field}.
         *
         * @param name The name of the field.
         */
        public Builder withName(@NotNull Optional<String> name) {
            this.name = Optional.ofNullable(StringUtil.trimToNull(name.orElse("")));
            return this;
        }

        /**
         * Sets the value of the {@link Field}.
         *
         * @param value The value of the field.
         */
        public Builder withValue(@Nullable String value) {
            return this.withValue(Optional.ofNullable(value));
        }

        /**
         * Formats the value of the {@link Field} with the given objects.
         *
         * @param value The value of the field.
         * @param args Objects used to format the value.
         */
        public Builder withValue(@PrintFormat @Nullable String value, @Nullable Object... args) {
            return this.withValue(StringUtil.formatNullable(value, args));
        }

        /**
         * Sets the value of the {@link Field}.
         *
         * @param value The value of the field.
         */
        public Builder withValue(@NotNull Optional<String> value) {
            this.value = Optional.ofNullable(StringUtil.trimToNull(value.orElse("")));
            return this;
        }

        public @NotNull Field build() {
            Reflection.validateFlags(this);

            return new Field(
                this.name,
                this.value,
                this.inline,
                this.emoji
            );
        }

    }

}