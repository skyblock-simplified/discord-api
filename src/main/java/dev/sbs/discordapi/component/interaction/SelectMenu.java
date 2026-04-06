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
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
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
 * An immutable dropdown select menu component rendered within a Discord message.
 * <p>
 * Select menus present a list of {@link Option options} from which a user may choose one
 * or more values, bounded by {@link #getMinValues()} and {@link #getMaxValues()}. The menu
 * {@link Type} determines whether options are user-defined strings or Discord entity
 * selectors (user, role, mentionable, channel).
 * <p>
 * A computed {@link #getInteraction()} dispatches first to the menu-level handler, then
 * to the selected {@link Option}'s own handler when a single option is chosen.
 * <p>
 * Instances are created via {@link #builder()} and can be copied for modification
 * via {@link #mutate()}.
 *
 * @see Option
 * @see Type
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelectMenu implements ActionComponent, EventInteractable<SelectMenuContext>, LabelComponent, Toggleable {

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

    /** The entity type of this select menu. */
    private final @NotNull Type menuType;

    private final @NotNull PageType pageType;

    /** The currently selected options. */
    private @NotNull ConcurrentList<Option> selected;

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

        SelectMenu that = (SelectMenu) o;

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
            && Objects.equals(this.getMenuType(), that.getMenuType())
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
    public static @NotNull Builder from(@NotNull SelectMenu selectMenu) {
        return new Builder()
            .withIdentifier(selectMenu.getIdentifier())
            .setDisabled(selectMenu.isEnabled())
            .withPlaceholder(selectMenu.getPlaceholder())
            .withMinValues(selectMenu.getMinValues())
            .withMaxValues(selectMenu.getMaxValues())
            .withPlaceholderShowingSelectedOption(selectMenu.isPlaceholderShowingSelectedOption())
            .withOptions(selectMenu.getOptions())
            .onInteract(selectMenu.userInteraction)
            .withType(selectMenu.getMenuType())
            .withPageType(selectMenu.getPageType());
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
     * <p>
     * Dispatches first to the menu-level user interaction, then to the selected
     * {@link Option}'s own interaction handler when exactly one option is selected.
     */
    @Override
    public @NotNull Function<SelectMenuContext, Mono<Void>> getInteraction() {
        return selectMenuContext -> Mono.just(selectMenuContext)
            //.doOnNext(context -> this.updateSelected(context.getEvent().getValues()))
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

    /** {@inheritDoc} */
    @Override
    public @NotNull Component.Type getType() {
        return this.getMenuType().getInternalType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIdentifier(), this.getPlaceholder(), this.getMinValues(), this.getMaxValues(), this.isPlaceholderShowingSelectedOption(), this.getOptions(), this.isDeferEdit(), this.isRequired(), this.userInteraction, this.getMenuType(), this.getSelected(), this.isEnabled());
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
     * Updates the selected options to an empty selection.
     *
     * @return this select menu
     */
    public @NotNull SelectMenu updateSelected() {
        return this.updateSelected(Concurrent.newList());
    }

    /**
     * Updates the selected options by matching the given values against existing options.
     *
     * @param values the option values to select
     * @return this select menu
     */
    public @NotNull SelectMenu updateSelected(@NotNull String... values) {
        return this.updateSelected(Arrays.asList(values));
    }

    /**
     * Updates the selected options by matching the given values against existing options.
     *
     * @param values the option values to select
     * @return this select menu
     */
    public @NotNull SelectMenu updateSelected(@NotNull List<String> values) {
        this.selected = values.stream()
            .map(value -> this.findOption(Option::getValue, value))
            .flatMap(Optional::stream)
            .collect(Concurrent.toUnmodifiableList());
        return this;
    }

    /**
     * A builder for constructing {@link SelectMenu} instances.
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
        private Type type = Type.STRING;
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
         * Sets the interaction handler invoked when the {@link SelectMenu} selection changes.
         *
         * @param interaction the interaction function, or {@code null} for no menu-level handler
         */
        public Builder onInteract(@Nullable Function<SelectMenuContext, Mono<Void>> interaction) {
            return this.onInteract(Optional.ofNullable(interaction));
        }

        /**
         * Sets the interaction handler invoked when the {@link SelectMenu} selection changes.
         *
         * @param interaction the optional interaction function
         */
        public Builder onInteract(@NotNull Optional<Function<SelectMenuContext, Mono<Void>>> interaction) {
            this.interaction = interaction;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} as disabled.
         */
        public Builder setDisabled() {
            return this.setDisabled(true);
        }

        /**
         * Sets whether the {@link SelectMenu} is disabled.
         *
         * @param value {@code true} to disable the select menu
         */
        public Builder setDisabled(boolean value) {
            return this.setEnabled(!value);
        }

        /**
         * Sets the {@link SelectMenu} to automatically defer interactions as edits.
         */
        public Builder withDeferEdit() {
            return this.withDeferEdit(true);
        }

        /**
         * Sets whether the {@link SelectMenu} automatically defers interactions as edits.
         *
         * @param deferEdit {@code true} to defer interactions
         */
        public Builder withDeferEdit(boolean deferEdit) {
            this.deferEdit = deferEdit;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} as enabled.
         */
        public Builder setEnabled() {
            return this.setEnabled(true);
        }

        /**
         * Sets whether the {@link SelectMenu} is enabled.
         *
         * @param value {@code true} to enable the select menu
         */
        public Builder setEnabled(boolean value) {
            this.enabled = value;
            return this;
        }

        /**
         * Sets the {@link SelectMenu} as required.
         */
        public Builder setRequired() {
            return this.setRequired(true);
        }

        /**
         * Sets whether the {@link SelectMenu} is required.
         *
         * @param value {@code true} to mark the select menu as required
         */
        public Builder setRequired(boolean value) {
            this.required = value;
            return this;
        }

        /**
         * Sets the identifier of the {@link SelectMenu}, overriding the default random UUID.
         *
         * @param identifier the identifier to use
         */
        public Builder withIdentifier(@NotNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        /**
         * Sets the identifier of the {@link SelectMenu} using a format string, overriding the default random UUID.
         *
         * @param identifier the format string for the identifier
         * @param args the format arguments
         */
        public Builder withIdentifier(@PrintFormat @NotNull String identifier, @Nullable Object... args) {
            this.identifier = String.format(identifier, args);
            return this;
        }

        /**
         * Adds {@link Option options} to the {@link SelectMenu}.
         *
         * @param options variable number of options to add
         */
        public Builder withOptions(@NotNull Option... options) {
            return this.withOptions(Arrays.asList(options));
        }

        /**
         * Adds {@link Option options} to the {@link SelectMenu}.
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
         * Sets the {@link SelectMenu} to replace its placeholder with the selected option's label.
         */
        public Builder withPlaceholderShowingSelectedOption() {
            return this.withPlaceholderShowingSelectedOption(true);
        }

        /**
         * Sets whether the {@link SelectMenu} replaces its placeholder with the selected option's label.
         *
         * @param value {@code true} to show the selected option as the placeholder
         */
        public Builder withPlaceholderShowingSelectedOption(boolean value) {
            this.placeholderShowingSelectedOption = value;
            return this;
        }

        /**
         * Sets the entity {@link Type} of the {@link SelectMenu}.
         *
         * @param type the menu type
         */
        public Builder withType(@NotNull Type type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the page type of the {@link SelectMenu}.
         *
         * @param pageType The page type of the select menu.
         */
        public Builder withPageType(@NotNull PageType pageType) {
            this.pageType = pageType;
            return this;
        }

        /**
         * Builds a new {@link SelectMenu} from the configured fields.
         *
         * @return a new {@link SelectMenu} instance
         */
        public @NotNull SelectMenu build() {
            Reflection.validateFlags(this);

            if (this.type == Type.UNKNOWN)
                throw new IllegalStateException("Type must be set.");

            return new SelectMenu(
                this.identifier,
                this.placeholder,
                this.minValues,
                this.maxValues,
                this.placeholderShowingSelectedOption,
                this.options,
                this.deferEdit,
                this.required,
                this.interaction,
                this.type,
                this.pageType,
                Concurrent.newUnmodifiableList(),
                this.enabled
            );
        }

    }

    /**
     * An individual option within a {@link SelectMenu}.
     * <p>
     * Each option has a display label, a submission value, and may include an optional
     * description and {@link Emoji}. An option-level interaction handler is invoked when
     * the enclosing {@link SelectMenu} is limited to a single selection.
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Option {

        /** The maximum number of options allowed in a single {@link SelectMenu}. */
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

        //discord4j.core.object.component.SelectMenu.DefaultValue dv;
        // uses id: snowflake and value: EntitySelectMenu.Type#lowercase
        // TODO: See if this can be abstracted to support Snowflake/Value? (See DefaultValue in d4j)
        //  Label could be the Role name, Username, Channel name
        //  Value would be the snowflake
        //  Description would remain empty
        //  Emoji would remain empty
        //  Snowflake could be used to find an interaction

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
             * <p>
             * This handler only executes when the enclosing {@link SelectMenu} is limited
             * to a single selection. See {@link SelectMenu#getMinValues()} and
             * {@link SelectMenu#getMaxValues()}.
             *
             * @param interaction the interaction function, or {@code null} for the default no-op handler
             */
            public Builder onInteract(@Nullable Function<OptionContext, Mono<Void>> interaction) {
                return this.onInteract(Optional.ofNullable(interaction));
            }

            /**
             * Sets the interaction handler invoked when this {@link Option} is selected.
             * <p>
             * This handler only executes when the enclosing {@link SelectMenu} is limited
             * to a single selection. See {@link SelectMenu#getMinValues()} and
             * {@link SelectMenu#getMaxValues()}.
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
     * Entity type of a {@link SelectMenu}, mapping to the corresponding {@link Component.Type}.
     */
    @Getter
    @RequiredArgsConstructor
    public enum Type {

        /** Fallback for unrecognized type values. */
        UNKNOWN(Component.Type.UNKNOWN),
        /** User-defined string options. */
        STRING(Component.Type.SELECT_MENU_STRING),
        /** Discord user entity selector. */
        USER(Component.Type.SELECT_MENU_USER),
        /** Discord role entity selector. */
        ROLE(Component.Type.SELECT_MENU_ROLE),
        /** Discord mentionable entity selector. */
        MENTIONABLE(Component.Type.SELECT_MENU_MENTIONABLE),
        /** Discord channel entity selector. */
        CHANNEL(Component.Type.SELECT_MENU_CHANNEL);

        /** The corresponding {@link Component.Type} for this menu type. */
        private final @NotNull Component.Type internalType;

        /**
         * Returns the constant matching the given value, or {@code UNKNOWN} if unrecognized.
         *
         * @param value the Discord integer value
         * @return the matching {@link Type}
         */
        public static @NotNull Type of(int value) {
            return Arrays.stream(values())
                .filter(type -> type.getInternalType().getValue() == value)
                .findFirst()
                .orElse(UNKNOWN);
        }

    }

    /**
     * Identifier for built-in pagination select menu roles.
     *
     * <p>
     * Used to tag select menus for identification. Interaction handlers are
     * provided by {@link dev.sbs.discordapi.response.handler.PaginationHandler
     * PaginationHandler}.
     *
     * @see dev.sbs.discordapi.response.handler.PaginationHandler
     */
    public enum PageType {

        /** No pagination role. */
        NONE,
        /** Top-level page selection. */
        PAGE_SELECTOR,
        /** Subpage selection with back navigation. */
        SUBPAGE_SELECTOR,
        /** Item selection for editing. */
        ITEM

    }


}
