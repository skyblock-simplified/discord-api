package dev.sbs.discordapi;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.command.parameter.Argument;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.context.capability.ExceptionContext;
import dev.sbs.discordapi.context.command.AutoCompleteContext;
import dev.sbs.discordapi.context.command.MessageCommandContext;
import dev.sbs.discordapi.context.command.SlashCommandContext;
import dev.sbs.discordapi.context.command.UserCommandContext;
import dev.sbs.discordapi.context.component.ButtonContext;
import dev.sbs.discordapi.context.component.ModalContext;
import dev.sbs.discordapi.context.component.OptionContext;
import dev.sbs.discordapi.context.component.SelectMenuContext;
import dev.sbs.discordapi.context.message.ReactionContext;
import dev.sbs.discordapi.exception.DiscordClientException;
import dev.sbs.discordapi.exception.DiscordGatewayException;
import dev.sbs.discordapi.handler.CommandHandler;
import dev.sbs.discordapi.handler.DiscordConfig;
import dev.sbs.discordapi.handler.EmojiHandler;
import dev.sbs.discordapi.handler.exception.CompositeExceptionHandler;
import dev.sbs.discordapi.handler.exception.DiscordExceptionHandler;
import dev.sbs.discordapi.handler.exception.ExceptionHandler;
import dev.sbs.discordapi.handler.exception.SentryExceptionHandler;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.handler.response.ResponseFollowup;
import dev.sbs.discordapi.handler.response.ResponseHandler;
import dev.sbs.discordapi.handler.shard.ShardHandler;
import dev.sbs.discordapi.listener.DiscordListener;
import dev.sbs.discordapi.listener.command.AutoCompleteListener;
import dev.sbs.discordapi.listener.command.MessageCommandListener;
import dev.sbs.discordapi.listener.command.SlashCommandListener;
import dev.sbs.discordapi.listener.command.UserCommandListener;
import dev.sbs.discordapi.listener.component.ButtonListener;
import dev.sbs.discordapi.listener.component.ModalListener;
import dev.sbs.discordapi.listener.component.SelectMenuListener;
import dev.sbs.discordapi.listener.message.MessageCreateListener;
import dev.sbs.discordapi.listener.message.MessageDeleteListener;
import dev.sbs.discordapi.listener.message.ReactionRemoveListener;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.page.FormPage;
import dev.sbs.discordapi.response.page.Page;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import dev.simplified.scheduler.Scheduler;
import dev.simplified.util.Logging;
import dev.simplified.util.SystemUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.Event;
import discord4j.core.event.domain.lifecycle.ConnectEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.discordjson.json.UserData;
import discord4j.rest.request.RouteMatcher;
import discord4j.rest.response.ResponseFunction;
import discord4j.rest.route.Routes;
import io.netty.channel.unix.Errors;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.lang.reflect.Modifier;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Discord4J Framework Wrapper for Discord Bots.
 * <ul>
 *     <li>Commands
 *     <ul>
 *         <li>{@link Structure Immutable API Structure}</li>
 *         <li>{@link CommandHandler Registration & Caching}</li>
 *         <li>{@link DiscordCommand Implementation}
 *         <ul>
 *             <li>Message Commands ({@link MessageCommandContext Context}, {@link MessageCommandListener Listener})</li>
 *             <li>Slash Commands ({@link SlashCommandContext Context}, {@link SlashCommandListener Listener})
 *             <ul>
 *                 <li>{@link Parameter Parameters}</li>
 *                 <li>{@link Argument Arguments}</li>
 *             </ul></li>
 *             <li>User Commands ({@link UserCommandContext Context}, {@link UserCommandListener Listener})</li>
 *             <li>Auto Complete ({@link AutoCompleteContext Context}, {@link AutoCompleteListener Listener})</li>
 *         </ul></li>
 *     </ul></li>
 *     <li>Responses
 *     <ul>
 *         <li>{@link ResponseHandler Registration & Caching}</li>
 *         <li>{@link Page Pages}</li>
 *         <li>Implementations
 *         <ul>
 *             <li>{@link Response}</li>
 *             <li>{@link FormPage}</li>
 *             <li>{@link ResponseFollowup Followups}</li>
 *         </ul></li>
 *         <li>Components
 *         <ul>
 *             <li>Buttons ({@link ButtonContext Context}, {@link ButtonListener Listener})</li>
 *             <li>Modals ({@link ModalContext Context}, {@link TextInput Text Input Context}, {@link ModalListener Listener})</li>
 *             <li>Select Menus ({@link SelectMenuContext Context}, {@link OptionContext Option Context}, {@link SelectMenuListener Listener})</li>
 *         </ul></li>
 *         <li>Messages ({@link MessageCreateListener Create Listener}, {@link MessageDeleteListener Delete Listener})</li>
 *         <li>Reactions ({@link ReactionContext Context}, {@link ReactionRemoveListener Add Listener}, {@link ReactionRemoveListener Remove Listener})
 *     </ul></li>
 * </ul>
 * @see <a href="https://github.com/Discord4J/Discord4J">Discord4J</a>
 */
@Getter
@Log4j2
public abstract class DiscordBot {

    private final @NotNull Scheduler scheduler = new Scheduler();
    private final @NotNull DiscordConfig config;

    // Handlers
    private final @NotNull ExceptionHandler exceptionHandler;
    private final @NotNull EmojiHandler emojiHandler;
    private final @NotNull ResponseHandler responseHandler;
    private final @NotNull CommandHandler commandHandler;

    // REST
    private DiscordClient client;
    private UserData self;

    // Gateway
    private GatewayDiscordClient gateway;
    private ShardHandler shardHandler;

    protected DiscordBot(@NotNull DiscordConfig config) {
        this.config = config;
        this.exceptionHandler = this.buildExceptionHandler();
        this.emojiHandler = new EmojiHandler(this);
        this.responseHandler = new ResponseHandler();
        Logging.setRootLevel(this.getConfig().getLogLevel());

        this.commandHandler = CommandHandler.builder(this)
            .withCommands(this.getConfig().getCommands())
            .build();
    }

    /**
     * Establish a connection to the Discord Gateway, enabling real-time events, presence, voice, etc.
     * <ul>
     *   <li>Initializes the Discord Gateway with specified intents, client presence, and member request filters.</li>
     *   <li>Handles the {@link ConnectEvent} to initialize additional components and perform post-connection setup:
     *     <ul>
     *       <li>Calls the {@code onGatewayConnected} method upon a successful connection.</li>
     *       <li>Schedules a periodic task to clean up inactive cached responses and update message states.</li>
     *       <li>Registers event listeners dynamically by scanning resources and loading implementations of
     *           {@code DiscordListener} or user-defined listeners from the configuration.</li>
     *       <li>Registers and uploads custom emojis using the configured emoji handler.</li>
     *       <li>Updates global application commands through the command handler.</li>
     *     </ul>
     *   </li>
     *   <li>Logs the bot's username after successfully logging in.</li>
     * </ul>
     * <p>
     * Waits for manual gateway termination to remain online and operational indefinitely.
     *
     * @throws DiscordGatewayException If unable to connect to the Discord Gateway.
     */
    protected final void connect() throws DiscordGatewayException {
        if (this.gateway != null)
            throw new IllegalStateException("Discord Gateway already connected");

        log.info("Connecting to Discord Gateway");
        this.gateway = this.getClient()
            .gateway()
            .setEnabledIntents(this.getConfig().getIntents())
            .setInitialPresence(this.getConfig()::getClientPresence)
            .setMemberRequestFilter(this.getConfig().getMemberRequestFilter())
            .withEventDispatcher(eventDispatcher -> eventDispatcher.on(ConnectEvent.class)
                .map(ConnectEvent::getClient)
                .flatMap(gatewayDiscordClient -> {
                    log.info("Gateway Connected");
                    this.onGatewayConnected(gatewayDiscordClient);

                    log.info("Scheduling Cache Cleaner");
                    this.scheduler.scheduleAsync(() -> this.responseHandler.matchAll(CachedResponse::notActive).forEach(entry -> {
                        // Clear Cached Message
                        this.responseHandler.remove(entry);

                        // Clear Message Components and Reactions
                        this.getGateway()
                            .getChannelById(entry.getChannelId())
                            .ofType(MessageChannel.class)
                            .flatMap(channel -> channel.getMessageById(entry.getMessageId()))
                            .flatMap(message -> Mono.just(entry.getResponse())
                                .flatMap(response -> message.removeAllReactions().then(message.edit(
                                    response.mutate()
                                        .disableAllComponents()
                                        .isRenderingPagingComponents(false)
                                        .build()
                                        .getD4jEditSpec()
                                )))
                            )
                            .subscribe();
                    }), 0, 1, TimeUnit.SECONDS);

                    log.info("Registering Event Listeners");
                    ConcurrentList<Publisher<Void>> eventListeners = Reflection.getResources()
                        .filterPackage(DiscordListener.class)
                        .getSubtypesOf(DiscordListener.class)
                        .stream()
                        .filter(listenerClass -> !Modifier.isAbstract(listenerClass.getModifiers()))
                        .map(listenerClass -> this.createListener(eventDispatcher, listenerClass))
                        .collect(Concurrent.toList());

                    this.getConfig()
                        .getListeners()
                        .stream()
                        .map(listenerClass -> this.createListener(eventDispatcher, listenerClass))
                        .forEach(eventListeners::add);

                    log.info("Logged in as {}", this.getSelf().username());
                    return Mono.when(eventListeners)
                        .and(this.getCommandHandler().updateApplicationCommands())
                        .and(this.getEmojiHandler().sync());
                })
            )
            .login()
            .blockOptional()
            .orElseThrow(() -> new DiscordGatewayException("Unable to connect to gateway."));

        this.shardHandler = new ShardHandler(this);
        this.getGateway().onDisconnect().block(); // Stay Online
    }

    /**
     * Initializes and configures the Discord REST Client, allowing for REST-only API usage.
     * <ul>
     *   <li>Creates a Discord client using the token provided.</li>
     *   <li>Sets the default allowed mentions for the client.</li>
     *   <li>Suppresses certain client responses:
     *     <ul>
     *       <li>404 Not Found responses are ignored.</li>
     *       <li>400 Bad Request responses for reaction creation are suppressed.</li>
     *     </ul>
     *   </li>
     *   <li>Implements retry logic for network exceptions such as {@code SocketException}
     *       or {@code NativeIoException}, with exponential backoff up to 10 retries.</li>
     * </ul>
     */
    protected final void login() {
        if (this.client != null)
            throw new IllegalStateException("Discord Client already initialized.");

        log.info("Creating Discord Client");
        this.client = DiscordClientBuilder.create(this.getConfig().getToken())
            .setDefaultAllowedMentions(this.getConfig().getAllowedMentions())
            .onClientResponse(ResponseFunction.emptyIfNotFound()) // Suppress 404 Not Found
            .onClientResponse(ResponseFunction.emptyOnErrorStatus(RouteMatcher.route(Routes.REACTION_CREATE), 400)) // Suppress (Reaction Add) 400 Bad Request
            .onClientResponse(ResponseFunction.retryWhen( // Retry Network Exceptions
                RouteMatcher.any(),
                Retry.backoff(10, Duration.ofSeconds(2))
                    .filter(throwable -> throwable instanceof SocketException || throwable instanceof Errors.NativeIoException))
            )
            .build();

        this.self = this.client.getSelf()
            .blockOptional()
            .orElseThrow(() -> new DiscordClientException("Unable to locate self."));

        this.onClientCreated(this.client);
    }

    public final @NotNull DiscordClient getClient() {
        if (this.client == null)
            throw new IllegalStateException("Discord Client not initialized.");

        return this.client;
    }

    public final @NotNull Snowflake getClientId() {
        return this.getClient().getCoreResources().getSelfId();
    }

    public final @NotNull GatewayDiscordClient getGateway() {
        if (this.gateway == null)
            throw new IllegalStateException("Discord Gateway not connected");

        return this.gateway;
    }

    public final @NotNull Guild getMainGuild() {
        return this.getGateway()
            .getGuildById(Snowflake.of(this.getConfig().getMainGuildId()))
            .blockOptional()
            .orElseThrow(() -> new DiscordGatewayException("Unable to locate main guild."));
    }

    /**
     * Instantiates the given {@link DiscordListener} subclass and registers it
     * with the event dispatcher, wrapping it with top-level error handling that
     * forwards unhandled exceptions to the exception handler.
     *
     * @param <T> the Discord4J event type
     * @param eventDispatcher the event dispatcher to register with
     * @param listenerClass the listener class to instantiate and register
     * @return a publisher completing when the listener subscription ends
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T extends Event> @NonNull Publisher<Void> createListener(@NotNull EventDispatcher eventDispatcher, @NotNull Class<? extends DiscordListener> listenerClass) {
        DiscordListener<T> instance = (DiscordListener<T>) new Reflection<>(listenerClass).newInstance(this);
        return eventDispatcher.on(instance.getEventClass(), event ->
            Mono.from(instance.apply(event)).onErrorResume(throwable -> this.getExceptionHandler().handleException(
                ExceptionContext.of(this, event, throwable, instance.getTitle() + " Exception")
            ))
        );
    }

    /**
     * Builds the exception handler chain based on configuration. Adds a
     * {@link SentryExceptionHandler} if a Sentry DSN is available (config
     * takes priority over {@code SENTRY_DSN} environment variable), and a
     * {@link DiscordExceptionHandler} if a debug channel is configured.
     *
     * @return the configured exception handler
     */
    private @NotNull ExceptionHandler buildExceptionHandler() {
        ConcurrentList<ExceptionHandler> handlers = Concurrent.newList();

        // Resolve Sentry DSN: config > env var
        this.config.getSentryDsn()
            .or(() -> SystemUtil.getEnv("SENTRY_DSN"))
            .ifPresent(dsn -> handlers.add(new SentryExceptionHandler(this, dsn)));

        // Add Discord handler
        handlers.add(this.config.getLogChannelId()
            .map(channelId -> new DiscordExceptionHandler(this, channelId))
            .orElse(new DiscordExceptionHandler(this, -1L)));

        if (handlers.size() == 1)
            return handlers.getFirst();

        return new CompositeExceptionHandler(this, handlers);
    }

    /**
     * Starts the bot by executing the full two-phase initialization lifecycle.
     * <p>
     * <b>Phase 1 - REST Client ({@link #login()})</b>
     * <ul>
     *     <li>Creates and configures the {@link DiscordClient} with the bot token, allowed mentions,
     *         response suppression rules, and network retry logic.</li>
     *     <li>Fetches the bot's own {@link UserData} from Discord.</li>
     *     <li>Invokes the {@link #onClientCreated(DiscordClient)} hook.</li>
     * </ul>
     * <p>
     * <b>Phase 2 - Gateway ({@link #connect()})</b>
     * <ul>
     *     <li>Opens a Gateway connection with the configured intents, presence, and member request filter.</li>
     *     <li>On the initial {@link ConnectEvent}:
     *     <ul>
     *         <li>Invokes the {@link #onGatewayConnected(GatewayDiscordClient)} hook.</li>
     *         <li>Schedules a periodic cache cleaner that removes inactive {@link CachedResponse} entries.</li>
     *         <li>Discovers and registers all {@link DiscordListener} implementations.</li>
     *         <li>Syncs custom emojis via the {@link EmojiHandler}.</li>
     *         <li>Updates global application commands via the {@link CommandHandler}.</li>
     *     </ul></li>
     *     <li>Blocks the calling thread on {@link GatewayDiscordClient#onDisconnect()} to keep the bot online
     *         until the gateway is terminated.</li>
     * </ul>
     *
     * @throws DiscordGatewayException if the gateway connection cannot be established
     * @see #login()
     * @see #connect()
     */
    protected final void start() {
        this.login();
        this.connect();
    }

    @SuppressWarnings("unused")
    protected void onClientCreated(@NotNull DiscordClient discordClient) { }

    @SuppressWarnings("unused")
    protected void onGatewayConnected(@NotNull GatewayDiscordClient gatewayDiscordClient) { }

    protected void onGatewayDisconnect() { }

}
