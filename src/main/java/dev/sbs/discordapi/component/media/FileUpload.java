package dev.sbs.discordapi.component.media;

import dev.sbs.discordapi.component.layout.Label;
import dev.sbs.discordapi.component.scope.LabelComponent;
import dev.simplified.util.StringUtil;
import discord4j.core.spec.MessageCreateFields;
import discord4j.discordjson.json.ComponentData;
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
 * An immutable file upload component wrapping {@link MediaData} with min/max value
 * constraints, a required flag, a Discord file ID, and file size.
 *
 * <p>
 * Can be wrapped in a {@link Label} for use in modal contexts. Delegates media
 * configuration to a nested {@link MediaData.Builder} during construction.
 *
 * @see MediaData
 * @see Label
 * @see Attachment
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FileUpload implements LabelComponent {

    /** The unique identifier for this file upload. */
    private final @NotNull String identifier;

    /** The minimum number of values accepted. */
    private final int minValues;

    /** The maximum number of values accepted. */
    private final int maxValues;

    /** Whether a file is required. */
    private final boolean required;

    /** The underlying media metadata. */
    private final @NotNull MediaData mediaData;

    /** The Discord file snowflake ID. */
    private final long fileId;

    /** The file size in bytes. */
    private final long size;

    /**
     * Creates a new builder.
     *
     * @return a new {@link Builder}
     */
    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        FileUpload that = (FileUpload) o;

        return Objects.equals(this.getMediaData(), that.getMediaData())
            && this.getFileId() == that.getFileId()
            && this.getSize() == that.getSize();
    }

    /**
     * Creates a pre-filled builder from the given file upload.
     *
     * @param attachment the file upload to copy from
     * @return a pre-filled {@link Builder}
     */
    public static @NotNull Builder from(@NotNull FileUpload attachment) {
        return new Builder()
            .withIdentifier(attachment.getIdentifier())
            .withMediaData(attachment.getMediaData())
            .withFileId(attachment.getFileId())
            .withSize(attachment.getSize());
    }

    /**
     * Creates a builder initialized from the given D4J attachment entity.
     *
     * @param d4jAttachment the D4J attachment to initialize from
     * @return a {@link Builder} populated with the attachment's data
     */
    public static @NotNull Builder from(@NotNull discord4j.core.object.entity.Attachment d4jAttachment) {
        return builder()
            .withName(d4jAttachment.getFilename())
            .withUrl(d4jAttachment.getUrl())
            .build()
            .mutate(d4jAttachment);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.File getD4jComponent() {
        return discord4j.core.object.component.File.of(
            this.getMediaData().getComponentId(),
            this.getMediaData().getD4jMediaItem(),
            this.getMediaData().isSpoiler()
        );
    }

    /**
     * Converts this file upload to a Discord4J {@link MessageCreateFields.File} for upload.
     *
     * @return a D4J file suitable for message creation
     * @throws dev.sbs.discordapi.exception.DiscordException if no upload stream is present
     * @see MediaData#getD4jFile()
     */
    public @NotNull MessageCreateFields.File getD4jFile() {
        return this.getMediaData().getD4jFile();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.FILE;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getMediaData());
    }

    /**
     * Returns {@code true} if this file upload has upload data pending.
     */
    public boolean isPendingUpload() {
        return this.getMediaData().isPendingUpload();
    }

    /** {@inheritDoc} */
    @Override
    public void updateFromData(@NotNull ComponentData data) {
        // File upload state is managed externally
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /**
     * Creates a pre-filled builder updated with data from the given D4J attachment entity.
     *
     * @param d4jAttachment the D4J attachment to update from
     */
    public @NotNull Builder mutate(@NotNull discord4j.core.object.entity.Attachment d4jAttachment) {
        return from(this)
            .withMediaData(this.getMediaData().mutate(d4jAttachment))
            .withFileId(d4jAttachment.getId().asLong())
            .withSize(d4jAttachment.getSize());
    }

    /**
     * Creates a pre-filled builder updated with data from the given D4J file component.
     *
     * @param d4jFile the D4J file component to update from
     */
    public @NotNull Builder mutate(@NotNull discord4j.core.object.component.File d4jFile) {
        return from(this)
            .withMediaData(this.getMediaData().mutate(d4jFile.getFile()));
    }

    /**
     * A builder for constructing {@link FileUpload} instances.
     * <p>
     * Media-related configuration (name, URL, upload stream, spoiler) is delegated to
     * a nested {@link MediaData.Builder}.
     */
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private String identifier;
        private int minValues = 1;
        private int maxValues = 1;
        private boolean required;
        private MediaData.Builder mediaData = MediaData.builder();
        private long fileId = 0L;
        private long size = 0;

        /**
         * Sets the identifier of the {@link FileUpload}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the minimum number of values accepted.
         *
         * @param minValues the minimum value count
         */
        public Builder withMinValues(int minValues) {
            this.minValues = minValues;
            return this;
        }

        /**
         * Sets the maximum number of values accepted.
         *
         * @param maxValues the maximum value count
         */
        public Builder withMaxValues(int maxValues) {
            this.maxValues = maxValues;
            return this;
        }

        /**
         * Sets the {@link FileUpload} as required.
         */
        public Builder setRequired() {
            return this.setRequired(true);
        }

        /**
         * Sets whether the {@link FileUpload} is required.
         *
         * @param value {@code true} to require a file
         */
        public Builder setRequired(boolean value) {
            this.required = value;
            return this;
        }

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
         * Sets the Discord file snowflake ID.
         *
         * @param id the file ID
         */
        public Builder withFileId(long id) {
            this.fileId = id;
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
         * Sets the file size in bytes.
         *
         * @param size the file size
         */
        public Builder withSize(long size) {
            this.size = size;
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
         * Builds a new {@link FileUpload} from the configured fields.
         */
        public @NotNull FileUpload build() {
            return new FileUpload(
                this.identifier,
                this.minValues,
                this.maxValues,
                this.required,
                this.mediaData.build(),
                this.fileId,
                this.size
            );
        }

    }

}
