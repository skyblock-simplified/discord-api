package dev.sbs.discordapi.handler.locale;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.handler.DiscordLocale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable localization override for a single Discord-addressable field
 * on a slash command, option, or choice.
 *
 * <p>
 * Entries are loaded from {@code resources/locale/<shortName>/commands.json}
 * at bot startup or supplied programmatically via {@link DiscordCommand#getLocaleOverrides()}.
 * Each entry pairs a {@link DiscordLocale} with a {@link Target target field}
 * and a natural-key {@link #getPath() path} identifying the command, option,
 * or choice being translated.
 *
 * <p>
 * Path grammar (all segments lowercased):
 * <ul>
 *     <li><b>Command:</b> {@code [parent.][group.]commandName}</li>
 *     <li><b>Option on command:</b> {@code <commandPath>#optionName}</li>
 *     <li><b>Choice on option:</b> {@code <commandPath>#optionName:choiceName}</li>
 * </ul>
 *
 * @see DiscordLocale
 * @see Target
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
public final class LocaleEntry {

    /** Locale this override applies to. */
    private final @NotNull DiscordLocale locale;

    /** Discord field this entry localizes. */
    private final @NotNull Target target;

    /** Natural-key path identifying the command, option, or choice. */
    private final @NotNull String path;

    /** Localized value, subject to Discord's per-field length constraints. */
    private final @NotNull String value;

    /**
     * Returns a new builder for constructing a {@link LocaleEntry}.
     *
     * @return a new builder instance
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Returns a derived identifier unique per {@code (locale, target, path)}
     * triple, useful for logging and diffing.
     *
     * @return the derived id
     */
    public @NotNull String getId() {
        return this.locale.getShortName() + "|" + this.target.name() + "|" + this.path;
    }

    /**
     * Discord-addressable field categories supported by the localization API.
     *
     * @see <a href="https://discord.com/developers/docs/interactions/application-commands#localization">Application Commands - Localization</a>
     */
    public enum Target {

        /** The {@code name} field of an {@code ApplicationCommandRequest}. */
        COMMAND_NAME,

        /** The {@code description} field of an {@code ApplicationCommandRequest}. */
        COMMAND_DESCRIPTION,

        /** The {@code name} field of an {@code ApplicationCommandOptionData}. */
        OPTION_NAME,

        /** The {@code description} field of an {@code ApplicationCommandOptionData}. */
        OPTION_DESCRIPTION,

        /** The {@code name} field of an {@code ApplicationCommandOptionChoiceData}. */
        CHOICE_NAME

    }

    /**
     * Mutable builder for constructing {@link LocaleEntry} instances.
     */
    public static final class Builder {

        private DiscordLocale locale;
        private Target target;
        private String path;
        private String value;

        private Builder() { }

        /**
         * Sets the locale this entry applies to.
         *
         * @param locale the locale
         * @return this builder
         */
        public @NotNull Builder withLocale(@NotNull DiscordLocale locale) {
            this.locale = locale;
            return this;
        }

        /**
         * Sets the Discord field this entry localizes.
         *
         * @param target the target field
         * @return this builder
         */
        public @NotNull Builder withTarget(@NotNull Target target) {
            this.target = target;
            return this;
        }

        /**
         * Sets the natural-key path identifying the command, option, or choice.
         *
         * @param path the path
         * @return this builder
         */
        public @NotNull Builder withPath(@NotNull String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the localized value.
         *
         * @param value the translated text
         * @return this builder
         */
        public @NotNull Builder withValue(@NotNull String value) {
            this.value = value;
            return this;
        }

        /**
         * Validates builder state and constructs a new {@link LocaleEntry}.
         *
         * @return the constructed entry
         * @throws IllegalStateException if any required field is unset or blank
         */
        public @NotNull LocaleEntry build() {
            if (this.locale == null) throw new IllegalStateException("Locale is required");
            if (this.target == null) throw new IllegalStateException("Target is required");
            if (this.path == null || this.path.isBlank()) throw new IllegalStateException("Path is required");
            if (this.value == null || this.value.isBlank()) throw new IllegalStateException("Value is required");
            return new LocaleEntry(this.locale, this.target, this.path, this.value);
        }

    }

}
