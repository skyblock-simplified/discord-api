package dev.sbs.discordapi.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.exception.BotPermissionException;
import dev.sbs.discordapi.command.exception.CommandException;
import dev.sbs.discordapi.command.exception.DeveloperPermissionException;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.ParameterException;
import dev.sbs.discordapi.command.exception.SingletonCommandException;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.sbs.discordapi.context.command.MessageCommandContext;
import dev.sbs.discordapi.context.command.SlashCommandContext;
import dev.sbs.discordapi.context.command.UserCommandContext;
import dev.sbs.discordapi.context.scope.CommandContext;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.util.DiscordReference;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.unmodifiable.ConcurrentUnmodifiableList;
import dev.simplified.reflection.Reflection;
import discord4j.core.object.entity.channel.GuildChannel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

/**
 * Abstract base for all Discord bot commands, providing permission checks,
 * parameter validation, singleton enforcement, and error handling around the
 * user-defined {@link #process} method.
 *
 * <p>
 * Subclasses are parameterized by their {@link CommandContext} type, which
 * determines the command's {@link Type} (slash, user, or message). Every
 * concrete command must be annotated with {@link Structure} to define its
 * identity and behavioral flags.
 *
 * @param <C> the command context type this command operates on
 * @see Structure
 * @see CommandContext
 */
@Getter
public abstract class DiscordCommand<C extends CommandContext<?>> extends DiscordReference implements Function<C, Mono<Void>> {

    /**
     * Reusable empty example list for commands that provide no usage examples.
     */
    protected static final ConcurrentUnmodifiableList<String> NO_EXAMPLES = Concurrent.newUnmodifiableList();

    /**
     * The structural metadata annotation declared on this command class.
     */
    protected final @NotNull Structure structure;

    /**
     * The resolved command type derived from the generic context parameter.
     */
    protected final @NotNull Type type;

    /**
     * Whether this command is currently being executed.
     */
    private boolean processing = false;

    /**
     * Constructs a new command instance and resolves its {@link Structure} annotation
     * and {@link Type} from the concrete class definition.
     *
     * @param discordBot the bot instance this command belongs to
     * @throws CommandException if the concrete class is not annotated with {@link Structure}
     */
    protected DiscordCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.structure = super.getAnnotation(Structure.class, this.getClass())
            .orElseThrow(() -> new CommandException("Cannot instantiate a command with no structure."));
        this.type = Type.of(Reflection.getSuperClass(this));
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Executes the full command lifecycle: defers the reply, enforces developer-only
     * and disabled-command restrictions, validates bot permissions, checks singleton
     * constraints, runs parameter validation for slash commands, and finally delegates
     * to {@link #process}. Errors are routed to the bot's {@link ExceptionContext exception handler}.
     */
    @Override
    public final @NotNull Mono<Void> apply(@NotNull C context) {
        return context.withEvent(event -> context.withChannel(messageChannel -> context
            .deferReply(this.getStructure().ephemeral())
            .then(Mono.defer(() -> {
                // Handle Developer Command
                if (this.getStructure().developerOnly() && !this.isDeveloper(context.getInteractUserId()))
                    throw new DeveloperPermissionException();

                // Handle Disabled Command
                if (!this.isEnabled() && !this.isDeveloper(context.getInteractUserId()))
                    throw new DisabledCommandException();

                // Handle Bot Permissions
                if (!context.isPrivateChannel()) {
                    // Handle Required Permissions
                    if (!this.hasChannelPermissions(this.getDiscordBot().getClientId(), context.getChannel().ofType(GuildChannel.class), this.getStructure().botPermissions()))
                        throw new BotPermissionException(context, Concurrent.newUnmodifiableSet(this.getStructure().botPermissions()));
                }

                // Handle Singleton Command
                if (this.getStructure().singleton() && this.isProcessing())
                    throw new SingletonCommandException();

                // Process Parameter Checks
                if (context instanceof SlashCommandContext slashCommandContext)
                    this.handleParameterChecks(slashCommandContext);

                // Process Command
                this.processing = true;
                return this.process(context);
            }))
            .thenEmpty(Mono.fromRunnable(() -> this.processing = false))
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    context,
                    throwable
                )
            ))
        ));
    }

    /**
     * Returns the Discord-assigned application command identifier for this command.
     *
     * @return the snowflake ID registered with Discord
     */
    public final long getId() {
        return this.getDiscordBot()
            .getCommandHandler()
            .getCommandId(this.getClass());
    }

    /**
     * Returns the parameter at the given index, or empty if the index is out of range.
     *
     * <p>
     * Negative indices are clamped to zero.
     *
     * @param index the zero-based parameter index
     * @return the parameter at the specified index, or empty if none exists
     */
    public final @NotNull Optional<Parameter> getParameter(int index) {
        ConcurrentList<Parameter> parameters = this.getParameters();
        index = Math.max(0, index);
        return Optional.ofNullable(index < parameters.size() ? parameters.get(index) : null);
    }

    /**
     * Returns the list of parameters accepted by this command.
     *
     * <p>
     * Override this in {@link SlashCommandContext slash commands} to declare command
     * options. This method is not used by {@link UserCommandContext user commands}
     * or {@link MessageCommandContext message commands}.
     *
     * @return an unmodifiable list of parameters, empty by default
     */
    public @NotNull ConcurrentUnmodifiableList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList();
    }

    /**
     * Validates each supplied argument against its corresponding {@link Parameter} type
     * and custom validator, throwing a {@link ParameterException} on the first mismatch.
     *
     * @param slashCommandContext the slash command context containing the arguments
     * @throws ParameterException if an argument fails type or custom validation
     */
    private void handleParameterChecks(@NotNull SlashCommandContext slashCommandContext) {
        // Validate Arguments
        for (Parameter parameter : this.getParameters()) {
            Optional<Argument> argument = slashCommandContext.getArgument(parameter.getName());

            if (argument.isEmpty())
                continue;

            String value = argument.map(Argument::asString).get();

            if (!parameter.getType().isValid(value))
                throw new ParameterException(parameter, value, "Value '%s' does not match parameter type for '%s'.", value, parameter.getType().name());

            if (!parameter.isValid(value, slashCommandContext))
                throw new ParameterException(parameter, value, "Value '%s' does not validate against parameter '%s'.", value, parameter.getName());
        }
    }

    /**
     * Returns whether this command is currently enabled.
     *
     * @return {@code true} if the command is enabled
     */
    public boolean isEnabled() {
        return true; // TODO: Reimplement
    }

    /**
     * Executes the command logic for the given context.
     *
     * <p>
     * Subclasses implement this method to define the command's behavior. It is
     * invoked after all permission, singleton, and parameter checks have passed.
     *
     * @param context the command context providing access to the interaction, channel, and arguments
     * @return a {@link Mono} that completes when the command finishes
     * @throws DiscordException if a discord-related error occurs during processing
     */
    protected abstract @NotNull Mono<Void> process(@NotNull C context) throws DiscordException;

    /**
     * Interaction access contexts defining where a command can be invoked.
     *
     * @see Structure#contexts()
     */
    @Getter
    @RequiredArgsConstructor
    public enum Access {

        /**
         * Unknown or unrecognized access context.
         */
        UNKNOWN(-1),

        /**
         * Interaction can be used within servers.
         */
        GUILD(0),

        /**
         * Interaction can be used within DMs with the app's bot user.
         */
        DIRECT_MESSAGE(1),

        /**
         * Interaction can be used within group DMs and DMs other than the app's bot user.
         */
        PRIVATE_CHANNEL(2);

        /**
         * The underlying integer value as represented by Discord.
         */
        private final int value;

        /**
         * Returns the {@code Access} constant matching the given Discord integer value,
         * or {@link #UNKNOWN} if no match is found.
         *
         * @param value the Discord integer value
         * @return the matching access constant
         */
        public static @NotNull Access of(final int value) {
            return switch (value) {
                case 0 -> GUILD;
                case 1 -> DIRECT_MESSAGE;
                case 2 -> PRIVATE_CHANNEL;
                default -> UNKNOWN;
            };
        }

        /**
         * Converts an array of {@code Access} constants to their integer representations.
         *
         * @param contexts the access constants to convert
         * @return an array of corresponding integer values
         */
        public static @NotNull Integer[] intValues(@NotNull Access[] contexts) {
            return Arrays.stream(contexts).map(Access::getValue).toArray(Integer[]::new);
        }

    }

    /**
     * Installation contexts defining where a command can be installed.
     *
     * @see Structure#integrations()
     */
    @Getter
    @RequiredArgsConstructor
    public enum Install {

        /**
         * Unknown or unrecognized installation context.
         */
        UNKNOWN(-1),

        /**
         * Installable to servers.
         */
        GUILD(0),

        /**
         * Installable to users.
         */
        USER(1);

        /**
         * The underlying integer value as represented by Discord.
         */
        private final int value;

        /**
         * Returns the {@code Install} constant matching the given Discord integer value,
         * or {@link #UNKNOWN} if no match is found.
         *
         * @param value the Discord integer value
         * @return the matching install constant
         */
        public static @NotNull Install of(final int value) {
            return switch (value) {
                case 0 -> GUILD;
                case 1 -> USER;
                default -> UNKNOWN;
            };
        }

        /**
         * Converts an array of {@code Install} constants to their integer representations.
         *
         * @param contexts the install constants to convert
         * @return an array of corresponding integer values
         */
        public static @NotNull Integer[] intValues(@NotNull Install[] contexts) {
            return Arrays.stream(contexts).map(Install::getValue).toArray(Integer[]::new);
        }

    }

    /**
     * Discord application command types, corresponding to the numeric identifiers
     * defined by the Discord API.
     *
     * @see <a href="https://discord.com/developers/docs/interactions/application-commands#application-command-object-application-command-types">Application Command Types</a>
     */
    @Getter
    @RequiredArgsConstructor
    public enum Type {

        /**
         * Unknown or unrecognized command type.
         */
        UNKNOWN(-1),

        /**
         * Slash command - a text-based command that shows up when a user types {@code /}.
         */
        CHAT_INPUT(1),

        /**
         * Context-menu command that appears when right-clicking or tapping on a user.
         */
        USER(2),

        /**
         * Context-menu command that appears when right-clicking or tapping on a message.
         */
        MESSAGE(3),

        /**
         * Entry-point command representing the primary way to invoke an app's Activity.
         */
        PRIMARY_ENTRY_POINT(4);

        /**
         * The underlying integer value as represented by Discord.
         */
        private final int value;

        /**
         * Returns the {@code Type} constant matching the given Discord integer value,
         * or {@link #UNKNOWN} if no match is found.
         *
         * @param value the Discord integer value
         * @return the matching command type constant
         */
        public static @NotNull Type of(final int value) {
            return switch (value) {
                case 1 -> CHAT_INPUT;
                case 2 -> USER;
                case 3 -> MESSAGE;
                case 4 -> PRIMARY_ENTRY_POINT;
                default -> UNKNOWN;
            };
        }

        /**
         * Resolves the command type from the given {@link CommandContext} subclass.
         *
         * @param contextType the command context class
         * @param <C> the context type
         * @return the matching command type, or {@link #UNKNOWN} if the context is unrecognized
         */
        public static <C extends CommandContext<?>> @NotNull Type of(final Class<C> contextType) {
            if (SlashCommandContext.class.isAssignableFrom(contextType))
                return CHAT_INPUT;
            else if (UserCommandContext.class.isAssignableFrom(contextType))
                return USER;
            else if (MessageCommandContext.class.isAssignableFrom(contextType))
                return MESSAGE;

            return UNKNOWN;
        }

    }

    /*public Embed createHelpEmbed() {
        String commandPath = this.getCommandPath();
        ConcurrentList<Parameter> parameters = this.getParameters();

        Embed.Builder builder = Embed.builder()
            .withAuthor(
                Author.builder()
                    .withName("Help")
                    .withIconUrl(getEmoji("STATUS_INFO").map(Emoji::getUrl))
                    .build()
            )
            .withTitle("Command :: %s", this.getStructure().name())
            .withDescription(this.getStructure().description())
            .withFooter(
                Footer.builder()
                    .withTimestamp(Instant.now())
                    .build()
            )
            .withColor(Color.DARK_GRAY);

        if (parameters.notEmpty()) {
            builder.withField(
                "Usage",
                String.format(
                    """
                        <> - Required Parameters
                        [] - Optional Parameters

                        %s %s""",
                    commandPath,
                    StringUtil.join(
                        parameters.stream()
                            .map(parameter -> parameter.isRequired() ? String.format("<%s>", parameter.getName()) : String.format("[%s]", parameter.getName()))
                            .collect(Concurrent.toList()),
                        " "
                    )
                )
            );
        }*/

        /*if (this.getExampleArguments().notEmpty()) {
            builder.withField(
                "Examples",
                StringUtil.join(
                    this.getExampleArguments()
                        .stream()
                        .map(example -> String.format("%s %s", commandPath, example))
                        .collect(Concurrent.toList()),
                    "\n"
                )
            );
        }*/

        /*return builder.build();
    }*/

}
