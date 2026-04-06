package dev.sbs.discordapi.component.media;

import dev.sbs.discordapi.exception.DiscordException;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.NumberUtil;
import dev.simplified.util.StringUtil;
import discord4j.core.object.component.UnfurledMediaItem;
import discord4j.core.spec.MessageCreateFields;
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
import java.util.UUID;

/**
 * An immutable value object holding media metadata for Discord file and media components.
 * <p>
 * Encapsulates a component ID, file name, spoiler flag, URL, optional upload stream,
 * optional proxy URL, dimensions, content type, and loading state. Instances are
 * constructed via the {@link Builder}, which enforces that either a {@code url} or an
 * {@code uploadStream} is provided through a {@link BuildFlag} group constraint.
 * <p>
 * Provides conversion helpers for Discord4J types:
 * <ul>
 *   <li>{@link #getD4jFile()} - converts to a {@link MessageCreateFields.File} for upload</li>
 *   <li>{@link #getD4jMediaItem()} - converts to an {@link UnfurledMediaItem} for embedding</li>
 * </ul>
 *
 * @see Attachment
 * @see FileUpload
 * @see Thumbnail
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class MediaData {

    /** The unique component identifier. */
    private final int componentId;

    /** The file name. */
    private final String name;

    /** Whether this media is marked as a spoiler. */
    private final boolean spoiler;

    /** The URL of the media resource. */
    private final @NotNull String url;

    /** The optional upload stream for pending file data. */
    private final @NotNull Optional<InputStream> uploadStream;

    /** The optional CDN proxy URL. */
    private final @NotNull Optional<String> proxyUrl;

    /** The width of the media in pixels. */
    private final int width;

    /** The height of the media in pixels. */
    private final int height;

    /** The MIME content type of the media. */
    private final @NotNull String contentType;

    /** The current loading state. */
    private final @NotNull State state;

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

        MediaData mediaData = (MediaData) o;

        return this.getComponentId() == mediaData.getComponentId()
            && Objects.equals(this.getName(), mediaData.getName())
            && this.isSpoiler() == mediaData.isSpoiler()
            && Objects.equals(this.getUrl(), mediaData.getUrl())
            && Objects.equals(this.getUploadStream(), mediaData.getUploadStream())
            && Objects.equals(this.getProxyUrl(), mediaData.getProxyUrl())
            && this.getWidth() == mediaData.getWidth()
            && this.getHeight() == mediaData.getHeight()
            && Objects.equals(this.getContentType(), mediaData.getContentType())
            && Objects.equals(this.getState(), mediaData.getState());
    }

    /**
     * Creates a pre-filled builder from the given media data.
     *
     * @param mediaData the media data to copy from
     * @return a pre-filled {@link Builder}
     */
    public static @NotNull Builder from(@NotNull MediaData mediaData) {
        return builder()
            .withFileId(mediaData.getComponentId())
            .withName(mediaData.getName())
            .isSpoiler(mediaData.isSpoiler())
            .withUrl(mediaData.getUrl())
            .withStream(mediaData.getUploadStream())
            .withProxyUrl(mediaData.getProxyUrl())
            .withWidth(mediaData.getWidth())
            .withHeight(mediaData.getHeight())
            .withContentType(mediaData.getContentType())
            .withState(mediaData.getState());
    }

    /**
     * Converts this media data to a Discord4J {@link MessageCreateFields.File} for upload.
     * <p>
     * Throws a {@link DiscordException} if no upload stream is present, indicating the
     * file has already been uploaded.
     *
     * @return a D4J file suitable for message creation
     * @throws DiscordException if the upload stream is empty
     */
    public @NotNull MessageCreateFields.File getD4jFile() {
        if (this.getUploadStream().isEmpty())
            throw new DiscordException("The file named %s has already been uploaded.", this.getName());

        return this.isSpoiler() ?
            MessageCreateFields.File.of(this.getName(), this.getUploadStream().orElseThrow()) :
            MessageCreateFields.FileSpoiler.of(this.getName(), this.getUploadStream().orElseThrow());
    }

    /**
     * Converts this media data to a Discord4J {@link UnfurledMediaItem}.
     * <p>
     * If an upload stream is present, the returned item wraps the file for upload;
     * otherwise it wraps the URL for embedding.
     *
     * @return a D4J unfurled media item
     */
    public @NotNull UnfurledMediaItem getD4jMediaItem() {
        if (this.getUploadStream().isPresent())
            return UnfurledMediaItem.of(this.getD4jFile());
        else
            return UnfurledMediaItem.of(this.getUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getComponentId(), this.getName(), this.isSpoiler(), this.getUrl(), this.getUploadStream(), this.getProxyUrl(), this.getWidth(), this.getHeight(), this.getContentType(), this.getState());
    }

    /**
     * Returns {@code true} if this media data has upload data pending.
     */
    public boolean isPendingUpload() {
        return this.getUploadStream().isPresent();
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /**
     * Creates a pre-filled builder updated with data from the given D4J attachment.
     *
     * @param d4jAttachment the D4J attachment to update from
     */
    public @NotNull Builder mutate(@NotNull discord4j.core.object.entity.Attachment d4jAttachment) {
        return mutate()
            .withUrl(d4jAttachment.getUrl())
            .withProxyUrl(d4jAttachment.getProxyUrl())
            .withWidth(d4jAttachment.getWidth().orElse(0))
            .withHeight(d4jAttachment.getHeight().orElse(0))
            .withContentType(d4jAttachment.getContentType())
            .withState(State.LOADED_SUCCESS);
    }

    /**
     * Creates a pre-filled builder updated with data from the given D4J unfurled media item.
     *
     * @param d4jMediaItem the D4J unfurled media item to update from
     */
    public @NotNull Builder mutate(@NotNull UnfurledMediaItem d4jMediaItem) {
        return mutate()
            .withUrl(d4jMediaItem.getURL())
            .withProxyUrl(d4jMediaItem.getProxyUrl())
            .withWidth(d4jMediaItem.getWidth())
            .withHeight(d4jMediaItem.getHeight())
            .withContentType(d4jMediaItem.getContentType())
            .withState(State.LOADED_SUCCESS);
    }

    /**
     * A builder for constructing {@link MediaData} instances.
     * <p>
     * The builder requires either a {@code url} or an {@code uploadStream} to be set,
     * enforced by a {@link BuildFlag} group constraint named {@code "file"}.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private int fileId = NumberUtil.rand(0);
        private String name = UUID.randomUUID().toString();
        private boolean spoiler = false;
        private Optional<String> proxyUrl = Optional.empty();
        private int width = 0;
        private int height = 0;
        private Optional<String> contentType = Optional.empty();
        private State state = State.LOADING;

        @BuildFlag(notEmpty = true, group = "file")
        private Optional<String> url = Optional.empty();
        @BuildFlag(notEmpty = true, group = "file")
        private Optional<InputStream> uploadStream = Optional.empty();

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
            this.spoiler = value;
            return this;
        }

        /**
         * Sets the MIME content type.
         *
         * @param contentType the content type, or {@code null} to clear
         */
        public Builder withContentType(@Nullable String contentType) {
            return this.withContentType(Optional.ofNullable(contentType));
        }

        /**
         * Sets the MIME content type.
         *
         * @param contentType the content type
         */
        public Builder withContentType(@NotNull Optional<String> contentType) {
            this.contentType = contentType;
            return this;
        }

        /**
         * Sets the component file identifier.
         *
         * @param fileId the file identifier
         */
        public Builder withFileId(int fileId) {
            this.fileId = fileId;
            return this;
        }

        /**
         * Sets the media height in pixels.
         *
         * @param height the height in pixels
         */
        public Builder withHeight(int height) {
            this.height = height;
            return this;
        }

        /**
         * Sets the file name.
         *
         * @param name the file name
         */
        public Builder withName(@NotNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the file name using a format string.
         *
         * @param name the format string for the file name
         * @param args the format arguments
         */
        public Builder withName(@NotNull @PrintFormat String name, @Nullable Object... args) {
            this.name = String.format(name, args);
            return this;
        }

        /**
         * Sets the CDN proxy URL.
         *
         * @param proxyUrl the proxy URL, or {@code null} to clear
         */
        public Builder withProxyUrl(@Nullable String proxyUrl) {
            return this.withProxyUrl(Optional.ofNullable(proxyUrl));
        }

        /**
         * Sets the CDN proxy URL.
         *
         * @param proxyUrl the proxy URL
         */
        public Builder withProxyUrl(@NotNull Optional<String> proxyUrl) {
            this.proxyUrl = proxyUrl;
            return this;
        }

        /**
         * Sets the loading state.
         *
         * @param state the loading state
         */
        public Builder withState(@NotNull State state) {
            this.state = state;
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
            this.uploadStream = uploadStream;
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
            this.url = url;
            return this;
        }

        /**
         * Sets the media width in pixels.
         *
         * @param width the width in pixels
         */
        public Builder withWidth(int width) {
            this.width = width;
            return this;
        }

        /**
         * Builds a new {@link MediaData} from the configured fields.
         *
         * @throws dev.simplified.reflection.exception.ReflectionException if neither url nor uploadStream is set
         */
        public @NotNull MediaData build() {
            Reflection.validateFlags(this);

            return new MediaData(
                this.fileId,
                this.name,
                this.spoiler,
                this.url.orElse(""),
                this.uploadStream,
                this.proxyUrl,
                this.width,
                this.height,
                this.contentType.orElse("multipart/form-data"),
                this.state
            );
        }

    }

    /**
     * The loading state of a media resource.
     */
    @Getter
    @RequiredArgsConstructor
    public enum State {

        /** Unrecognized or default state. */
        UNKNOWN(0),

        /** The media is currently being loaded. */
        LOADING(1),

        /** The media loaded successfully. */
        LOADED_SUCCESS(2),

        /** The media was not found during loading. */
        LOADED_NOT_FOUND(3);

        /** The integer value identifying this state. */
        private final int value;

        /**
         * Returns the constant matching the given value, or {@code UNKNOWN} if unrecognized.
         *
         * @param value the integer value to look up
         */
        public static @NotNull State of(int value) {
            return switch (value) {
                case 1 -> LOADING;
                case 2 -> LOADED_SUCCESS;
                case 3 -> LOADED_NOT_FOUND;
                default -> UNKNOWN;
            };
        }

    }

}
