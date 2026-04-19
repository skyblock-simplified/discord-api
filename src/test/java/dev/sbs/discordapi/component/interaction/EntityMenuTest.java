package dev.sbs.discordapi.component.interaction;

import discord4j.common.util.Snowflake;
import discord4j.core.object.component.MessageComponent;
import discord4j.core.object.entity.channel.Channel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link SelectMenu.EntityMenu} builder, default-value validation, channel-type
 * guard, and wire-value parsing on submit.
 */
class EntityMenuTest {

    @Test
    void userMenuBuildsAsSelectMenuUser() {
        SelectMenu.EntityMenu menu = SelectMenu.entity(SelectMenu.Type.USER)
            .withIdentifier("test-user")
            .withMinValues(1)
            .withMaxValues(3)
            .build();

        assertEquals(SelectMenu.Type.USER, menu.getMenuType());
        assertEquals(MessageComponent.Type.SELECT_MENU_USER, menu.getD4jComponent().getType());
    }

    @Test
    void roleMenuBuildsAsSelectMenuRole() {
        SelectMenu.EntityMenu menu = SelectMenu.entity(SelectMenu.Type.ROLE).build();
        assertEquals(MessageComponent.Type.SELECT_MENU_ROLE, menu.getD4jComponent().getType());
    }

    @Test
    void mentionableMenuBuildsAsSelectMenuMentionable() {
        SelectMenu.EntityMenu menu = SelectMenu.entity(SelectMenu.Type.MENTIONABLE).build();
        assertEquals(MessageComponent.Type.SELECT_MENU_MENTIONABLE, menu.getD4jComponent().getType());
    }

    @Test
    void channelMenuBuildsAsSelectMenuChannel() {
        SelectMenu.EntityMenu menu = SelectMenu.entity(SelectMenu.Type.CHANNEL).build();
        assertEquals(MessageComponent.Type.SELECT_MENU_CHANNEL, menu.getD4jComponent().getType());
    }

    @Test
    void stringTypeRejectedByEntityFactory() {
        assertThrows(IllegalArgumentException.class, () -> SelectMenu.entity(SelectMenu.Type.STRING));
    }

    @Test
    void userMenuRejectsRoleDefault() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> SelectMenu.entity(SelectMenu.Type.USER)
            .withDefaultValues(SelectMenu.DefaultValue.ofRole(Snowflake.of(1L)))
            .build());

        assertTrue(ex.getMessage().contains("ROLE"));
    }

    @Test
    void mentionableMenuAcceptsUserAndRoleDefaults() {
        SelectMenu.EntityMenu menu = SelectMenu.entity(SelectMenu.Type.MENTIONABLE)
            .withDefaultValues(
                SelectMenu.DefaultValue.ofUser(Snowflake.of(1L)),
                SelectMenu.DefaultValue.ofRole(Snowflake.of(2L))
            )
            .build();

        assertEquals(2, menu.getDefaultValues().size());
    }

    @Test
    void mentionableMenuRejectsChannelDefault() {
        assertThrows(IllegalStateException.class, () -> SelectMenu.entity(SelectMenu.Type.MENTIONABLE)
            .withDefaultValues(SelectMenu.DefaultValue.ofChannel(Snowflake.of(1L)))
            .build());
    }

    @Test
    void channelMenuRoundTripsAllowedChannelTypes() {
        SelectMenu.EntityMenu menu = SelectMenu.entity(SelectMenu.Type.CHANNEL)
            .withAllowedChannelTypes(Channel.Type.GUILD_TEXT, Channel.Type.GUILD_VOICE)
            .build();

        assertTrue(menu.getAllowedChannelTypes().contains(Channel.Type.GUILD_TEXT));
        assertTrue(menu.getAllowedChannelTypes().contains(Channel.Type.GUILD_VOICE));
        assertTrue(menu.getD4jComponent().getAllowedChannelTypes().contains(Channel.Type.GUILD_TEXT));
    }

    @Test
    void userMenuRejectsAllowedChannelTypes() {
        assertThrows(IllegalStateException.class, () -> SelectMenu.entity(SelectMenu.Type.USER)
            .withAllowedChannelTypes(Channel.Type.GUILD_TEXT)
            .build());
    }

    @Test
    void updateSelectedParsesSnowflakes() {
        SelectMenu.EntityMenu menu = SelectMenu.entity(SelectMenu.Type.USER).build();
        menu.updateSelected(List.of("12345", "67890"));

        assertEquals(2, menu.getSelectedSnowflakes().size());
        assertEquals(12345L, menu.getSelectedSnowflakes().getFirst().asLong());
        assertEquals(67890L, menu.getSelectedSnowflakes().getLast().asLong());
        assertEquals(List.of("12345", "67890"), menu.getSelectedValues());
    }

    @Test
    void defaultValueD4jConversionPreservesKind() {
        SelectMenu.DefaultValue user = SelectMenu.DefaultValue.ofUser(Snowflake.of(1L));
        assertEquals(discord4j.core.object.component.SelectMenu.DefaultValue.Type.USER, user.getD4jDefaultValue().getType());

        SelectMenu.DefaultValue role = SelectMenu.DefaultValue.ofRole(Snowflake.of(2L));
        assertEquals(discord4j.core.object.component.SelectMenu.DefaultValue.Type.ROLE, role.getD4jDefaultValue().getType());

        SelectMenu.DefaultValue channel = SelectMenu.DefaultValue.ofChannel(Snowflake.of(3L));
        assertEquals(discord4j.core.object.component.SelectMenu.DefaultValue.Type.CHANNEL, channel.getD4jDefaultValue().getType());
    }

    @Test
    void typeIsEntityExcludesString() {
        assertFalse(SelectMenu.Type.STRING.isEntity());
        assertTrue(SelectMenu.Type.USER.isEntity());
        assertTrue(SelectMenu.Type.ROLE.isEntity());
        assertTrue(SelectMenu.Type.MENTIONABLE.isEntity());
        assertTrue(SelectMenu.Type.CHANNEL.isEntity());
    }

    @Test
    void stringMenuFactoryReturnsStringMenu() {
        SelectMenu.StringMenu menu = SelectMenu.builder()
            .withOptions(SelectMenu.Option.builder().withLabel("A").withValue("a").build())
            .build();

        assertEquals(SelectMenu.Type.STRING, menu.getMenuType());
        assertEquals(1, menu.getOptions().size());
    }

}
