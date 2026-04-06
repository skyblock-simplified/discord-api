package dev.sbs.discordapi.response.page.item;

import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.simplified.collection.ConcurrentMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public interface Item {

    @NotNull Item applyVariables(@NotNull ConcurrentMap<String, Object> variables);

    /**
     * Casts the current {@link Item} to the given type {@link T}.
     * <br><br>
     * Throws {@link ClassCastException} if it's cast incorrectly.
     *
     * @param type Class to cast to.
     * @param <T> Type to cast to.
     */
    default <T extends Item> @NotNull T asType(@NotNull Class<T> type) {
        return type.cast(this);
    }

    default @NotNull String getIdentifier() {
        return this.getOption().getValue();
    }

    @NotNull SelectMenu.Option getOption();

    @NotNull Type getType();

    boolean isEditable();

    default boolean isSingular() {
        return true;
    }

    @Getter
    @RequiredArgsConstructor
    enum Type {

        UNKNOWN(-1, true),
        PAGE(1, true),
        AUTHOR(2, false),
        TITLE(3, false),
        DESCRIPTION(4, false),
        THUMBNAIL_URL(5, false),
        IMAGE_URL(6, false),
        FIELD(7, true),
        FOOTER(8, false);

        private final int value;
        private final boolean fieldRender;

        public static @NotNull Type of(int value) {
            return Arrays.stream(values())
                .filter(style -> style.getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

}
