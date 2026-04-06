package dev.sbs.discordapi.component.media;

import dev.sbs.discordapi.component.layout.Container;
import dev.sbs.discordapi.component.scope.ContainerComponent;
import dev.sbs.discordapi.component.scope.TopLevelMessageComponent;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * An immutable layout component displaying multiple {@link Thumbnail} items in a gallery grid.
 *
 * <p>
 * Can be used as a top-level message component or nested within a {@link Container}. Each
 * gallery item is converted to a D4J {@link discord4j.core.object.component.MediaGalleryItem}
 * via {@link Thumbnail#getD4jGalleryItem()}.
 *
 * @see Thumbnail
 * @see Container
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MediaGallery implements TopLevelMessageComponent, ContainerComponent {

    /** The thumbnail items displayed in the gallery. */
    private final @NotNull ConcurrentList<Thumbnail> items;

    /**
     * Creates a new builder.
     *
     * @return a new {@link Builder}
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Creates a pre-filled builder from the given media gallery.
     *
     * @param mediaGallery the media gallery to copy from
     * @return a pre-filled {@link Builder}
     */
    public static @NotNull Builder from(@NotNull MediaGallery mediaGallery) {
        return builder().withItems(mediaGallery.getItems());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.MediaGallery getD4jComponent() {
        return discord4j.core.object.component.MediaGallery.of(
            this.getItems()
                .stream()
                .map(Thumbnail::getD4jGalleryItem)
                .collect(Concurrent.toList())
        );
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.MEDIA_GALLERY;
    }

    /**
     * A builder for constructing {@link MediaGallery} instances.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        private @NotNull ConcurrentList<Thumbnail> items = Concurrent.newList();

        /**
         * Adds a single thumbnail item to the gallery.
         *
         * @param thumbnail the thumbnail to add
         */
        public Builder withItem(@NotNull Thumbnail thumbnail) {
            this.items.add(thumbnail);
            return this;
        }

        /**
         * Adds multiple thumbnail items to the gallery.
         *
         * @param items the thumbnails to add
         */
        public Builder withItems(@NotNull Thumbnail... items) {
            return this.withItems(Arrays.asList(items));
        }

        /**
         * Adds multiple thumbnail items from the given iterable to the gallery.
         *
         * @param items the thumbnails to add
         */
        public Builder withItems(@NotNull Iterable<Thumbnail> items) {
            items.forEach(this.items::add);
            return this;
        }

        /**
         * Builds a new {@link MediaGallery} from the configured fields.
         */
        public @NotNull MediaGallery build() {
            return new MediaGallery(this.items);
        }

    }

}
