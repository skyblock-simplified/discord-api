package dev.sbs.discordapi.response.page.editor;

import dev.sbs.discordapi.component.TextDisplay;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.component.interaction.RadioGroup;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.layout.Container;
import dev.sbs.discordapi.component.layout.Label;
import dev.sbs.discordapi.component.layout.Section;
import dev.sbs.discordapi.component.layout.Separator;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.component.ButtonContext;
import dev.sbs.discordapi.context.component.ModalContext;
import dev.sbs.discordapi.context.component.SelectMenuContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.handler.HistoryHandler;
import dev.sbs.discordapi.response.handler.item.ItemHandler;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.response.page.editor.field.AggregateField;
import dev.sbs.discordapi.response.page.editor.field.BuilderField;
import dev.sbs.discordapi.response.page.editor.field.Choice;
import dev.sbs.discordapi.response.page.editor.field.EditableField;
import dev.sbs.discordapi.response.page.editor.field.FieldEdit;
import dev.sbs.discordapi.response.page.editor.field.FieldKind;
import dev.sbs.discordapi.response.page.editor.modal.FieldModalFactory;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import discord4j.core.object.entity.Message;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.awt.Color;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link Page} that renders a domain object {@code T} and lets the user edit tagged
 * fields via per-field modals or in-page selections.
 *
 * <p>
 * Two sealed sub-types lock the save semantics at build time:
 * <ul>
 *   <li><b>{@link Aggregate}</b> - live-save editing over an existing {@code T}.</li>
 *   <li><b>{@link Builder}</b> - on-submit assembly of a new {@code T} from a seed.</li>
 * </ul>
 *
 * <p>
 * Persistent responses are not supported - the enclosing {@link Response} build-time
 * validator rejects any persistent response that contains an {@code EditorPage}.
 *
 * @param <T> the domain type edited by the page
 */
@Getter
public abstract sealed class EditorPage<T> implements Page permits EditorPage.Aggregate, EditorPage.Builder {

    /** In-page confirmation state distinguishing normal render from submit/delete confirmation views. */
    protected enum ConfirmState { NONE, SUBMIT, DELETE }

    /** The option rendered in the parent select menu when this page is part of a tree. */
    protected final @NotNull SelectMenu.Option option;

    /** The header text displayed at the top of the editor container. */
    protected final @NotNull String header;

    /** The optional secondary description displayed under the header. */
    protected final @NotNull Optional<String> details;

    /** The item handler holding the editable fields. */
    protected final @NotNull ItemHandler<? extends EditableField<T, ?>> itemHandler;

    /** The history handler tracking navigation state. */
    protected final @NotNull HistoryHandler<? extends EditableField<T, ?>, String> historyHandler;

    /** Whether read-only fields are hidden from the rendered layout. */
    protected final boolean hideReadOnlyFields;

    /** The optional accent color applied to the editor container. */
    protected final @NotNull Optional<Color> accent;

    /** The optional cancel/close button configuration. */
    protected final @NotNull Optional<CancelConfig> cancelButton;

    /** The reactions displayed on the editor message. */
    protected final @NotNull ConcurrentList<Emoji> reactions;

    /** The cached layout components rebuilt on demand from the current state. */
    protected @NotNull ConcurrentList<LayoutComponent> cachedComponents;

    /** The currently active in-page edit session, if any. */
    protected @NotNull Optional<InPageEditSession> inPageSession;

    /** The current in-page confirmation state. */
    protected @NotNull ConfirmState confirmState;

    protected EditorPage(
        @NotNull SelectMenu.Option option,
        @NotNull String header,
        @NotNull Optional<String> details,
        @NotNull ItemHandler<? extends EditableField<T, ?>> itemHandler,
        @NotNull HistoryHandler<? extends EditableField<T, ?>, String> historyHandler,
        boolean hideReadOnlyFields,
        @NotNull Optional<Color> accent,
        @NotNull Optional<CancelConfig> cancelButton,
        @NotNull ConcurrentList<Emoji> reactions
    ) {
        this.option = option;
        this.header = header;
        this.details = details;
        this.itemHandler = itemHandler;
        this.historyHandler = historyHandler;
        this.hideReadOnlyFields = hideReadOnlyFields;
        this.accent = accent;
        this.cancelButton = cancelButton;
        this.reactions = reactions;
        this.cachedComponents = Concurrent.newUnmodifiableList();
        this.inPageSession = Optional.empty();
        this.confirmState = ConfirmState.NONE;
    }

    /** {@inheritDoc} */
    @Override
    public final @NotNull ConcurrentList<LayoutComponent> getComponents() {
        return this.getCachedLayoutComponents();
    }

    /**
     * Computes and caches the editor's layout components from the current state.
     *
     * @return the unmodifiable list of layout components
     */
    public @NotNull ConcurrentList<LayoutComponent> getCachedLayoutComponents() {
        Container.Builder container = Container.builder();
        this.accent.ifPresent(container::withAccent);

        StringBuilder headerText = new StringBuilder("# ").append(this.header);
        this.details.ifPresent(d -> headerText.append('\n').append(d));
        container.withComponents(TextDisplay.of(headerText.toString()), Separator.small());

        boolean sessionActive = this.inPageSession.isPresent();
        String sessionFieldId = this.inPageSession.map(InPageEditSession::fieldId).orElse(null);
        boolean confirming = this.confirmState != ConfirmState.NONE;

        for (EditableField<T, ?> field : this.itemHandler.getItems()) {
            if (field.readOnly() && this.hideReadOnlyFields)
                continue;

            boolean targetedBySession = field.identifier().equals(sessionFieldId);
            boolean disableEdit = confirming || (sessionActive && !targetedBySession);
            container.withComponents(this.buildFieldSection(field, disableEdit));
        }

        container.withComponents(Separator.small());
        ConcurrentList<LayoutComponent> output = Concurrent.newList();
        output.add(container.build());

        this.inPageSession.ifPresent(session -> {
            this.buildEscalationRows(session).forEach(output::add);
        });

        this.buildActionRow().ifPresent(output::add);
        this.cachedComponents = output.toUnmodifiableList();
        return this.cachedComponents;
    }

    /**
     * Builds the {@link Section} rendering a single field with its edit accessory.
     *
     * @param field the field to render
     * @param forceDisabled whether the edit button is forced disabled by an outer state machine
     * @return a section carrying the field label, current value, and edit button
     */
    protected @NotNull Section buildFieldSection(@NotNull EditableField<T, ?> field, boolean forceDisabled) {
        String display = this.renderFieldDisplay(field);
        String labelText = String.format("**%s%s**", field.label(), field.required() ? " *" : "");

        Button accessory = Button.builder()
            .withStyle(Button.Style.PRIMARY)
            .withLabel("Edit")
            .withIdentifier(this.editButtonCustomId(field.identifier()))
            .setEnabled(!field.readOnly() && !forceDisabled)
            .onInteract(ctx -> this.handleEditClick(ctx, field.identifier()))
            .build();

        return Section.builder()
            .withAccessory(accessory)
            .withComponents(TextDisplay.of(labelText + "\n" + display))
            .build();
    }

    /**
     * Builds the escalation rows rendered when an in-page Choice session is active.
     *
     * @param session the active session
     * @return the rows to append below the container
     */
    protected @NotNull ConcurrentList<LayoutComponent> buildEscalationRows(@NotNull InPageEditSession session) {
        ConcurrentList<LayoutComponent> rows = Concurrent.newList();
        int size = session.cachedChoices().size();
        int totalSlices = Math.max(1, (int) Math.ceil(size / 25.0));
        int slice = Math.max(0, Math.min(session.sliceIndex(), totalSlices - 1));
        int from = slice * 25;
        int to = Math.min(from + 25, size);

        SelectMenu.Builder menuBuilder = SelectMenu.builder()
            .withIdentifier(this.escalationCustomId(session.fieldId(), "select"))
            .withPlaceholder(String.format("Page %d of %d - pick a value", slice + 1, totalSlices))
            .onInteract(ctx -> this.handleEscalationSelect(ctx, session.fieldId()));

        for (int i = from; i < to; i++) {
            Choice<?> entry = session.cachedChoices().get(i);
            SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder()
                .withLabel(entry.label())
                .withValue(entry.label());
            entry.description().ifPresent(optionBuilder::withDescription);
            entry.emoji().ifPresent(optionBuilder::withEmoji);
            menuBuilder = menuBuilder.withOptions(optionBuilder.build());
        }

        rows.add(ActionRow.of(menuBuilder.build()));

        Button prev = Button.builder()
            .withStyle(Button.Style.SECONDARY)
            .withLabel("Previous")
            .withIdentifier(this.escalationCustomId(session.fieldId(), "prev"))
            .setEnabled(slice > 0)
            .onInteract(ctx -> this.handleEscalationNavigate(ctx, session.fieldId(), -1))
            .build();

        Button next = Button.builder()
            .withStyle(Button.Style.SECONDARY)
            .withLabel("Next")
            .withIdentifier(this.escalationCustomId(session.fieldId(), "next"))
            .setEnabled(slice < totalSlices - 1)
            .onInteract(ctx -> this.handleEscalationNavigate(ctx, session.fieldId(), 1))
            .build();

        Button cancel = Button.builder()
            .withStyle(Button.Style.DANGER)
            .withLabel("Cancel")
            .withIdentifier(this.escalationCustomId(session.fieldId(), "cancel"))
            .onInteract(ctx -> this.handleEscalationCancel(ctx))
            .build();

        rows.add(ActionRow.of(prev, next, cancel));
        return rows;
    }

    /**
     * Builds the mode-specific action row rendered below the field sections.
     *
     * @return the row wrapped in an optional, or empty when no buttons are configured
     */
    protected abstract @NotNull Optional<ActionRow> buildActionRow();

    /**
     * Renders the current value of a field as a display string.
     *
     * @param field the field being rendered
     * @return the display text with obfuscation applied where configured
     */
    protected abstract @NotNull String renderFieldDisplay(@NotNull EditableField<T, ?> field);

    /**
     * Dispatches an edit-button click to the correct modal or in-page flow based on the
     * field's {@link FieldKind}.
     *
     * @param context the click context
     * @param fieldId the targeted field identifier
     * @return the reactive completion of the dispatch
     */
    protected abstract @NotNull Mono<Void> handleEditClick(@NotNull ButtonContext context, @NotNull String fieldId);

    /**
     * Handles a SelectMenu pick inside an active in-page Choice escalation session.
     *
     * @param context the select menu context
     * @param fieldId the field identifier the session targets
     * @return the reactive completion
     */
    protected abstract @NotNull Mono<Void> handleEscalationSelect(@NotNull SelectMenuContext context, @NotNull String fieldId);

    /**
     * Advances or rewinds the current in-page escalation slice.
     *
     * @param context the button context
     * @param fieldId the field identifier the session targets
     * @param delta {@code -1} for previous, {@code +1} for next
     * @return the reactive completion
     */
    protected @NotNull Mono<Void> handleEscalationNavigate(@NotNull ButtonContext context, @NotNull String fieldId, int delta) {
        return Mono.defer(() -> {
            this.inPageSession.ifPresent(session -> {
                if (!session.fieldId().equals(fieldId))
                    return;

                int newSlice = Math.max(0, session.sliceIndex() + delta);
                this.inPageSession = Optional.of(new InPageEditSession(session.fieldId(), newSlice, session.cachedChoices()));
                this.markDirty();
            });
            return Mono.empty();
        });
    }

    /**
     * Cancels the active in-page Choice escalation session without applying a value.
     *
     * @param context the button context
     * @return the reactive completion
     */
    protected @NotNull Mono<Void> handleEscalationCancel(@NotNull ButtonContext context) {
        return Mono.defer(() -> {
            this.clearInPageSession();
            this.markDirty();
            return Mono.empty();
        });
    }

    /**
     * Derives the edit button custom id for a given field identifier.
     *
     * @param fieldId the field identifier
     * @return the custom id string
     */
    protected @NotNull String editButtonCustomId(@NotNull String fieldId) {
        return String.format("editor:%s:%s", this.option.getValue(), fieldId);
    }

    /**
     * Derives a custom id for an escalation-row component.
     *
     * @param fieldId the field identifier
     * @param suffix the suffix distinguishing prev/next/cancel/select
     * @return the custom id string
     */
    protected @NotNull String escalationCustomId(@NotNull String fieldId, @NotNull String suffix) {
        return String.format("editor:%s:%s:%s", this.option.getValue(), fieldId, suffix);
    }

    /**
     * Clears the active in-page edit session.
     */
    public void clearInPageSession() {
        this.inPageSession = Optional.empty();
    }

    /**
     * Installs an in-page edit session for the given field.
     *
     * @param session the session to track
     */
    public void withInPageSession(@NotNull InPageEditSession session) {
        this.inPageSession = Optional.of(session);
    }

    /**
     * Marks the item handler's cache as stale so the dispatcher issues a re-render.
     */
    protected void markDirty() {
        this.itemHandler.setCacheUpdateRequired();
    }

    /**
     * Deletes the Discord message and removes the response cache entry.
     *
     * @param context the component context whose response should be closed
     * @return the reactive completion of the delete + cache removal chain
     */
    protected @NotNull Mono<Void> closeResponse(@NotNull ButtonContext context) {
        return context.getMessage()
            .flatMap(Message::delete)
            .onErrorResume(throwable -> Mono.empty())
            .then(context.getDiscordBot().getResponseLocator().remove(context.getResponseId()));
    }

    /** The configuration for a Cancel or Close button rendered in the action row. */
    public record CancelConfig(
        @NotNull String label,
        @NotNull Function<EventContext<?>, Mono<Void>> onCancel
    ) { }

    /**
     * The aggregate editing mode - operations save through to the underlying domain object
     * immediately via the per-field {@link AggregateField#liveSaver}.
     *
     * @param <T> the domain type
     */
    public static final class Aggregate<T> extends EditorPage<T> {

        private final @NotNull T initialValue;
        private @NotNull T currentValue;
        private final @NotNull Optional<Function<T, Mono<Void>>> onDelete;
        private final boolean showDirtyIndicator;

        private Aggregate(
            @NotNull T initialValue,
            @NotNull SelectMenu.Option option,
            @NotNull String header,
            @NotNull Optional<String> details,
            @NotNull ItemHandler<AggregateField<T, ?>> itemHandler,
            @NotNull HistoryHandler<AggregateField<T, ?>, String> historyHandler,
            boolean hideReadOnlyFields,
            @NotNull Optional<Color> accent,
            @NotNull Optional<CancelConfig> cancelButton,
            @NotNull ConcurrentList<Emoji> reactions,
            @NotNull Optional<Function<T, Mono<Void>>> onDelete,
            boolean showDirtyIndicator
        ) {
            super(option, header, details, itemHandler, historyHandler, hideReadOnlyFields, accent, cancelButton, reactions);
            this.initialValue = initialValue;
            this.currentValue = initialValue;
            this.onDelete = onDelete;
            this.showDirtyIndicator = showDirtyIndicator;
        }

        /**
         * Creates a new aggregate editor builder.
         *
         * @param initial the initial domain value
         * @param <T> the domain type
         * @return a new builder
         */
        public static <T> @NotNull AggregateBuilder<T> builder(@NotNull T initial) {
            return new AggregateBuilder<>(initial);
        }

        /**
         * Creates a pre-filled builder mirroring the given page.
         *
         * @param page the page to copy
         * @param <T> the domain type
         * @return a pre-filled builder
         */
        @SuppressWarnings("unchecked")
        public static <T> @NotNull AggregateBuilder<T> from(@NotNull Aggregate<T> page) {
            AggregateBuilder<T> builder = new AggregateBuilder<>(page.initialValue);
            builder.header = page.getHeader();
            builder.details = page.getDetails();
            builder.hideReadOnlyFields = page.isHideReadOnlyFields();
            builder.accent = page.getAccent();
            builder.cancelButton = page.getCancelButton();
            builder.onDelete = page.onDelete;
            builder.showDirtyIndicator = page.showDirtyIndicator;
            page.getItemHandler().getItems().forEach(field -> builder.fields.add((AggregateField<T, ?>) field));
            builder.withOption(page.getOption());
            builder.withReactions(page.getReactions());
            return builder;
        }

        /** The current domain value after any live saves. */
        public @NotNull T getCurrentValue() {
            return this.currentValue;
        }

        /**
         * Replaces the current domain value and marks the layout cache as stale.
         *
         * @param value the new value
         */
        public void setCurrentValue(@NotNull T value) {
            this.currentValue = value;
            this.markDirty();
        }

        /** Whether the page shows transient dirty indicators while live-saving. */
        public boolean isShowDirtyIndicator() {
            return this.showDirtyIndicator;
        }

        /** The optional delete handler invoked after confirmation. */
        public @NotNull Optional<Function<T, Mono<Void>>> getOnDelete() {
            return this.onDelete;
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull AggregateBuilder<T> mutate() {
            return from(this);
        }

        @Override
        protected @NotNull String renderFieldDisplay(@NotNull EditableField<T, ?> field) {
            return this.renderFieldDisplayTyped(field);
        }

        @SuppressWarnings("unchecked")
        private <V> @NotNull String renderFieldDisplayTyped(@NotNull EditableField<T, V> field) {
            AggregateField<T, V> aggregate = (AggregateField<T, V>) field;
            V value = aggregate.getter().apply(this.currentValue);
            if (value == null)
                return "_(none)_";
            String rendered = aggregate.renderer().apply(value);
            return field.obfuscated() ? "***" : rendered;
        }

        @Override
        protected @NotNull Optional<ActionRow> buildActionRow() {
            if (this.confirmState == ConfirmState.DELETE)
                return Optional.of(this.buildConfirmDeleteRow());

            ConcurrentList<dev.sbs.discordapi.component.scope.ActionComponent> actions = Concurrent.newList();

            this.cancelButton.ifPresent(cfg -> actions.add(
                Button.builder()
                    .withStyle(Button.Style.SECONDARY)
                    .withLabel(cfg.label())
                    .withIdentifier("editor:%s:cancel", this.option.getValue())
                    .onInteract(ctx -> this.handleCancel(ctx, cfg))
                    .build()
            ));

            this.onDelete.ifPresent(__ -> actions.add(
                Button.builder()
                    .withStyle(Button.Style.DANGER)
                    .withLabel("Delete")
                    .withIdentifier("editor:%s:delete", this.option.getValue())
                    .onInteract(this::handleDeleteRequest)
                    .build()
            ));

            if (actions.isEmpty())
                return Optional.empty();

            return Optional.of(ActionRow.of(actions));
        }

        private @NotNull ActionRow buildConfirmDeleteRow() {
            Button confirm = Button.builder()
                .withStyle(Button.Style.DANGER)
                .withLabel("Confirm delete")
                .withIdentifier("editor:%s:confirm-delete", this.option.getValue())
                .onInteract(this::handleConfirmDelete)
                .build();

            Button back = Button.builder()
                .withStyle(Button.Style.SECONDARY)
                .withLabel("Go back")
                .withIdentifier("editor:%s:cancel-delete", this.option.getValue())
                .onInteract(ctx -> this.handleGoBack(ctx))
                .build();

            return ActionRow.of(confirm, back);
        }

        @Override
        protected @NotNull Mono<Void> handleEditClick(@NotNull ButtonContext context, @NotNull String fieldId) {
            Optional<AggregateField<T, ?>> lookup = this.findField(fieldId);
            if (lookup.isEmpty())
                return Mono.empty();

            AggregateField<T, ?> field = lookup.get();
            if (field.readOnly())
                return Mono.empty();

            return this.presentOrEscalate(context, field);
        }

        @SuppressWarnings("unchecked")
        private <V> @NotNull Mono<Void> presentOrEscalate(@NotNull ButtonContext context, @NotNull AggregateField<T, V> field) {
            V current = field.getter().apply(this.currentValue);
            Optional<V> currentOpt = Optional.ofNullable(current);

            if (field.kind() instanceof FieldKind.Choice<?> choice && choice.choices().size() > SelectMenu.Option.MAX_ALLOWED) {
                ConcurrentList<Choice<?>> cached = Concurrent.newList();
                choice.choices().forEach(c -> cached.add((Choice<?>) c));
                this.withInPageSession(new InPageEditSession(field.identifier(), 0, cached.toUnmodifiableList()));
                this.markDirty();
                return Mono.empty();
            }

            Optional<Modal> built = FieldModalFactory.forField(field, currentOpt);
            if (built.isEmpty())
                return Mono.empty();

            Modal modal = built.get()
                .mutate()
                .onInteract(modalCtx -> this.handleModalSubmit(modalCtx, field))
                .build();

            return context.presentModal(modal);
        }

        private <V> @NotNull Mono<Void> handleModalSubmit(@NotNull ModalContext context, @NotNull AggregateField<T, V> field) {
            Optional<V> parsed = extractModalValue(context, field);
            if (field.required() && parsed.isEmpty())
                return context.deferEdit();

            V newValue = parsed.orElse(null);
            if (newValue == null)
                return context.deferEdit();

            V oldValue = field.getter().apply(this.currentValue);
            if (oldValue == null)
                return context.deferEdit();

            return field.liveSaver().apply(this.currentValue, new FieldEdit<>(field.identifier(), oldValue, newValue))
                .doOnNext(this::setCurrentValue)
                .then();
        }

        private @NotNull Mono<Void> handleCancel(@NotNull ButtonContext context, @NotNull CancelConfig cfg) {
            return cfg.onCancel().apply(context).then(this.closeResponse(context));
        }

        private @NotNull Mono<Void> handleDeleteRequest(@NotNull ButtonContext context) {
            this.confirmState = ConfirmState.DELETE;
            this.markDirty();
            return Mono.empty();
        }

        private @NotNull Mono<Void> handleConfirmDelete(@NotNull ButtonContext context) {
            return this.onDelete
                .map(handler -> handler.apply(this.currentValue).then(this.closeResponse(context)))
                .orElseGet(Mono::empty);
        }

        private @NotNull Mono<Void> handleGoBack(@NotNull ButtonContext context) {
            this.confirmState = ConfirmState.NONE;
            this.markDirty();
            return Mono.empty();
        }

        @Override
        protected @NotNull Mono<Void> handleEscalationSelect(@NotNull SelectMenuContext context, @NotNull String fieldId) {
            Optional<AggregateField<T, ?>> lookup = this.findField(fieldId);
            if (lookup.isEmpty() || context.getSelected().isEmpty())
                return Mono.empty();

            AggregateField<T, ?> field = lookup.get();
            String pickedLabel = context.getSelected().getFirst().getValue();
            return this.applyEscalationPick(context, field, pickedLabel);
        }

        @SuppressWarnings("unchecked")
        private <V> @NotNull Mono<Void> applyEscalationPick(@NotNull SelectMenuContext context, @NotNull AggregateField<T, V> field, @NotNull String pickedLabel) {
            if (!(field.kind() instanceof FieldKind.Choice<?> choice))
                return Mono.empty();

            Optional<? extends Choice<?>> match = choice.choices()
                .stream()
                .filter(c -> c.label().equals(pickedLabel))
                .findFirst();

            if (match.isEmpty())
                return Mono.empty();

            V newValue = (V) match.get().value();
            V oldValue = field.getter().apply(this.currentValue);

            this.clearInPageSession();

            return field.liveSaver().apply(this.currentValue, new FieldEdit<>(field.identifier(), oldValue, newValue))
                .doOnNext(this::setCurrentValue)
                .then();
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private @NotNull Optional<AggregateField<T, ?>> findField(@NotNull String fieldId) {
            for (EditableField<T, ?> field : this.getItemHandler().getItems()) {
                if (field.identifier().equals(fieldId))
                    return Optional.of((AggregateField<T, ?>) (AggregateField) field);
            }
            return Optional.empty();
        }

        /** A builder for {@link Aggregate} editor pages. */
        public static final class AggregateBuilder<T> extends Page.Builder {

            @BuildFlag(nonNull = true)
            private final T initialValue;
            @BuildFlag(nonNull = true)
            private String header;
            private Optional<String> details = Optional.empty();
            private final ConcurrentList<AggregateField<T, ?>> fields = Concurrent.newList();
            private boolean hideReadOnlyFields;
            private Optional<Color> accent = Optional.empty();
            private Optional<CancelConfig> cancelButton = Optional.empty();
            private Optional<Function<T, Mono<Void>>> onDelete = Optional.empty();
            private boolean showDirtyIndicator;

            private AggregateBuilder(@NotNull T initialValue) {
                this.initialValue = initialValue;
            }

            /**
             * Adds an editable field to the aggregate page.
             *
             * @param field the field to add
             * @return this builder
             */
            public AggregateBuilder<T> withField(@NotNull AggregateField<T, ?> field) {
                this.fields.add(field);
                return this;
            }

            /**
             * Sets the header text rendered at the top of the editor.
             *
             * @param header the header text
             * @return this builder
             */
            public AggregateBuilder<T> withHeader(@NotNull String header) {
                this.header = header;
                return this;
            }

            /**
             * Sets the optional secondary description.
             *
             * @param details the description text
             * @return this builder
             */
            public AggregateBuilder<T> withDetails(@NotNull String details) {
                this.details = Optional.of(details);
                return this;
            }

            /**
             * Hides read-only fields from the rendered layout.
             *
             * @return this builder
             */
            public AggregateBuilder<T> hideReadOnlyFields() {
                this.hideReadOnlyFields = true;
                return this;
            }

            /**
             * Enables the per-field dirty indicator flash.
             *
             * @return this builder
             */
            public AggregateBuilder<T> showDirtyIndicator() {
                this.showDirtyIndicator = true;
                return this;
            }

            /**
             * Sets the accent color applied to the editor container.
             *
             * @param accent the accent color
             * @return this builder
             */
            public AggregateBuilder<T> withAccent(@NotNull Color accent) {
                this.accent = Optional.of(accent);
                return this;
            }

            /**
             * Enables a cancel/close button with the given label and handler.
             *
             * @param label the button label
             * @param onCancel the cancel handler
             * @return this builder
             */
            public AggregateBuilder<T> withCancel(@NotNull String label, @NotNull Function<EventContext<?>, Mono<Void>> onCancel) {
                this.cancelButton = Optional.of(new CancelConfig(label, onCancel));
                return this;
            }

            /**
             * Enables a delete button invoking the given handler after confirmation.
             *
             * @param onDelete the delete handler
             * @return this builder
             */
            public AggregateBuilder<T> withDelete(@NotNull Function<T, Mono<Void>> onDelete) {
                this.onDelete = Optional.of(onDelete);
                return this;
            }

            /** {@inheritDoc} */
            @Override
            public @NotNull Aggregate<T> build() {
                Reflection.validateFlags(this);

                ConcurrentList<AggregateField<T, ?>> fieldList = this.fields.toUnmodifiableList();

                ItemHandler<AggregateField<T, ?>> items = ItemHandler.<AggregateField<T, ?>>embed()
                    .withItems(fieldList)
                    .build();

                HistoryHandler<AggregateField<T, ?>, String> history = HistoryHandler.<AggregateField<T, ?>, String>builder()
                    .withPages(fieldList)
                    .withMatcher((field, identifier) -> field.identifier().equals(identifier))
                    .withTransformer(AggregateField::identifier)
                    .build();

                return new Aggregate<>(
                    this.initialValue,
                    this.optionBuilder.build(),
                    this.header,
                    this.details,
                    items,
                    history,
                    this.hideReadOnlyFields,
                    this.accent,
                    this.cancelButton,
                    this.reactions.toUnmodifiableList(),
                    this.onDelete,
                    this.showDirtyIndicator
                );
            }

        }

    }

    /**
     * The builder editing mode - field edits accumulate in a caller-supplied seed until
     * the user submits the page, at which point {@link #onSubmit} materializes the final
     * {@code T} from the seed.
     *
     * @param <T> the builder seed type
     */
    public static final class Builder<T> extends EditorPage<T> {

        private final @NotNull T initialSeed;
        private @NotNull T seed;
        private final @NotNull Function<T, Mono<Void>> onSubmit;
        private final boolean confirmSubmit;

        private Builder(
            @NotNull T seed,
            @NotNull SelectMenu.Option option,
            @NotNull String header,
            @NotNull Optional<String> details,
            @NotNull ItemHandler<BuilderField<T, ?>> itemHandler,
            @NotNull HistoryHandler<BuilderField<T, ?>, String> historyHandler,
            boolean hideReadOnlyFields,
            @NotNull Optional<Color> accent,
            @NotNull Optional<CancelConfig> cancelButton,
            @NotNull ConcurrentList<Emoji> reactions,
            @NotNull Function<T, Mono<Void>> onSubmit,
            boolean confirmSubmit
        ) {
            super(option, header, details, itemHandler, historyHandler, hideReadOnlyFields, accent, cancelButton, reactions);
            this.initialSeed = seed;
            this.seed = seed;
            this.onSubmit = onSubmit;
            this.confirmSubmit = confirmSubmit;
        }

        /**
         * Creates a new builder-mode editor builder.
         *
         * @param seed the initial builder seed
         * @param <T> the builder seed type
         * @return a new builder
         */
        public static <T> @NotNull EditorBuilder<T> builder(@NotNull T seed) {
            return new EditorBuilder<>(seed);
        }

        /**
         * Creates a pre-filled builder mirroring the given page.
         *
         * @param page the page to copy
         * @param <T> the builder seed type
         * @return a pre-filled builder
         */
        @SuppressWarnings("unchecked")
        public static <T> @NotNull EditorBuilder<T> from(@NotNull Builder<T> page) {
            EditorBuilder<T> builder = new EditorBuilder<>(page.initialSeed);
            builder.header = page.getHeader();
            builder.details = page.getDetails();
            builder.hideReadOnlyFields = page.isHideReadOnlyFields();
            builder.accent = page.getAccent();
            builder.cancelButton = page.getCancelButton();
            builder.onSubmit = page.onSubmit;
            builder.confirmSubmit = page.confirmSubmit;
            page.getItemHandler().getItems().forEach(field -> builder.fields.add((BuilderField<T, ?>) field));
            builder.withOption(page.getOption());
            builder.withReactions(page.getReactions());
            return builder;
        }

        /** The current builder seed. */
        public @NotNull T getSeed() {
            return this.seed;
        }

        /**
         * Replaces the current seed and marks the layout cache as stale.
         *
         * @param seed the new seed
         */
        public void setSeed(@NotNull T seed) {
            this.seed = seed;
            this.markDirty();
        }

        /** The handler invoked when the user submits the assembled seed. */
        public @NotNull Function<T, Mono<Void>> getOnSubmit() {
            return this.onSubmit;
        }

        /** Whether a confirmation step is presented before {@link #onSubmit} runs. */
        public boolean isConfirmSubmit() {
            return this.confirmSubmit;
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull EditorBuilder<T> mutate() {
            return from(this);
        }

        @Override
        protected @NotNull String renderFieldDisplay(@NotNull EditableField<T, ?> field) {
            return this.renderFieldDisplayTyped(field);
        }

        @SuppressWarnings("unchecked")
        private <V> @NotNull String renderFieldDisplayTyped(@NotNull EditableField<T, V> field) {
            BuilderField<T, V> builderField = (BuilderField<T, V>) field;
            V value = builderField.getter().apply(this.seed);
            if (value == null)
                return "_(none)_";
            String rendered = builderField.renderer().apply(value);
            return field.obfuscated() ? "***" : rendered;
        }

        @Override
        protected @NotNull Optional<ActionRow> buildActionRow() {
            if (this.confirmState == ConfirmState.SUBMIT)
                return Optional.of(this.buildConfirmSubmitRow());

            ConcurrentList<dev.sbs.discordapi.component.scope.ActionComponent> actions = Concurrent.newList();

            actions.add(
                Button.builder()
                    .withStyle(Button.Style.SUCCESS)
                    .withLabel("Submit")
                    .withIdentifier("editor:%s:submit", this.option.getValue())
                    .onInteract(this::handleSubmit)
                    .build()
            );

            this.cancelButton.ifPresent(cfg -> actions.add(
                Button.builder()
                    .withStyle(Button.Style.SECONDARY)
                    .withLabel(cfg.label())
                    .withIdentifier("editor:%s:cancel", this.option.getValue())
                    .onInteract(ctx -> this.handleCancel(ctx, cfg))
                    .build()
            ));

            return Optional.of(ActionRow.of(actions));
        }

        private @NotNull ActionRow buildConfirmSubmitRow() {
            Button confirm = Button.builder()
                .withStyle(Button.Style.SUCCESS)
                .withLabel("Confirm submit")
                .withIdentifier("editor:%s:confirm-submit", this.option.getValue())
                .onInteract(this::handleConfirmSubmit)
                .build();

            Button back = Button.builder()
                .withStyle(Button.Style.SECONDARY)
                .withLabel("Go back")
                .withIdentifier("editor:%s:cancel-submit", this.option.getValue())
                .onInteract(this::handleGoBack)
                .build();

            return ActionRow.of(confirm, back);
        }

        @Override
        protected @NotNull Mono<Void> handleEditClick(@NotNull ButtonContext context, @NotNull String fieldId) {
            Optional<BuilderField<T, ?>> lookup = this.findField(fieldId);
            if (lookup.isEmpty())
                return Mono.empty();

            BuilderField<T, ?> field = lookup.get();
            if (field.readOnly())
                return Mono.empty();

            return this.presentOrEscalate(context, field);
        }

        @SuppressWarnings("unchecked")
        private <V> @NotNull Mono<Void> presentOrEscalate(@NotNull ButtonContext context, @NotNull BuilderField<T, V> field) {
            V current = field.getter().apply(this.seed);
            Optional<V> currentOpt = Optional.ofNullable(current);

            if (field.kind() instanceof FieldKind.Choice<?> choice && choice.choices().size() > SelectMenu.Option.MAX_ALLOWED) {
                ConcurrentList<Choice<?>> cached = Concurrent.newList();
                choice.choices().forEach(c -> cached.add((Choice<?>) c));
                this.withInPageSession(new InPageEditSession(field.identifier(), 0, cached.toUnmodifiableList()));
                this.markDirty();
                return Mono.empty();
            }

            Optional<Modal> built = FieldModalFactory.forField(field, currentOpt);
            if (built.isEmpty())
                return Mono.empty();

            Modal modal = built.get()
                .mutate()
                .onInteract(modalCtx -> this.handleModalSubmit(modalCtx, field))
                .build();

            return context.presentModal(modal);
        }

        private <V> @NotNull Mono<Void> handleModalSubmit(@NotNull ModalContext context, @NotNull BuilderField<T, V> field) {
            Optional<V> parsed = extractModalValue(context, field);
            if (field.required() && parsed.isEmpty())
                return context.deferEdit();

            V newValue = parsed.orElse(null);
            if (newValue == null)
                return context.deferEdit();

            T updated = field.applier().apply(this.seed, newValue);
            this.setSeed(updated);
            return Mono.empty();
        }

        @Override
        protected @NotNull Mono<Void> handleEscalationSelect(@NotNull SelectMenuContext context, @NotNull String fieldId) {
            Optional<BuilderField<T, ?>> lookup = this.findField(fieldId);
            if (lookup.isEmpty() || context.getSelected().isEmpty())
                return Mono.empty();

            BuilderField<T, ?> field = lookup.get();
            String pickedLabel = context.getSelected().getFirst().getValue();
            return this.applyEscalationPick(context, field, pickedLabel);
        }

        @SuppressWarnings("unchecked")
        private <V> @NotNull Mono<Void> applyEscalationPick(@NotNull SelectMenuContext context, @NotNull BuilderField<T, V> field, @NotNull String pickedLabel) {
            if (!(field.kind() instanceof FieldKind.Choice<?> choice))
                return Mono.empty();

            Optional<? extends Choice<?>> match = choice.choices()
                .stream()
                .filter(c -> c.label().equals(pickedLabel))
                .findFirst();

            if (match.isEmpty())
                return Mono.empty();

            V newValue = (V) match.get().value();
            T updated = field.applier().apply(this.seed, newValue);
            this.setSeed(updated);
            this.clearInPageSession();
            return Mono.empty();
        }

        private @NotNull Mono<Void> handleSubmit(@NotNull ButtonContext context) {
            if (this.confirmSubmit) {
                this.confirmState = ConfirmState.SUBMIT;
                this.markDirty();
                return Mono.empty();
            }

            return this.onSubmit.apply(this.seed).then(this.closeResponse(context));
        }

        private @NotNull Mono<Void> handleConfirmSubmit(@NotNull ButtonContext context) {
            return this.onSubmit.apply(this.seed).then(this.closeResponse(context));
        }

        private @NotNull Mono<Void> handleCancel(@NotNull ButtonContext context, @NotNull CancelConfig cfg) {
            return cfg.onCancel().apply(context).then(this.closeResponse(context));
        }

        private @NotNull Mono<Void> handleGoBack(@NotNull ButtonContext context) {
            this.confirmState = ConfirmState.NONE;
            this.markDirty();
            return Mono.empty();
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private @NotNull Optional<BuilderField<T, ?>> findField(@NotNull String fieldId) {
            for (EditableField<T, ?> field : this.getItemHandler().getItems()) {
                if (field.identifier().equals(fieldId))
                    return Optional.of((BuilderField<T, ?>) (BuilderField) field);
            }
            return Optional.empty();
        }

        /** A builder for {@link Builder builder-mode} editor pages. */
        public static final class EditorBuilder<T> extends Page.Builder {

            @BuildFlag(nonNull = true)
            private final T seed;
            @BuildFlag(nonNull = true)
            private String header;
            private Optional<String> details = Optional.empty();
            private final ConcurrentList<BuilderField<T, ?>> fields = Concurrent.newList();
            private boolean hideReadOnlyFields;
            private Optional<Color> accent = Optional.empty();
            private Optional<CancelConfig> cancelButton = Optional.empty();
            @BuildFlag(nonNull = true)
            private Function<T, Mono<Void>> onSubmit;
            private boolean confirmSubmit;

            private EditorBuilder(@NotNull T seed) {
                this.seed = seed;
            }

            /**
             * Adds an editable field to the builder-mode page.
             *
             * @param field the field to add
             * @return this builder
             */
            public EditorBuilder<T> withField(@NotNull BuilderField<T, ?> field) {
                this.fields.add(field);
                return this;
            }

            /**
             * Sets the header text rendered at the top of the editor.
             *
             * @param header the header text
             * @return this builder
             */
            public EditorBuilder<T> withHeader(@NotNull String header) {
                this.header = header;
                return this;
            }

            /**
             * Sets the optional secondary description.
             *
             * @param details the description text
             * @return this builder
             */
            public EditorBuilder<T> withDetails(@NotNull String details) {
                this.details = Optional.of(details);
                return this;
            }

            /**
             * Hides read-only fields from the rendered layout.
             *
             * @return this builder
             */
            public EditorBuilder<T> hideReadOnlyFields() {
                this.hideReadOnlyFields = true;
                return this;
            }

            /**
             * Requires a confirmation step before the submit handler runs.
             *
             * @return this builder
             */
            public EditorBuilder<T> confirmSubmit() {
                this.confirmSubmit = true;
                return this;
            }

            /**
             * Sets the accent color applied to the editor container.
             *
             * @param accent the accent color
             * @return this builder
             */
            public EditorBuilder<T> withAccent(@NotNull Color accent) {
                this.accent = Optional.of(accent);
                return this;
            }

            /**
             * Enables a cancel button with the given label and handler.
             *
             * @param label the button label
             * @param onCancel the cancel handler
             * @return this builder
             */
            public EditorBuilder<T> withCancel(@NotNull String label, @NotNull Function<EventContext<?>, Mono<Void>> onCancel) {
                this.cancelButton = Optional.of(new CancelConfig(label, onCancel));
                return this;
            }

            /**
             * Sets the handler invoked after the seed is submitted.
             *
             * @param onSubmit the submit handler
             * @return this builder
             */
            public EditorBuilder<T> onSubmit(@NotNull Function<T, Mono<Void>> onSubmit) {
                this.onSubmit = onSubmit;
                return this;
            }

            /** {@inheritDoc} */
            @Override
            public @NotNull Builder<T> build() {
                Reflection.validateFlags(this);

                ConcurrentList<BuilderField<T, ?>> fieldList = this.fields.toUnmodifiableList();

                ItemHandler<BuilderField<T, ?>> items = ItemHandler.<BuilderField<T, ?>>embed()
                    .withItems(fieldList)
                    .build();

                HistoryHandler<BuilderField<T, ?>, String> history = HistoryHandler.<BuilderField<T, ?>, String>builder()
                    .withPages(fieldList)
                    .withMatcher((field, identifier) -> field.identifier().equals(identifier))
                    .withTransformer(BuilderField::identifier)
                    .build();

                return new Builder<>(
                    this.seed,
                    this.optionBuilder.build(),
                    this.header,
                    this.details,
                    items,
                    history,
                    this.hideReadOnlyFields,
                    this.accent,
                    this.cancelButton,
                    this.reactions.toUnmodifiableList(),
                    this.onSubmit,
                    this.confirmSubmit
                );
            }

        }

    }

    /**
     * Extracts the field value submitted in a modal based on the field's {@link FieldKind}.
     *
     * @param context the modal context whose components carry submitted values
     * @param field the field whose kind drives extraction
     * @param <T> the domain/seed type
     * @param <V> the value type
     * @return the submitted value, or empty if parsing fails or no value was provided
     */
    @SuppressWarnings("unchecked")
    protected static <T, V> @NotNull Optional<V> extractModalValue(@NotNull ModalContext context, @NotNull EditableField<T, V> field) {
        FieldKind<V> kind = field.kind();
        Modal modal = context.getComponent();

        return switch (kind) {
            case FieldKind.Text text -> modal.getComponents()
                .stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .map(Label::getComponent)
                .filter(TextInput.class::isInstance)
                .map(TextInput.class::cast)
                .findFirst()
                .flatMap(TextInput::getValue)
                .map(v -> (V) v);

            case FieldKind.Numeric<?> numeric -> (Optional<V>) modal.getComponents()
                .stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .map(Label::getComponent)
                .filter(TextInput.class::isInstance)
                .map(TextInput.class::cast)
                .findFirst()
                .flatMap(TextInput::getValue)
                .flatMap(numeric.parser()::apply);

            case FieldKind.Bool bool -> {
                Optional<V> picked = modal.getComponents()
                    .stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .map(Label::getComponent)
                    .filter(RadioGroup.class::isInstance)
                    .map(RadioGroup.class::cast)
                    .findFirst()
                    .flatMap(rg -> rg.getSelected().map(opt -> (V) Boolean.valueOf("true".equals(opt.getValue()))));
                yield picked;
            }

            case FieldKind.Choice<?> choice -> {
                Optional<String> pickedLabel = modal.getComponents()
                    .stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .map(Label::getComponent)
                    .filter(SelectMenu.class::isInstance)
                    .map(SelectMenu.class::cast)
                    .findFirst()
                    .flatMap(menu -> menu.getSelected().stream().findFirst().map(SelectMenu.Option::getValue));

                yield pickedLabel.flatMap(label -> choice.choices()
                    .stream()
                    .filter(c -> c.label().equals(label))
                    .findFirst()
                    .map(c -> (V) c.value())
                );
            }
        };
    }

}
