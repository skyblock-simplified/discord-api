package dev.sbs.discordapi.component.media;

import dev.sbs.discordapi.component.layout.Section;
import dev.sbs.discordapi.component.scope.AccessoryComponent;
import dev.simplified.util.StringUtil;
import discord4j.core.object.component.MediaGalleryItem;
import discord4j.core.object.component.UnfurledMediaItem;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable media component displaying an image with an optional description.
 *
 * <p>
 * Can be used as an accessory within a {@link Section}. Wraps {@link MediaData} for
 * underlying media metadata and provides conversion to both a D4J
 * {@link discord4j.core.object.component.Thumbnail} component and a
 * {@link MediaGalleryItem} for use inside a {@link MediaGallery}.
 *
 * @see MediaData
 * @see Section
 * @see MediaGallery
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Thumbnail implements AccessoryComponent {

    /** The underlying media metadata. */
    private final @NotNull MediaData mediaData;

    /** The optional alt-text description of the image. */
    private final @NotNull Optional<String> description;

    /**
     * Creates a new builder.
     *
     * @return a new {@link Builder}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Thumbnail thumbnail = (Thumbnail) o;

        return Objects.equals(this.getMediaData(), thumbnail.getMediaData())
            && Objects.equals(this.getDescription(), thumbnail.getDescription());
    }

    /**
     * Creates a pre-filled builder from the given thumbnail.
     *
     * @param thumbnail the thumbnail to copy from
     * @return a pre-filled {@link Builder}
     */
    public static @NotNull Builder from(@NotNull Thumbnail thumbnail) {
        return builder()
            .withMediaData(thumbnail.getMediaData())
            .withDescription(thumbnail.getDescription());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.Thumbnail getD4jComponent() {
        return discord4j.core.object.component.Thumbnail.of(
            this.getMediaData().getComponentId(),
            UnfurledMediaItem.of(this.getMediaData().getUrl()),
            this.getDescription().orElse(""),
            this.getMediaData().isSpoiler()
        );
    }

    /**
     * Converts this thumbnail to a Discord4J {@link MediaGalleryItem} for use inside
     * a {@link MediaGallery}.
     *
     * @return a D4J media gallery item
     */
    public @NotNull MediaGalleryItem getD4jGalleryItem() {
        return MediaGalleryItem.of(
            UnfurledMediaItem.of(this.getMediaData().getUrl()),
            this.getDescription().orElse(""),
            this.getMediaData().isSpoiler()
        );
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.THUMBNAIL;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getMediaData(), this.getDescription());
    }

    /**
     * Returns {@code true} if this thumbnail has upload data pending.
     */
    public boolean isPendingUpload() {
        return this.getMediaData().isPendingUpload();
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /**
     * Creates a pre-filled builder updated with data from the given D4J thumbnail component.
     *
     * @param d4jThumbnail the D4J thumbnail to update from
     */
    public @NotNull Builder mutate(@NotNull discord4j.core.object.component.Thumbnail d4jThumbnail) {
        return from(this).withMediaData(this.getMediaData().mutate(d4jThumbnail.getMedia()));
    }

    /**
     * A builder for constructing {@link Thumbnail} instances.
     * <p>
     * Media-related configuration (name, URL, upload stream, spoiler) is delegated to
     * a nested {@link MediaData.Builder}.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private MediaData.Builder mediaData = MediaData.builder();
        private Optional<String> description = Optional.empty();

        /**
         * Sets the spoiler flag to {@code true}.
         */
        public Builder isSpoiler() {
            return this.isSpoiler(true);
        }

        /**
         * Sets the spoiler flag.
         *
         * @param value {@code true} to mark as a spoiler
         */
        public Builder isSpoiler(boolean value) {
            this.mediaData.isSpoiler(value);
            return this;
        }

        /**
         * Sets the alt-text description.
         *
         * @param description the description, or {@code null} to clear
         */
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * Sets the alt-text description using a format string.
         *
         * @param description the format string for the description
         * @param args the format arguments
         */
        public Builder withDescription(@Nullable @PrintFormat String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * Sets the alt-text description.
         *
         * @param description the description
         */
        public Builder withDescription(@NotNull Optional<String> description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the file name.
         *
         * @param name the file name
         */
        public Builder withName(@NotNull String name) {
            this.mediaData.withName(name);
            return this;
        }

        /**
         * Sets the file name using a format string.
         *
         * @param name the format string for the file name
         * @param args the format arguments
         */
        public Builder withName(@NotNull @PrintFormat String name, @Nullable Object... args) {
            this.mediaData.withName(String.format(name, args));
            return this;
        }

        /**
         * Sets the upload stream for pending file data.
         *
         * @param uploadStream the input stream, or {@code null} to clear
         */
        public Builder withStream(@Nullable InputStream uploadStream) {
            return this.withStream(Optional.ofNullable(uploadStream));
        }

        /**
         * Sets the upload stream for pending file data.
         *
         * @param uploadStream the input stream
         */
        public Builder withStream(@NotNull Optional<InputStream> uploadStream) {
            this.mediaData.withStream(uploadStream);
            return this;
        }

        /**
         * Sets the media URL.
         *
         * @param url the URL, or {@code null} to clear
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the media URL using a format string.
         *
         * @param url the format string for the URL
         * @param args the format arguments
         */
        public Builder withUrl(@Nullable @PrintFormat String url, @Nullable Object... args) {
            return this.withUrl(StringUtil.formatNullable(url, args));
        }

        /**
         * Sets the media URL.
         *
         * @param url the URL
         */
        public Builder withUrl(@NotNull Optional<String> url) {
            this.mediaData.withUrl(url);
            return this;
        }

        private Builder withMediaData(@NotNull MediaData mediaData) {
            this.mediaData = mediaData.mutate();
            return this;
        }

        private Builder withMediaData(@NotNull MediaData.Builder mediaData) {
            this.mediaData = mediaData;
            return this;
        }

        /**
         * Builds a new {@link Thumbnail} from the configured fields.
         */
        public @NotNull Thumbnail build() {
            return new Thumbnail(
                this.mediaData.build(),
                this.description
            );
        }

    }

}
