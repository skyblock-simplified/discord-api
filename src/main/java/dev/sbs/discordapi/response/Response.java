package dev.sbs.discordapi.response;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.media.Attachment;
import dev.sbs.discordapi.component.media.MediaData;
import dev.sbs.discordapi.component.scope.TopLevelMessageComponent;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.scope.MessageContext;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Field;
import dev.sbs.discordapi.response.handler.HistoryHandler;
import dev.sbs.discordapi.response.handler.PaginationHandler;
import dev.sbs.discordapi.response.handler.item.EmbedItemHandler;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.TreePage;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.query.SearchFunction;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.ExceptionUtil;
import dev.simplified.util.NumberUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.core.spec.MessageCreateMono;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.discordjson.possible.Possible;
import discord4j.rest.util.AllowedMentions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Response {

    private final long buildTime = System.currentTimeMillis();
    private final @NotNull UUID uniqueId;
    private final @NotNull EventContext<?> eventContext;
    private final @NotNull Optional<Snowflake> referenceId;
    private final @NotNull Scheduler reactorScheduler;
    private final @NotNull AllowedMentions allowedMentions;
    private final int timeToLive;
    private final boolean ephemeral;
    private final @NotNull ConcurrentList<Attachment> attachments;
    private final @NotNull Function<MessageContext<MessageCreateEvent>, Mono<Void>> createInteraction;
    private final boolean renderingPagingComponents;
    private final @NotNull HistoryHandler<Page, String> historyHandler;
    private final @NotNull PaginationHandler paginationHandler;
    @Getter(AccessLevel.NONE)
    private ConcurrentList<TopLevelMessageComponent> cachedPageComponents = Concurrent.newUnmodifiableList();

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull Builder from(@NotNull Response response) {
        return builder()
            .withContext(response.getEventContext())
            .withUniqueId(response.getUniqueId())
            .withPages(response.getPages())
            .withAttachments(response.getAttachments())
            .withReference(response.getReferenceId())
            .withReactorScheduler(response.getReactorScheduler())
            .withTimeToLive(response.getTimeToLive())
            .isRenderingPagingComponents(response.isRenderingPagingComponents())
            .isEphemeral(response.isEphemeral())
            .withPageHistory(response.getHistoryHandler().getIdentifierHistory())
            .withItemPage(response.getHistoryHandler().getCurrentPage().getItemHandler().getCurrentIndex())
            .onCreate(response.getCreateInteraction());
    }

    public @NotNull ConcurrentList<TopLevelMessageComponent> getCachedPageComponents() {
        if (this.isRenderingPagingComponents() && this.isCacheUpdateRequired())
            this.cachedPageComponents = this.getPaginationHandler().buildCachedPageComponents(this.getHistoryHandler());

        return this.cachedPageComponents;
    }

    public @NotNull ConcurrentList<Page> getPages() {
        return this.getHistoryHandler().getItems();
    }

    public @NotNull Builder mutate() {
        return from(this);
    }

    // --- Content/Embed helpers ---

    private @NotNull ConcurrentList<Embed> getCurrentEmbeds() {
        Page currentPage = this.getHistoryHandler().getCurrentPage();
        ConcurrentList<Embed> embeds = Concurrent.newList();

        if (currentPage instanceof TreePage treePage)
            embeds.addAll(treePage.getEmbeds());

        if (currentPage.hasItems()) {
            EmbedItemHandler<?> legacyHandler = (EmbedItemHandler<?>) currentPage.getItemHandler();
            ConcurrentList<Field> renderFields = legacyHandler.getRenderFields();

            embeds.add(
                Embed.builder()
                    .withItems(legacyHandler.getCachedStaticItems())
                    .withFields(renderFields)
                    .build()
            );
        }

        return embeds;
    }

    private @NotNull Optional<String> getCurrentContent() {
        Page currentPage = this.getHistoryHandler().getCurrentPage();

        if (currentPage instanceof TreePage treePage)
            return treePage.getContent();

        return Optional.empty();
    }

    // --- Reply Streams ---

    public @NotNull Stream<Attachment> getPendingAttachments() {
        return Stream.concat(
            this.getAttachments().stream(),
            this.getCurrentComponents()
                .flatMap(Component::flattenComponents)
                .filter(Attachment.class::isInstance)
                .map(Attachment.class::cast)
        ).filter(Attachment::isPendingUpload);
    }

    public @NotNull Stream<TopLevelMessageComponent> getCurrentComponents() {
        return Stream.concat(this.getCachedPageComponents().stream(), this.getHistoryHandler().getCurrentPage().getComponents().stream());
    }

    public @NotNull ConcurrentList<Message.Flag> getCurrentFlags() {
        ConcurrentList<Message.Flag> flags = Concurrent.newList();
        flags.addIf(this::isComponentsV2, Message.Flag.IS_COMPONENTS_V2);
        return flags;
    }

    public boolean isComponentsV2() {
        return this.getCurrentComponents()
            .flatMap(Component::flattenComponents)
            .anyMatch(component -> component.getType().isRequireFlag());
    }

    // --- Cache ---

    /*@SuppressWarnings("unchecked")
    public <S> void editPageButton(@NotNull Function<Button, S> function, S value, Function<Button.Builder, Button.Builder> buttonBuilder) {
        this.getCachedPageComponents().forEach(topLevelComponent -> topLevelComponent.flattenComponents()
            .filter(LayoutComponent.class::isInstance)
            .map(LayoutComponent.class::cast)
            .forEach(layoutComponent -> layoutComponent.findComponent(Button.class, function, value)
                .ifPresent(button -> ((ConcurrentList<Component>) layoutComponent.getComponents()).set(
                    layoutComponent.getComponents().indexOf(button),
                    buttonBuilder.apply(button.mutate()).build()
                ))
            )
        );
    }*/

    /**
     * Edits the current page in-place using the given builder editor function,
     * casting the page's builder to the specified type.
     *
     * @param <B> the concrete builder type
     * @param builderType the builder class to cast to
     * @param editor the function that transforms the builder
     * @return this response
     */
    public <B extends Page.Builder> @NotNull Response editCurrentPage(@NotNull Class<B> builderType, @NotNull Function<B, B> editor) {
        this.historyHandler.editCurrentPage(page -> editor.apply(builderType.cast(page.mutate())).build());
        return this;
    }

    /**
     * Edits the current {@link TreePage} in-place using the given builder editor function.
     *
     * @param editor the function that transforms the tree page builder
     * @return this response
     */
    public @NotNull Response editCurrentPage(@NotNull Function<TreePage.TreePageBuilder, TreePage.TreePageBuilder> editor) {
        return this.editCurrentPage(TreePage.TreePageBuilder.class, editor);
    }

    public boolean isCacheUpdateRequired() {
        return this.getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getHistoryHandler().isCacheUpdateRequired() ||
            this.getHistoryHandler().getCurrentPage().getItemHandler().isCacheUpdateRequired();
    }

    public void setNoCacheUpdateRequired() {
        this.getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getHistoryHandler().setCacheUpdateRequired(false);
        this.getHistoryHandler().getCurrentPage().getItemHandler().setCacheUpdateRequired(false);
    }

    public void updateAttachments(@NotNull Message message) {
        if (this.getAttachments().contains(attachment -> attachment.getMediaData().getState(), MediaData.State.LOADING)) {
            for (int i = 0; this.getAttachments().notEmpty(); i++) {
                Attachment attachment = this.getAttachments().get(i);
                final int index = i;

                // Update Attachment
                message.getAttachments()
                    .stream()
                    .filter(d4jAttachment -> d4jAttachment.getFilename().equals(attachment.getMediaData().getName()))
                    .findFirst()
                    .ifPresent(d4jAttachment -> this.getAttachments().set(index, attachment.mutate(d4jAttachment).build()));

                // Update File
                message.getComponentById(attachment.getMediaData().getComponentId())
                    .filter(discord4j.core.object.component.File.class::isInstance)
                    .map(discord4j.core.object.component.File.class::cast)
                    .ifPresent(d4jFile -> this.getAttachments().set(index, attachment.mutate(d4jFile).build()));
            }
        }
    }

    // --- D4J Specs ---

    public @NotNull MessageCreateSpec getD4jCreateSpec() {
        return MessageCreateSpec.builder()
            .content(this.getCurrentContent().orElse(""))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .flags(this.getCurrentFlags())
            .nonce(this.getUniqueId().toString().substring(0, 25))
            .allowedMentions(this.getAllowedMentions())
            .messageReference(this.getReferenceId().isPresent() ? Possible.of(MessageReferenceData.builder().messageId(this.getReferenceId().get().asLong()).build()) : Possible.absent())
            .files(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(
                this.getCurrentComponents()
                    .map(TopLevelMessageComponent::getD4jComponent)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    public @NotNull MessageCreateMono getD4jCreateMono(@NotNull MessageChannel channel) {
        return MessageCreateMono.of(channel)
            .withContent(this.getCurrentContent().orElse(""))
            .withEmbeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .withFlags(this.getCurrentFlags())
            .withNonce(this.getUniqueId().toString().substring(0, 25))
            .withFlags()
            .withAllowedMentions(this.getAllowedMentions())
            .withMessageReference(this.getReferenceId().isPresent() ? Possible.of(MessageReferenceData.builder().messageId(this.getReferenceId().get().asLong()).build()) : Possible.absent())
            .withFiles(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .withComponents(
                this.getCurrentComponents()
                    .map(TopLevelMessageComponent::getD4jComponent)
                    .collect(Concurrent.toList())
            );
    }

    public @NotNull MessageEditSpec getD4jEditSpec() {
        return MessageEditSpec.builder()
            .contentOrNull(this.getCurrentContent().orElse(""))
            .embedsOrNull(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .addAllFlags(this.getCurrentFlags())
            .addAllFiles(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .addAllComponents(
                this.getCurrentComponents()
                    .map(TopLevelMessageComponent::getD4jComponent)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    public @NotNull InteractionApplicationCommandCallbackSpec getD4jComponentCallbackSpec() {
        return InteractionApplicationCommandCallbackSpec.builder()
            .content(this.getCurrentContent().orElse(""))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .ephemeral(this.isEphemeral())
            .allowedMentions(AllowedMentions.suppressEveryone())
            .files(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(
                this.getCurrentComponents()
                    .map(TopLevelMessageComponent::getD4jComponent)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    public @NotNull InteractionFollowupCreateSpec getD4jInteractionFollowupCreateSpec() {
        return InteractionFollowupCreateSpec.builder()
            .content(this.getCurrentContent().orElse(""))
            .embeds(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .ephemeral(this.isEphemeral())
            .allowedMentions(this.getAllowedMentions())
            .files(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .components(
                this.getCurrentComponents()
                    .map(TopLevelMessageComponent::getD4jComponent)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    public @NotNull InteractionReplyEditSpec getD4jInteractionReplyEditSpec() {
        return InteractionReplyEditSpec.builder()
            .contentOrNull(this.getCurrentContent().orElse(""))
            .embedsOrNull(this.getCurrentEmbeds().stream().map(Embed::getD4jEmbed).collect(Concurrent.toList()))
            .allowedMentionsOrNull(this.getAllowedMentions())
            .files(this.getPendingAttachments().map(Attachment::getD4jFile).collect(Concurrent.toList()))
            .componentsOrNull(
                this.getCurrentComponents()
                    .map(TopLevelMessageComponent::getD4jComponent)
                    .collect(Concurrent.toList())
            )
            .build();
    }

    // --- Builder ---

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder {

        @BuildFlag(nonNull = true)
        private UUID uniqueId = UUID.randomUUID();
        @BuildFlag(nonNull = true)
        private EventContext<?> eventContext;
        @BuildFlag(notEmpty = true)
        private final ConcurrentList<Page> pages = Concurrent.newList();
        private final ConcurrentList<Attachment> attachments = Concurrent.newList();
        private Optional<Snowflake> referenceId = Optional.empty();
        @BuildFlag(nonNull = true)
        private Scheduler reactorScheduler = Schedulers.boundedElastic();
        private int timeToLive = 10;
        private boolean renderingPagingComponents = true;
        private boolean ephemeral = false;
        @BuildFlag(nonNull = true)
        private AllowedMentions allowedMentions = AllowedMentions.suppressEveryone();
        private Optional<Function<MessageContext<MessageCreateEvent>, Mono<Void>>> createInteraction = Optional.empty();

        // Navigation state
        private Optional<String> defaultPage = Optional.empty();
        private ConcurrentList<String> pageHistory = Concurrent.newList();
        private int currentItemPage = 1;

        /**
         * Recursively disable all interactable components from all {@link Page Pages} in {@link Response}.
         */
        public Builder disableAllComponents() {
            this.pages.forEach(page -> this.editPage(page.mutate().disableComponents(true).build()));
            return this;
        }

        /**
         * Sets the {@link EventContext} for the {@link Response}, providing access to the
         * {@link DiscordBot} instance for emoji resolution and other bot operations.
         *
         * @param eventContext the event context
         */
        public Builder withContext(@NotNull EventContext<?> eventContext) {
            this.eventContext = eventContext;
            return this;
        }

        /**
         * Updates an existing {@link Page}.
         *
         * @param page the page to edit
         */
        public Builder editPage(@NotNull Page page) {
            this.pages.stream()
                .filter(existingPage -> existingPage.getOption().getValue().equals(page.getOption().getValue()))
                .findFirst()
                .ifPresent(existingPage -> this.pages.set(this.pages.indexOf(existingPage), page));

            return this;
        }

        /**
         * Sets the {@link Response} should be ephemeral.
         */
        public Builder isEphemeral() {
            return this.isEphemeral(true);
        }

        /**
         * Sets the {@link Response} should be ephemeral.
         *
         * @param value true if ephemeral
         */
        public Builder isEphemeral(boolean value) {
            this.ephemeral = value;
            return this;
        }

        /**
         * Sets the {@link Response} to render paging components.
         */
        public Builder isRenderingPagingComponents() {
            return this.isRenderingPagingComponents(true);
        }

        /**
         * Sets if the {@link Response} should render paging components.
         *
         * @param value true if rendering page components
         */
        public Builder isRenderingPagingComponents(boolean value) {
            this.renderingPagingComponents = value;
            return this;
        }

        /**
         * Sets the interaction to execute when the {@link Response} is known to exist.
         *
         * @param interaction the interaction function
         */
        public Builder onCreate(@Nullable Function<MessageContext<MessageCreateEvent>, Mono<Void>> interaction) {
            return this.onCreate(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction to execute when the {@link Response} is known to exist.
         *
         * @param interaction the interaction function
         */
        public Builder onCreate(@NotNull Optional<Function<MessageContext<MessageCreateEvent>, Mono<Void>>> interaction) {
            this.createInteraction = interaction;
            return this;
        }

        /**
         * Specifies the allowed mentions for the {@link Response}.
         *
         * @param allowedMentions an {@link AllowedMentions} object that defines which mentions should be allowed in the response
         */
        public Builder withAllowedMentions(@NotNull AllowedMentions allowedMentions) {
            this.allowedMentions = allowedMentions;
            return this;
        }

        /**
         * Adds an {@link Attachment} to the {@link Response}.
         *
         * @param name the name of the attachment
         * @param inputStream the stream of attachment data
         */
        public Builder withAttachment(@NotNull String name, @NotNull InputStream inputStream) {
            return this.withAttachment(name, inputStream, false);
        }

        /**
         * Adds an {@link Attachment} to the {@link Response}.
         *
         * @param name the name of the attachment
         * @param inputStream the stream of attachment data
         * @param spoiler true if the attachment should be a spoiler
         */
        public Builder withAttachment(@NotNull String name, @NotNull InputStream inputStream, boolean spoiler) {
            return this.withAttachments(
                Attachment.builder()
                    .isSpoiler(spoiler)
                    .withName(name)
                    .withStream(inputStream)
                    .build()
            );
        }

        /**
         * Add {@link Attachment Attachments} to the {@link Response}.
         *
         * @param attachments variable number of attachments to add
         */
        public Builder withAttachments(@NotNull Attachment... attachments) {
            Arrays.stream(attachments)
                .filter(attachment -> !this.attachments.contains(SearchFunction.combine(Attachment::getMediaData, MediaData::getName), attachment.getMediaData().getName()))
                .forEach(this.attachments::add);

            return this;
        }

        /**
         * Add {@link Attachment Attachments} to the {@link Response}.
         *
         * @param attachments collection of attachments to add
         */
        public Builder withAttachments(@NotNull Iterable<Attachment> attachments) {
            attachments.forEach(attachment -> {
                if (!this.attachments.contains(SearchFunction.combine(Attachment::getMediaData, MediaData::getName), attachment.getMediaData().getName()))
                    this.attachments.add(attachment);
            });

            return this;
        }

        /**
         * Sets the default page the {@link Response} should load.
         *
         * @param pageIdentifier the page identifier to load
         */
        public Builder withDefaultPage(@Nullable String pageIdentifier) {
            return this.withDefaultPage(Optional.ofNullable(pageIdentifier));
        }

        /**
         * Sets the default page the {@link Response} should load.
         *
         * @param pageIdentifier the page identifier to load
         */
        public Builder withDefaultPage(@NotNull Optional<String> pageIdentifier) {
            this.defaultPage = pageIdentifier;
            return this;
        }

        private Builder withItemPage(int currentItemPage) {
            this.currentItemPage = currentItemPage;
            return this;
        }

        private Builder withPageHistory(@NotNull ConcurrentList<String> pageHistory) {
            this.pageHistory = pageHistory;
            return this;
        }

        /**
         * Adds the stack trace of an {@link Throwable Exception} as an {@link Attachment} to the {@link Response}.
         *
         * @param throwable the throwable exception stack trace to add
         */
        public Builder withException(@NotNull Throwable throwable) {
            this.attachments.add(
                Attachment.builder()
                    .withName("stacktrace-%s.log", DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("EST", ZoneId.SHORT_IDS)).format(Instant.now()))
                    .withStream(new ByteArrayInputStream(ExceptionUtil.getStackTrace(throwable).getBytes(StandardCharsets.UTF_8)))
                    .build()
            );

            return this;
        }

        /**
         * Add {@link File Files} to the {@link Response}.
         *
         * @param files variable number of files to add
         */
        public Builder withFiles(@NotNull File... files) {
            return this.withFiles(Arrays.asList(files));
        }

        /**
         * Add {@link File Files} to the {@link Response}.
         *
         * @param files collection of files to add
         */
        public Builder withFiles(@NotNull Iterable<File> files) {
            List<File> fileList = List.class.isAssignableFrom(files.getClass()) ? (List<File>) files : StreamSupport.stream(files.spliterator(), false).toList();

            fileList.stream()
                .map(file -> {
                    try {
                        return Attachment.builder()
                            .withName(file.getName())
                            .withStream(new FileInputStream(file))
                            .build();
                    } catch (FileNotFoundException fnfex) {
                        throw new RuntimeException(fnfex);
                    }
                })
                .forEach(this::withAttachments);

            return this;
        }

        /**
         * Add {@link Page Pages} to the {@link Response}.
         *
         * @param pages variable number of pages to add
         */
        public Builder withPages(@NotNull Page... pages) {
            return this.withPages(Arrays.asList(pages));
        }

        /**
         * Add {@link Page Pages} to the {@link Response}.
         *
         * @param pages collection of pages to add
         */
        public Builder withPages(@NotNull Iterable<? extends Page> pages) {
            pages.forEach(this.pages::add);
            return this;
        }

        public Builder withReactorScheduler(@NotNull Scheduler reactorScheduler) {
            this.reactorScheduler = reactorScheduler;
            return this;
        }

        public Builder withReactorScheduler(@NotNull ExecutorService executorService) {
            this.reactorScheduler = Schedulers.fromExecutorService(executorService);
            return this;
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageContext the message to reference
         */
        public Builder withReference(@NotNull MessageContext<?> messageContext) {
            return this.withReference(messageContext.getMessageId());
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId the message to reference
         */
        public Builder withReference(@Nullable Snowflake messageId) {
            return this.withReference(Optional.ofNullable(messageId));
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId the message to reference
         */
        public Builder withReference(@NotNull Mono<Snowflake> messageId) {
            return this.withReference(messageId.blockOptional());
        }

        /**
         * Sets the message the {@link Response} should reply to.
         *
         * @param messageId the message to reference
         */
        public Builder withReference(@NotNull Optional<Snowflake> messageId) {
            this.referenceId = messageId;
            return this;
        }

        /**
         * Sets the time in seconds for the {@link Response} to live in {@link DiscordBot#getResponseHandler()}.
         * <br><br>
         * This value moves whenever the user interacts with the {@link Response}.
         * <br><br>
         * Maximum 300 seconds and minimum is 5 seconds.
         *
         * @param timeToLive how long the response should live without interaction in seconds
         */
        public Builder withTimeToLive(int timeToLive) {
            this.timeToLive = NumberUtil.ensureRange(timeToLive, 5, 300);
            return this;
        }

        /**
         * Used to replace an existing message with a known ID.
         *
         * @param uniqueId unique ID to assign to {@link Response}
         */
        public Builder withUniqueId(@NotNull UUID uniqueId) {
            this.uniqueId = uniqueId;
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return a built {@link Response}
         */
        public @NotNull Response build() {
            Reflection.validateFlags(this);

            Response response = new Response(
                this.uniqueId,
                this.eventContext,
                this.referenceId,
                this.reactorScheduler,
                this.allowedMentions,
                this.timeToLive,
                this.ephemeral,
                this.attachments,
                this.createInteraction.orElse(__ -> Mono.empty()),
                this.renderingPagingComponents,
                HistoryHandler.<Page, String>builder()
                    .withPages(this.pages.toUnmodifiableList())
                    .withMatcher((page, identifier) -> page.getOption().getValue().equals(identifier))
                    .withTransformer(page -> page.getOption().getValue())
                    .build(),
                new PaginationHandler(this.eventContext.getDiscordBot())
            );

            // Navigation state restoration
            if (this.defaultPage.isPresent() && response.getHistoryHandler().getPage(this.defaultPage.get()).isPresent())
                response.getHistoryHandler().gotoTopLevelPage(this.defaultPage.get());
            else {
                if (!this.pageHistory.isEmpty()) {
                    response.getHistoryHandler().gotoTopLevelPage(this.pageHistory.removeFirst());
                    this.pageHistory.forEach(identifier -> response.getHistoryHandler().gotoSubPage(identifier));
                    response.getHistoryHandler().getCurrentPage().getItemHandler().gotoPage(this.currentItemPage);
                } else
                    response.getHistoryHandler().gotoPage(response.getPages().getFirst());
            }

            return response;
        }

    }

}
