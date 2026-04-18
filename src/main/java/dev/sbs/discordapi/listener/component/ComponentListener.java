package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.capability.EventInteractable;
import dev.sbs.discordapi.component.capability.UserInteractable;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.sbs.discordapi.context.scope.ComponentContext;
import dev.sbs.discordapi.handler.DispatchingClassContextKey;
import dev.sbs.discordapi.handler.PersistentComponentHandler;
import dev.sbs.discordapi.handler.response.CachedResponse;
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
 * matching an incoming event to a {@link CachedResponse} via the
 * {@link dev.sbs.discordapi.handler.response.ResponseLocator ResponseLocator},
 * locating the interacted {@link EventInteractable}, and dispatching to its
 * registered interaction handler.
 *
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
        if (event.getInteraction().getUser().isBot())
            return Mono.empty();

        return this.getDiscordBot()
            .getResponseLocator()
            .findForInteraction(event)
            .switchIfEmpty(event.deferEdit().then(Mono.empty()))
            .flatMap(entry -> this.handleEvent(event, entry))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Routes a matched cache entry to the appropriate dispatch path. Override
     * for special handling (e.g. modals) that needs to bypass the standard
     * tree walk. Persistent entries first try the
     * {@link PersistentComponentHandler} routing registry for an explicit
     * {@code @Component}-annotated handler; if no registered route exists,
     * the inline path is used as a fallback.
     */
    protected Mono<Void> handleEvent(@NotNull E event, @NotNull CachedResponse entry) {
        if (entry.isPersistent()) {
            Optional<PersistentComponentHandler.ComponentRoute> route = this.getDiscordBot()
                .getPersistentComponentHandler()
                .findComponent(event.getCustomId());

            if (route.isPresent())
                return this.dispatchPersistent(event, entry, route.get());
        }

        return this.dispatchInline(event, entry);
    }

    /**
     * Legacy in-memory dispatch path: walks the response's component tree to
     * find a matching {@link UserInteractable} by custom id and invokes its
     * inline interaction lambda.
     */
    private @NotNull Mono<Void> dispatchInline(@NotNull E event, @NotNull CachedResponse entry) {
        entry.setBusy();
        Optional<CachedResponse> followup = entry.isFollowup() ? Optional.of(entry) : Optional.empty();
        CachedResponse target = followup.orElse(entry);

        return Flux.fromIterable(target.getResponse().getCachedPageComponents())
            .concatWith(Flux.fromIterable(target.getResponse().getHistoryHandler().getCurrentPage().getComponents()))
            .concatMap(tlmComponent -> Flux.fromStream(tlmComponent.flattenComponents()))
            .filter(UserInteractable.class::isInstance)
            .filter(component -> event.getCustomId().equals(((UserInteractable) component).getIdentifier()))
            .filter(this.componentClass::isInstance)
            .map(this.componentClass::cast)
            .singleOrEmpty()
            .flatMap(component -> this.handleInteraction(event, entry, component, followup))
            .then(entry.updateLastInteract())
            .then();
    }

    /**
     * Persistent dispatch path: invokes the {@link PersistentComponentHandler}
     * route via its {@link java.lang.invoke.MethodHandle MethodHandle} and
     * decorates the resulting publisher with the dispatching class context
     * key so any nested {@link dev.sbs.discordapi.context.EventContext#reply
     * reply} call sees the registered owner class.
     */
    @SuppressWarnings("unchecked")
    private @NotNull Mono<Void> dispatchPersistent(@NotNull E event, @NotNull CachedResponse entry, @NotNull PersistentComponentHandler.ComponentRoute route) {
        entry.setBusy();
        Optional<CachedResponse> followup = entry.isFollowup() ? Optional.of(entry) : Optional.empty();
        CachedResponse target = followup.orElse(entry);

        Class<?> expected = route.getExpectedContextType();
        if (!this.expectedContextMatches(expected)) {
            this.getLog().warn(
                "@Component route '{}' on {} expected {} but listener {} dispatches a different context type",
                event.getCustomId(),
                route.getOwnerClass().getName(),
                expected.getSimpleName(),
                this.getClass().getSimpleName()
            );
            return event.deferEdit().then();
        }

        // Find the matching component on the response so the inline handler
        // contract (modal handlers, deferEdit gating, etc.) still sees a
        // concrete component instance.
        Optional<T> matched = target.getResponse()
            .getCachedPageComponents()
            .stream()
            .flatMap(Component::flattenComponents)
            .filter(UserInteractable.class::isInstance)
            .filter(component -> event.getCustomId().equals(((UserInteractable) component).getIdentifier()))
            .filter(this.componentClass::isInstance)
            .map(this.componentClass::cast)
            .findFirst();

        if (matched.isEmpty())
            return event.deferEdit().then();

        C context = this.getContext(event, target.getResponse(), matched.get(), followup);

        return Mono.from((Publisher<Void>) tryInvoke(route, context))
            .checkpoint("ComponentListener#dispatchPersistent Processing")
            .contextWrite(reactorCtx -> reactorCtx.put(DispatchingClassContextKey.KEY, route.getOwnerClass()))
            .onErrorResume(throwable -> this.getDiscordBot().getExceptionHandler().handleException(
                ExceptionContext.of(
                    this.getDiscordBot(),
                    context,
                    throwable,
                    String.format("%s Exception", this.getTitle())
                )
            ))
            .then(Mono.defer(() -> entry.isModified()
                ? (followup.isEmpty() ? context.edit() : context.editFollowup())
                : Mono.empty()
            ))
            .then(entry.updateLastInteract())
            .then();
    }

    /**
     * Returns whether the given expected context type from a registered
     * persistent route matches what this listener dispatches.
     */
    private boolean expectedContextMatches(@NotNull Class<?> expected) {
        return expected.isAssignableFrom(this.getContextClass())
            || this.getContextClass().isAssignableFrom(expected);
    }

    /** The static context class this listener constructs and dispatches. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private @NotNull Class<C> getContextClass() {
        return (Class<C>) (Class) Reflection.getSuperClass(this, 1);
    }

    /** Reflection helper that throws checked exceptions through {@link RuntimeException}. */
    private static Object tryInvoke(@NotNull PersistentComponentHandler.ComponentRoute route, @NotNull Object context) {
        try {
            return route.getMethodHandle().invoke(route.getInstance(), context);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
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
    protected abstract @NotNull C getContext(@NotNull E event, @NotNull Response cachedMessage, @NotNull T component, @NotNull Optional<CachedResponse> followup);

    /**
     * Executes the component's registered inline interaction handler within an
     * error-handling pipeline, then edits the response if it was modified.
     *
     * @param event the Discord4J interaction event
     * @param entry the matched response cache entry
     * @param component the matched component
     * @param followup the matched followup, if the interaction targets one
     * @return a reactive pipeline completing when the interaction is handled
     */
    protected final @NotNull Mono<Void> handleInteraction(@NotNull E event, @NotNull CachedResponse entry, @NotNull T component, @NotNull Optional<CachedResponse> followup) {
        C context = this.getContext(event, entry.getResponse(), component, followup);

        Mono<Void> deferEdit = Mono.defer(() -> entry.getState() == CachedResponse.State.DEFERRED ? Mono.empty() : context.deferEdit());

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
            .then(Mono.defer(() -> entry.isModified()
                ? (followup.isEmpty() ? context.edit() : context.editFollowup())
                : Mono.empty()
            ));
    }

}
