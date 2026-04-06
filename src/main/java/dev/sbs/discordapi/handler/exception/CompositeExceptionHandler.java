package dev.sbs.discordapi.handler.exception;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * Composite exception handler that delegates to an ordered list of
 * {@link ExceptionHandler} instances, executing each in sequence.
 *
 * <p>
 * Earlier handlers in the chain run first and are expected to complete
 * without error (e.g. Sentry capture). The final handler in the chain
 * determines the terminal reactive signal (e.g. user-facing embed plus
 * possible error re-emission).
 *
 * @see ExceptionHandler
 * @see SentryExceptionHandler
 * @see DiscordExceptionHandler
 */
public final class CompositeExceptionHandler extends ExceptionHandler {

    private final @NotNull ConcurrentList<ExceptionHandler> handlers;

    /**
     * Constructs a new {@code CompositeExceptionHandler} with the given handler chain.
     *
     * @param discordBot the bot this handler belongs to
     * @param handlers the ordered list of handlers to invoke
     */
    public CompositeExceptionHandler(@NotNull DiscordBot discordBot, @NotNull ConcurrentList<ExceptionHandler> handlers) {
        super(discordBot);
        this.handlers = handlers;
    }

    /** {@inheritDoc} */
    @Override
    public <T> @NotNull Mono<T> handleException(@NotNull ExceptionContext<?> exceptionContext) {
        Mono<T> chain = Mono.empty();

        for (ExceptionHandler handler : this.handlers)
            chain = chain.then(handler.handleException(exceptionContext));

        return chain;
    }

}
