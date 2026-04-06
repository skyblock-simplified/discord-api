package dev.sbs.discordapi.util;

import dev.simplified.util.time.SimpleDate;
import discord4j.common.util.Snowflake;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.Optional;

/**
 * A date representation that supports Discord's timestamp formatting and
 * {@link Snowflake}-based time extraction.
 *
 * <p>
 * Extends {@link SimpleDate} with the ability to construct dates from Discord
 * snowflakes and render them in Discord's {@code <t:...>} markdown format via
 * the {@link #as(Type)} method.
 *
 * @see Type
 * @see <a href="https://discord.com/developers/docs/reference#snowflakes">Discord Snowflakes</a>
 */
public class DiscordDate extends SimpleDate {

    /**
     * The Discord epoch offset in milliseconds (January 1, 2015 00:00:00 UTC).
     */
    public static long DISCORD_EPOCH = 1420070400000L;

    /**
     * Constructs a new {@code DiscordDate} by parsing a duration string.
     *
     * @param duration the duration string to parse
     */
    public DiscordDate(@NotNull String duration) {
        super(duration);
    }

    /**
     * Constructs a new {@code DiscordDate} from a Unix timestamp in milliseconds.
     *
     * @param realTime the timestamp in milliseconds since the Unix epoch
     */
    public DiscordDate(long realTime) {
        super(realTime);
    }

    /**
     * Constructs a new {@code DiscordDate} by extracting the timestamp encoded
     * in a Discord {@link Snowflake}.
     *
     * @param snowflake the Discord snowflake to extract the timestamp from
     */
    public DiscordDate(@NotNull Snowflake snowflake) {
        super(DISCORD_EPOCH + (snowflake.asLong() >> 22));
    }

    /**
     * Formats this date as a Discord timestamp markdown string using the specified style.
     *
     * @param type the formatting style to apply
     * @return the formatted Discord timestamp string (e.g., {@code <t:1234567890:R>})
     */
    public @NotNull String as(@NotNull Type type) {
        return type.toFormat(this.getRealTime());
    }

    /**
     * Discord timestamp formatting styles, each corresponding to a format flag
     * used in the {@code <t:...:flag>} markdown syntax.
     *
     * <p>
     * All sample patterns follow the {@code Month/Day/Year} convention.
     */
    @Getter
    public enum Type {

        /**
         * Short date format - {@code MM/dd/yyyy}.
         */
        DATE("d", "MM/dd/yyyy"),

        /**
         * Long date format - {@code MMMM d, yyyy}.
         */
        FULL_DATE("D", "MMMM d, yyyy"),

        /**
         * Short time format - {@code h:m a}.
         */
        TIME("t", "h:m a"),

        /**
         * Long time format - {@code h:m:s a}.
         */
        FULL_TIME("T", "h:m:s a"),

        /**
         * Long date with short time - {@code MMMM d, yyyy h:m a}.
         */
        FULL_DATE_TIME("f", "MMMM d, yyyy h:m a"),

        /**
         * Full date with day of week and short time - {@code EEEE, MMMM d, yyyy h:m a}.
         */
        WEEKDAY_FULL_DATE_TIME("F", "EEEE, MMMM d, yyyy h:m a"),

        /**
         * Relative time display (e.g., "3 hours ago"), updated live by the Discord client.
         */
        RELATIVE("R"),

        /**
         * Raw Unix timestamp in milliseconds with no Discord formatting.
         */
        TIMESTAMP(Optional.empty());

        /**
         * The Discord format flag character, or empty for raw timestamps.
         */
        private final Optional<String> format;

        /**
         * The corresponding {@link SimpleDateFormat}, or empty for non-pattern types.
         */
        private final Optional<SimpleDateFormat> dateFormat;

        /**
         * Constructs a type with a format flag and no date pattern.
         *
         * @param format the Discord format flag character
         */
        Type(@NotNull String format) {
            this(Optional.of(format));
        }

        /**
         * Constructs a type with an optional format flag and no date pattern.
         *
         * @param format the optional Discord format flag character
         */
        Type(@NotNull Optional<String> format) {
            this(format, Optional.empty());
        }

        /**
         * Constructs a type with a format flag and an optional date format pattern.
         *
         * @param format the Discord format flag character
         * @param dateFormat the {@link SimpleDateFormat} pattern, or {@code null}
         */
        Type(@NotNull String format, @Nullable String dateFormat) {
            this(Optional.of(format), Optional.ofNullable(dateFormat));
        }

        /**
         * Primary constructor for all type constants.
         *
         * @param format the optional Discord format flag character
         * @param dateFormat the optional date format pattern string
         */
        Type(@NotNull Optional<String> format, @NotNull Optional<String> dateFormat) {
            this.format = format;
            this.dateFormat = dateFormat.map(SimpleDateFormat::new);
        }

        /**
         * Formats the given timestamp according to this type's Discord format flag.
         *
         * <p>
         * If a format flag is present, returns a Discord timestamp markdown string
         * (e.g., {@code <t:1234567890:R>}). Otherwise, returns the raw millisecond value
         * as a string.
         *
         * @param milliseconds the timestamp in milliseconds since the Unix epoch
         * @return the formatted timestamp string
         */
        public @NotNull String toFormat(long milliseconds) {
            return this.getFormat().map(format -> String.format("<t:%s:%s>", milliseconds, format)).orElse(String.valueOf(milliseconds));
        }

    }

}
