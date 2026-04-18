package dev.sbs.discordapi.response.page.editor;

import dev.sbs.discordapi.response.page.editor.field.Choice;
import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;

/**
 * A snapshot of the in-page select menu paging state used when editing a
 * {@link dev.sbs.discordapi.response.page.editor.field.FieldKind.Choice} field whose option
 * count exceeds Discord's 25-option per-menu cap.
 *
 * <p>
 * The cached choices are fetched once at session start so that Prev/Next navigation sees a
 * consistent option list even if the underlying source mutates mid-session.
 *
 * @param fieldId the identifier of the field being edited
 * @param sliceIndex the zero-based slice (each slice covers 25 options)
 * @param cachedChoices the full option list captured at session start
 */
public record InPageEditSession(
    @NotNull String fieldId,
    int sliceIndex,
    @NotNull ConcurrentList<Choice<?>> cachedChoices
) { }
