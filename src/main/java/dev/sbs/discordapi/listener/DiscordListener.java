package dev.sbs.discordapi.listener;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.util.DiscordReference;
import dev.simplified.reflection.Reflection;
import dev.simplified.util.StringUtil;
import discord4j.core.event.domain.Event;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.util.function.Function;

/**
 * Abstract base for all Discord event listeners, binding a specific {@link Event}
 * subtype to a reactive handler function.
 * <p>
 * Subclasses are discovered via classpath scanning and registered through
 * {@link DiscordBot}. The resolved event class and a human-readable title
 * (derived from the subclass name) are captured at construction time.
 *
 * @param <T> the Discord4J event type this listener handles
 * @see DiscordBot
 * @see DiscordReference
 */
@Getter
public abstract class DiscordListener<T extends Event> extends DiscordReference implements Function<T, Publisher<Void>> {

    /** The resolved {@link Event} subclass this listener is bound to. */
    private final @NotNull Class<T> eventClass;

    /** A human-readable title derived from the listener's class name. */
    private final @NotNull String title;

    /**
     * Constructs a new {@code DiscordListener} bound to the given bot instance.
     *
     * @param discordBot the bot instance this listener belongs to
     */
    public DiscordListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
        this.eventClass = Reflection.getSuperClass(this);
        this.title = StringUtil.join(this.getClass().getSimpleName().split("(?=\\p{Upper})"), " ");
    }

}
