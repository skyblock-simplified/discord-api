package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.capability.EventInteractable;
import dev.sbs.discordapi.component.capability.UserInteractable;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.sbs.discordapi.context.scope.ComponentContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.ResponseFollowup;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.response.Response;
import dev.simplified.reflection.Reflection;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

/**
 * Abstract base for component interaction listeners, providing the shared flow of
 * matching an incoming event to a {@link CachedResponse}, locating the interacted
 * {@link EventInteractable}, and dispatching to its registered interaction handler.
 * <p>
 * Concrete subclasses ({@link ButtonListener}, {@link SelectMenuListener},
 * {@link ModalListener}) supply the appropriate {@link ComponentContext} via
 * {@link #getContext}.
 *
 * @param <E> the Discord4J component interaction event type
 * @param <C> the context type passed to the component's interaction handler
 * @param <T> the component type this listener handles
 */
public abstract class ComponentListener<E extends ComponentInteractionEvent, C extends ComponentContext, T extends EventInteractable<C>> extends DiscordListener<E> {

    /** The resolved component class, used to filter matching components from the response tree. */
    private final Class<T> componentClass;

    /**
     * Constructs a new {@code ComponentListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    protected ComponentListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.componentClass = Reflection.getSuperClass(this, 2);
    }

    @Override
    public final Publisher<Void> apply(@NotNull E event) {
        return Flux.fromIterable(this.getDiscordBot().getResponseHandler())
            .filter(entry -> !event.getInteraction().getUser().isBot()) // Ignore Bots
            .filter(entry -> entry.matchesMessage(event.getMessageId(), event.getInteraction().getUser().getId())) // Validate Message & User ID
            .singleOrEmpty()
            .switchIfEmpty(event.deferEdit().then(Mono.empty())) // Invalid User Interaction
            .doOnNext(CachedResponse::setBusy)
            .flatMap(entry -> this.handleEvent(event, entry, entry.findFollowup(event.getMessageId())))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates the typed context for the given component interaction.
     *
     * @param event the Discord4J interaction event
     * @param cachedMessage the cached response containing the component
     * @param component the matched component
     * @param followup the matched followup, if the interaction targets one
     * @return the constructed context
     */
    protected abstract @NotNull C getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull T component, @NotNull Optional<ResponseFollowup> followup);

    /**
     * Locates the interacted component within the response tree and delegates
     * to {@link #handleInteraction}. Override for special handling (e.g. modals).
     *
     * @param event the Discord4J interaction event
     * @param entry the matched response cache entry
     * @param followup the matched followup, if the interaction targets one
     * @return a reactive pipeline completing when the interaction is handled
     */
    protected Mono<Void> handleEvent(@NotNull E event, @NotNull CachedResponse entry, @NotNull Optional<ResponseFollowup> followup) {
        return Flux.fromIterable((followup.isPresent() ? followup.get() : entry).getResponse().getCachedPageComponents())
            .concatWith(Flux.fromIterable((followup.isPresent() ? followup.get() : entry).getResponse().getHistoryHandler().getCurrentPage().getComponents()))
            .flatMap(tlmComponent -> Flux.fromStream(tlmComponent.flattenComponents()))
            .filter(UserInteractable.class::isInstance)
            .filter(component -> event.getCustomId().equals(((UserInteractable) component).getIdentifier())) // Validate Component ID
            .filter(this.componentClass::isInstance) // Validate Component Type
            .map(this.componentClass::cast)
            .singleOrEmpty()
            .flatMap(component -> this.handleInteraction(event, entry, component, followup))
            .then(entry.updateLastInteract())
            .then();
    }

    /**
     * Executes the component's registered interaction handler within an error-handling
     * pipeline, then edits the response if it was modified.
     *
     * @param event the Discord4J interaction event
     * @param entry the matched response cache entry
     * @param component the matched component
     * @param followup the matched followup, if the interaction targets one
     * @return a reactive pipeline completing when the interaction is handled
     */
    protected final Mono<Void> handleInteraction(@NotNull E event, @NotNull CachedResponse entry, @NotNull T component, @NotNull Optional<ResponseFollowup> followup) {
        C context = this.getContext(event, entry.getResponse(), component, followup);

        Mono<Void> deferEdit = Mono.defer(() -> entry.isDeferred() ? Mono.empty() : context.deferEdit());

        return (component.isDeferEdit() ? deferEdit : Mono.<Void>empty())
            .then(Mono.defer(() -> component.getInteraction().apply(context)))
            .checkpoint("ComponentListener#handleInteraction Processing")
            .onErrorResume(throwable -> deferEdit.then(
                this.getDiscordBot().getExceptionHandler().handleException(
                    ExceptionContext.of(
                        this.getDiscordBot(),
                        context,
                        throwable,
                        String.format("%s Exception", this.getTitle())
                    )
                )
            ))
            .switchIfEmpty(
                Mono.just(entry)
                    .filter(CachedResponse::isModified)
                    .flatMap(__ -> followup.isEmpty() ? context.edit() : context.editFollowup())
            );
    }

}
