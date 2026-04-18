package dev.sbs.discordapi.response.page.editor;

import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.layout.Container;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.response.page.editor.field.AggregateField;
import dev.sbs.discordapi.response.page.editor.field.Choice;
import dev.sbs.discordapi.response.page.editor.field.FieldKind;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the in-page escalation flow for a {@link FieldKind.Choice} field whose option
 * count exceeds Discord's 25-option modal limit. Tests navigate through state transitions
 * without invoking real Discord gateway events.
 */
class EditorPageEscalationTest {

    private record Record(String picked) {
        Record withPicked(String picked) {
            return new Record(picked);
        }
    }

    private static AggregateField<Record, String> buildChoiceField(int total) {
        ConcurrentList<Choice<String>> choices = Concurrent.newList();
        for (int i = 0; i < total; i++)
            choices.add(Choice.of("Option " + i, "v" + i));

        return AggregateField.<Record, String>builder()
            .withIdentifier("big")
            .withLabel("Big List")
            .withKind(new FieldKind.Choice<>(choices.toUnmodifiableList(), false))
            .withGetter(Record::picked)
            .withLiveSaver((record, edit) -> Mono.just(record.withPicked(edit.newValue())))
            .build();
    }

    @Test
    void largeChoiceFieldTriggersEscalationRowsWhenSessionActive() {
        Record initial = new Record("v0");
        AggregateField<Record, String> field = buildChoiceField(30);

        EditorPage.Aggregate.AggregateBuilder<Record> builder = EditorPage.Aggregate.builder(initial)
            .withHeader("Pick something")
            .withField(field);
        builder.withLabel("Big picker");
        builder.withValue("big-picker");
        EditorPage.Aggregate<Record> page = builder.build();

        ConcurrentList<LayoutComponent> before = page.getCachedLayoutComponents();
        long actionRowsBefore = before.stream().filter(ActionRow.class::isInstance).count();
        assertEquals(0, actionRowsBefore, "no escalation rows when no session");

        page.withInPageSession(new InPageEditSession(
            field.identifier(),
            0,
            Concurrent.newUnmodifiableList(field.kind() instanceof FieldKind.Choice<?> c
                ? c.choices().stream().map(x -> (Choice<?>) x).toList()
                : java.util.List.of())
        ));

        ConcurrentList<LayoutComponent> rendered = page.getCachedLayoutComponents();
        assertInstanceOf(Container.class, rendered.get(0));

        long actionRows = rendered.stream().filter(ActionRow.class::isInstance).count();
        assertEquals(2, actionRows, "expected two action rows: SelectMenu row + nav row");

        ActionRow selectRow = (ActionRow) rendered.stream()
            .filter(ActionRow.class::isInstance)
            .findFirst()
            .orElseThrow();
        assertTrue(
            selectRow.getComponents().stream().anyMatch(SelectMenu.class::isInstance),
            "first escalation row should carry the slice SelectMenu"
        );

        ActionRow navRow = (ActionRow) rendered.stream()
            .filter(ActionRow.class::isInstance)
            .skip(1)
            .findFirst()
            .orElseThrow();
        long navButtonCount = navRow.getComponents().stream().filter(Button.class::isInstance).count();
        assertEquals(3, navButtonCount, "nav row should carry Prev, Next, Cancel buttons");
    }

    @Test
    void escalationClearsSessionWhenCleared() {
        Record initial = new Record("v0");
        AggregateField<Record, String> field = buildChoiceField(30);

        EditorPage.Aggregate.AggregateBuilder<Record> builder = EditorPage.Aggregate.builder(initial)
            .withHeader("Pick something")
            .withField(field);
        builder.withLabel("Big picker");
        builder.withValue("big-picker");
        EditorPage.Aggregate<Record> page = builder.build();

        ConcurrentList<Choice<?>> snap = Concurrent.newList();
        ((FieldKind.Choice<?>) field.kind()).choices().forEach(c -> snap.add(c));
        page.withInPageSession(new InPageEditSession(field.identifier(), 1, snap.toUnmodifiableList()));

        assertTrue(page.getInPageSession().isPresent(), "precondition: session active");

        page.clearInPageSession();
        assertFalse(page.getInPageSession().isPresent(), "clearInPageSession should clear the session");
    }

}
