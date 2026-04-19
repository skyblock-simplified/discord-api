package dev.sbs.discordapi.handler;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.listener.Component;
import dev.sbs.discordapi.response.PersistentResponse;
import lombok.experimental.UtilityClass;
import reactor.util.context.Context;

/**
 * Holds the singleton Reactor {@link Context} key used to track which class
 * is currently dispatching a reactive pipeline.
 *
 * <p>
 * Writers:
 * <ul>
 *   <li>{@link DiscordCommand#apply} wraps its call to {@code process(context)}
 *       with {@code .contextWrite(ctx -> ctx.put(KEY, this.getClass()))}</li>
 *   <li>The {@link PersistentComponentHandler} dispatch wrapper does the same
 *       when invoking a {@link Component}-annotated method, passing the route's
 *       owner class</li>
 * </ul>
 *
 * <p>
 * Reader:
 * <ul>
 *   <li>The response locator's {@code store} method uses
 *       {@code Mono.deferContextual(ctx -> ...)} to read the key when
 *       persisting a {@link dev.sbs.discordapi.response.Response Response}
 *       whose {@link dev.sbs.discordapi.response.Response.Builder#isPersistent(boolean)
 *       isPersistent} flag is set. The key identifies the class whose
 *       {@link PersistentResponse} method should be associated with the
 *       new persistent row</li>
 * </ul>
 *
 * <p>
 * Using a Reactor {@link Context} key instead of a {@link ThreadLocal} ensures
 * the binding survives scheduler boundaries within a single reactive pipeline,
 * which the Discord4J dispatch chain relies on.
 */
@UtilityClass
public final class DispatchingClassContextKey {

    /** The Reactor {@link Context} key under which the dispatching class is stored. */
    public static final String KEY = "dev.sbs.discordapi.dispatching-class";

}
