package dev.sbs.discordapi.command.parameter;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.context.command.AutoCompleteContext;
import dev.sbs.discordapi.context.scope.CommandContext;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.Emoji;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.collection.linked.ConcurrentLinkedMap;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.NumberUtil;
import dev.simplified.util.Range;
import dev.simplified.util.StringUtil;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * An immutable slash command option definition, encapsulating name, description, type,
 * validation rules, autocomplete behavior, and static choices.
 *
 * <p>
 * Instances are created via {@link #builder()} and can be copied with {@link #mutate()}.
 *
 * @see Argument
 * @see DiscordCommand#getParameters()
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Parameter {

    /**
     * Default validator that accepts all values.
     */
    private static final BiPredicate<String, CommandContext<?>> NOOP_HANDLER = (s_, c_) -> true;

    /**
     * Default autocomplete function that returns no suggestions.
     */
    private static final Function<AutoCompleteContext, ConcurrentMap<String, Object>> NOOP_COMPLETE = c_ -> Concurrent.newUnmodifiableMap();

    /**
     * Pattern matching any Discord mention (user, role, or channel).
     */
    private static final Pattern MENTIONABLE_PATTERN = Pattern.compile("<(?:@!?&?|#)[\\d]+>");

    /**
     * Pattern matching a Discord user mention.
     */
    private static final Pattern MENTIONABLE_USER_PATTERN = Pattern.compile("<@!?[\\d]+>");

    /**
     * Pattern matching a Discord role mention.
     */
    private static final Pattern MENTIONABLE_ROLE_PATTERN = Pattern.compile("<@!?&[\\d]+>");

    /**
     * Pattern matching a Discord channel mention.
     */
    private static final Pattern MENTIONABLE_CHANNEL_PATTERN = Pattern.compile("<#[\\d]+>");

    /**
     * Pattern matching a custom Discord emoji.
     */
    private static final Pattern EMOJI_PATTERN = Pattern.compile("<:[\\w]+:[\\d]+>");

    /**
     * The unique identifier for this parameter instance.
     */
    private final @NotNull UUID uniqueId;

    /**
     * The parameter name displayed in the Discord command UI.
     */
    private final @NotNull String name;

    /**
     * The parameter description displayed in the Discord command UI.
     */
    private final @NotNull String description;

    /**
     * The data type of this parameter.
     */
    private final @NotNull Type type;

    /**
     * Whether the user must supply this parameter.
     */
    private final boolean required;

    /**
     * The optional emoji associated with this parameter.
     */
    private final @NotNull Optional<Emoji> emoji;

    /**
     * The set of allowed channel types when {@link #type} is {@link Type#CHANNEL}.
     */
    private final @NotNull ConcurrentSet<Channel.Type> channelTypes;

    /**
     * The numeric size range for {@link Type#INTEGER}, {@link Type#LONG}, and {@link Type#DOUBLE} parameters.
     */
    private final @NotNull Range<Double> sizeLimit;

    /**
     * The character length range for {@link Type#TEXT} and {@link Type#WORD} parameters.
     */
    private final @NotNull Range<Integer> lengthLimit;

    /**
     * The custom validation predicate applied after type validation.
     */
    private final @NotNull BiPredicate<String, CommandContext<?>> validator;

    /**
     * The autocomplete callback invoked when the user types into this parameter.
     */
    private final @NotNull Function<AutoCompleteContext, ConcurrentMap<String, Object>> autoComplete;

    /**
     * The fixed set of choices the user may select from.
     */
    private final @NotNull ConcurrentLinkedMap<String, Object> choices;

    /**
     * Creates a new builder for constructing a {@link Parameter}.
     *
     * @return a new builder instance with a random unique identifier
     */
    public static @NotNull Builder builder() {
        return new Builder(UUID.randomUUID());
    }

    /**
     * Creates a new builder pre-populated with the values from the given parameter.
     *
     * @param parameter the parameter to copy values from
     * @return a new builder initialized with the parameter's current state
     */
    public static @NotNull Builder from(@NotNull Parameter parameter) {
        return new Builder(parameter.getUniqueId())
            .withName(parameter.getName())
            .withDescription(parameter.getDescription())
            .withType(parameter.getType())
            .isRequired(parameter.isRequired())
            .withEmoji(parameter.getEmoji())
            .withChannelTypes(parameter.getChannelTypes())
            .withSizeLimit(parameter.getSizeLimit().getMinimum(), parameter.getSizeLimit().getMaximum())
            .withLengthLimit(parameter.getLengthLimit().getMinimum(), parameter.getLengthLimit().getMaximum())
            .withValidator(parameter.getValidator())
            .withAutoComplete(parameter.getAutoComplete())
            .withChoices(parameter.getChoices());
    }

    /**
     * Returns whether this parameter has a custom autocomplete handler configured.
     *
     * @return {@code true} if a non-default autocomplete function is set
     */
    public boolean isAutocompleting() {
        return !NOOP_COMPLETE.equals(this.getAutoComplete());
    }

    /**
     * Validates an optional argument value against this parameter's custom validator.
     *
     * <p>
     * If the argument is empty, returns {@code true} when the parameter is optional,
     * or {@code false} when it is required.
     *
     * @param argument the optional argument value to validate
     * @param commandContext the current command context
     * @return {@code true} if the argument passes validation
     */
    public boolean isValid(@NotNull Optional<String> argument, @NotNull CommandContext<?> commandContext) {
        return argument.map(value -> this.isValid(value, commandContext)).orElse(!this.isRequired());
    }

    /**
     * Validates an argument value against this parameter's custom validator.
     *
     * <p>
     * Null or empty arguments are normalized to an empty string before validation.
     *
     * @param argument the argument value to validate
     * @param commandContext the current command context
     * @return {@code true} if the argument passes validation
     */
    public boolean isValid(@Nullable String argument, @NotNull CommandContext<?> commandContext) {
        return this.getValidator().test(StringUtil.defaultIfEmpty(argument, ""), commandContext);
    }

    /**
     * Returns a new builder pre-populated with this parameter's values for modification.
     *
     * @return a builder initialized with this parameter's current state
     */
    public @NotNull Builder mutate() {
        return from(this);
    }

    /**
     * Supported parameter data types, each mapped to a Discord
     * {@link ApplicationCommandOption.Type} and a validation function.
     */
    @Getter
    public enum Type {

        /**
         * Unknown or unsupported parameter type - always fails validation.
         */
        UNKNOWN(-1, ApplicationCommandOption.Type.UNKNOWN, String.class, argument -> false),

        /**
         * Free-form text string.
         */
        TEXT(3, ApplicationCommandOption.Type.STRING, String.class, argument -> true),

        /**
         * Single word consisting only of letters, numbers, and underscores.
         */
        WORD(3, ApplicationCommandOption.Type.STRING, String.class, "Only Letters, Numbers and Underscores are allowed.", argument -> !argument.matches("\\w")),

        /**
         * 32-bit signed integer value.
         */
        INTEGER(4, ApplicationCommandOption.Type.INTEGER, Integer.class, String.format("Only numbers between %s and %s are allowed.", Integer.MIN_VALUE, Integer.MAX_VALUE), argument -> {
            if (NumberUtil.isCreatable(argument) && !argument.contains(".")) {
                double value = NumberUtil.createBigDecimal(argument).doubleValue();
                return value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE;
            }

            return false;
        }),

        /**
         * 64-bit signed integer value.
         */
        LONG(4, ApplicationCommandOption.Type.INTEGER, Long.class, String.format("Only numbers between %s and %s are allowed.", Long.MIN_VALUE, Long.MAX_VALUE), argument -> {
            if (NumberUtil.isCreatable(argument) && !argument.contains(".")) {
                double value = NumberUtil.createBigDecimal(argument).doubleValue();
                return value <= Long.MAX_VALUE && value >= Long.MIN_VALUE;
            }

            return false;
        }),

        /**
         * Boolean value accepting {@code true} or {@code false}.
         */
        BOOLEAN(5, ApplicationCommandOption.Type.BOOLEAN, Boolean.class, "Only `true` or `false` values are allowed.", argument -> argument.equalsIgnoreCase("true") || argument.equalsIgnoreCase("false")),

        /**
         * Discord user mention or snowflake ID.
         */
        USER(6, ApplicationCommandOption.Type.USER, User.class, "Only User Mentions and IDs are allowed.", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_USER_PATTERN.matcher(argument).matches()),

        /**
         * Discord channel mention or snowflake ID.
         */
        CHANNEL(7, ApplicationCommandOption.Type.CHANNEL, Channel.class, "Only Channel Mentions and IDs are allowed.", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_CHANNEL_PATTERN.matcher(argument).matches()),

        /**
         * Discord role mention or snowflake ID.
         */
        ROLE(8, ApplicationCommandOption.Type.ROLE, Role.class, "Only Role Mentions and IDs are allowed.", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_ROLE_PATTERN.matcher(argument).matches()),

        /**
         * Any mentionable entity (user, role, or channel) or snowflake ID.
         */
        MENTIONABLE(9, ApplicationCommandOption.Type.MENTIONABLE, String.class, "Only Mentions and IDs are allowed.", argument -> LONG.getValidator().apply(argument) || MENTIONABLE_PATTERN.matcher(argument).matches()),

        /**
         * Custom Discord emoji.
         */
        EMOJI(9, ApplicationCommandOption.Type.MENTIONABLE, discord4j.core.object.emoji.Emoji.class, "Only Emojis are allowed.", argument -> EMOJI_PATTERN.matcher(argument).matches()),

        /**
         * Double-precision floating-point number.
         */
        DOUBLE(10, ApplicationCommandOption.Type.NUMBER, Double.class, String.format("Only numbers between %s and %s are allowed.", Double.MIN_VALUE, Double.MAX_VALUE), NumberUtil::isCreatable),

        /**
         * File attachment.
         */
        ATTACHMENT(11, ApplicationCommandOption.Type.ATTACHMENT, Attachment.class, "Only Attachments are allowed.", argument -> true);

        /**
         * The Discord integer type identifier.
         */
        private final int value;

        /**
         * The corresponding Discord4J option type.
         */
        private final @NotNull ApplicationCommandOption.Type optionType;

        /**
         * The Java class this type maps to.
         */
        private final @NotNull Class<?> javaType;

        /**
         * The optional human-readable error message shown when validation fails.
         */
        private final @NotNull Optional<String> errorMessage;

        /**
         * The function that validates raw string input for this type.
         */
        private final @NotNull Function<String, Boolean> validator;

        Type(int value, @NotNull ApplicationCommandOption.Type optionType, @NotNull Class<?> javaType, @NotNull Function<String, Boolean> validator) {
            this(value, optionType, javaType, (String) null, validator);
        }

        Type(int value, @NotNull ApplicationCommandOption.Type optionType, @NotNull Class<?> javaType, @Nullable String errorMessage, @NotNull Function<String, Boolean> validator) {
            this(value, optionType, javaType, Optional.ofNullable(errorMessage), validator);
        }

        Type(int value, @NotNull ApplicationCommandOption.Type optionType, @NotNull Class<?> javaType, @NotNull Optional<String> errorMessage, @NotNull Function<String, Boolean> validator) {
            this.value = value;
            this.optionType = optionType;
            this.javaType = javaType;
            this.errorMessage = errorMessage;
            this.validator = validator;
        }

        /**
         * Validates an optional argument string against this type's rules.
         *
         * @param argument the optional argument value
         * @return {@code true} if the argument is present and valid for this type
         */
        public boolean isValid(@NotNull Optional<String> argument) {
            return argument.map(this::isValid).orElse(false);
        }

        /**
         * Validates a raw argument string against this type's rules.
         *
         * <p>
         * Null or empty arguments are normalized to an empty string before validation.
         *
         * @param argument the argument value to validate
         * @return {@code true} if the argument is valid for this type
         */
        public boolean isValid(@Nullable String argument) {
            return this.getValidator().apply(StringUtil.defaultIfEmpty(argument, ""));
        }

        /**
         * Returns the {@code Type} constant matching the given Discord integer value,
         * or {@link #UNKNOWN} if no match is found.
         *
         * @param value the Discord integer type identifier
         * @return the matching type constant
         */
        public static @NotNull Type of(int value) {
            return Arrays.stream(values())
                .filter(parameter -> parameter.getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

    /**
     * A mutable builder for constructing {@link Parameter} instances.
     *
     * <p>
     * Mandatory fields ({@code name}, {@code description}, {@code type}) are enforced
     * at build time via {@link BuildFlag} validation.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Builder {

        private final UUID uniqueId;
        @BuildFlag(nonNull = true, notEmpty = true)
        private String name;
        @BuildFlag(nonNull = true, notEmpty = true)
        private String description;
        @BuildFlag(nonNull = true, notEmpty = true)
        private Type type = Type.UNKNOWN;
        private boolean required = false;
        private Optional<Emoji> emoji = Optional.empty();
        private Range<Double> sizeLimit = Range.between(Double.MIN_VALUE, Double.MAX_VALUE);
        private Range<Integer> lengthLimit = Range.between(0, 6000);
        private final ConcurrentSet<Channel.Type> channelTypes = Concurrent.newSet();
        private Optional<BiPredicate<String, CommandContext<?>>> validator = Optional.empty();
        private Function<AutoCompleteContext, ConcurrentMap<String, Object>> autoComplete = NOOP_COMPLETE;
        private final ConcurrentLinkedMap<String, Object> choices = Concurrent.newLinkedMap();

        /**
         * Removes all configured channel type restrictions.
         *
         * @return this builder
         */
        public Builder clearChannelTypes() {
            this.channelTypes.clear();
            return this;
        }

        /**
         * Marks this parameter as required.
         *
         * @return this builder
         */
        public Builder isRequired() {
            return this.isRequired(true);
        }

        /**
         * Sets whether this parameter is required.
         *
         * @param required {@code true} if the user must supply this parameter
         * @return this builder
         */
        public Builder isRequired(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets the autocomplete callback for this parameter.
         *
         * <p>
         * This callback is not used if static {@link #withChoices choices} are defined.
         *
         * @param autoComplete the autocomplete function returning name-value suggestions
         * @return this builder
         */
        public Builder withAutoComplete(@NotNull Function<AutoCompleteContext, ConcurrentMap<String, Object>> autoComplete) {
            this.autoComplete = autoComplete;
            return this;
        }

        /**
         * Restricts this channel parameter to the specified channel types.
         *
         * <p>
         * Only valid when the parameter {@link #withType type} is {@link Type#CHANNEL}.
         *
         * @param channelTypes the channel types to allow
         * @return this builder
         */
        public Builder withChannelTypes(@NotNull Channel.Type... channelTypes) {
            return this.withChannelTypes(Arrays.asList(channelTypes));
        }

        /**
         * Restricts this channel parameter to the specified channel types.
         *
         * <p>
         * Only valid when the parameter {@link #withType type} is {@link Type#CHANNEL}.
         *
         * @param channelTypes the channel types to allow
         * @return this builder
         */
        public Builder withChannelTypes(@NotNull Iterable<Channel.Type> channelTypes) {
            channelTypes.forEach(this.channelTypes::add);
            return this;
        }

        /**
         * Defines the fixed choices a user can select from for this parameter.
         *
         * @param choices the name-value entries to add
         * @return this builder
         */
        public Builder withChoices(@NotNull Map.Entry<String, Object>... choices) {
            return this.withChoices(Arrays.asList(choices));
        }

        /**
         * Defines the fixed choices a user can select from for this parameter.
         *
         * @param choices the name-value entries to add
         * @return this builder
         */
        public Builder withChoices(@NotNull Iterable<Map.Entry<String, Object>> choices) {
            choices.forEach(this.choices::put);
            return this;
        }

        /**
         * Sets the description displayed in the Discord command UI.
         *
         * @param description the parameter description
         * @return this builder
         */
        public Builder withDescription(@NotNull String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the description displayed in the Discord command UI using a format string.
         *
         * @param description the format string for the parameter description
         * @param args the arguments referenced by the format specifiers
         * @return this builder
         */
        public Builder withDescription(@PrintFormat @NotNull String description, @Nullable Object... args) {
            this.description = String.format(description, args);
            return this;
        }

        /**
         * Sets the emoji associated with this parameter.
         *
         * @param emoji the emoji, or {@code null} to clear
         * @return this builder
         */
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * Sets the emoji associated with this parameter.
         *
         * @param emoji the optional emoji
         * @return this builder
         */
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.emoji = emoji;
            return this;
        }

        /**
         * Sets the character length range for {@link Type#TEXT} and {@link Type#WORD} parameters.
         *
         * <p>
         * The maximum is clamped to {@code [1, 6000]} and the minimum is clamped to
         * {@code [0, maximum]}.
         *
         * @param minimum the minimum number of characters (default 0)
         * @param maximum the maximum number of characters (default 6000)
         * @return this builder
         */
        public Builder withLengthLimit(int minimum, int maximum) {
            maximum = Range.between(1, 6000).fit(maximum);
            minimum = Range.between(0, maximum).fit(minimum);
            this.lengthLimit = Range.between(minimum, maximum);
            return this;
        }

        /**
         * Sets the parameter name displayed in the Discord command UI.
         *
         * @param name the parameter name
         * @return this builder
         */
        public Builder withName(@NotNull String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the parameter name displayed in the Discord command UI using a format string.
         *
         * @param name the format string for the parameter name
         * @param args the arguments referenced by the format specifiers
         * @return this builder
         */
        public Builder withName(@PrintFormat @NotNull String name, @Nullable Object... args) {
            this.name = String.format(name, args);
            return this;
        }

        /**
         * Sets the numeric size range for {@link Type#INTEGER}, {@link Type#LONG},
         * and {@link Type#DOUBLE} parameters.
         *
         * @param minimum the minimum allowed value (default {@link Double#MIN_VALUE})
         * @param maximum the maximum allowed value (default {@link Double#MAX_VALUE})
         * @return this builder
         */
        public Builder withSizeLimit(double minimum, double maximum) {
            this.sizeLimit = Range.between(minimum, maximum);
            return this;
        }

        /**
         * Sets the data type of this parameter.
         *
         * @param type the parameter type
         * @return this builder
         */
        public Builder withType(@NotNull Type type) {
            this.type = type;
            return this;
        }

        /**
         * Sets a custom validation predicate for this parameter.
         *
         * @param validator the validator, or {@code null} to use the default (accept-all)
         * @return this builder
         */
        public Builder withValidator(@Nullable BiPredicate<String, CommandContext<?>> validator) {
            return this.withValidator(Optional.ofNullable(validator));
        }

        /**
         * Sets a custom validation predicate for this parameter.
         *
         * @param validator an optional validator; empty clears any custom validation
         * @return this builder
         */
        public Builder withValidator(@NotNull Optional<BiPredicate<String, CommandContext<?>>> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Validates the builder state and constructs a new {@link Parameter}.
         *
         * @return the constructed parameter
         * @throws DiscordException if channel types are specified for a non-channel parameter
         */
        public @NotNull Parameter build() {
            Reflection.validateFlags(this);

            if (this.choices.notEmpty() && this.type != Type.CHANNEL)
                throw new DiscordException("You can only specify channel types for parameters of type Channel.");

            return new Parameter(
                this.uniqueId,
                this.name,
                this.description,
                this.type,
                this.required,
                this.emoji,
                this.channelTypes,
                this.sizeLimit,
                this.lengthLimit,
                this.validator.orElse(NOOP_HANDLER),
                this.autoComplete,
                this.choices
            );
        }

    }

}
