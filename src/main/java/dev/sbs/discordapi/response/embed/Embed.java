package dev.sbs.discordapi.response.embed;

import dev.sbs.discordapi.component.TextDisplay;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.layout.Container;
import dev.sbs.discordapi.component.layout.Section;
import dev.sbs.discordapi.component.media.MediaGallery;
import dev.sbs.discordapi.component.media.Thumbnail;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.ExceptionUtil;
import dev.simplified.util.StringUtil;
import discord4j.core.spec.EmbedCreateSpec;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

@Getter
@AllArgsConstructor
public final class Embed {

    private final static @NotNull DateTimeFormatter FOOTER_TIME_FORMAT = DateTimeFormatter.ofPattern("M/dd/yyyy h:mm a", Locale.CANADA);
    private final @NotNull Optional<Color> color;
    private final @NotNull Optional<Author> author;
    private final @NotNull Optional<String> title;
    private final @NotNull Optional<String> url;
    private final @NotNull Optional<String> thumbnailUrl;
    private final @NotNull Optional<String> description;
    private final @NotNull Optional<String> imageUrl;
    private final @NotNull Optional<Footer> footer;
    private final @NotNull ConcurrentList<Field> fields;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Embed embed = (Embed) o;

        return Objects.equals(this.getColor(), embed.getColor())
            && Objects.equals(this.getAuthor(), embed.getAuthor())
            && Objects.equals(this.getTitle(), embed.getTitle())
            && Objects.equals(this.getUrl(), embed.getUrl())
            && Objects.equals(this.getThumbnailUrl(), embed.getThumbnailUrl())
            && Objects.equals(this.getDescription(), embed.getDescription())
            && Objects.equals(this.getImageUrl(), embed.getImageUrl())
            && Objects.equals(this.getFooter(), embed.getFooter())
            && Objects.equals(this.getFields(), embed.getFields());
    }

    public static @NotNull Builder from(@NotNull Embed embed) {
        return new Builder()
            .withColor(embed.getColor())
            .withAuthor(embed.getAuthor())
            .withTitle(embed.getTitle())
            .withUrl(embed.getUrl())
            .withThumbnailUrl(embed.getThumbnailUrl())
            .withDescription(embed.getDescription())
            .withImageUrl(embed.getImageUrl())
            .withFooter(embed.getFooter())
            .withFields(embed.getFields());
    }

    public static @NotNull Builder from(@NotNull Throwable throwable) {
        return new Builder()
            .withColor(Color.RED)
            .withTitle("An exception has occurred!")
            .withDescription(ExceptionUtil.getRootCauseMessage(throwable))
            .withFooter(
                Footer.builder()
                    .withTimestamp(Instant.now())
                    .build()
            );
    }

    public @NotNull EmbedCreateSpec getD4jEmbed() {
        EmbedCreateSpec.Builder builder = EmbedCreateSpec.builder()
            .title(this.getTitle().orElse(""))
            .description(this.getDescription().orElse(""))
            .url(this.getUrl().orElse(""))
            .color(discord4j.rest.util.Color.of(this.getColor().orElse(Color.WHITE).getRGB()))
            .image(this.getImageUrl().orElse(""))
            .thumbnail(this.getThumbnailUrl().orElse(""))
            .title(this.getTitle().orElse(""))
            .fields(this.getFields().stream().map(Field::getD4jField).collect(Concurrent.toList()));

        this.getAuthor().ifPresent(author -> builder.author(author.getD4jAuthor()));

        this.getFooter().ifPresent(footer -> {
            builder.footer(footer.getD4jFooter());
            footer.getTimestamp().ifPresent(builder::timestamp);
        });

        return builder.build();
    }

    public @NotNull discord4j.core.object.component.Container getD4jContainer() {
        Container.Builder builder = Container.builder();
        builder.withAccent(this.getColor());

        // Top of Embed
        StringJoiner joiner = new StringJoiner("\n");
        String authorLine = this.getAuthor().map(author -> {
            String text = author.getName();
            if (author.getUrl().isPresent()) text = String.format("[%s](%s)", text, author.getUrl().get());
            if (author.getIconUrl().isPresent()) text = String.format("%s %s", author.getIconUrl().get(), text);
            return text;
        }).orElse("");

        joiner.add(authorLine);
        String titleLine = this.getTitle().orElse("");
        if (StringUtil.isNotEmpty(titleLine) && this.getUrl().isPresent()) titleLine = String.format("[%s](%s)", titleLine, this.getUrl().get());
        if (StringUtil.isNotEmpty(titleLine)) joiner.add(String.format("### %s", titleLine));
        this.getDescription().ifPresent(joiner::add);

        String joinText = joiner.toString();
        if (StringUtil.isNotEmpty(joinText)) {
            TextDisplay textDisplay = TextDisplay.of(joiner.toString());

            if (this.getThumbnailUrl().isPresent()) {
                builder.withComponents(
                    Section.builder()
                        .withComponents(textDisplay)
                        .withAccessory(
                            Thumbnail.builder()
                                .withUrl(this.getThumbnailUrl())
                                .build()
                        )
                        .build()
                );
            } else
                builder.withComponents(textDisplay);
        }

        // TODO: Handle Fields

        if (this.getImageUrl().isPresent()) {
            builder.withComponents(
                MediaGallery.builder()
                    .withItem(
                        Thumbnail.builder()
                            .withUrl(this.getImageUrl())
                            .build()
                    )
                    .build()
            );
        }

        String footerLine = this.getFooter()
            .map(footer -> {
                StringJoiner footerJoiner = new StringJoiner(" • ");
                if (footer.getText().isPresent())
                    footerJoiner.add(footer.getText().orElseThrow());

                if (footer.getTimestamp().isPresent())
                    footerJoiner.add(FOOTER_TIME_FORMAT.format(footer.getTimestamp().orElseThrow()));

                return footerJoiner.toString();
            })
            .map(value -> String.format("-# %s", value))
            .orElse("");

        if (StringUtil.isNotEmpty(footerLine))
            builder.withComponents(TextDisplay.of(footerLine));

        return builder.build().getD4jComponent();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getColor(), this.getAuthor(), this.getTitle(), this.getUrl(), this.getThumbnailUrl(), this.getDescription(), this.getImageUrl(), this.getFooter(), this.getFields());
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        @BuildFlag(limit = 256)
        private Optional<String> title = Optional.empty();
        @BuildFlag(limit = 4096)
        private Optional<String> description = Optional.empty();
        private Optional<String> url = Optional.empty();
        private Optional<Color> color = Optional.empty();
        private Optional<String> imageUrl = Optional.empty();
        private Optional<String> thumbnailUrl = Optional.empty();
        private Optional<Footer> footer = Optional.empty();
        private Optional<Author> author = Optional.empty();
        @BuildFlag(limit = Field.MAX_ALLOWED)
        private ConcurrentList<Field> fields = Concurrent.newList();

        /**
         * Clears all existing {@link Field Fields}.
         */
        public Builder clearFields() {
            this.fields.clear();
            return this;
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param author The author of the embed.
         */
        public Builder withAuthor(@Nullable Author author) {
            return this.withAuthor(Optional.ofNullable(author));
        }

        /**
         * Sets the {@link Author} of the {@link Embed}.
         *
         * @param author The author of the embed.
         */
        public Builder withAuthor(@NotNull Optional<Author> author) {
            this.author = author;
            return this;
        }

        /**
         * Sets the line color used on the left side of the {@link Embed}.
         *
         * @param color The color of the embed.
         */
        public Builder withColor(@Nullable Color color) {
            return this.withColor(Optional.ofNullable(color));
        }

        /**
         * Sets the line color used on the left side of the {@link Embed}.
         *
         * @param color The color of the embed.
         */
        public Builder withColor(@NotNull Optional<Color> color) {
            this.color = color;
            return this;
        }

        /**
         * Formats the description of the {@link Embed} with the given objects.
         *
         * @param description The description of the embed.
         */
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Formats the description of the {@link Embed} with the given objects.
         *
         * @param description The description of the embed.
         */
        public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the description of the {@link Embed}.
         *
         * @param description The description of the embed.
         */
        public Builder withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        /**
         * Adds an empty {@link Field} to the {@link Embed}.
         */
        public Builder withEmptyField() {
            return this.withEmptyField(false);
        }

        /**
         * Adds an empty {@link Field} to the {@link Embed}.
         *
         * @param inline True if field should render inline.
         */
        public Builder withEmptyField(boolean inline) {
            return this.withField(null, Optional.empty(), inline);
        }

        /**
         * Adds a {@link Field} to the {@link Embed}.
         *
         * @param name The name of the field.
         * @param value The value of the field.
         */
        public Builder withField(@Nullable String name, @Nullable String value) {
            return this.withField(name, value, false);
        }

        /**
         * Adds a {@link Field} to the {@link Embed}.
         *
         * @param name The name of the field.
         * @param value The value of the field.
         * @param inline True if field should render inline.
         */
        public Builder withField(@Nullable String name, @Nullable String value, boolean inline) {
            return this.withField(name, Optional.ofNullable(value), inline);
        }

        /**
         * Adds a {@link Field} to the {@link Embed}.
         *
         * @param name The name of the field.
         * @param value The value of the field.
         */
        public Builder withField(@Nullable String name, @NotNull Optional<String> value) {
            return this.withField(name, value, false);
        }

        /**
         * Adds a {@link Field} to the {@link Embed}.
         *
         * @param name The name of the field.
         * @param value The value of the field.
         * @param inline True if field should render inline.
         */
        public Builder withField(@Nullable String name, @NotNull Optional<String> value, boolean inline) {
            return this.withFields(
                Field.builder()
                    .withName(name)
                    .withValue(value)
                    .isInline(inline)
                    .build()
            );
        }

        /**
         * Adds {@link Field Fields} to the {@link Embed}.
         *
         * @param fields Variable number of fields to add.
         */
        public Builder withFields(@NotNull Field... fields) {
            return this.withFields(Arrays.asList(fields));
        }

        /**
         * Adds {@link Field Fields} to the {@link Embed}.
         *
         * @param fields Collection of fields to add.
         */
        public Builder withFields(@NotNull Iterable<Field> fields) {
            fields.forEach(this.fields::add);
            return this;
        }

        /**
         * Sets the {@link Footer} of the {@link Embed}.
         *
         * @param footer The footer of the embed.
         */
        public Builder withFooter(@Nullable Footer footer) {
            return this.withFooter(Optional.ofNullable(footer));
        }

        /**
         * Sets the {@link Footer} of the {@link Embed}.
         *
         * @param footer The footer of the embed.
         */
        public Builder withFooter(@NotNull Optional<Footer> footer) {
            this.footer = footer;
            return this;
        }

        /**
         * Sets the image url used in the {@link Embed}.
         *
         * @param imageUrl The url of the embed image.
         */
        public Builder withImageUrl(@Nullable String imageUrl) {
            return this.withImageUrl(Optional.ofNullable(imageUrl));
        }

        /**
         * Formats the image url used in the {@link Embed} with the given objects.
         *
         * @param imageUrl The url of the embed image.
         * @param args Objects used to format the image url.
         */
        public Builder withImageUrl(@PrintFormat @Nullable String imageUrl, @Nullable Object... args) {
            return this.withImageUrl(StringUtil.formatNullable(imageUrl, args));
        }

        /**
         * Sets the image url used in the {@link Embed}.
         *
         * @param imageUrl The url of the embed image.
         */
        public Builder withImageUrl(@NotNull Optional<String> imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        /**
         * Sets the thumbnail image url used in the {@link Embed}.
         *
         * @param thumbnailUrl The url of the embed thumbnail image.
         */
        public Builder withThumbnailUrl(@Nullable String thumbnailUrl) {
            return this.withThumbnailUrl(Optional.ofNullable(thumbnailUrl));
        }

        /**
         * Formats the thumbnail image url used in the {@link Embed} with the given objects.
         *
         * @param thumbnailUrl The url of the embed thumbnail image.
         * @param args Objects used to format the thumbnail image url.
         */
        public Builder withThumbnailUrl(@PrintFormat @Nullable String thumbnailUrl, @Nullable Object... args) {
            return this.withThumbnailUrl(StringUtil.formatNullable(thumbnailUrl, args));
        }

        /**
         * Sets the thumbnail image url used in the {@link Embed}.
         *
         * @param thumbnailUrl The url of the embed thumbnail image.
         */
        public Builder withThumbnailUrl(@NotNull Optional<String> thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        /**
         * Sets the title text of the {@link Embed}.
         *
         * @param title Title of the embed.
         */
        public Builder withTitle(@Nullable String title) {
            return this.withTitle(Optional.ofNullable(title));
        }

        /**
         * Formats the title text of the {@link Embed} with given objects.
         *
         * @param title Title of the embed.
         * @param args Objects used to format the title.
         */
        public Builder withTitle(@PrintFormat @Nullable String title, @Nullable Object... args) {
            return this.withTitle(StringUtil.formatNullable(title, args));
        }

        /**
         * Sets the title text of the {@link Embed}.
         *
         * @param title Title of the embed.
         */
        public Builder withTitle(@NotNull Optional<String> title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the url used in the {@link Embed}'s title.
         * <br><br>
         * See {@link #getTitle()}.
         *
         * @param url The url of the embed title.
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the url used in the {@link Embed}'s title.
         * <br><br>
         * See {@link #getTitle()}.
         *
         * @param url The url of the embed title.
         * @param args Objects used to format the url.
         */
        public Builder withUrl(@PrintFormat @Nullable String url, @Nullable Object... args) {
            return this.withUrl(StringUtil.formatNullable(url, args));
        }

        /**
         * Sets the url of the {@link Embed}'s title.
         * <br><br>
         * See {@link #getTitle()}.
         *
         * @param url The url of the embed title.
         */
        public Builder withUrl(@NotNull Optional<String> url) {
            this.url = url;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link SelectMenu} component.
         */
        public @NotNull Embed build() {
            Reflection.validateFlags(this);

            return new Embed(
                this.color,
                this.author,
                this.title,
                this.url,
                this.thumbnailUrl,
                this.description,
                this.imageUrl,
                this.footer,
                this.fields.toUnmodifiable()
            );
        }

    }

}