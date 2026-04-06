package dev.sbs.discordapi.response.page;

import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.handler.HistoryHandler;
import dev.sbs.discordapi.response.handler.item.ItemHandler;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import dev.simplified.reflection.builder.BuildFlag;
import dev.simplified.util.StringUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FormPage implements Page {

    private final @NotNull SelectMenu.Option option;
    private final @NotNull ConcurrentList<LayoutComponent> components;
    private final @NotNull ConcurrentList<Emoji> reactions;
    private final @NotNull ItemHandler<Question<?>> itemHandler;
    private final @NotNull HistoryHandler<Question<?>, String> historyHandler;

    // Form
    private final @NotNull String header;
    private final @NotNull Optional<String> details;

    public static @NotNull QuestionBuilder builder() {
        return new QuestionBuilder();
    }

    public @NotNull ConcurrentList<Question<?>> getQuestions() {
        return this.getItemHandler().getItems();
    }

    public static @NotNull QuestionBuilder from(@NotNull FormPage formPage) {
        return new QuestionBuilder()
            .withOption(formPage.getOption())
            .withComponents(formPage.getComponents())
            .withReactions(formPage.getReactions())
            .withQuestions(formPage.getQuestions())
            .withTitle(formPage.getHeader())
            .withDetails(formPage.getDetails());
    }

    public @NotNull QuestionBuilder mutate() {
        return from(this);
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class QuestionBuilder extends Builder {

        @BuildFlag(notEmpty = true)
        private final ConcurrentList<Question<?>> questions = Concurrent.newList();
        @BuildFlag(notEmpty = true)
        private Optional<String> title = Optional.empty();
        private Optional<String> details = Optional.empty();

        /**
         * Clear all but preservable components from {@link TreePage}.
         */
        @Override
        public QuestionBuilder disableComponents() {
            return this.disableComponents(false);
        }

        /**
         * Clear all but preservable components from {@link TreePage}.
         *
         * @param recursive True to recursively clear components.
         */
        @Override
        public QuestionBuilder disableComponents(boolean recursive) {
            super.disableComponents(recursive);
            return this;
        }

        @Override
        public QuestionBuilder clearReaction(@NotNull Emoji emoji) {
            super.clearReaction(emoji);
            return this;
        }

        @Override
        public QuestionBuilder clearReactions() {
            super.clearReactions();
            return this;
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link TreePage}.
         *
         * @param components Variable number of layout components to add.
         */
        @Override
        public QuestionBuilder withComponents(@NotNull LayoutComponent... components) {
            return this.withComponents(Arrays.asList(components));
        }

        /**
         * Add {@link LayoutComponent LayoutComponents} to the {@link TreePage}.
         *
         * @param components Collection of layout components to add.
         */
        @Override
        public QuestionBuilder withComponents(@NotNull Iterable<LayoutComponent> components) {
            super.withComponents(components);
            return this;
        }

        @Override
        public QuestionBuilder withDescription(@Nullable String description) {
            return this.withDescription(Optional.ofNullable(description));
        }

        @Override
        public QuestionBuilder withDescription(@PrintFormat @Nullable String description, @Nullable Object... args) {
            return this.withDescription(StringUtil.formatNullable(description, args));
        }

        @Override
        public QuestionBuilder withDescription(@NotNull Optional<String> description) {
            super.withDescription(description);
            return this;
        }

        public QuestionBuilder withDetails(@Nullable String details) {
            return this.withDetails(Optional.ofNullable(details));
        }

        public QuestionBuilder withDetails(@PrintFormat @Nullable String details, @Nullable Object... args) {
            return this.withDetails(StringUtil.formatNullable(details, args));
        }

        public QuestionBuilder withDetails(@NotNull Optional<String> details) {
            this.details = details;
            return this;
        }

        @Override
        public QuestionBuilder withEmoji(@Nullable Emoji emoji) {
            return this.withEmoji(Optional.ofNullable(emoji));
        }

        @Override
        public QuestionBuilder withEmoji(@NotNull Optional<Emoji> emoji) {
            super.withEmoji(emoji);
            return this;
        }

        @Override
        public QuestionBuilder withLabel(@NotNull String label) {
            super.withLabel(label);
            return this;
        }

        @Override
        public QuestionBuilder withLabel(@PrintFormat @NotNull String label, @Nullable Object... args) {
            super.withLabel(label, args);
            return this;
        }

        @Override
        public QuestionBuilder withOption(@NotNull SelectMenu.Option option) {
            super.withOption(option);
            return this;
        }

        public <T> QuestionBuilder withQuestion(@NotNull Question<T> question) {
            return this.withQuestions(question);
        }

        public QuestionBuilder withQuestions(@NotNull Question<?>... questions) {
            return this.withQuestions(Arrays.asList(questions));
        }

        public QuestionBuilder withQuestions(@NotNull Iterable<Question<?>> questions) {
            questions.forEach(this.questions::add);
            return this;
        }

        /**
         * Sets the reactions to add to the {@link TreePage}.
         *
         * @param reactions The reactions to add to the response.
         */
        @Override
        public QuestionBuilder withReactions(@NotNull Emoji... reactions) {
            return this.withReactions(Arrays.asList(reactions));
        }

        /**
         * Sets the reactions to add to the {@link TreePage}.
         *
         * @param reactions The reactions to add to the response.
         */
        @Override
        public QuestionBuilder withReactions(@NotNull Iterable<Emoji> reactions) {
            super.withReactions(reactions);
            return this;
        }

        public QuestionBuilder withTitle(@Nullable String title) {
            return this.withTitle(Optional.ofNullable(title));
        }

        public QuestionBuilder withTitle(@PrintFormat @Nullable String title, @Nullable Object... args) {
            return this.withTitle(StringUtil.formatNullable(title, args));
        }

        public QuestionBuilder withTitle(@NotNull Optional<String> title) {
            this.title = title;
            return this;
        }

        @Override
        public QuestionBuilder withValue(@NotNull String value) {
            super.withLabel(value);
            return this;
        }

        @Override
        public QuestionBuilder withValue(@PrintFormat @NotNull String value, @Nullable Object... args) {
            super.withLabel(value, args);
            return this;
        }

        /**
         * Build using the configured fields.
         *
         * @return A built {@link FormPage}.
         */
        public @NotNull FormPage build() {
            Reflection.validateFlags(this);

            return new FormPage(
                this.optionBuilder.build(),
                this.components.toUnmodifiableList(),
                this.reactions.toUnmodifiableList(),
                ItemHandler.<Question<?>>embed()
                    .withItems(this.questions.toUnmodifiableList())
                    .withTransformer((question, index, size) -> question.getFieldItem())
                    .build(),
                HistoryHandler.<Question<?>, String>builder()
                    .withPages(this.questions.toUnmodifiableList())
                    .withMatcher((question, identifier) -> question.getIdentifier().equals(identifier))
                    .withTransformer(Question::getIdentifier)
                    .build(),
                this.title.orElseThrow(),
                this.details
            );
        }

    }

}
