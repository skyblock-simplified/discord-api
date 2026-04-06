package dev.sbs.discordapi.response.page;

import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.context.component.ButtonContext;
import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.ConcurrentList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * A read-only confirmation view rendered from all answered questions in a {@link Response}.
 *
 * <p>
 * Provides "Confirm" and "Go Back" buttons as a {@link LayoutComponent} for form submission
 * or returning to the last question page.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Summary {

    private final @NotNull ConcurrentList<FormPage> pages;
    private final @NotNull ActionRow actionRow;

    /**
     * Builds a summary from the given form pages with confirm and go-back buttons.
     *
     * @param pages the form pages containing answered questions
     * @param submitInteraction the interaction handler for the confirm button
     * @param backInteraction the interaction handler for the go back button
     * @return the built summary
     */
    public static @NotNull Summary of(
        @NotNull ConcurrentList<FormPage> pages,
        @NotNull Function<ButtonContext, Mono<Void>> submitInteraction,
        @NotNull Function<ButtonContext, Mono<Void>> backInteraction
    ) {
        return new Summary(
            pages,
            ActionRow.of(
                Button.builder()
                    .withStyle(Button.Style.SUCCESS)
                    .withLabel("Confirm")
                    .onInteract(submitInteraction)
                    .build(),
                Button.builder()
                    .withStyle(Button.Style.SECONDARY)
                    .withLabel("Go Back")
                    .onInteract(backInteraction)
                    .build()
            )
        );
    }

    /**
     * Builds a text representation of all answered questions across all form pages.
     *
     * @return the formatted summary text
     */
    public @NotNull String buildSummaryText() {
        StringBuilder sb = new StringBuilder();

        for (FormPage page : this.pages) {
            for (Question<?> question : page.getQuestions()) {
                sb.append("**").append(question.getTitle()).append("**: ");

                String answer = question.getAnswer()
                    .map(Object::toString)
                    .orElse("_Not answered_");

                sb.append(answer).append("\n");
            }
        }

        return sb.toString().trim();
    }

}
