package dev.sbs.discordapi.response.page.editor;

import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.page.editor.field.AggregateField;
import dev.sbs.discordapi.response.page.editor.field.FieldKind;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ensures the {@link Response.Builder} build-time guard rejects an attempt to build a
 * persistent response whose pages include an {@link EditorPage}.
 */
class PersistentEditorRejectedTest {

    private record User(String name) {
        User withName(String name) {
            return new User(name);
        }
    }

    @Test
    void persistentEditorResponseRejectedAtBuildTime() {
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

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> Response.builder()
                .withPages(page)
                .isPersistent(true)
                .build()
        );

        assertEquals("Persistent responses are not yet supported for EditorPage", thrown.getMessage());
    }

}
