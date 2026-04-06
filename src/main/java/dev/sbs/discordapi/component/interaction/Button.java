package dev.sbs.discordapi.component.interaction;

import dev.sbs.discordapi.component.capability.EventInteractable;
import dev.sbs.discordapi.component.capability.Toggleable;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.scope.AccessoryComponent;
import dev.sbs.discordapi.component.scope.ActionComponent;
import dev.sbs.discordapi.context.component.ButtonContext;
import dev.sbs.discordapi.context.scope.ComponentContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.handler.PaginationHandler;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * An immutable interactive button component rendered within a Discord message.
 * <p>
 * Buttons appear inside an {@link ActionRow} and support multiple visual {@link Style styles}
 * including primary, secondary, success, danger, and link. Each button may display an
 * {@link Emoji}, a text label, or both. A {@link PageType} can be assigned to provide
 * built-in pagination behavior for item-handler-backed responses.
 * <p>
 * Instances are created via {@link #builder()} and can be copied for modification
 * via {@link #mutate()}.
 *
 * @see ActionRow
 * @see Style
 * @see PageType
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Button implements ActionComponent, AccessoryComponent, EventInteractable<ButtonContext>, Toggleable {

    private static final Function<ButtonContext, Mono<Void>> NOOP_HANDLER = ComponentContext::deferEdit;

    /** The unique identifier for this button. */
    private final @NotNull String identifier;

    /** The visual style of this button. */
    private final @NotNull Style style;

    /** The optional emoji displayed on this button. */
    private final @NotNull Optional<Emoji> emoji;

    /** The optional text label displayed on this button. */
    private final @NotNull Optional<String> label;

    /** The optional URL opened when a {@link Style#LINK} button is clicked. */
    private final @NotNull Optional<String> url;

    /** Whether the interaction is automatically deferred as an edit. */
    private final boolean deferEdit;

    /** The built-in pagination behavior assigned to this button. */
    private final @NotNull PageType pageType;

    /** The interaction handler invoked when this button is clicked. */
    private final @NotNull Function<ButtonContext, Mono<Void>> interaction;

    /** Whether this button is currently enabled. */
    private boolean enabled;

    /**
     * Creates a new builder with a random identifier.
     *
     * @return a new {@link Builder} instance
     */
    public static @NotNull Builder builder() {
        return new Builder().withIdentifier(UUID.randomUUID().toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Button button = (Button) o;

        return Objects.equals(this.getIdentifier(), button.getIdentifier())
            && Objects.equals(this.getStyle(), button.getStyle())
            && this.isEnabled() == button.isEnabled()
            && Objects.equals(this.getEmoji(), button.getEmoji())
            && Objects.equals(this.getLabel(), button.getLabel())
            && Objects.equals(this.getUrl(), button.getUrl())
            && this.isDeferEdit() == button.isDeferEdit()
            && Objects.equals(this.getPageType(), button.getPageType());
    }

    /**
     * Creates a pre-filled builder from the given button.
     *
     * @param button the button to copy fields from
     * @return a pre-filled {@link Builder} instance
     */
    public static @NotNull Builder from(@NotNull Button button) {
        return new Builder()
            .withIdentifier(button.getIdentifier())
            .withStyle(button.getStyle())
            .setDisabled(button.isEnabled())
            .withEmoji(button.getEmoji())
            .withLabel(button.getLabel())
            .withUrl(button.getUrl())
            .withDeferEdit(button.isDeferEdit())
            .withPageType(button.getPageType())
            .onInteract(button.getInteraction());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull discord4j.core.object.component.Button getD4jComponent() {
        discord4j.core.object.emoji.Emoji d4jReaction = this.getEmoji().map(Emoji::getD4jReaction).orElse(null);
        String label = this.getLabel().orElse(null);

        return (switch (this.getStyle()) {
            case PRIMARY -> discord4j.core.object.component.Button.primary(this.getIdentifier(), d4jReaction, label);
            case SUCCESS -> discord4j.core.object.component.Button.success(this.getIdentifier(), d4jReaction, label);
            case DANGER -> discord4j.core.object.component.Button.danger(this.getIdentifier(), d4jReaction, label);
            case LINK -> discord4j.core.object.component.Button.link(this.getUrl().orElse(""), d4jReaction, label);
            case SECONDARY, UNKNOWN -> discord4j.core.object.component.Button.secondary(this.getIdentifier(), d4jReaction, label);
        }).disabled(this.isEnabled());
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Type getType() {
        return Type.BUTTON;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.getStyle(), this.isEnabled(), this.getEmoji(), this.getLabel(), this.getUrl(), this.getPageType());
    }

    /**
     * Creates a pre-filled builder from this instance for modification.
     *
     * @return a pre-filled {@link Builder} instance
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /** {@inheritDoc} */
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * A builder for constructing {@link Button} instances.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {

        @BuildFlag(nonNull = true)
        private String identifier;
        @BuildFlag(nonNull = true)
        private Style style = Style.UNKNOWN;
        private boolean disabled;
        private boolean deferEdit;
        @BuildFlag(nonNull = true)
        private PageType pageType = PageType.NONE;
        private Optional<Function<ButtonContext, Mono<Void>>> interaction = Optional.empty();
        @BuildFlag(nonNull = true, group = "face")
        private Optional<Emoji> emoji = Optional.empty();
        @BuildFlag(nonNull = true, group = "face")
        private Optional<String> label = Optional.empty();
        private Optional<String> url = Optional.empty();

        /**
         * Sets the interaction handler invoked when the {@link Button} is clicked.
         *
         * @param interaction the interaction function, or {@code null} for the default no-op handler
         */
        public Builder onInteract(@Nullable Function<ButtonContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction handler invoked when the {@link Button} is clicked.
         *
         * @param interaction the optional interaction function
         */
        public Builder onInteract(@NotNull Optional<Function<ButtonContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link Button} as enabled.
         */
        public Builder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets whether the {@link Button} is enabled.
         *
         * @param value {@code true} to enable the button
         */
        public Builder setEnabled(boolean value) {
            return this.setDisabled(!value);
        }

        /**
         * Sets the {@link Button} as disabled.
         */
        public Builder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets whether the {@link Button} is disabled.
         *
         * @param value {@code true} to disable the button
         */
        public Builder setDisabled(boolean value) {
            this.disabled = value;
            return this;
        }

        /**
         * Sets the {@link Button} to automatically defer interactions as edits.
         */
        public Builder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether the {@link Button} automatically defers interactions as edits.
         *
         * @param value {@code true} to defer interactions
         */
        public Builder withDeferEdit(boolean value) {
            this.deferEdit = value;
            return this;
        }

        /**
         * Sets the identifier of the {@link Button}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the identifier of the {@link Button} using a format string, overriding the default random UUID.
         *
         * @param identifier the format string for the identifier
         * @param args the format arguments
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Sets the text label displayed on the {@link Button}.
         *
         * @param label the label text, or {@code null} to clear
         */
        public Builder withLabel(@Nullable String label) {
            return this.withLabel(Optional.ofNullable(label));
        }

        /**
         * Sets the text label displayed on the {@link Button} using a format string.
         *
         * @param label the format string for the label
         * @param args the format arguments
         */
        public Builder withLabel(@PrintFormat @Nullable String label, @Nullable Object... args) {
            return this.withLabel(StringUtil.formatNullable(label, args));
        }

        /**
         * Sets the text label displayed on the {@link Button}.
         *
         * @param label the optional label text
         */
        public Builder withLabel(@NotNull Optional<String> label) {
            this.label = label;
            return this;
        }

        /**
         * Sets the {@link Emoji} displayed on the {@link Button}.
         *
         * @param emoji the emoji to display, or {@code null} to clear
         */
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the {@link Emoji} displayed on the {@link Button}.
         *
         * @param emoji the optional emoji to display
         */
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.emoji = emoji;
            return this;
        }

        /**
         * Sets the {@link PageType} controlling built-in pagination behavior.
         *
         * @param pageType the page type to assign
         */
        public Builder withPageType(@NotNull PageType pageType) {
            this.pageType = pageType;
            return this;
        }

        /**
         * Sets the visual {@link Style} of the {@link Button}.
         *
         * @param style the button style
         */
        public Builder withStyle(@NotNull Style style) {
            this.style = style;
            return this;
        }

        /**
         * Sets the URL opened when a {@link Style#LINK} button is clicked.
         *
         * @param url the url to open, or {@code null} to clear
         */
        public Builder withUrl(@Nullable String url) {
            return this.withUrl(Optional.ofNullable(url));
        }

        /**
         * Sets the URL opened when a {@link Style#LINK} button is clicked, using a format string.
         *
         * @param url the format string for the url
         * @param args the format arguments
         */
        public Builder withUrl(@PrintFormat @Nullable String url, @Nullable Object... args) {
            return this.withUrl(StringUtil.formatNullable(url, args));
        }

        /**
         * Sets the URL opened when a {@link Style#LINK} button is clicked.
         *
         * @param url the optional url to open
         */
        public Builder withUrl(@NotNull Optional<String> url) {
            this.url = url;
            return this;
        }

        /**
         * Builds a new {@link Button} from the configured fields.
         *
         * @return a new {@link Button} instance
         */
        public @NotNull Button build() {
            Reflection.validateFlags(this);

            return new Button(
                this.identifier,
                this.style,
                this.emoji,
                this.label,
                this.url,
                this.deferEdit,
                this.pageType,
                this.interaction.orElse(NOOP_HANDLER),
                this.disabled
            );
        }

    }

    /**
     * Visual style of a {@link Button}.
     */
    @Getter
    @RequiredArgsConstructor
    public enum Style {

        /** Fallback for unrecognized style values. */
        UNKNOWN(-1),
        /** Blue-colored button. */
        PRIMARY(1),
        /** Gray-colored button. */
        SECONDARY(2),
        /** Green-colored button. */
        SUCCESS(3),
        /** Red-colored button. */
        DANGER(4),
        /** Non-interactive link that opens a URL. */
        LINK(5);

        /** The Discord integer value for this style. */
        private final int value;

        /**
         * Returns the constant matching the given value, or {@code UNKNOWN} if unrecognized.
         *
         * @param value the Discord integer value
         * @return the matching {@link Style}
         */
        public static @NotNull Style of(int value) {
            return Arrays.stream(values())
                .filter(style -> style.getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

    /**
     * Identifier for built-in pagination button roles.
     *
     * <p>
     * Interaction handlers and component construction are provided by
     * {@link PaginationHandler PaginationHandler}.
     *
     * @see PaginationHandler
     */
    @Getter
    @RequiredArgsConstructor
    public enum PageType {

        /** No pagination role. */
        NONE(""),
        /** Navigates to the previous page. */
        PREVIOUS("Previous"),
        /** Presents a sort interface. */
        SORT("Sort"),
        /** Displays the current page index. */
        INDEX("Index"),
        /** Presents a filter interface. */
        FILTER("Filter"),
        /** Navigates to the next page. */
        NEXT("Next");

        /** The display label for this page type's button. */
        private final @NotNull String label;

    }

}
