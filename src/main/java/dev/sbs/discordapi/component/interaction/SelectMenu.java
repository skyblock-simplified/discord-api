package dev.sbs.discordapi.component.interaction;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.capability.EventInteractable;
import dev.sbs.discordapi.component.capability.Toggleable;
import dev.sbs.discordapi.component.scope.ActionComponent;
import dev.sbs.discordapi.component.scope.LabelComponent;
import dev.sbs.discordapi.context.component.OptionContext;
import dev.sbs.discordapi.context.component.SelectMenuContext;
import dev.sbs.discordapi.response.Emoji;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentSet;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.channel.Channel;
import discord4j.discordjson.json.ComponentData;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * A dropdown select menu component rendered within a Discord message.
 *
 * <p>
 * Select menus come in two shapes, mirroring Discord's component protocol:
 * <ul>
 *   <li><b>{@link StringMenu}</b> - developer-defined {@link Option} choices.</li>
 *   <li><b>{@link EntityMenu}</b> - auto-populated user, role, mentionable, or channel
 *       selectors, optionally pre-seeded with {@link DefaultValue default values}.</li>
 * </ul>
 *
 * <p>
 * Instances are built via {@link #builder()} for a string menu or {@link #entity(Type)} for
 * an auto-populated entity menu. The {@link dev.sbs.discordapi.listener.component.SelectMenuListener
 * SelectMenuListener} calls {@link #updateSelected(List)} internally on interaction, so user
 * code only needs to consume {@link #getSelectedValues()} (or the variant-specific accessors
 * on the concrete subtype) from within its {@code onInteract} handler.
 *
 * @see StringMenu
 * @see EntityMenu
 * @see Option
 * @see DefaultValue
 */
public sealed interface SelectMenu
    extends ActionComponent, EventInteractable<SelectMenuContext>, LabelComponent, Toggleable
    permits SelectMenu.StringMenu, SelectMenu.EntityMenu {

    /** The unique identifier for this select menu. */
    @NotNull String getIdentifier();

    /** The optional placeholder text shown when no option is selected. */
    @NotNull Optional<String> getPlaceholder();

    /** The minimum number of values that must be selected. */
    int getMinValues();

    /** The maximum number of values that may be selected. */
    int getMaxValues();

    /** Whether this select menu is required. */
    boolean isRequired();

    /** The variant of this select menu. */
    @NotNull Type getMenuType();

    /**
     * Returns the raw wire values from the last interaction.
     *
     * <p>
     * Discord emits a {@code List<String>} for every select-menu variant - option values
     * for {@link StringMenu}, snowflake ids for {@link EntityMenu}. This agnostic accessor
     * lets {@link SelectMenuContext} stay variant-independent.
     *
     * @return the selected values, never null, possibly empty
     */
    @NotNull ConcurrentList<String> getSelectedValues();

    /**
     * Updates this menu's internal selection state from raw wire values.
     *
     * <p>
     * Called by the framework before dispatching to the user's {@code onInteract} handler.
     * Implementations parse the values appropriately - {@link StringMenu} matches them
     * against {@link Option} values, while {@link EntityMenu} parses them as snowflakes.
     *
     * @param values the raw wire values delivered by Discord
     * @return this select menu, for chaining
     */
    @NotNull SelectMenu updateSelected(@NotNull List<String> values);

    /** {@inheritDoc} */
    @Override
    default @NotNull Component.Type getType() {
        return this.getMenuType().getInternalType();
    }

    /** {@inheritDoc} */
    @Override
    @NotNull discord4j.core.object.component.SelectMenu getD4jComponent();

    /**
     * Creates a new {@link StringMenu} builder with a random identifier.
     *
     * @return a new {@link StringMenu.Builder} instance
     */
    static @NotNull StringMenu.Builder builder() {
        return StringMenu.builder();
    }

    /**
     * Creates a new {@link EntityMenu} builder seeded with the given auto-populated type.
     *
     * @param type the entity menu variant (must not be {@link Type#STRING})
     * @return a new {@link EntityMenu.Builder} instance
     * @throws IllegalArgumentException if the given type is {@link Type#STRING}
     */
    static @NotNull EntityMenu.Builder entity(@NotNull Type type) {
        return EntityMenu.builder(type);
    }

    /**
     * Variant of a {@link SelectMenu}, mapping to the corresponding {@link Component.Type}.
     */
    @Getter
    @RequiredArgsConstructor
    enum Type {

        /** User-defined string options. */
        STRING(Component.Type.SELECT_MENU_STRING),
        /** Discord user entity selector. */
        USER(Component.Type.SELECT_MENU_USER),
        /** Discord role entity selector. */
        ROLE(Component.Type.SELECT_MENU_ROLE),
        /** Discord mentionable entity selector (users or roles). */
        MENTIONABLE(Component.Type.SELECT_MENU_MENTIONABLE),
        /** Discord channel entity selector. */
        CHANNEL(Component.Type.SELECT_MENU_CHANNEL);

        /** The corresponding {@link Component.Type} for this menu type. */
        private final @NotNull Component.Type internalType;

        /** Whether this type is an auto-populated entity selector (anything other than {@link #STRING}). */
        public boolean isEntity() {
            return this != STRING;
        }

    }

    /**
     * Identifier for built-in pagination select menu roles on a {@link StringMenu}.
     *
     * <p>
     * Used to tag select menus for identification. Interaction handlers are provided by
     * {@link dev.sbs.discordapi.response.handler.PaginationHandler PaginationHandler}.
     *
     * @see dev.sbs.discordapi.response.handler.PaginationHandler
     */
    enum PageType {

        /** No pagination role. */
        NONE,
        /** Top-level page selection. */
        PAGE_SELECTOR,
        /** Subpage selection with back navigation. */
        SUBPAGE_SELECTOR,
        /** Item selection for editing. */
        ITEM

    }

    /**
     * An individual option within a {@link StringMenu}.
     *
     * <p>
     * Each option has a display label, a submission value, and may include an optional
     * description and {@link Emoji}. An option-level interaction handler is invoked when the
     * enclosing {@link StringMenu} is limited to a single selection.
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class Option {

        /** The maximum number of options allowed in a single {@link StringMenu}. */
        public static final int MAX_ALLOWED = 25;
        private static final Function<OptionContext, Mono<Void>> NOOP_HANDLER = __ -> Mono.empty();

        /** The internal unique identifier for this option. */
        private final @NotNull UUID uniqueId;

        /** The display label shown to the user. */
        private final @NotNull String label;

        /** The value submitted when this option is selected. */
        private final @NotNull String value;

        /** The optional description shown beneath the label. */
        private final @NotNull Optional<String> description;

        /** The optional emoji shown to the left of the label. */
        private final @NotNull Optional<Emoji> emoji;

        /** The interaction handler invoked when this option is selected. */
        private final @NotNull Function<OptionContext, Mono<Void>> interaction;

        /**
         * Creates a new builder with a random unique identifier.
         *
         * @return a new {@link Builder} instance
         */
        public static @NotNull Builder builder() {
            return new Builder(UUID.randomUUID());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Option option = (Option) o;

            return Objects.equals(this.getUniqueId(), option.getUniqueId())
                && Objects.equals(this.getLabel(), option.getLabel())
                && Objects.equals(this.getValue(), option.getValue())
                && Objects.equals(this.getDescription(), option.getDescription())
                && Objects.equals(this.getEmoji(), option.getEmoji());
        }

        /**
         * Creates a pre-filled builder from the given option.
         *
         * @param option the option to copy fields from
         * @return a pre-filled {@link Builder} instance
         */
        public static @NotNull Builder from(@NotNull Option option) {
            return new Builder(option.getUniqueId())
                .withLabel(option.getLabel())
                .withValue(option.getValue())
                .withDescription(option.getDescription())
                .withEmoji(option.getEmoji())
                .onInteract(option.interaction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getUniqueId(), this.getLabel(), this.getValue(), this.getDescription(), this.getEmoji());
        }

        /**
         * Converts this option to its Discord4J representation.
         *
         * @param selected whether this option should be marked as the default selection
         * @return the Discord4J option
         */
        public @NotNull discord4j.core.object.component.SelectMenu.Option getD4jOption(boolean selected) {
            discord4j.core.object.component.SelectMenu.Option d4jOption = discord4j.core.object.component.SelectMenu.Option.of(this.getLabel(), this.getValue())
                .withDescription(this.getDescription().orElse(""))
                .withDefault(selected);

            if (this.getEmoji().isPresent())
                d4jOption = d4jOption.withEmoji(this.getEmoji().get().getD4jReaction());

            return d4jOption;
        }

        /**
         * Creates a pre-filled builder from this instance for modification.
         *
         * @return a pre-filled {@link Builder} instance
         */
        public @NotNull Builder mutate() {
            return from(this);
        }

        /**
         * A builder for constructing {@link Option} instances.
         */
        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static final class Builder {

            private final UUID uniqueId;
            private Optional<String> label = Optional.empty();
            private Optional<String> value = Optional.empty();
            private Optional<String> description = Optional.empty();
            private Optional<Emoji> emoji = Optional.empty();
            private Optional<Function<OptionContext, Mono<Void>>> interaction = Optional.empty();

            /**
             * Sets the interaction handler invoked when this {@link Option} is selected.
             *
             * <p>
             * This handler only executes when the enclosing {@link StringMenu} is limited to
             * a single selection.
             *
             * @param interaction the interaction function, or {@code null} for the default no-op handler
             */
            public Builder onInteract(@Nullable Function<OptionContext, Mono<Void>> interaction) {
                return this.onInteract(Optional.ofNullable(interaction));
            }

            /**
             * Sets the interaction handler invoked when this {@link Option} is selected.
             *
             * <p>
             * This handler only executes when the enclosing {@link StringMenu} is limited to
             * a single selection.
             *
             * @param interaction the optional interaction function
             */
            public Builder onInteract(@NotNull Optional<Function<OptionContext, Mono<Void>>> interaction) {
                this.interaction = interaction;
                return this;
            }

            /**
             * Sets the description displayed beneath the {@link Option} label.
             *
             * @param description the description text, or {@code null} to clear
             */
            public Builder withDescription(@Nullable String description) {
                return this.withDescription(Optional.ofNullable(description));
            }

            /**
             * Sets the description displayed beneath the {@link Option} label using a format string.
             *
             * @param description the format string for the description
             * @param args the format arguments
             */
            public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
                return this.withDescription(StringUtil.formatNullable(description, args));
            }

            /**
             * Sets the description displayed beneath the {@link Option} label.
             *
             * @param description the optional description text
             */
            public Builder withDescription(@NotNull Optional<String> description) {
                this.description = description;
                return this;
            }

            /**
             * Sets the {@link Emoji} displayed to the left of the label.
             *
             * @param emoji the emoji to display, or {@code null} to clear
             */
            public Builder withEmoji(@Nullable Emoji emoji) {
                return this.withEmoji(Optional.ofNullable(emoji));
            }

            /**
             * Sets the {@link Emoji} displayed to the left of the label.
             *
             * @param emoji the optional emoji to display
             */
            public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
                this.emoji = emoji;
                return this;
            }

            /**
             * Sets the display label of the {@link Option}.
             *
             * @param label the label text
             */
            public Builder withLabel(@NotNull String label) {
                this.label = Optional.of(label);
                return this;
            }

            /**
             * Sets the display label of the {@link Option} using a format string.
             *
             * @param label the format string for the label
             * @param args the format arguments
             */
            public Builder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
                this.label = Optional.of(String.format(label, args));
                return this;
            }

            /**
             * Sets the submission value of the {@link Option}.
             *
             * @param value the option value
             */
            public Builder withValue(@NotNull String value) {
                this.value = Optional.of(value);
                return this;
            }

            /**
             * Sets the submission value of the {@link Option} using a format string.
             *
             * @param value the format string for the value
             * @param args the format arguments
             */
            public Builder withValue(@PrintFormat @NotNull String value, @Nullable Object... args) {
                this.value = Optional.of(String.format(value, args));
                return this;
            }

            /**
             * Builds a new {@link Option} from the configured fields.
             *
             * @return a new {@link Option} instance
             */
            public @NotNull Option build() {
                return new Option(
                    this.uniqueId,
                    this.label.orElse(this.uniqueId.toString()),
                    this.value.orElse(this.uniqueId.toString()),
                    this.description,
                    this.emoji,
                    this.interaction.orElse(NOOP_HANDLER)
                );
            }

        }

    }

    /**
     * A default value for an auto-populated {@link EntityMenu}.
     *
     * <p>
     * Default values are pre-selected entities Discord renders in the menu. Each default
     * value carries a {@link Snowflake} id and a {@link Type kind} describing the referenced
     * entity. Only {@link Type#USER}, {@link Type#ROLE}, and {@link Type#CHANNEL} are valid
     * kinds per Discord's component protocol - mentionable menus accept {@code USER} or
     * {@code ROLE} defaults.
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class DefaultValue {

        /** The snowflake id of the referenced entity. */
        private final @NotNull Snowflake id;

        /** The kind of entity this default value references. */
        private final @NotNull Type kind;

        /**
         * Creates a default value referencing a Discord user.
         *
         * @param id the user snowflake id
         * @return a new default value of kind {@link Type#USER}
         */
        public static @NotNull DefaultValue ofUser(@NotNull Snowflake id) {
            return new DefaultValue(id, Type.USER);
        }

        /**
         * Creates a default value referencing a Discord role.
         *
         * @param id the role snowflake id
         * @return a new default value of kind {@link Type#ROLE}
         */
        public static @NotNull DefaultValue ofRole(@NotNull Snowflake id) {
            return new DefaultValue(id, Type.ROLE);
        }

        /**
         * Creates a default value referencing a Discord channel.
         *
         * @param id the channel snowflake id
         * @return a new default value of kind {@link Type#CHANNEL}
         */
        public static @NotNull DefaultValue ofChannel(@NotNull Snowflake id) {
            return new DefaultValue(id, Type.CHANNEL);
        }

        /**
         * Converts this default value to its Discord4J representation.
         *
         * @return the Discord4J default value
         * @throws IllegalStateException if this default value's kind is not representable in Discord4J
         */
        public @NotNull discord4j.core.object.component.SelectMenu.DefaultValue getD4jDefaultValue() {
            return discord4j.core.object.component.SelectMenu.DefaultValue.of(this.id, switch (this.kind) {
                case USER    -> discord4j.core.object.component.SelectMenu.DefaultValue.Type.USER;
                case ROLE    -> discord4j.core.object.component.SelectMenu.DefaultValue.Type.ROLE;
                case CHANNEL -> discord4j.core.object.component.SelectMenu.DefaultValue.Type.CHANNEL;
                case STRING, MENTIONABLE -> throw new IllegalStateException("DefaultValue kind '%s' is not representable in Discord4J".formatted(this.kind));
            });
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DefaultValue that = (DefaultValue) o;

            return Objects.equals(this.id, that.id)
                && this.kind == that.kind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.id, this.kind);
        }

    }

    /**
     * A select menu populated with developer-defined string {@link Option options}.
     *
     * <p>
     * When exactly one option is selected, the option's own interaction handler is dispatched
     * after the menu-level handler.
     *
     * @see Option
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class StringMenu implements SelectMenu {

        /** The unique identifier for this select menu. */
        private final @NotNull String identifier;

        /** The optional placeholder text shown when no option is selected. */
        private final @NotNull Optional<String> placeholder;

        /** The minimum number of options that must be selected. */
        private final int minValues;

        /** The maximum number of options that may be selected. */
        private final int maxValues;

        /** Whether the placeholder is replaced with the currently selected option's label. */
        private final boolean placeholderShowingSelectedOption;

        /** The available options within this select menu. */
        private final @NotNull ConcurrentList<Option> options;

        /** Whether the interaction is automatically deferred as an edit. */
        private final boolean deferEdit;

        /** Whether this select menu is required. */
        private final boolean required;

        @Getter(AccessLevel.NONE)
        private final @NotNull Optional<Function<SelectMenuContext, Mono<Void>>> userInteraction;

        /** The pagination role tag for this select menu. */
        private final @NotNull PageType pageType;

        /** The currently selected options (those whose values matched on the last interaction). */
        private @NotNull ConcurrentList<Option> selected;

        /** The raw wire values from the last interaction. */
        private @NotNull ConcurrentList<String> selectedValues;

        /** Whether this select menu is currently enabled. */
        private boolean enabled;

        /**
         * Creates a new builder with a random identifier.
         *
         * @return a new {@link Builder} instance
         */
        public static @NotNull Builder builder() {
            return new Builder().withIdentifier(UUID.randomUUID().toString());
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            StringMenu that = (StringMenu) o;

            return this.getMinValues() == that.getMinValues()
                && this.getMaxValues() == that.getMaxValues()
                && this.isPlaceholderShowingSelectedOption() == that.isPlaceholderShowingSelectedOption()
                && this.isDeferEdit() == that.isDeferEdit()
                && this.isRequired() == that.isRequired()
                && this.isEnabled() == that.isEnabled()
                && Objects.equals(this.getIdentifier(), that.getIdentifier())
                && Objects.equals(this.getPlaceholder(), that.getPlaceholder())
                && Objects.equals(this.getOptions(), that.getOptions())
                && Objects.equals(this.userInteraction, that.userInteraction)
                && Objects.equals(this.getSelected(), that.getSelected())
                && Objects.equals(this.getPageType(), that.getPageType());
        }

        /**
         * Finds the first {@link Option} matching the given predicate.
         *
         * @param function the accessor used to extract the comparison value from each option
         * @param value the value to match against
         * @param <S> the comparison type
         * @return the matching option, if present
         */
        public <S> Optional<Option> findOption(@NotNull Function<Option, S> function, S value) {
            return this.getOptions().stream()
                .filter(option -> Objects.equals(function.apply(option), value))
                .findFirst();
        }

        /**
         * Creates a pre-filled builder from the given select menu.
         *
         * @param selectMenu the select menu to copy fields from
         * @return a pre-filled {@link Builder} instance
         */
        public static @NotNull Builder from(@NotNull StringMenu selectMenu) {
            return new Builder()
                .withIdentifier(selectMenu.getIdentifier())
                .setDisabled(selectMenu.isEnabled())
                .withPlaceholder(selectMenu.getPlaceholder())
                .withMinValues(selectMenu.getMinValues())
                .withMaxValues(selectMenu.getMaxValues())
                .withPlaceholderShowingSelectedOption(selectMenu.isPlaceholderShowingSelectedOption())
                .withOptions(selectMenu.getOptions())
                .onInteract(selectMenu.userInteraction)
                .withPageType(selectMenu.getPageType());
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull Type getMenuType() {
            return Type.STRING;
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull discord4j.core.object.component.SelectMenu getD4jComponent() {
            return discord4j.core.object.component.SelectMenu.of(
                    this.getIdentifier(),
                    this.getOptions()
                        .stream()
                        .map(option -> option.getD4jOption(this.getSelected().contains(option)))
                        .collect(Concurrent.toList())
                )
                .withPlaceholder(this.getPlaceholder().orElse(""))
                .withMinValues(this.getMinValues())
                .withMaxValues(this.getMaxValues())
                .disabled(this.isDisabled());
        }

        /**
         * {@inheritDoc}
         *
         * <p>
         * Dispatches first to the menu-level user interaction, then to the selected
         * {@link Option}'s own interaction handler when exactly one option is selected.
         */
        @Override
        public @NotNull Function<SelectMenuContext, Mono<Void>> getInteraction() {
            return selectMenuContext -> Mono.just(selectMenuContext)
                .flatMap(context -> Mono.justOrEmpty(this.userInteraction)
                    .flatMap(interaction -> interaction.apply(context))
                    .thenReturn(context)
                )
                .filter(context -> context.getEvent().getValues().size() == 1)
                .flatMap(context -> Mono.justOrEmpty(this.getSelected().findFirst())
                    .flatMap(option -> option.getInteraction().apply(OptionContext.of(context, context.getResponse(), option)))
                    .switchIfEmpty(context.deferEdit())
                );
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getIdentifier(), this.getPlaceholder(), this.getMinValues(), this.getMaxValues(), this.isPlaceholderShowingSelectedOption(), this.getOptions(), this.isDeferEdit(), this.isRequired(), this.userInteraction, this.getSelected(), this.isEnabled(), this.getPageType());
        }

        /**
         * Creates a pre-filled builder from this instance for modification.
         *
         * @return a pre-filled {@link Builder} instance
         */
        public @NotNull Builder mutate() {
            return from(this);
        }

        /** {@inheritDoc} */
        @Override
        public void updateFromData(@NotNull ComponentData data) {
            this.updateSelected(data.values().toOptional().orElse(Concurrent.newList()));
        }

        /** {@inheritDoc} */
        @Override
        public void setEnabled(boolean value) {
            this.enabled = value;
        }

        /**
         * {@inheritDoc}
         *
         * <p>
         * Matches each wire value against {@link Option#getValue()} and stores matched
         * options in {@link #getSelected()}. Unmatched values are dropped from the option
         * view but preserved in {@link #getSelectedValues()}.
         */
        @Override
        public @NotNull StringMenu updateSelected(@NotNull List<String> values) {
            this.selectedValues = values.stream().collect(Concurrent.toUnmodifiableList());
            this.selected = values.stream()
                .map(value -> this.findOption(Option::getValue, value))
                .flatMap(Optional::stream)
                .collect(Concurrent.toUnmodifiableList());
            return this;
        }

        /**
         * Updates the selected options to an empty selection.
         *
         * @return this select menu
         */
        public @NotNull StringMenu updateSelected() {
            return this.updateSelected(Concurrent.newList());
        }

        /**
         * Updates the selected options by matching the given values against existing options.
         *
         * @param values the option values to select
         * @return this select menu
         */
        public @NotNull StringMenu updateSelected(@NotNull String... values) {
            return this.updateSelected(Arrays.asList(values));
        }

        /**
         * A builder for constructing {@link StringMenu} instances.
         */
        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static class Builder {

            @BuildFlag(nonNull = true)
            private String identifier;
            private boolean enabled;
            private Optional<String> placeholder = Optional.empty();
            private boolean placeholderShowingSelectedOption;
            @Range(from = 0, to = Option.MAX_ALLOWED)
            private int minValues = 1;
            @Range(from = 1, to = Option.MAX_ALLOWED)
            private int maxValues = 1;
            @BuildFlag(limit = Option.MAX_ALLOWED)
            private final ConcurrentList<Option> options = Concurrent.newList();
            private boolean deferEdit;
            private boolean required;
            private Optional<Function<SelectMenuContext, Mono<Void>>> interaction = Optional.empty();
            private PageType pageType = PageType.NONE;

            /**
             * Replaces an existing {@link Option} matched by unique ID with the given option.
             *
             * @param option the updated option
             */
            public Builder editOption(@NotNull Option option) {
                this.options.stream()
                    .filter(innerOption -> innerOption.getUniqueId().equals(option.getUniqueId()))
                    .findFirst()
                    .ifPresent(innerOption -> {
                        int index = this.options.indexOf(innerOption);
                        this.options.remove(index);
                        this.options.add(index, option);
                    });
                return this;
            }

            /**
             * Finds the first {@link Option} matching the given predicate.
             *
             * @param function the accessor used to extract the comparison value from each option
             * @param value the value to match against
             * @param <S> the comparison type
             * @return the matching option, if present
             */
            public final <S> Optional<Option> findOption(@NotNull Function<Option, S> function, S value) {
                return this.options.stream()
                    .filter(option -> Objects.equals(function.apply(option), value))
                    .findFirst();
            }

            /**
             * Sets the interaction handler invoked when the {@link StringMenu} selection changes.
             *
             * @param interaction the interaction function, or {@code null} for no menu-level handler
             */
            public Builder onInteract(@Nullable Function<SelectMenuContext, Mono<Void>> interaction) {
                return this.onInteract(Optional.ofNullable(interaction));
            }

            /**
             * Sets the interaction handler invoked when the {@link StringMenu} selection changes.
             *
             * @param interaction the optional interaction function
             */
            public Builder onInteract(@NotNull Optional<Function<SelectMenuContext, Mono<Void>>> interaction) {
                this.interaction = interaction;
                return this;
            }

            /**
             * Sets the {@link StringMenu} as disabled.
             */
            public Builder setDisabled() {
                return this.setDisabled(true);
            }

            /**
             * Sets whether the {@link StringMenu} is disabled.
             *
             * @param value {@code true} to disable the select menu
             */
            public Builder setDisabled(boolean value) {
                return this.setEnabled(!value);
            }

            /**
             * Sets the {@link StringMenu} to automatically defer interactions as edits.
             */
            public Builder withDeferEdit() {
                return this.withDeferEdit(true);
            }

            /**
             * Sets whether the {@link StringMenu} automatically defers interactions as edits.
             *
             * @param deferEdit {@code true} to defer interactions
             */
            public Builder withDeferEdit(boolean deferEdit) {
                this.deferEdit = deferEdit;
                return this;
            }

            /**
             * Sets the {@link StringMenu} as enabled.
             */
            public Builder setEnabled() {
                return this.setEnabled(true);
            }

            /**
             * Sets whether the {@link StringMenu} is enabled.
             *
             * @param value {@code true} to enable the select menu
             */
            public Builder setEnabled(boolean value) {
                this.enabled = value;
                return this;
            }

            /**
             * Sets the {@link StringMenu} as required.
             */
            public Builder setRequired() {
                return this.setRequired(true);
            }

            /**
             * Sets whether the {@link StringMenu} is required.
             *
             * @param value {@code true} to mark the select menu as required
             */
            public Builder setRequired(boolean value) {
                this.required = value;
                return this;
            }

            /**
             * Sets the identifier of the {@link StringMenu}, overriding the default random UUID.
             *
             * @param identifier the identifier to use
             */
            public Builder withIdentifier(@NotNull String identifier) {
                this.identifier = identifier;
                return this;
            }

            /**
             * Sets the identifier of the {@link StringMenu} using a format string, overriding the default random UUID.
             *
             * @param identifier the format string for the identifier
             * @param args the format arguments
             */
            public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
                this.identifier = String.format(identifier, args);
                return this;
            }

            /**
             * Adds {@link Option options} to the {@link StringMenu}.
             *
             * @param options variable number of options to add
             */
            public Builder withOptions(@NotNull Option... options) {
                return this.withOptions(Arrays.asList(options));
            }

            /**
             * Adds {@link Option options} to the {@link StringMenu}.
             *
             * @param options collection of options to add
             */
            public Builder withOptions(@NotNull Iterable<Option> options) {
                options.forEach(this.options::add);
                return this;
            }

            /**
             * Sets the placeholder text displayed when no option is selected.
             *
             * @param placeholder the placeholder text, or {@code null} to clear
             */
            public Builder withPlaceholder(@Nullable String placeholder) {
                return this.withPlaceholder(Optional.ofNullable(placeholder));
            }

            /**
             * Sets the placeholder text displayed when no option is selected.
             *
             * @param placeholder the optional placeholder text
             */
            public Builder withPlaceholder(@NotNull Optional<String> placeholder) {
                this.placeholder = placeholder;
                return this;
            }

            /**
             * Sets the minimum number of options that must be selected.
             *
             * @param minValues the minimum selection count
             */
            public Builder withMinValues(int minValues) {
                this.minValues = minValues;
                return this;
            }

            /**
             * Sets the maximum number of options that may be selected.
             *
             * @param maxValues the maximum selection count
             */
            public Builder withMaxValues(int maxValues) {
                this.maxValues = maxValues;
                return this;
            }

            /**
             * Sets the {@link StringMenu} to replace its placeholder with the selected option's label.
             */
            public Builder withPlaceholderShowingSelectedOption() {
                return this.withPlaceholderShowingSelectedOption(true);
            }

            /**
             * Sets whether the {@link StringMenu} replaces its placeholder with the selected option's label.
             *
             * @param value {@code true} to show the selected option as the placeholder
             */
            public Builder withPlaceholderShowingSelectedOption(boolean value) {
                this.placeholderShowingSelectedOption = value;
                return this;
            }

            /**
             * Sets the pagination role tag of the {@link StringMenu}.
             *
             * @param pageType the page type of the select menu
             */
            public Builder withPageType(@NotNull PageType pageType) {
                this.pageType = pageType;
                return this;
            }

            /**
             * Builds a new {@link StringMenu} from the configured fields.
             *
             * @return a new {@link StringMenu} instance
             */
            public @NotNull StringMenu build() {
                Reflection.validateFlags(this);

                return new StringMenu(
                    this.identifier,
                    this.placeholder,
                    this.minValues,
                    this.maxValues,
                    this.placeholderShowingSelectedOption,
                    this.options,
                    this.deferEdit,
                    this.required,
                    this.interaction,
                    this.pageType,
                    Concurrent.newUnmodifiableList(),
                    Concurrent.newUnmodifiableList(),
                    this.enabled
                );
            }

        }

    }

    /**
     * An auto-populated select menu for Discord entities - users, roles, mentionables,
     * or channels.
     *
     * <p>
     * Entity menus are populated by Discord based on the server context; the developer only
     * supplies selection bounds, an optional placeholder, and optional
     * {@link DefaultValue default values}. Channel menus may additionally filter the
     * selectable channels via {@link Builder#withAllowedChannelTypes allowed channel types}.
     *
     * @see DefaultValue
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class EntityMenu implements SelectMenu {

        /** The unique identifier for this select menu. */
        private final @NotNull String identifier;

        /** The optional placeholder text shown when no entity is selected. */
        private final @NotNull Optional<String> placeholder;

        /** The minimum number of entities that must be selected. */
        private final int minValues;

        /** The maximum number of entities that may be selected. */
        private final int maxValues;

        /** Whether the interaction is automatically deferred as an edit. */
        private final boolean deferEdit;

        /** Whether this select menu is required. */
        private final boolean required;

        @Getter(AccessLevel.NONE)
        private final @NotNull Optional<Function<SelectMenuContext, Mono<Void>>> userInteraction;

        /** The entity variant of this menu. */
        private final @NotNull Type menuType;

        /** The pre-selected default values. */
        private final @NotNull ConcurrentList<DefaultValue> defaultValues;

        /** The allowed channel types for a {@link Type#CHANNEL} menu; empty for other variants. */
        private final @NotNull ConcurrentSet<Channel.Type> allowedChannelTypes;

        /** The snowflakes parsed from the last interaction's wire values. */
        private @NotNull ConcurrentList<Snowflake> selectedSnowflakes;

        /** The raw wire values from the last interaction. */
        private @NotNull ConcurrentList<String> selectedValues;

        /** Whether this select menu is currently enabled. */
        private boolean enabled;

        /**
         * Creates a new builder seeded with the given entity variant.
         *
         * @param type the entity menu variant (must not be {@link Type#STRING})
         * @return a new {@link Builder} instance
         * @throws IllegalArgumentException if the given type is {@link Type#STRING}
         */
        public static @NotNull Builder builder(@NotNull Type type) {
            if (!type.isEntity())
                throw new IllegalArgumentException("EntityMenu type must be an entity variant, got '%s'".formatted(type));

            return new Builder(type).withIdentifier(UUID.randomUUID().toString());
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;

            EntityMenu that = (EntityMenu) o;

            return this.getMinValues() == that.getMinValues()
                && this.getMaxValues() == that.getMaxValues()
                && this.isDeferEdit() == that.isDeferEdit()
                && this.isRequired() == that.isRequired()
                && this.isEnabled() == that.isEnabled()
                && Objects.equals(this.getIdentifier(), that.getIdentifier())
                && Objects.equals(this.getPlaceholder(), that.getPlaceholder())
                && Objects.equals(this.userInteraction, that.userInteraction)
                && Objects.equals(this.getMenuType(), that.getMenuType())
                && Objects.equals(this.getDefaultValues(), that.getDefaultValues())
                && Objects.equals(this.getAllowedChannelTypes(), that.getAllowedChannelTypes())
                && Objects.equals(this.getSelectedSnowflakes(), that.getSelectedSnowflakes());
        }

        /**
         * Creates a pre-filled builder from the given entity menu.
         *
         * @param selectMenu the entity menu to copy fields from
         * @return a pre-filled {@link Builder} instance
         */
        public static @NotNull Builder from(@NotNull EntityMenu selectMenu) {
            return new Builder(selectMenu.getMenuType())
                .withIdentifier(selectMenu.getIdentifier())
                .setDisabled(selectMenu.isEnabled())
                .withPlaceholder(selectMenu.getPlaceholder())
                .withMinValues(selectMenu.getMinValues())
                .withMaxValues(selectMenu.getMaxValues())
                .withDefaultValues(selectMenu.getDefaultValues())
                .withAllowedChannelTypes(selectMenu.getAllowedChannelTypes())
                .onInteract(selectMenu.userInteraction);
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull discord4j.core.object.component.SelectMenu getD4jComponent() {
            List<discord4j.core.object.component.SelectMenu.DefaultValue> d4jDefaults = this.defaultValues.stream()
                .map(DefaultValue::getD4jDefaultValue)
                .collect(Concurrent.toList());

            discord4j.core.object.component.SelectMenu menu = switch (this.menuType) {
                case USER        -> discord4j.core.object.component.SelectMenu.ofUser(this.identifier, d4jDefaults);
                case ROLE        -> discord4j.core.object.component.SelectMenu.ofRole(this.identifier, d4jDefaults);
                case MENTIONABLE -> discord4j.core.object.component.SelectMenu.ofMentionable(this.identifier, d4jDefaults);
                case CHANNEL     -> discord4j.core.object.component.SelectMenu.ofChannel(this.identifier, d4jDefaults, this.allowedChannelTypes.stream().toList());
                case STRING      -> throw new IllegalStateException("EntityMenu constructed with Type.STRING");
            };

            return menu.withPlaceholder(this.placeholder.orElse(""))
                .withMinValues(this.minValues)
                .withMaxValues(this.maxValues)
                .disabled(this.isDisabled());
        }

        /**
         * {@inheritDoc}
         *
         * <p>
         * Dispatches to the menu-level user interaction. Entity menus have no per-option
         * concept, so there is no secondary dispatch after the menu-level handler.
         */
        @Override
        public @NotNull Function<SelectMenuContext, Mono<Void>> getInteraction() {
            return selectMenuContext -> Mono.justOrEmpty(this.userInteraction)
                .flatMap(interaction -> interaction.apply(selectMenuContext))
                .switchIfEmpty(selectMenuContext.deferEdit());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.getIdentifier(), this.getPlaceholder(), this.getMinValues(), this.getMaxValues(), this.isDeferEdit(), this.isRequired(), this.userInteraction, this.getMenuType(), this.getDefaultValues(), this.getAllowedChannelTypes(), this.getSelectedSnowflakes(), this.isEnabled());
        }

        /**
         * Creates a pre-filled builder from this instance for modification.
         *
         * @return a pre-filled {@link Builder} instance
         */
        public @NotNull Builder mutate() {
            return from(this);
        }

        /** {@inheritDoc} */
        @Override
        public void updateFromData(@NotNull ComponentData data) {
            this.updateSelected(data.values().toOptional().orElse(Concurrent.newList()));
        }

        /** {@inheritDoc} */
        @Override
        public void setEnabled(boolean value) {
            this.enabled = value;
        }

        /**
         * {@inheritDoc}
         *
         * <p>
         * Parses each wire value as a {@link Snowflake} and stores them in
         * {@link #getSelectedSnowflakes()}. The raw strings are preserved in
         * {@link #getSelectedValues()}.
         */
        @Override
        public @NotNull EntityMenu updateSelected(@NotNull List<String> values) {
            this.selectedValues = values.stream().collect(Concurrent.toUnmodifiableList());
            this.selectedSnowflakes = values.stream()
                .map(Snowflake::of)
                .collect(Concurrent.toUnmodifiableList());
            return this;
        }

        /**
         * A builder for constructing {@link EntityMenu} instances.
         */
        @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
        public static class Builder {

            private final @NotNull Type menuType;
            @BuildFlag(nonNull = true)
            private String identifier;
            private boolean enabled;
            private Optional<String> placeholder = Optional.empty();
            @Range(from = 0, to = 25)
            private int minValues = 1;
            @Range(from = 1, to = 25)
            private int maxValues = 1;
            private final ConcurrentList<DefaultValue> defaultValues = Concurrent.newList();
            private final ConcurrentSet<Channel.Type> allowedChannelTypes = Concurrent.newSet();
            private boolean deferEdit;
            private boolean required;
            private Optional<Function<SelectMenuContext, Mono<Void>>> interaction = Optional.empty();

            /**
             * Sets the interaction handler invoked when the {@link EntityMenu} selection changes.
             *
             * @param interaction the interaction function, or {@code null} for no handler
             */
            public Builder onInteract(@Nullable Function<SelectMenuContext, Mono<Void>> interaction) {
                return this.onInteract(Optional.ofNullable(interaction));
            }

            /**
             * Sets the interaction handler invoked when the {@link EntityMenu} selection changes.
             *
             * @param interaction the optional interaction function
             */
            public Builder onInteract(@NotNull Optional<Function<SelectMenuContext, Mono<Void>>> interaction) {
                this.interaction = interaction;
                return this;
            }

            /** Sets the {@link EntityMenu} as disabled. */
            public Builder setDisabled() {
                return this.setDisabled(true);
            }

            /**
             * Sets whether the {@link EntityMenu} is disabled.
             *
             * @param value {@code true} to disable the menu
             */
            public Builder setDisabled(boolean value) {
                return this.setEnabled(!value);
            }

            /** Sets the {@link EntityMenu} to automatically defer interactions as edits. */
            public Builder withDeferEdit() {
                return this.withDeferEdit(true);
            }

            /**
             * Sets whether the {@link EntityMenu} automatically defers interactions as edits.
             *
             * @param deferEdit {@code true} to defer interactions
             */
            public Builder withDeferEdit(boolean deferEdit) {
                this.deferEdit = deferEdit;
                return this;
            }

            /** Sets the {@link EntityMenu} as enabled. */
            public Builder setEnabled() {
                return this.setEnabled(true);
            }

            /**
             * Sets whether the {@link EntityMenu} is enabled.
             *
             * @param value {@code true} to enable the menu
             */
            public Builder setEnabled(boolean value) {
                this.enabled = value;
                return this;
            }

            /** Sets the {@link EntityMenu} as required. */
            public Builder setRequired() {
                return this.setRequired(true);
            }

            /**
             * Sets whether the {@link EntityMenu} is required.
             *
             * @param value {@code true} to mark the menu as required
             */
            public Builder setRequired(boolean value) {
                this.required = value;
                return this;
            }

            /**
             * Sets the identifier of the {@link EntityMenu}, overriding the default random UUID.
             *
             * @param identifier the identifier to use
             */
            public Builder withIdentifier(@NotNull String identifier) {
                this.identifier = identifier;
                return this;
            }

            /**
             * Sets the identifier of the {@link EntityMenu} using a format string, overriding the default random UUID.
             *
             * @param identifier the format string for the identifier
             * @param args the format arguments
             */
            public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
                this.identifier = String.format(identifier, args);
                return this;
            }

            /**
             * Sets the placeholder text displayed when no entity is selected.
             *
             * @param placeholder the placeholder text, or {@code null} to clear
             */
            public Builder withPlaceholder(@Nullable String placeholder) {
                return this.withPlaceholder(Optional.ofNullable(placeholder));
            }

            /**
             * Sets the placeholder text displayed when no entity is selected.
             *
             * @param placeholder the optional placeholder text
             */
            public Builder withPlaceholder(@NotNull Optional<String> placeholder) {
                this.placeholder = placeholder;
                return this;
            }

            /**
             * Sets the minimum number of entities that must be selected.
             *
             * @param minValues the minimum selection count
             */
            public Builder withMinValues(int minValues) {
                this.minValues = minValues;
                return this;
            }

            /**
             * Sets the maximum number of entities that may be selected.
             *
             * @param maxValues the maximum selection count
             */
            public Builder withMaxValues(int maxValues) {
                this.maxValues = maxValues;
                return this;
            }

            /**
             * Adds {@link DefaultValue default values} to pre-select in the {@link EntityMenu}.
             *
             * @param defaultValues variable number of default values to add
             */
            public Builder withDefaultValues(@NotNull DefaultValue... defaultValues) {
                return this.withDefaultValues(Arrays.asList(defaultValues));
            }

            /**
             * Adds {@link DefaultValue default values} to pre-select in the {@link EntityMenu}.
             *
             * @param defaultValues collection of default values to add
             */
            public Builder withDefaultValues(@NotNull Iterable<DefaultValue> defaultValues) {
                defaultValues.forEach(this.defaultValues::add);
                return this;
            }

            /**
             * Adds allowed {@link Channel.Type channel types} to filter the {@link EntityMenu}
             * by. Only meaningful for {@link Type#CHANNEL} menus.
             *
             * @param channelTypes variable number of channel types to allow
             */
            public Builder withAllowedChannelTypes(@NotNull Channel.Type... channelTypes) {
                return this.withAllowedChannelTypes(Arrays.asList(channelTypes));
            }

            /**
             * Adds allowed {@link Channel.Type channel types} to filter the {@link EntityMenu}
             * by. Only meaningful for {@link Type#CHANNEL} menus.
             *
             * @param channelTypes collection of channel types to allow
             */
            public Builder withAllowedChannelTypes(@NotNull Iterable<Channel.Type> channelTypes) {
                channelTypes.forEach(this.allowedChannelTypes::add);
                return this;
            }

            /**
             * Builds a new {@link EntityMenu} from the configured fields.
             *
             * @return a new {@link EntityMenu} instance
             * @throws IllegalStateException if any {@link DefaultValue} kind is not accepted by
             *   the configured menu type, or if channel types are set on a non-channel menu
             */
            public @NotNull EntityMenu build() {
                Reflection.validateFlags(this);

                for (DefaultValue defaultValue : this.defaultValues) {
                    if (!acceptsDefaultKind(this.menuType, defaultValue.getKind()))
                        throw new IllegalStateException("DefaultValue kind '%s' is not accepted by EntityMenu type '%s'".formatted(defaultValue.getKind(), this.menuType));
                }

                if (this.menuType != Type.CHANNEL && !this.allowedChannelTypes.isEmpty())
                    throw new IllegalStateException("Allowed channel types may only be set on an EntityMenu of type '%s'".formatted(Type.CHANNEL));

                return new EntityMenu(
                    this.identifier,
                    this.placeholder,
                    this.minValues,
                    this.maxValues,
                    this.deferEdit,
                    this.required,
                    this.interaction,
                    this.menuType,
                    this.defaultValues.toUnmodifiable(),
                    this.allowedChannelTypes.toUnmodifiable(),
                    Concurrent.newUnmodifiableList(),
                    Concurrent.newUnmodifiableList(),
                    this.enabled
                );
            }

            private static boolean acceptsDefaultKind(@NotNull Type menuType, @NotNull Type defaultKind) {
                return switch (menuType) {
                    case USER        -> defaultKind == Type.USER;
                    case ROLE        -> defaultKind == Type.ROLE;
                    case MENTIONABLE -> defaultKind == Type.USER || defaultKind == Type.ROLE;
                    case CHANNEL     -> defaultKind == Type.CHANNEL;
                    case STRING      -> false;
                };
            }

        }

    }

}
