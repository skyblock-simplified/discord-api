package dev.sbs.discordapi.debug.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.command.parameter.Parameter;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.layout.Label;
import dev.sbs.discordapi.context.command.SlashCommandContext;
import dev.sbs.discordapi.context.scope.MessageContext;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.response.Emoji;
import dev.sbs.discordapi.response.Response;
import dev.sbs.discordapi.response.embed.Embed;
import dev.sbs.discordapi.response.embed.Footer;
import dev.sbs.discordapi.response.page.Page;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.tuple.pair.Pair;
import dev.simplified.collection.unmodifiable.ConcurrentUnmodifiableList;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.time.Instant;

@Structure(
    parent = @Structure.Parent(
        name = "debug",
        description = "Debugging Commands"
    ),
    name = "components",
    guildId = 652148034448261150L,
    description = "Debug Components Handler",
    developerOnly = true
)
public class DebugComponentsCommand extends DiscordCommand<SlashCommandContext> {

    protected DebugComponentsCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public @NotNull ConcurrentUnmodifiableList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList(
            Parameter.builder()
                .withName("test1")
                .withDescription("test parameter")
                .withType(Parameter.Type.ATTACHMENT)
                //.withAutoComplete(value -> Mono.empty())
                .build(),
            Parameter.builder()
                .withName("test2")
                .withDescription("test parameter")
                .withType(Parameter.Type.WORD)
                .withAutoComplete(context -> Concurrent.newMap(
                    Pair.of("abc", "def")
                ))
                .build()
        );
    }

    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        return commandContext.reply(
            Response.builder()
                .withContext(commandContext)
                .withTimeToLive(30)
                /*.withAttachments(
                    Attachment.of(
                        "test.txt",
                        new ByteArrayInputStream("file upload test".getBytes())
                    )
                )*/
                .withPages(
                    Page.builder()
                        .withContent("test command")
                        /*.withReactions(Emoji.of("\uD83D\uDC80", reactionContext -> reactionContext.removeUserReaction()
                            .then(this.editPage(reactionContext, pageBuilder -> pageBuilder.withContent("reaction: " + reactionContext.getEmoji().asFormat()))))
                        )*/
                        .withEmbeds(
                            Embed.builder()
                                .withDescription("[Is this google?](https://google.com/)")
                                .build()
                        )
                        .withComponents(
                            ActionRow.of(
                                Button.builder()
                                    .withStyle(Button.Style.PRIMARY)
                                    .withEmoji(Emoji.of("\uD83C\uDF85"))
                                    .withLabel("Santa upload")
                                    .withDeferEdit()
                                    .onInteract(context -> context.edit(
                                        response -> response.mutate()
                                            .withAttachment(
                                                "test2.txt",
                                                new ByteArrayInputStream("santa test".getBytes())
                                            )
                                            .build()
                                    ))
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.SECONDARY)
                                    .withEmoji(Emoji.of("\uD83D\uDC31"))
                                    .withLabel("Cat")
                                    .withDeferEdit()
                                    //.onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent("cat!")))
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.SUCCESS)
                                    .withLabel("Open Modal!")
                                    .onInteract(buttonContext -> buttonContext.presentModal(
                                        Modal.builder()
                                            .withTitle("Test title")
                                            .withComponents(
                                                Label.builder()
                                                    .withTitle("Label")
                                                    .withComponent(
                                                        TextInput.builder()
                                                            .withStyle(TextInput.Style.SHORT)
                                                            .withIdentifier("something")
                                                            .withPlaceholder("placeholder")
                                                            .isRequired(false)
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            .onInteract(context -> context.edit(response -> response.editCurrentPage(builder -> builder.withContent("modal: " + context.getComponent()
                                                .findComponent(TextInput.class, TextInput::getIdentifier, "something")
                                                .flatMap(TextInput::getValue)
                                                .orElse("hurdur i'm a failure")
                                            ))))
                                            .build()
                                    ))
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.DANGER)
                                    .withLabel("Nothing!")
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.LINK)
                                    .withUrl("https://google.com/")
                                    .withLabel("Google")
                                    .build()
                            ),
                            ActionRow.of(
                                SelectMenu.builder()
                                    .withPlaceholder("Derpy menu")
                                    .withPlaceholderShowingSelectedOption()
                                    .withOptions(
                                        SelectMenu.Option.builder()
                                            .withLabel("Neigh")
                                            .withValue("value 1")
                                            .withEmoji(this.getEmoji("OTHER_HORSE_HEAD"))
                                            //.onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent(context.getOption().getValue())))
                                            .build(),
                                        SelectMenu.Option.builder()
                                            .withLabel("Buni")
                                            .withValue("value 2")
                                            .withDescription("Looking for ores!")
                                            .withEmoji(Emoji.of(769279331875946506L, "Buni", true))
                                            //.onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent(context.getOption().getValue())))
                                            .build(),
                                        SelectMenu.Option.builder()
                                            .withLabel("Yes sir!")
                                            .withValue("value 3")
                                            .withEmoji(Emoji.of(837805777187241985L, "linasalute"))
                                            //.onInteract(context -> this.editPage(context, pageBuilder -> pageBuilder.withContent(context.getOption().getValue())))
                                            .build(),
                                        SelectMenu.Option.builder()
                                            .withLabel("I do nothing :)")
                                            .withValue("value 4")
                                            //.withEmoji(Emoji.of(851662312925954068L, "goosewalk", true))
                                            .build()
                                    )
                                    .build()
                            ),
                            ActionRow.of(
                                Button.builder()
                                    .withStyle(Button.Style.PRIMARY)
                                    .withEmoji(Emoji.of(480079705077186560L, "chiefhmm"))
                                    .withLabel("Create Followup")
                                    .onInteract(context -> context.followup(
                                        "followuptest",
                                        Response.builder()
                                            .withPages(
                                                Page.builder()
                                                    .withContent("Followup")
                                                    .withEmbeds(
                                                        Embed.builder()
                                                            .withDescription("Hmm")
                                                            .withFooter(
                                                                Footer.builder()
                                                                    //.withText("Herro")
                                                                    .withTimestamp(Instant.now())
                                                                    .build()
                                                            )
                                                            .build()
                                                    )
                                                    .withComponents(ActionRow.of(
                                                        Button.builder()
                                                            .withStyle(Button.Style.SECONDARY)
                                                            .withEmoji(Emoji.of(769266796057985044L, "sip"))
                                                            .withLabel("FEdit")
                                                            .onInteract(context2 -> context2.editFollowup(
                                                                followup -> followup.getResponse()
                                                                    .editCurrentPage(builder -> builder.withContent("Followup Edit"))
                                                                )
                                                            )
                                                            .build(),
                                                        Button.builder()
                                                            .withStyle(Button.Style.DANGER)
                                                            .withEmoji(Emoji.of(769266796057985044L, "sip"))
                                                            .withLabel("FDelete")
                                                            .onInteract(MessageContext::deleteFollowup)
                                                            .build()
                                                    ))
                                                    .build()
                                            )
                                            .build()
                                    )) // Create Followup ^^
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.SECONDARY)
                                    .withEmoji(Emoji.of(769266796057985044L, "sip"))
                                    .withLabel("FEdit 2")
                                    .onInteract(context -> context.editFollowup(
                                        "followuptest",
                                        followup -> followup.getResponse()
                                            .editCurrentPage(builder -> builder.withContent("Followup Edit 2"))
                                    ))
                                    .build(),
                                Button.builder()
                                    .withStyle(Button.Style.DANGER)
                                    .withEmoji(Emoji.of(769266796057985044L, "sip"))
                                    .withLabel("FDelete 2")
                                    .onInteract(context -> context.deleteFollowup("followuptest"))
                                    .build()
                            )
                        )
                        .build()
                )
                .build()
            );
    }

}
