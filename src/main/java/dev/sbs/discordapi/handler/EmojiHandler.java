package dev.sbs.discordapi.handler;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.util.DiscordReference;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.reflection.info.ResourceInfo;
import dev.simplified.util.Range;
import discord4j.core.object.entity.ApplicationEmoji;
import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.spec.ApplicationEmojiCreateSpec;
import discord4j.rest.util.Image;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Handler for custom application emojis, managing the discovery of
 * classpath-based emoji resources, uploading new emojis to the Discord
 * application, and caching the current set of registered emojis for
 * lookup by name.
 *
 * @see ResourceEmoji
 * @see Emoji
 */
@Getter
@Log4j2
public final class EmojiHandler extends DiscordReference {

    /** Valid Discord emoji name length range. */
    private static final Range<Integer> EMOJI_NAME_LENGTH = Range.between(2, 32);

    /** Emoji resources discovered from the classpath at construction time. */
    private final @NotNull ConcurrentSet<ResourceEmoji> resourceEmojis;

    /** Cached list of emojis currently registered with the Discord application. */
    private @NotNull ConcurrentList<Emoji> emojis = Concurrent.newList();

    /**
     * Constructs a new {@code EmojiHandler} and resolves classpath emoji
     * resources from the bot's {@link DiscordBot#getConfig() configuration}.
     *
     * @param discordBot the bot this handler belongs to
     */
    public EmojiHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);

        this.resourceEmojis = this.getDiscordBot()
            .getConfig()
            .getEmojis()
            .stream()
            .map(ResourceEmoji::new)
            .collect(Concurrent.toUnmodifiableSet());
    }

    /**
     * Deletes all emojis currently registered with the Discord application.
     *
     * @return a mono that completes when all emojis have been deleted
     */
    public @NotNull Mono<Void> purgeAll() {
        return this.getDiscordBot()
            .getGateway()
            .getApplicationInfo()
            .flatMapMany(ApplicationInfo::getEmojis)
            .flatMap(ApplicationEmoji::delete)
            .then();
    }

    /**
     * Synchronizes application emojis in a single reactor chain - purges
     * orphaned emojis not backed by a classpath resource, uploads missing
     * resource emojis, and refreshes the local cache using one
     * {@link ApplicationInfo} fetch.
     *
     * @return a mono that completes when synchronization is finished
     */
    public @NotNull Mono<Void> sync() {
        return this.getDiscordBot()
            .getGateway()
            .getApplicationInfo()
            .flatMap(applicationInfo -> applicationInfo.getEmojis()
                .collectList()
                .flatMap(registeredEmojis -> {
                    log.info("Registering Emojis");

                    // Purge: delete registered emojis with no matching resource
                    Flux<Void> purge = Flux.fromIterable(registeredEmojis)
                        .filter(appEmoji -> this.resourceEmojis.stream()
                            .noneMatch(res -> res.getName().equalsIgnoreCase(appEmoji.getName()))
                        )
                        .flatMap(ApplicationEmoji::delete);

                    // Upload: create resource emojis not yet registered
                    Flux<?> upload = Flux.fromStream(
                        this.resourceEmojis.stream()
                            .filter(resourceEmoji -> {
                                if (!EMOJI_NAME_LENGTH.contains(resourceEmoji.getName().length())) {
                                    log.warn(
                                        "Skipping emoji '{}' - name length {} is outside valid range {}",
                                        resourceEmoji.getName(),
                                        resourceEmoji.getName().length(),
                                        EMOJI_NAME_LENGTH
                                    );
                                    return false;
                                }

                                return true;
                            })
                            .filter(resourceEmoji -> registeredEmojis.stream()
                                .noneMatch(appEmoji -> appEmoji.getName().equalsIgnoreCase(resourceEmoji.getName()))
                            )
                            .map(resourceEmoji -> ApplicationEmojiCreateSpec.builder()
                                .name(resourceEmoji.getName())
                                .image(Image.ofRaw(
                                    resourceEmoji.getResourceInfo().toBytes(),
                                    resourceEmoji.getFormat()
                                ))
                                .build())
                            .map(applicationInfo::createEmoji)
                        )
                        .flatMap(Function.identity());

                    return purge.thenMany(upload)
                        .then()
                        .doOnSuccess(__ -> log.info("Emojis Registered"));
                })
                // Reload: refresh cache from final application state
                .then(applicationInfo.getEmojis()
                    .map(Emoji::of)
                    .collectList()
                    .doOnNext(list -> this.emojis = list.stream().collect(Concurrent.toUnmodifiableList()))
                    .then()
                )
            );
    }

    /**
     * Representation of a classpath-sourced emoji image resource, including
     * its derived name, file extension, and {@link Image.Format}.
     */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ResourceEmoji {

        /** Classpath resource metadata for the emoji image file. */
        private final @NotNull ResourceInfo resourceInfo;

        /** Derived emoji name, converted from the resource path. */
        private final @NotNull String name;

        /** File extension of the emoji image (e.g., "png", "gif"). */
        private final @NotNull String extension;

        /** Discord image format corresponding to the file extension. */
        private final @NotNull Image.Format format;

        /**
         * Constructs a new {@code ResourceEmoji} from the given classpath
         * resource, deriving the name, extension, and format automatically.
         *
         * @param resourceInfo the classpath resource metadata
         */
        private ResourceEmoji(@NotNull ResourceInfo resourceInfo) {
            this.resourceInfo = resourceInfo;
            this.name = getName(resourceInfo);
            this.extension = resourceInfo.getExtension();
            this.format = fromExtension(this.extension);
        }

        /**
         * Derives the emoji name from the resource path by extracting the
         * immediate parent directory and filename (without extension),
         * concatenating them as {@code parent_filename} in lowercase.
         *
         * @param resourceInfo the classpath resource metadata
         * @return the derived emoji name
         */
        private static @NotNull String getName(@NotNull ResourceInfo resourceInfo) {
            String path = resourceInfo.getPath();
            String parent = path.substring(path.lastIndexOf('/') + 1);
            return String.format("%s_%s", parent, resourceInfo.getName()).toUpperCase();
        }

        /**
         * Maps a file extension string to the corresponding Discord
         * {@link Image.Format}.
         *
         * @param extension the lowercase file extension without a leading dot
         * @return the matching image format, or {@link Image.Format#UNKNOWN}
         *         if unrecognized
         */
        private static @NotNull Image.Format fromExtension(@NotNull String extension) {
            return switch (extension) {
                case "jpg", "jpeg" -> Image.Format.JPEG;
                case "png" -> Image.Format.PNG;
                case "webp" -> Image.Format.WEB_P;
                case "gif" -> Image.Format.GIF;
                default -> Image.Format.UNKNOWN;
            };
        }

    }

}
