package dev.sbs.discordapi.response.handler;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.interaction.CheckboxGroup;
import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.component.interaction.RadioGroup;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.layout.Label;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.component.scope.TopLevelMessageComponent;
import dev.sbs.discordapi.context.component.ButtonContext;
import dev.sbs.discordapi.context.component.ModalContext;
import dev.sbs.discordapi.context.component.SelectMenuContext;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.handler.ItemHandler;
import dev.sbs.discordapi.response.page.Page;
import dev.sbs.discordapi.util.DiscordReference;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.util.NumberUtil;
import dev.simplified.util.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

/**
 * Builds pagination components with emoji access and provides interaction handlers
 * for paginated responses.
 *
 * <p>
 * Centralizes the creation of navigation buttons, page/subpage select menus, and
 * sort/filter/search modals that were previously scattered across
 * {@link Button.PageType}, {@link SelectMenu.PageType}, and
 * {@link TextInput.SearchType} enums.
 *
 * @see Button.PageType
 * @see SelectMenu.PageType
 * @see TextInput.SearchType
 */
public class PaginationHandler extends DiscordReference {

    public PaginationHandler(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    // --- Button Interactions ---

    /**
     * Returns the interaction handler for the {@link Button.PageType#PREVIOUS} button.
     *
     * @return the previous-page interaction
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> previousPageInteraction() {
        return context -> context.consumeResponse(response -> response.getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .gotoPreviousPage()
        );
    }

    /**
     * Returns the interaction handler for the {@link Button.PageType#SORT} button.
     * Presents a modal with a {@link RadioGroup} showing available sorters.
     *
     * @return the sort modal interaction
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> sortInteraction() {
        return context -> context.withResponse(response -> {
            ItemHandler<?> itemHandler = response.getHistoryHandler().getCurrentPage().getItemHandler();
            SortHandler<?> sortHandler = itemHandler.getSortHandler();

            RadioGroup.Builder radioBuilder = RadioGroup.builder();
            sortHandler.getItems().forEach(sorter -> radioBuilder.withOptions(sorter.buildOption()));

            RadioGroup radioGroup = radioBuilder.build();
            sortHandler.getCurrent().ifPresent(current -> radioGroup.updateSelected(current.getIdentifier()));

            return context.presentModal(
                Modal.builder()
                    .withTitle("Sort")
                    .withComponents(Label.builder().withTitle("Sort By").withComponent(radioGroup).build())
                    .build()
            );
        });
    }

    /**
     * Presents a modal with page/index text inputs and custom search fields.
     *
     * @return the search modal interaction
     * @see Button.PageType#INDEX
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> searchInteraction() {
        return context -> context.withResponse(response -> {
            ItemHandler<?> itemHandler = response.getHistoryHandler().getCurrentPage().getItemHandler();

            Modal.Builder modalBuilder = Modal.builder()
                .withComponents(
                    buildPageSearchLabel(itemHandler),
                    buildIndexSearchLabel(itemHandler)
                )
                .withTitle("Search");

            itemHandler.getSearchHandler()
                .getItems()
                .stream()
                .map(search -> Label.builder()
                    .withTitle("Search")
                    .withComponent(search.getTextInput())
                    .build()
                )
                .forEach(modalBuilder::withComponents);

            return context.presentModal(modalBuilder.build());
        });
    }

    /**
     * Returns the interaction handler for the {@link Button.PageType#FILTER} button.
     * Presents a modal with a {@link CheckboxGroup} showing available filters.
     *
     * @return the filter modal interaction
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> filterInteraction() {
        return context -> context.withResponse(response -> {
            ItemHandler<?> itemHandler = response.getHistoryHandler().getCurrentPage().getItemHandler();
            FilterHandler<?> filterHandler = itemHandler.getFilterHandler();

            CheckboxGroup.Builder checkboxBuilder = CheckboxGroup.builder()
                .withMinValues(0)
                .withMaxValues(filterHandler.getItems().size());

            filterHandler.getItems().forEach(filter -> checkboxBuilder.withOptions(filter.buildOption()));

            CheckboxGroup checkboxGroup = checkboxBuilder.build();
            String[] enabledIds = filterHandler.getItems()
                .stream()
                .filter(Filter::isEnabled)
                .map(Filter::getIdentifier)
                .toArray(String[]::new);
            checkboxGroup.updateSelected(enabledIds);

            return context.presentModal(
                Modal.builder()
                    .withTitle("Filters")
                    .withComponents(Label.builder().withTitle("Active Filters").withComponent(checkboxGroup).build())
                    .build()
            );
        });
    }

    /**
     * Returns the interaction handler for the {@link Button.PageType#NEXT} button.
     *
     * @return the next-page interaction
     */
    public static @NotNull Function<ButtonContext, Mono<Void>> nextPageInteraction() {
        return context -> context.consumeResponse(response -> response.getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .gotoNextPage()
        );
    }

    // --- SelectMenu Interactions ---

    /**
     * Returns the interaction handler for the {@link SelectMenu.PageType#PAGE_SELECTOR} menu.
     *
     * @return the page selector interaction
     */
    public static @NotNull Function<SelectMenuContext, Mono<Void>> pageSelectionInteraction() {
        return context -> context.consumeResponse(response -> {
            String selectedValue = context.getSelected().getFirst().getValue();
            response.getHistoryHandler().gotoTopLevelPage(selectedValue);
        });
    }

    /**
     * Returns the interaction handler for the {@link SelectMenu.PageType#SUBPAGE_SELECTOR} menu.
     *
     * @return the subpage selector interaction
     */
    public static @NotNull Function<SelectMenuContext, Mono<Void>> subpageSelectionInteraction() {
        return context -> context.consumeResponse(response -> {
            String selectedValue = context.getSelected().getFirst().getValue();

            if (selectedValue.equals("BACK"))
                response.getHistoryHandler().gotoParentPage();
            else
                response.getHistoryHandler().gotoSubPage(selectedValue);
        });
    }

    // --- SearchType Interactions ---

    /**
     * Returns the interaction handler for page navigation via modal text input.
     *
     * @return the page navigation interaction
     */
    public static @NotNull Mono<Void> handlePageSearch(@NotNull ModalContext context, @NotNull TextInput textInput) {
        return context.consumeResponse(response -> {
            ItemHandler<?> itemHandler = context.getResponse().getHistoryHandler().getCurrentPage().getItemHandler();
            Range<Integer> pageRange = Range.between(1, itemHandler.getTotalPages());
            itemHandler.gotoPage(pageRange.fit(Integer.parseInt(textInput.getValue().orElseThrow())));
        });
    }

    /**
     * Returns the interaction handler for index navigation via modal text input.
     *
     * @return the index navigation interaction
     */
    public static @NotNull Mono<Void> handleIndexSearch(@NotNull ModalContext context, @NotNull TextInput textInput) {
        return context.consumeResponse(response -> {
            ItemHandler<?> itemHandler = context.getResponse().getHistoryHandler().getCurrentPage().getItemHandler();
            Range<Integer> indexRange = Range.between(0, itemHandler.getCachedFilteredItems().size());
            int index = indexRange.fit(Integer.parseInt(textInput.getValue().orElseThrow()));
            itemHandler.gotoPage((int) Math.ceil((double) index / itemHandler.getAmountPerPage()));
        });
    }

    /**
     * Returns the interaction handler for custom search via modal text input.
     *
     * @return the custom search interaction
     */
    public static @NotNull Mono<Void> handleCustomSearch(@NotNull ModalContext context, @NotNull TextInput textInput) {
        return context.consumeResponse(response -> context.getResponse()
            .getHistoryHandler()
            .getCurrentPage()
            .getItemHandler()
            .getSearchHandler()
            .search(textInput)
        );
    }

    // --- Component Builders ---

    /**
     * Builds a pagination button for the given page type without an emoji.
     *
     * @param pageType the page type identifier
     * @return the built button
     */
    public static @NotNull Button buildButton(@NotNull Button.PageType pageType) {
        return buildButton(pageType, Optional.empty());
    }

    /**
     * Builds a pagination button for the given page type, resolving an emoji by name.
     *
     * @param pageType the page type identifier
     * @param emojiName the emoji name to resolve, or null for no emoji
     * @return the built button
     */
    public @NotNull Button buildButton(@NotNull Button.PageType pageType, @Nullable String emojiName) {
        Optional<Emoji> emoji = emojiName != null ? this.getEmoji(emojiName) : Optional.empty();
        return buildButton(pageType, emoji);
    }

    /**
     * Builds a pagination button for the given page type with an optional emoji.
     *
     * @param pageType the page type identifier
     * @param emoji the optional emoji
     * @return the built button
     */
    public static @NotNull Button buildButton(@NotNull Button.PageType pageType, @NotNull Optional<Emoji> emoji) {
        return Button.builder()
            .withStyle(Button.Style.SECONDARY)
            .withEmoji(emoji)
            .withLabel(pageType.getLabel())
            .withPageType(pageType)
            .setDisabled(true)
            .onInteract(getButtonInteraction(pageType))
            .build();
    }

    /**
     * Builds all pagination buttons without emojis.
     *
     * @return the list of pagination buttons
     */
    public static @NotNull ConcurrentList<Button> buildPaginationButtons() {
        return Concurrent.newList(
            buildButton(Button.PageType.PREVIOUS),
            buildButton(Button.PageType.SORT),
            buildButton(Button.PageType.INDEX),
            buildButton(Button.PageType.FILTER),
            buildButton(Button.PageType.NEXT)
        );
    }

    /**
     * Builds all pagination buttons, resolving emojis by name.
     *
     * @param previousEmoji the previous button emoji name, or null
     * @param searchEmoji the search button emoji name, or null
     * @param filterEmoji the filter button emoji name, or null
     * @param nextEmoji the next button emoji name, or null
     * @return the list of pagination buttons with emojis
     */
    public @NotNull ConcurrentList<Button> buildPaginationButtons(@Nullable String previousEmoji, @Nullable String searchEmoji, @Nullable String filterEmoji, @Nullable String nextEmoji) {
        return Concurrent.newList(
            this.buildButton(Button.PageType.PREVIOUS, previousEmoji),
            this.buildButton(Button.PageType.SORT, searchEmoji),
            buildButton(Button.PageType.INDEX),
            this.buildButton(Button.PageType.FILTER, filterEmoji),
            this.buildButton(Button.PageType.NEXT, nextEmoji)
        );
    }

    /**
     * Builds a Label wrapping a TextInput for page navigation search.
     *
     * @param itemHandler the item handler
     * @return the label containing the page search text input
     */
    public static @NotNull Label buildPageSearchLabel(@NotNull ItemHandler<?> itemHandler) {
        return Label.builder()
            .withTitle("Go to Page")
            .withComponent(
                TextInput.builder()
                    .withStyle(TextInput.Style.SHORT)
                    .withSearchType(TextInput.SearchType.PAGE)
                    .withPlaceholder("Enter a number between 1 and %d.", itemHandler.getTotalPages())
                    .withValidator(value -> {
                        if (!NumberUtil.isCreatable(value))
                            return false;

                        Range<Integer> pageRange = Range.between(1, itemHandler.getTotalPages());
                        int page = NumberUtil.createInteger(value);
                        return pageRange.contains(page);
                    })
                    .build()
            )
            .build();
    }

    /**
     * Builds a Label wrapping a TextInput for index navigation search.
     *
     * @param itemHandler the item handler
     * @return the label containing the index search text input
     */
    public static @NotNull Label buildIndexSearchLabel(@NotNull ItemHandler<?> itemHandler) {
        return Label.builder()
            .withTitle("Go to Index")
            .withComponent(
                TextInput.builder()
                    .withStyle(TextInput.Style.SHORT)
                    .withSearchType(TextInput.SearchType.INDEX)
                    .withPlaceholder("Enter a number between 0 and %d.", itemHandler.getCachedFilteredItems().size())
                    .withValidator(value -> {
                        if (!NumberUtil.isCreatable(value))
                            return false;

                        Range<Integer> indexRange = Range.between(0, itemHandler.getCachedFilteredItems().size());
                        int index = NumberUtil.createInteger(value);
                        return indexRange.contains(index);
                    })
                    .build()
            )
            .build();
    }

    // --- Instance methods with emoji support ---

    /**
     * Builds all pagination buttons with emoji resolution from the bot.
     *
     * @return the list of pagination buttons with emojis
     */
    public @NotNull ConcurrentList<Button> buildPaginationButtonsWithEmoji() {
        return this.buildPaginationButtons("ARROW_LEFT", "SEARCH", "FILTER", "ARROW_RIGHT");
    }

    // --- Cached Page Components ---

    /**
     * Builds all pagination components for the given response, including page/subpage
     * select menus, item pagination buttons, editor menus, and button state updates.
     *
     * @param historyHandler the history handler to build components for
     * @return the built pagination components
     */
    public @NotNull ConcurrentList<TopLevelMessageComponent> buildCachedPageComponents(@NotNull HistoryHandler<? extends Page, String> historyHandler) {
        ConcurrentList<TopLevelMessageComponent> pageComponents = Concurrent.newList();
        Page currentPage = historyHandler.getCurrentPage();

        // Page List
        if (historyHandler.getItems().size() > 1 && !historyHandler.hasPageHistory()) {
            pageComponents.add(ActionRow.of(
                SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.PAGE_SELECTOR)
                    .withPlaceholder("Select a page.")
                    .withPlaceholderShowingSelectedOption()
                    .withOptions(
                        historyHandler.getItems()
                            .stream()
                            .map(Page::getOption)
                            .collect(Concurrent.toList())
                    )
                    .onInteract(pageSelectionInteraction())
                    .build()
                    .updateSelected(historyHandler.getIdentifierHistory().getFirst())
            ));
        }

        // SubPage List
        if (historyHandler.hasChildNavigation()) {
            HistoryHandler<?, String> pageHistory = currentPage.getHistoryHandler();

            if (pageHistory.getItems().notEmpty() || historyHandler.hasPageHistory()) {
                SelectMenu.Builder subPageBuilder = SelectMenu.builder()
                    .withPageType(SelectMenu.PageType.SUBPAGE_SELECTOR)
                    .withPlaceholder("Select a subpage.")
                    .withPlaceholderShowingSelectedOption()
                    .onInteract(subpageSelectionInteraction());

                if (historyHandler.hasPageHistory()) {
                    subPageBuilder.withOptions(
                        SelectMenu.Option.builder()
                            .withValue("BACK")
                            .withLabel("Back")
                            .withEmoji(this.getEmoji("ARROW_LEFT"))
                            .build()
                    );
                }

                HistoryHandler<?, String> subpageSource = pageHistory;

                if (pageHistory.getItems().isEmpty()) {
                    Optional<? extends Page> previousPage = historyHandler.getPreviousPage();

                    if (previousPage.isPresent())
                        subpageSource = previousPage.get().getHistoryHandler();
                }

                subPageBuilder.withOptions(
                    subpageSource.getItems()
                        .stream()
                        .filter(Page.class::isInstance)
                        .map(Page.class::cast)
                        .map(Page::getOption)
                        .collect(Concurrent.toList())
                );

                pageComponents.add(ActionRow.of(subPageBuilder.build()));
            }
        }

        if (currentPage.hasItems()) {
            // Item List
            pageComponents.add(ActionRow.of(this.buildPaginationButtonsWithEmoji()));
        }

        ConcurrentList<TopLevelMessageComponent> result = pageComponents.toUnmodifiable();

        // Button state updates
        editButton(result, Button::getPageType, Button.PageType.PREVIOUS, builder -> builder.setEnabled(currentPage.getItemHandler().hasPreviousItemPage()));
        editButton(result, Button::getPageType, Button.PageType.NEXT, builder -> builder.setEnabled(currentPage.getItemHandler().hasNextItemPage()));
        editButton(result, Button::getPageType, Button.PageType.SORT, builder -> builder.setEnabled(currentPage.getItemHandler().getSortHandler().notEmpty()));
        editButton(result, Button::getPageType, Button.PageType.FILTER, builder -> builder.setEnabled(currentPage.getItemHandler().getFilterHandler().notEmpty()));
        editButton(
            result,
            Button::getPageType,
            Button.PageType.INDEX,
            builder -> builder.withLabel(
                "%s / %s",
                currentPage.getItemHandler().getCurrentIndex(),
                currentPage.getItemHandler().getTotalPages()
            )
        );

        return result;
    }

    // --- Internal Helpers ---

    @SuppressWarnings("unchecked")
    private static <S> void editButton(@NotNull ConcurrentList<TopLevelMessageComponent> components, @NotNull Function<Button, S> function, S value, @NotNull Function<Button.Builder, Button.Builder> buttonBuilder) {
        components.forEach(topLevelComponent -> topLevelComponent.flattenComponents()
            .filter(LayoutComponent.class::isInstance)
            .map(LayoutComponent.class::cast)
            .forEach(layoutComponent -> layoutComponent.findComponent(Button.class, function, value)
                .ifPresent(button -> ((ConcurrentList<Component>) layoutComponent.getComponents()).set(
                    layoutComponent.getComponents().indexOf(button),
                    buttonBuilder.apply(button.mutate()).build()
                ))
            )
        );
    }

    private static @NotNull Function<ButtonContext, Mono<Void>> getButtonInteraction(@NotNull Button.PageType pageType) {
        return switch (pageType) {
            case PREVIOUS -> previousPageInteraction();
            case SORT -> sortInteraction();
            case INDEX -> searchInteraction();
            case FILTER -> filterInteraction();
            case NEXT -> nextPageInteraction();
            case NONE -> __ -> Mono.empty();
        };
    }

}
