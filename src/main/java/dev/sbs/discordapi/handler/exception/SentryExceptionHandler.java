package dev.sbs.discordapi.handler.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.sbs.discordapi.context.scope.CommandContext;
import dev.simplified.util.SystemUtil;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * Sentry-backed exception handler that captures exceptions to the
 * <a href="https://sentry.io/">Sentry</a> error tracking service with
 * enriched Discord context tags and user information.
 *
 * <p>
 * Initializes the Sentry SDK during construction with the provided DSN.
 * Environment and release are read from the {@code SENTRY_ENVIRONMENT}
 * and {@code SENTRY_RELEASE} environment variables respectively.
 *
 * <p>
 * This handler never re-emits the exception as a reactive error signal.
 * It always returns an empty {@link Mono} after capture, allowing
 * downstream handlers in a {@link CompositeExceptionHandler} chain to
 * provide user-facing feedback independently.
 *
 * <p>
 * Errors during Sentry capture are logged and swallowed to prevent
 * interference with user-facing error handling.
 *
 * @see ExceptionHandler
 * @see CompositeExceptionHandler
 */
@Getter
public final class SentryExceptionHandler extends ExceptionHandler {

    private final @NotNull String dsn;

    /**
     * Constructs a new {@code SentryExceptionHandler} with the given bot instance
     * and initializes the Sentry SDK with the provided DSN.
     *
     * @param discordBot the bot this handler belongs to
     * @param dsn the Sentry DSN to initialize with
     */
    public SentryExceptionHandler(@NotNull DiscordBot discordBot, @NotNull String dsn) {
        super(discordBot);
        this.dsn = dsn;

        Sentry.init(options -> {
            options.setDsn(this.getDsn());
            options.setEnvironment(
                SystemUtil.getEnv("SENTRY_ENVIRONMENT").orElse("production")
            );
            SystemUtil.getEnv("SENTRY_RELEASE").ifPresent(options::setRelease);
        });
    }

    /** {@inheritDoc} */
    @Override
    public <T> @NotNull Mono<T> handleException(@NotNull ExceptionContext<?> exceptionContext) {
        return Mono.<T>fromRunnable(() -> Sentry.withScope(scope -> {
            // Tags
            scope.setTag("exception.title", exceptionContext.getTitle());
            scope.setTag("response.id", exceptionContext.getResponseId().toString());
            scope.setTag("channel.id", exceptionContext.getChannelId().asString());
            exceptionContext.getGuildId().ifPresent(guildId ->
                scope.setTag("guild.id", guildId.asString())
            );

            // Command tags
            if (exceptionContext.getEventContext() instanceof CommandContext<?> commandContext) {
                scope.setTag("command.name", commandContext.getStructure().name());
                scope.setTag("command.type", commandContext.getType().name());
            }

            // User context
            io.sentry.protocol.User sentryUser = new io.sentry.protocol.User();
            sentryUser.setId(exceptionContext.getInteractUserId().asString());
            sentryUser.setUsername(exceptionContext.getInteractUser().getUsername());
            scope.setUser(sentryUser);

            // Level and capture
            scope.setLevel(SentryLevel.ERROR);
            Sentry.captureException(exceptionContext.getException());
        })).onErrorResume(error -> {
            this.getLog().warn("Failed to capture exception to Sentry", error);
            return Mono.empty();
        });
    }

}
