package dev.sbs.discordapi.handler.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.exception.BotPermissionException;
import dev.sbs.discordapi.command.exception.DisabledCommandException;
import dev.sbs.discordapi.command.exception.ExpectedInputException;
import dev.sbs.discordapi.command.exception.InputException;
import dev.sbs.discordapi.command.exception.ParameterException;
import dev.sbs.discordapi.command.exception.PermissionException;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.sbs.discordapi.context.scope.CommandContext;
import dev.sbs.discordapi.context.scope.MessageContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.embed.Author;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.embed.Footer;
import dev.sbs.discordapi.response.page.TreePage;
import dev.simplified.client.exception.ApiException;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.collection.linked.ConcurrentLinkedMap;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.util.StringUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.GuildChannel;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.rest.util.Permission;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.awt.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Default {@link ExceptionHandler} implementation that renders exception
 * details as ephemeral Discord embed messages sent to the user, and
 * forwards developer-level error reports to a configured debug channel.
 *
 * <p>
 * This handler differentiates between user-facing errors (API failures,
 * invalid input, permission violations, disabled commands) and unexpected
 * exceptions. User-facing errors produce a descriptive embed without
 * logging. Unexpected exceptions produce a generic error embed for the
 * user and a detailed developer embed logged to the debug channel,
 * then re-emit the exception as a reactive error signal.
 *
 * @see ExceptionHandler
 */
@Getter
public final class DiscordExceptionHandler extends ExceptionHandler {

    private final @NotNull Snowflake logChannel;

    /**
     * Constructs a new {@code DiscordExceptionHandler} with the given bot instance
     * and debug channel identifier.
     *
     * @param discordBot the bot this handler belongs to
     * @param logChannelId the channel to log developer error reports to
     */
    public DiscordExceptionHandler(@NotNull DiscordBot discordBot, long logChannelId) {
        super(discordBot);
        this.logChannel = Snowflake.of(logChannelId);
    }

    /**
     * Builds a user-facing embed for recognized reactive exceptions such as
     * {@link ApiException}, {@link InputException}, {@link ExpectedInputException},
     * {@link ParameterException}, {@link PermissionException}, and
     * {@link DisabledCommandException}.
     *
     * @param exceptionContext the context wrapping the exception and its originating event
     * @return an embed describing the error, or empty if the exception is unrecognized
     */
    private @NotNull Optional<Embed> buildReactiveUserError(@NotNull ExceptionContext<?> exceptionContext) {
        Optional<Embed> responseBuilder = Optional.empty();

        if (exceptionContext.getException() instanceof ApiException apiException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor(
                        Author.builder()
                            .withName("%s Api", apiException.getName())
                            .withIconUrl(this.getEmoji("CLOUD_DISABLED").map(Emoji::getUrl))
                            .build()
                    )
                    .withDescription(apiException.getResponse().getReason())
                    .withFields(
                        Field.builder()
                            .withName("State")
                            .withValue(apiException.getStatus().getState().getTitle())
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName("Code")
                            .withValue(String.valueOf(apiException.getStatus().getCode()))
                            .isInline()
                            .build(),
                        Field.builder()
                            .withName("Message")
                            .withValue(apiException.getStatus().getMessage())
                            .isInline()
                            .build()
                    )
                    .build()
            );
        } else if (exceptionContext.getException() instanceof InputException inputException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor(
                        Author.builder()
                            .withName("Invalid Input")
                            .withIconUrl(this.getEmoji("STATUS_IMPORTANT").map(Emoji::getUrl))
                            .build()
                    )
                    .withDescription(inputException.getMessage())
                    .withField(
                        "Invalid Input",
                        inputException.getInvalidInput().orElse(this.getEmoji("TEXT_NULL").map(Emoji::asFormat).orElse("***null***"))
                    )
                    .build()
            );
        } else if (exceptionContext.getException() instanceof ExpectedInputException expectedInputException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor(
                        Author.builder()
                            .withName("Expected Input")
                            .withIconUrl(this.getEmoji("STATUS_IMPORTANT").map(Emoji::getUrl))
                            .build()
                    )
                    .withDescription(expectedInputException.getMessage())
                    .withField(
                        "Invalid Input",
                        expectedInputException.getInvalidInput().orElse(this.getEmoji("TEXT_NULL").map(Emoji::asFormat).orElse("***null***"))
                    )
                    .withField(
                        "Expected Input",
                        expectedInputException.getExpectedInput()
                    )
                    .build()
            );
        } else if (exceptionContext.getException() instanceof ParameterException parameterException) {
            Parameter parameter = parameterException.getParameter();
            Optional<String> value = parameterException.getValue();

            Embed.Builder builder = Embed.builder()
                .withAuthor(
                    Author.builder()
                        .withName("Invalid Parameter")
                        .withIconUrl(this.getEmoji("STATUS_INFO").map(Emoji::getUrl))
                        .build()
                )
                .withDescription("The provided argument does not validate against the expected parameter.")
                .withFields(
                    Field.builder()
                        .withName("Parameter")
                        .withValue(parameter.getName())
                        .isInline()
                        .build(),
                    Field.builder()
                        .withName("Required")
                        .withValue(parameter.isRequired() ? "Yes" : "No")
                        .isInline()
                        .build(),
                    Field.builder()
                        .withName("Type")
                        .withValue(parameter.getType().name())
                        .isInline()
                        .build()
                )
                .withField(
                    "Description",
                    parameter.getDescription()
                )
                .withField(
                    "Argument",
                    value.orElse(this.getEmoji("TEXT_NULL").map(Emoji::asFormat).orElse("***null***"))
                );

            responseBuilder = Optional.of(builder.build());
        } else if (exceptionContext.getException() instanceof PermissionException permissionException) {
            boolean botPermissions = (permissionException instanceof BotPermissionException);

            Embed.Builder builder = Embed.builder()
                .withAuthor(
                    Author.builder()
                        .withName("Missing %s Permissions", (botPermissions ? "Bot" : "User"))
                        .withIconUrl(this.getEmoji("STATUS_HIGH_IMPORTANCE").map(Emoji::getUrl))
                        .build()
                )
                .withDescription(permissionException.getMessage());

            if (botPermissions) {
                Snowflake snowflake = this.getDiscordBot().getClientId();
                ConcurrentSet<Permission> permissions = ((BotPermissionException) permissionException).getRequiredPermissions();
                ConcurrentLinkedMap<Permission, Boolean> permissionMap = this.getChannelPermissionMap(snowflake, exceptionContext.getChannel().ofType(GuildChannel.class), permissions);

                builder.withField(
                        "Required Permissions",
                        StringUtil.join(
                            permissionMap.stream()
                                .filter(entry -> !entry.getValue())
                                .map(Map.Entry::getKey)
                                .map(Permission::name)
                                .collect(Concurrent.toList()),
                            "\n"
                        ),
                        true
                    )
                    .withField(
                        "Status",
                        StringUtil.join(
                            permissionMap.stream()
                                .map(Map.Entry::getValue)
                                .filter(value -> !value)
                                .map(value -> this.getEmoji("ACTION_DENY").map(Emoji::asFormat).orElse("No"))
                                .collect(Concurrent.toList()),
                            "\n"
                        ),
                        true
                    )
                    .withEmptyField(true);
            }

            builder.withField("Notify", "Please notify a Server Administrator.");
            responseBuilder = Optional.of(builder.build());
        } else if (exceptionContext.getException() instanceof DisabledCommandException) {
            responseBuilder = Optional.of(
                Embed.builder()
                    .withAuthor(
                        Author.builder()
                            .withName("Disabled Command")
                            .withIconUrl(this.getEmoji("STATUS_DISABLED").map(Emoji::getUrl))
                            .build()
                    )
                    .withDescription("This command is currently disabled.")
                    .build()
            );
        }

        return responseBuilder;
    }

    /**
     * Builds a developer-facing embed containing guild, channel, user, and
     * error ID information for logging to the debug channel.
     *
     * @param exceptionContext the context wrapping the exception and its originating event
     * @param defaultError a pair of the generated error ID and the base error embed
     * @param messageId the Discord message snowflake of the user-facing error, if available
     * @return a detailed embed intended for developer review
     */
    private @NotNull Embed buildDeveloperError(ExceptionContext<?> exceptionContext, Pair<String, Embed> defaultError, Optional<Snowflake> messageId) {
        String locationValue = "DM";
        String channelValue = "N/A";

        // Handle Private Channels
        if (!exceptionContext.isPrivateChannel()) {
            Optional<MessageChannel> messageChannel = exceptionContext.getChannel().blockOptional();
            String location = exceptionContext.getGuild()
                .map(Guild::getName)
                .blockOptional()
                .orElse("Unknown")
                .replace("`", "");

            locationValue = String.format(
                "%s\n%s",
                exceptionContext.getGuildId().isPresent() ?
                    String.format(
                        "[%s](%s)",
                        location,
                        String.format("https://discord.com/servers/%s", exceptionContext.getGuildId().get().asString())
                    ) : location,
                exceptionContext.getGuildId()
                    .map(Snowflake::asString)
                    .orElse("---")
            );

            channelValue = String.format(
                "%s\n%s",
                messageChannel.map(MessageChannel::getMention).orElse("Unknown"),
                exceptionContext.getChannelId().asString()
            );
        }

        // Build Log Channel Embed
        Embed.Builder logErrorBuilder = defaultError.getRight()
            .mutate()
            .clearFields()
            .withField(
                "Error ID",
                (!exceptionContext.isPrivateChannel() && messageId.isPresent()) ?
                    String.format(
                        "[%s](%s)",
                        defaultError.getLeft(),
                        String.format(
                            "https://discord.com/channels/%s/%s/%s",
                            exceptionContext.getGuildId().map(Snowflake::asString).orElse("@me"),
                            exceptionContext.getChannelId().asString(),
                            messageId.get().asString()
                        )
                    ) :
                    defaultError.getLeft()
            )
            .withFields(
                Field.builder()
                    .withName("User")
                    .withValue(
                        "%s\n%s",
                        exceptionContext.getInteractUser().getMention(),
                        exceptionContext.getInteractUserId().asString()
                    )
                    .isInline()
                    .build(),
                Field.builder()
                    .withName("Location")
                    .withValue(locationValue)
                    .isInline()
                    .build(),
                Field.builder()
                    .withName("Channel")
                    .withValue(channelValue)
                    .isInline()
                    .build()
            );

        return logErrorBuilder.build();
    }

    /**
     * Builds the default error embed with a randomly generated error ID,
     * exception stack trace summary, and a timestamp footer.
     *
     * @param exceptionContext the context wrapping the exception and its originating event
     * @return a pair of the generated error ID and the constructed embed
     */
    private Pair<String, Embed> buildDefaultError(ExceptionContext<?> exceptionContext) {
        String errorId = UUID.randomUUID().toString();

        return Pair.of(errorId, Embed.from(exceptionContext.getException())
            .withColor(Color.DARK_GRAY)
            .withAuthor(
                Author.builder()
                    .withName("Exception")
                    .withIconUrl(this.getEmoji("STATUS_HIGH_IMPORTANCE").map(Emoji::getUrl))
                    .build()
            )
            .withTitle("Error :: %s", exceptionContext.getTitle())
            .withField(
                "Error ID",
                errorId
            )
            .withFooter(
                Footer.builder()
                    .withTimestamp(Instant.now())
                    .build()
            )
            .build()
        );
    }

    /** {@inheritDoc} */
    public <T> @NotNull Mono<T> handleException(@NotNull ExceptionContext<?> exceptionContext) {
        // Build Default Error Embed
        Pair<String, Embed> defaultError = this.buildDefaultError(exceptionContext);

        // Handle User Only Errors
        Optional<Embed> userReactiveError = this.buildReactiveUserError(exceptionContext);

        // Load User Error
        Embed userError = userReactiveError.orElse(defaultError.getRight());

        // Modify Command Errors
        if (exceptionContext.getEventContext() instanceof CommandContext<?> commandContext) {
            userError = userError.mutate()
                .withTitle("Command :: %s", commandContext.getStructure().name())
                .build();
        }

        // Handle Notice
        if (userReactiveError.isEmpty()) {
            userError = userError.mutate()
                .withField(
                    "Notice",
                    "This error has been automatically reported to the developer."
                )
                .build();
        }

        // Build User Error
        Response userErrorResponse = Response.builder()
            .withContext(exceptionContext)
            .isEphemeral(true)
            .withPages(
                TreePage.builder()
                    .withEmbeds(userError)
                    .build()
            )
            .build();

        // Send User Error Response & Log Exception Error
        Mono<Void> reply = exceptionContext.getEventContext() instanceof MessageContext ?
            ((MessageContext<?>) exceptionContext.getEventContext()).followup(userErrorResponse) :
            exceptionContext.reply(userErrorResponse);

        return reply.then(Mono.justOrEmpty(userReactiveError).switchIfEmpty(
                // Log to debug channel when it's not an expected reactive user error
                Mono.just(this.getDiscordBot().getMainGuild())
                    .flatMap(guild -> guild.getChannelById(this.getLogChannel()))
                    .ofType(MessageChannel.class)
                    .flatMap(messageChannel -> {
                        // Get Message ID
                        Optional<Snowflake> messageId;

                        if (exceptionContext.getEventContext() instanceof MessageContext)
                            messageId = Optional.of(((MessageContext<?>) exceptionContext.getEventContext()).getMessageId());
                        else
                            messageId = this.getDiscordBot()
                                .getResponseHandler()
                                .findFirst(entry -> entry.getResponse().getUniqueId(), userErrorResponse.getUniqueId())
                                .map(CachedResponse::getMessageId);

                        // Build Exception Response
                        Response logResponse = Response.builder()
                            .withContext(exceptionContext)
                            .withException(exceptionContext.getException())
                            .withPages(
                                TreePage.builder()
                                    .withEmbeds(this.buildDeveloperError(exceptionContext, defaultError, messageId))
                                    .build()
                            )
                            .build();

                        return Mono.just(messageChannel)
                            .publishOn(logResponse.getReactorScheduler())
                            .flatMap(logResponse::getD4jCreateMono)
                            .doOnNext(__ -> messageId.ifPresent(id -> this.getDiscordBot()
                                .getResponseHandler()
                                .removeIf(entry -> entry.getMessageId().equals(id))
                            ))
                            .then(Mono.empty());
                    })
            ))
            .then(Mono.empty());
    }

}
