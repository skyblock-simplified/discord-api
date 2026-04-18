package dev.sbs.discordapi.response.page.editor;

import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.layout.Container;
import dev.sbs.discordapi.component.scope.LayoutComponent;
import dev.sbs.discordapi.response.page.editor.field.BuilderField;
import dev.sbs.discordapi.response.page.editor.field.FieldKind;
import dev.simplified.collection.ConcurrentList;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds an {@link EditorPage.Builder} over a mutable seed holder and verifies the
 * builder-mode submit button is rendered and field appliers compose as expected.
 */
class EditorPageBuilderModeTest {

    /** Simple mutable-style record acting as a builder seed for test purposes. */
    private record SignupSeed(String email, Boolean tos) {

        SignupSeed withEmail(String email) {
            return new SignupSeed(email, this.tos);
        }

        SignupSeed withTos(Boolean tos) {
            return new SignupSeed(this.email, tos);
        }

    }

    @Test
    void submitButtonRenderedAndApplierMaterializesSeed() {
        SignupSeed seed = new SignupSeed("", Boolean.FALSE);

        BuilderField<SignupSeed, String> emailField = BuilderField.<SignupSeed, String>builder()
            .withIdentifier("email")
            .withLabel("Email")
            .isRequired()
            .withKind(new FieldKind.Text(
                TextInput.Style.SHORT,
                3,
                64,
                (Predicate<String>) s -> s.contains("@"),
                Optional.empty()
            ))
            .withGetter(SignupSeed::email)
            .withApplier(SignupSeed::withEmail)
            .build();

        BuilderField<SignupSeed, Boolean> tosField = BuilderField.<SignupSeed, Boolean>builder()
            .withIdentifier("tos")
            .withLabel("Accept Terms")
            .isRequired()
            .withKind(new FieldKind.Bool())
            .withGetter(SignupSeed::tos)
            .withApplier(SignupSeed::withTos)
            .build();

        EditorPage.Builder.EditorBuilder<SignupSeed> builder = EditorPage.Builder.builder(seed)
            .withHeader("Create account")
            .confirmSubmit()
            .withField(emailField)
            .withField(tosField)
            .onSubmit(dto -> Mono.empty());
        builder.withLabel("Sign up");
        builder.withValue("signup");
        EditorPage.Builder<SignupSeed> page = builder.build();

        assertNotNull(page);
        assertSame(seed, page.getSeed());
        assertTrue(page.isConfirmSubmit());

        SignupSeed applied = emailField.applier().apply(seed, "user@example.com");
        assertEquals("user@example.com", applied.email());
        assertFalse(applied.tos());

        ConcurrentList<LayoutComponent> rendered = page.getCachedLayoutComponents();
        assertInstanceOf(Container.class, rendered.get(0));

        ActionRow row = assertInstanceOf(ActionRow.class, rendered.get(rendered.size() - 1));
        boolean hasSubmitButton = row.getComponents()
            .stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .anyMatch(button -> button.getLabel().orElse("").equalsIgnoreCase("Submit"));
        assertTrue(hasSubmitButton, "builder-mode page should render a Submit button");
    }

    @Test
    void setSeedReplacesCurrentSeedAndMarksDirty() {
        SignupSeed seed = new SignupSeed("", Boolean.FALSE);

        BuilderField<SignupSeed, String> emailField = BuilderField.<SignupSeed, String>builder()
            .withIdentifier("email")
            .withLabel("Email")
            .withKind(new FieldKind.Text(
                TextInput.Style.SHORT,
                3,
                64,
                (Predicate<String>) s -> s.contains("@"),
                Optional.empty()
            ))
            .withGetter(SignupSeed::email)
            .withApplier(SignupSeed::withEmail)
            .build();

        EditorPage.Builder.EditorBuilder<SignupSeed> builder = EditorPage.Builder.builder(seed)
            .withHeader("Create account")
            .withField(emailField)
            .onSubmit(dto -> Mono.empty());
        builder.withLabel("Sign up");
        builder.withValue("signup");
        EditorPage.Builder<SignupSeed> page = builder.build();

        page.getCachedLayoutComponents();
        page.getItemHandler().setCacheUpdateRequired(false);

        SignupSeed applied = emailField.applier().apply(page.getSeed(), "user@example.com");
        page.setSeed(applied);

        assertEquals("user@example.com", page.getSeed().email());
        assertTrue(page.getItemHandler().isCacheUpdateRequired(), "setSeed should mark the item handler dirty");
    }

    @Test
    void mutateRoundTripPreservesConfiguredFields() {
        SignupSeed seed = new SignupSeed("hi@example.com", Boolean.TRUE);

        BuilderField<SignupSeed, String> emailField = BuilderField.<SignupSeed, String>builder()
            .withIdentifier("email")
            .withLabel("Email")
            .withKind(new FieldKind.Text(
                TextInput.Style.SHORT,
                3,
                64,
                (Predicate<String>) s -> s.contains("@"),
                Optional.empty()
            ))
            .withGetter(SignupSeed::email)
            .withApplier(SignupSeed::withEmail)
            .build();

        EditorPage.Builder.EditorBuilder<SignupSeed> builder = EditorPage.Builder.builder(seed)
            .withHeader("Create account")
            .withDetails("Fill out your email.")
            .confirmSubmit()
            .withField(emailField)
            .onSubmit(dto -> Mono.empty());
        builder.withLabel("Sign up");
        builder.withValue("signup");
        EditorPage.Builder<SignupSeed> page = builder.build();

        EditorPage.Builder<SignupSeed> copy = page.mutate().build();

        assertEquals(page.getHeader(), copy.getHeader());
        assertEquals(page.getDetails(), copy.getDetails());
        assertEquals(page.isConfirmSubmit(), copy.isConfirmSubmit());
        assertEquals(page.getOption().getValue(), copy.getOption().getValue());
        assertEquals(page.getItemHandler().getItems().size(), copy.getItemHandler().getItems().size());
    }

}
