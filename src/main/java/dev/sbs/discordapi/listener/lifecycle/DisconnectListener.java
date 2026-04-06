package dev.sbs.discordapi.listener.lifecycle;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.lifecycle.DisconnectEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Listener for gateway disconnect events, invoking the bot's disconnect
 * hook and shutting down the scheduler on disconnection.
 */
public class DisconnectListener extends DiscordListener<DisconnectEvent> {

    /**
     * Constructs a new {@code DisconnectListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public DisconnectListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull DisconnectEvent event) {
        return Mono.fromRunnable(() -> {
            this.getDiscordBot().onGatewayDisconnect();
            this.getDiscordBot().getScheduler().shutdown();
        });
    }

}
