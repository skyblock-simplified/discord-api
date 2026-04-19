package dev.sbs.discordapi.handler;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.scope.ComponentContext;
import dev.sbs.discordapi.listener.Component;
import dev.sbs.discordapi.listener.PersistentComponentListener;
import dev.sbs.discordapi.response.PersistentResponse;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.util.DiscordReference;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.reflection.Reflection;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Central registry for {@link Component @Component}-annotated component
 * interaction handlers and {@link PersistentResponse @PersistentResponse}-
 * annotated response builder methods. Constructed once per bot during
 * {@link DiscordBot#connect()}, AFTER the {@link CommandHandler} (which it
 * reads from) and AFTER classpath scanning has produced the set of
 * {@link PersistentComponentListener} subclasses.
 *
 * <p>
 * Discovery walks each command and listener instance's declared methods,
 * validates the annotation contract, and stores a {@link MethodHandle} per
 * route so dispatch is reflection-free at runtime. Validation errors and
 * duplicate-id conflicts are logged via {@link DiscordReference#getLog()}.
 *
 * @see Component
 * @see PersistentResponse
 * @see PersistentComponentListener
 */
@Log4j2
public final class PersistentComponentHandler extends DiscordReference {

    /** Routing entry for a {@link Component @Component}-annotated method. */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class ComponentRoute {

        /** The instance hosting the annotated method. */
        private final @NotNull Object instance;

        /** The class hosting the annotated method - the dispatching owner. */
        private final @NotNull Class<?> ownerClass;

        /** Reflection-free invocation handle for the method. */
        private final @NotNull MethodHandle methodHandle;

        /** The expected static parameter type (a {@link ComponentContext} subtype). */
        private final @NotNull Class<?> expectedContextType;

    }

    /** Routing entry for a {@link PersistentResponse @PersistentResponse}-annotated builder method. */
    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class BuilderRoute {

        /** The instance hosting the annotated builder method. */
        private final @NotNull Object instance;

        /** The class hosting the annotated builder method. */
        private final @NotNull Class<?> ownerClass;

        /** Reflection-free invocation handle for the builder method. */
        private final @NotNull MethodHandle methodHandle;

    }

    /** Loaded {@link PersistentComponentListener} instances, in registration order. */
    @Getter private final @NotNull ConcurrentList<PersistentComponentListener> loadedListeners;

    /** Map from explicit {@code custom_id} to its routing entry. */
    @Getter private final @NotNull ConcurrentMap<String, ComponentRoute> componentRoutes = Concurrent.newMap();

    /** Map from {@code (ownerClass, builderId)} to its routing entry. */
    @Getter private final @NotNull ConcurrentMap<Pair<Class<?>, String>, BuilderRoute> builderRoutes = Concurrent.newMap();

    /**
     * Constructs the registry by scanning the given commands and listeners
     * for annotated methods.
     *
     * @param discordBot the bot this handler belongs to
     * @param loadedCommands command instances loaded by {@link CommandHandler}
     * @param listenerClasses persistent listener subclasses discovered via classpath scan
     */
    @SuppressWarnings("rawtypes")
    public PersistentComponentHandler(
        @NotNull DiscordBot discordBot,
        @NotNull ConcurrentList<DiscordCommand> loadedCommands,
        @NotNull ConcurrentSet<Class<? extends PersistentComponentListener>> listenerClasses
    ) {
        super(discordBot);

        this.getLog().info("Loading Persistent Component Listeners");
        this.loadedListeners = listenerClasses.stream()
            .map(listenerClass -> (PersistentComponentListener) new Reflection<>(listenerClass).newInstance(discordBot))
            .collect(Concurrent.toList());

        this.getLog().info("Discovering Persistent Component Routes");
        loadedCommands.forEach(command -> this.scanInstance(command, command.getClass()));
        this.loadedListeners.forEach(listener -> this.scanInstance(listener, listener.getClass()));
    }

    /**
     * Looks up the {@link BuilderRoute} for the given owner class and
     * optional builder discriminator id.
     *
     * @param ownerClass the host class for the {@code @PersistentResponse} method
     * @param builderId the builder discriminator id, empty for the single-builder case
     * @return the matching builder route, or empty if no route is registered
     */
    public @NotNull Optional<BuilderRoute> findBuilder(@NotNull Class<?> ownerClass, @NotNull String builderId) {
        return Optional.ofNullable(this.builderRoutes.get(Pair.of(ownerClass, builderId)));
    }

    /**
     * Looks up the {@link ComponentRoute} for the given Discord component
     * {@code custom_id}.
     *
     * @param customId the explicit custom id passed when constructing the component
     * @return the matching component route, or empty if no route is registered
     */
    public @NotNull Optional<ComponentRoute> findComponent(@NotNull String customId) {
        return Optional.ofNullable(this.componentRoutes.get(customId));
    }

    /**
     * Reflectively scans the given instance's declared methods for
     * {@link Component @Component} and {@link PersistentResponse @PersistentResponse}
     * annotations, validating the signatures and registering routes.
     */
    private void scanInstance(@NotNull Object instance, @NotNull Class<?> ownerClass) {
        for (Method method : ownerClass.getDeclaredMethods()) {
            Component componentAnnotation = method.getAnnotation(Component.class);
            if (componentAnnotation != null)
                this.registerComponentRoute(instance, ownerClass, method, componentAnnotation);

            PersistentResponse responseAnnotation = method.getAnnotation(PersistentResponse.class);
            if (responseAnnotation != null)
                this.registerBuilderRoute(instance, ownerClass, method, responseAnnotation);
        }
    }

    /** Validates and registers a {@code @Component}-annotated method. */
    private void registerComponentRoute(@NotNull Object instance, @NotNull Class<?> ownerClass, @NotNull Method method, @NotNull Component annotation) {
        if (method.getParameterCount() != 1) {
            this.getLog().warn(
                "@Component method '{}#{}' must declare exactly one parameter, ignoring",
                ownerClass.getName(),
                method.getName()
            );
            return;
        }

        Class<?> paramType = method.getParameterTypes()[0];
        if (!ComponentContext.class.isAssignableFrom(paramType)) {
            this.getLog().warn(
                "@Component method '{}#{}' parameter must be a ComponentContext subtype, found '{}', ignoring",
                ownerClass.getName(),
                method.getName(),
                paramType.getName()
            );
            return;
        }

        if (!Publisher.class.isAssignableFrom(method.getReturnType())) {
            this.getLog().warn(
                "@Component method '{}#{}' must return a Publisher<Void>, found '{}', ignoring",
                ownerClass.getName(),
                method.getName(),
                method.getReturnType().getName()
            );
            return;
        }

        String customId = annotation.value();
        if (this.componentRoutes.containsKey(customId)) {
            ComponentRoute existing = this.componentRoutes.get(customId);
            this.getLog().warn(
                "@Component custom id '{}' on '{}#{}' conflicts with '{}', ignoring",
                customId,
                ownerClass.getName(),
                method.getName(),
                existing.getOwnerClass().getName()
            );
            return;
        }

        try {
            method.setAccessible(true);
            MethodHandle handle = MethodHandles.lookup().unreflect(method);
            this.componentRoutes.put(customId, new ComponentRoute(instance, ownerClass, handle, paramType));
        } catch (IllegalAccessException ex) {
            this.getLog().error(
                "@Component method '{}#{}' could not be unreflected, ignoring",
                ownerClass.getName(),
                method.getName(),
                ex
            );
        }
    }

    /** Validates and registers a {@code @PersistentResponse}-annotated method. */
    private void registerBuilderRoute(@NotNull Object instance, @NotNull Class<?> ownerClass, @NotNull Method method, @NotNull PersistentResponse annotation) {
        if (method.getParameterCount() != 1) {
            this.getLog().warn(
                "@PersistentResponse method '{}#{}' must declare exactly one parameter, ignoring",
                ownerClass.getName(),
                method.getName()
            );
            return;
        }

        Class<?> paramType = method.getParameterTypes()[0];
        if (!EventContext.class.isAssignableFrom(paramType)) {
            this.getLog().warn(
                "@PersistentResponse method '{}#{}' parameter must be an EventContext (or subtype), found '{}', ignoring",
                ownerClass.getName(),
                method.getName(),
                paramType.getName()
            );
            return;
        }

        if (!Response.class.isAssignableFrom(method.getReturnType())) {
            this.getLog().warn(
                "@PersistentResponse method '{}#{}' must return Response, found '{}', ignoring",
                ownerClass.getName(),
                method.getName(),
                method.getReturnType().getName()
            );
            return;
        }

        Pair<Class<?>, String> key = Pair.of(ownerClass, annotation.value());
        if (this.builderRoutes.containsKey(key)) {
            this.getLog().warn(
                "@PersistentResponse builder id '{}' on '{}' is already registered, ignoring duplicate '{}#{}'",
                annotation.value(),
                ownerClass.getName(),
                ownerClass.getName(),
                method.getName()
            );
            return;
        }

        try {
            method.setAccessible(true);
            MethodHandle handle = MethodHandles.lookup().unreflect(method);
            this.builderRoutes.put(key, new BuilderRoute(instance, ownerClass, handle));
        } catch (IllegalAccessException ex) {
            this.getLog().error(
                "@PersistentResponse method '{}#{}' could not be unreflected, ignoring",
                ownerClass.getName(),
                method.getName(),
                ex
            );
        }
    }

}
