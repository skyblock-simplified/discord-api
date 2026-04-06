package dev.sbs.discordapi.response.page;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.capability.Toggleable;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.scope.ActionComponent;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.handler.HistoryHandler;
import dev.sbs.discordapi.response.handler.item.ItemHandler;
import dev.sbs.discordapi.response.page.item.Item;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

public interface Page {

    @NotNull ConcurrentList<LayoutComponent> getComponents();

    @NotNull HistoryHandler<?, String> getHistoryHandler();

    @NotNull ItemHandler<?> getItemHandler();

    @NotNull SelectMenu.Option getOption();

    @NotNull ConcurrentList<Emoji> getReactions();

    @NotNull Builder mutate();

    // Builders

    static @NotNull TreePage.TreePageBuilder builder() {
        return TreePage.builder();
    }

    static @NotNull FormPage.QuestionBuilder form() {
        return FormPage.builder();
    }

    static @NotNull TreePage.TreePageBuilder from(@NotNull TreePage treePage) {
        return TreePage.from(treePage);
    }

    static @NotNull FormPage.QuestionBuilder from(@NotNull FormPage formPage) {
        return FormPage.from(formPage);
    }

    // Accessors

    default boolean hasItems() {
        return this.getItemHandler().getItems().notEmpty();
    }

    default boolean hasNoItems() {
        return !this.hasItems();
    }

    /**
     * Finds an existing {@link ActionComponent}.
     *
     * @param tClass The component type to match.
     * @param function The method reference to match with.
     * @param value The value to match with.
     * @return The matching component, if it exists.
     */
    default  <S, T extends ActionComponent> @NotNull Optional<T> findComponent(@NotNull Class<T> tClass, @NotNull Function<T, S> function, S value) {
        return this.getComponents()
            .stream()
            .map(layoutComponent -> layoutComponent.findComponent(tClass, function, value))
            .flatMap(Optional::stream)
            .findFirst();
    }

    abstract class Builder {

        @BuildFlag(nonNull = true)
        protected SelectMenu.Option.Builder optionBuilder = SelectMenu.Option.builder();
        protected ConcurrentList<LayoutComponent> components = Concurrent.newList();
        protected ConcurrentList<Emoji> reactions = Concurrent.newList();
        @BuildFlag(nonNull = true)
        protected ItemHandler<?> itemHandler = ItemHandler.<Item>embed().build();

        /**
         * Clear all but preservable components from {@link Page}.
         */
        public Builder disableComponents() {
            return this.disableComponents(false);
        }

        /**
         * Clear all but preservable components from {@link Page}.
         *
         * @param recursive True to recursively clear components.
         */
        public Builder disableComponents(boolean recursive) {
            this.components.forEach(this::toggleComponents);
            return this;
        }

        protected final @NotNull Component toggleComponents(@NotNull Component component) {
            if (component instanceof LayoutComponent layoutComponent) {
                layoutComponent.getComponents()
                    .stream()
                    .map(this::toggleComponents)
                    .filter(Toggleable.class::isInstance)
                    .map(Toggleable.class::cast)
                    .forEach(component1 -> component1.setEnabled(false));
            }

            return component;
        }

        /**
         * Remove the {@link Emoji Reaction} from the {@link Page}.
         *
         * @param emoji Emoji to remove.
         */
        public Builder clearReaction(@NotNull Emoji emoji) {
            this.reactions.remove(emoji);
            return this;
        }

        /**
         * Remove all reactions from the {@link Page}.
         */
        public Builder clearReactions() {
            this.reactions.clear();
            return this;
        }

        /**
         * Updates an existing {@link ActionComponent}.
         *
         * @param actionComponent The component to edit.
         */
        public Builder editComponent(@NotNull ActionComponent actionComponent) {
            this.components.forEach(layoutComponent -> layoutComponent.modifyComponent(actionComponent));
            return this;
        }

        /**
         * Finds an existing {@link ActionComponent}.
         *
         * @param tClass The component type to match.
         * @param function The method reference to match with.
         * @param value The value to match with.
         * @return The matching component, if it exists.
         */
        public final <S, A extends ActionComponent> Optional<A> findComponent(@NotNull Class<A> tClass, @NotNull Function<A, S> function, S value) {
            return this.components.stream()
                .map(layoutComponent -> layoutComponent.findComponent(tClass, function, value))
                .flatMap(Optional::stream)
                .findFirst();
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Page}.
         *
         * @param components Variable number of layout components to add.
         */
        public Builder withComponents(@NotNull LayoutComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link Page}.
         *
         * @param components Collection of layout components to add.
         */
        public Builder withComponents(@NotNull Iterable<LayoutComponent> components) {
            components.forEach(this.components::add);
            return this;
        }
        
        /**
         * {@link SelectMenu.Option.Builder#withDescription(String)}
         */
        public Builder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        /**
         * {@link SelectMenu.Option.Builder#withDescription(String, Object...)}
         */
        public Builder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        /**
         * {@link SelectMenu.Option.Builder#withDescription(Optional)}
         */
        public Builder withDescription(@NotNull Optional<String> description) {
            this.optionBuilder.withDescription(description);
            return this;
        }

        /**
         * {@link SelectMenu.Option.Builder#withEmoji(Emoji)}
         */
        public Builder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        /**
         * {@link SelectMenu.Option.Builder#withEmoji(Optional)}
         */
        public Builder withEmoji(@NotNull Optional<Emoji> emoji) {
            this.optionBuilder.withEmoji(emoji);
            return this;
        }

        /**
         * Sets the item data to be used with the {@link TreePage}.
         *
         * @param itemHandler The item data for the page.
         */
        public Builder withItemHandler(@NotNull ItemHandler<?> itemHandler) {
            this.itemHandler = itemHandler;
            return this;
        }

        /**
         * {@link SelectMenu.Option.Builder#withLabel(String)}
         */
        public Builder withLabel(@NotNull String label) {
            this.optionBuilder.withLabel(label);
            return this;
        }

        /**
         * {@link SelectMenu.Option.Builder#withLabel(String, Object...)}
         */
        public Builder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            this.optionBuilder.withLabel(label, args);
            return this;
        }

        /**
         * Sets the {@link SelectMenu.Option} that defines the {@link Page}.
         *
         * @param option The option to set.
         */
        public Builder withOption(@NotNull SelectMenu.Option option) {
            this.optionBuilder = SelectMenu.Option.from(option);
            return this;
        }

        /**
         * Sets the reactions to add to the {@link Page}.
         *
         * @param reactions The reactions to add to the response.
         */
        public Builder withReactions(@NotNull Emoji... reactions) {
            return this.withReactions(Arrays.asList(reactions));
        }

        /**
         * Sets the reactions to add to the {@link Page}.
         *
         * @param reactions The reactions to add to the response.
         */
        public Builder withReactions(@NotNull Iterable<Emoji> reactions) {
            reactions.forEach(this.reactions::add);
            return this;
        }

        /**
         * {@link SelectMenu.Option.Builder#withValue(String)}
         */
        public Builder withValue(@NotNull String value) {
            this.optionBuilder.withValue(value);
            return this;
        }

        /**
         * {@link SelectMenu.Option.Builder#withLabel(String, Object...)}
         */
        public Builder withValue(@PrintFormat @NotNull String value, @Nullable Object... args) {
            this.optionBuilder.withValue(value, args);
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link Page}.
         */
        public abstract @NotNull Page build();

    }

}
