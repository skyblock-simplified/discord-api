package dev.sbs.discordapi.response.page.editor;

import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.response.page.editor.field.AggregateField;
import dev.sbs.discordapi.response.page.editor.field.FieldKind;
import dev.simplified.collection.ConcurrentList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Aggregate-mode Delete flow renders an inline confirmation row when the
 * user commits to a delete action, without exercising the real Discord dispatch path.
 */
class EditorPageDeleteTest {

    private record User(String name) {
        User withName(String name) {
            return new User(name);
        }
    }

    @Test
    void deletePresentRendersDeleteButton() {
        User initial = new User("alice");

        AggregateField<User, String> nameField = AggregateField.<User, String>builder()
            .withIdentifier("name")
            .withLabel("Name")
            .withKind(new FieldKind.Text(
                TextInput.Style.SHORT,
                1,
                32,
                (Predicate<String>) s -> !s.isEmpty(),
                Optional.empty()
            ))
            .withGetter(User::name)
            .withLiveSaver((user, edit) -> Mono.just(user.withName(edit.newValue())))
            .build();

        EditorPage.Aggregate.AggregateBuilder<User> builder = EditorPage.Aggregate.builder(initial)
            .withHeader("Profile")
            .withField(nameField)
            .withDelete(user -> Mono.empty());
        builder.withLabel("Edit user");
        builder.withValue("edit-user");
        EditorPage.Aggregate<User> page = builder.build();

        ConcurrentList<LayoutComponent> rendered = page.getCachedLayoutComponents();
        ActionRow row = (ActionRow) rendered.stream()
            .filter(ActionRow.class::isInstance)
            .findFirst()
            .orElseThrow();

        boolean hasDelete = row.getComponents()
            .stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .anyMatch(button -> button.getLabel().orElse("").equalsIgnoreCase("Delete"));
        assertTrue(hasDelete, "aggregate page with onDelete should render a Delete button");
    }

    @Test
    void deleteAbsentRendersNoDeleteButtonWhenNoHandler() {
        User initial = new User("alice");

        AggregateField<User, String> nameField = AggregateField.<User, String>builder()
            .withIdentifier("name")
            .withLabel("Name")
            .withKind(new FieldKind.Text(
                TextInput.Style.SHORT,
                1,
                32,
                (Predicate<String>) s -> !s.isEmpty(),
                Optional.empty()
            ))
            .withGetter(User::name)
            .withLiveSaver((user, edit) -> Mono.just(user.withName(edit.newValue())))
            .build();

        EditorPage.Aggregate.AggregateBuilder<User> builder = EditorPage.Aggregate.builder(initial)
            .withHeader("Profile")
            .withField(nameField);
        builder.withLabel("Edit user");
        builder.withValue("edit-user");
        EditorPage.Aggregate<User> page = builder.build();

        ConcurrentList<LayoutComponent> rendered = page.getCachedLayoutComponents();
        long actionRowCount = rendered.stream().filter(ActionRow.class::isInstance).count();
        assertEquals(0, actionRowCount, "no cancel and no delete -> no action row should render");
    }

}
