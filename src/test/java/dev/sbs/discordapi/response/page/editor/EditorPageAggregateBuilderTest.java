package dev.sbs.discordapi.response.page.editor;

import dev.sbs.discordapi.component.TextDisplay;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.layout.Container;
import dev.sbs.discordapi.component.layout.Section;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.response.page.editor.field.AggregateField;
import dev.sbs.discordapi.response.page.editor.field.Choice;
import dev.sbs.discordapi.response.page.editor.field.FieldKind;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds an {@link EditorPage.Aggregate} over a record with a text field and a bounded-choice
 * field, then verifies the rendered layout carries a container with section edit buttons.
 */
class EditorPageAggregateBuilderTest {

    private record User(String name, Role role) {

        User withName(String name) {
            return new User(name, this.role);
        }

        User withRole(Role role) {
            return new User(this.name, role);
        }

    }

    private enum Role { ADMIN, USER }

    @Test
    void buildsContainerWithSectionsCarryingEditButtons() {
        User initial = new User("alice", Role.USER);

        AggregateField<User, String> nameField = AggregateField.<User, String>builder()
            .withIdentifier("name")
            .withLabel("Name")
            .withKind(new FieldKind.Text(
                dev.sbs.discordapi.component.interaction.TextInput.Style.SHORT,
                1,
                32,
                (Predicate<String>) s -> !s.isEmpty(),
                Optional.of("Enter a name")
            ))
            .withGetter(User::name)
            .withLiveSaver((user, edit) -> Mono.just(user.withName(edit.newValue())))
            .build();

        ConcurrentList<Choice<Role>> roleChoices = Concurrent.newUnmodifiableList(
            Choice.of("Admin", Role.ADMIN),
            Choice.of("User", Role.USER)
        );

        AggregateField<User, Role> roleField = AggregateField.<User, Role>builder()
            .withIdentifier("role")
            .withLabel("Role")
            .withKind(new FieldKind.Choice<>(roleChoices, false))
            .withGetter(User::role)
            .withLiveSaver((user, edit) -> Mono.just(user.withRole(edit.newValue())))
            .build();

        EditorPage.Aggregate.AggregateBuilder<User> builder = EditorPage.Aggregate.builder(initial)
            .withHeader("Profile")
            .withDetails("Edits save immediately.")
            .withField(nameField)
            .withField(roleField);
        builder.withLabel("Edit user");
        builder.withValue("edit-user");
        EditorPage.Aggregate<User> page = builder.build();

        assertNotNull(page);
        assertEquals("alice", page.getCurrentValue().name());
        assertEquals(Role.USER, page.getCurrentValue().role());

        ConcurrentList<LayoutComponent> rendered = page.getCachedLayoutComponents();
        assertTrue(rendered.notEmpty(), "rendered layout should not be empty");
        Container container = assertInstanceOf(Container.class, rendered.get(0));

        long sectionCount = container.getComponents()
            .stream()
            .filter(Section.class::isInstance)
            .count();
        assertEquals(2, sectionCount, "expected one section per field");

        container.getComponents()
            .stream()
            .filter(Section.class::isInstance)
            .map(Section.class::cast)
            .forEach(section -> assertInstanceOf(Button.class, section.getAccessory()));

        assertTrue(
            container.getComponents().stream().anyMatch(TextDisplay.class::isInstance),
            "container should carry a header text display"
        );
    }

    @Test
    void liveSaverUpdateMutatesCurrentValueAndMarksDirty() {
        User initial = new User("alice", Role.USER);

        AggregateField<User, String> nameField = AggregateField.<User, String>builder()
            .withIdentifier("name")
            .withLabel("Name")
            .withKind(new FieldKind.Text(
                dev.sbs.discordapi.component.interaction.TextInput.Style.SHORT,
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

        page.getCachedLayoutComponents();
        page.getItemHandler().setCacheUpdateRequired(false);
        assertFalse(page.getItemHandler().isCacheUpdateRequired(), "precondition: page not dirty");

        User updated = nameField.liveSaver().apply(page.getCurrentValue(), new dev.sbs.discordapi.response.page.editor.field.FieldEdit<>("name", "alice", "bob")).block();
        assertNotNull(updated);
        page.setCurrentValue(updated);

        assertEquals("bob", page.getCurrentValue().name());
        assertTrue(page.getItemHandler().isCacheUpdateRequired(), "setCurrentValue should mark the handler dirty");
    }

    @Test
    void mutateRoundTripPreservesConfiguredFields() {
        User initial = new User("alice", Role.USER);

        AggregateField<User, String> nameField = AggregateField.<User, String>builder()
            .withIdentifier("name")
            .withLabel("Name")
            .withKind(new FieldKind.Text(
                dev.sbs.discordapi.component.interaction.TextInput.Style.SHORT,
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
            .withDetails("Edits save immediately.")
            .showDirtyIndicator()
            .withField(nameField);
        builder.withLabel("Edit user");
        builder.withValue("edit-user");
        EditorPage.Aggregate<User> page = builder.build();

        EditorPage.Aggregate<User> copy = page.mutate().build();

        assertEquals(page.getHeader(), copy.getHeader());
        assertEquals(page.getDetails(), copy.getDetails());
        assertEquals(page.isShowDirtyIndicator(), copy.isShowDirtyIndicator());
        assertEquals(page.getOption().getValue(), copy.getOption().getValue());
        assertEquals(page.getOption().getLabel(), copy.getOption().getLabel());
        assertEquals(page.getItemHandler().getItems().size(), copy.getItemHandler().getItems().size());
        assertEquals("alice", copy.getCurrentValue().name());
    }

}
