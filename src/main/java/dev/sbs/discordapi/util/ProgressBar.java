package dev.sbs.discordapi.util;

import dev.sbs.discordapi.response.Emoji;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import org.jetbrains.annotations.NotNull;

import java.util.stream.IntStream;

/**
 * Progress bar color variants that render as a sequence of five Discord
 * emojis based on a given completion percentage.
 */
public enum ProgressBar {

    BLUE,
    EMPTY,
    GRAY,
    GREEN,
    RED;

    private static final int[] PIECE = { 1, 2, 2, 2, 3 };

    /**
     * Renders a five-segment progress bar for the given percentage by
     * looking up the appropriate emojis from the provided list.
     *
     * @param percentage the completion percentage (0-100)
     * @param emojis the cached application emoji list
     * @return an unmodifiable list of five emojis representing the progress bar
     */
    public @NotNull ConcurrentList<Emoji> render(double percentage, @NotNull ConcurrentList<Emoji> emojis) {
        int step = Math.clamp((int) (percentage / 10), 0, 10);

        return IntStream.range(0, 5)
            .mapToObj(position -> resolveEmoji(step, position, emojis))
            .collect(Concurrent.toUnmodifiableList());
    }

    private @NotNull Emoji resolveEmoji(int step, int position, @NotNull ConcurrentList<Emoji> emojis) {
        int piece = PIECE[position];
        int startStep = 2 * position + 1;
        String name;

        if (step < startStep)
            name = String.format("EMPTY_PB_%d_HF", piece);
        else if (step == startStep)
            name = String.format("%s_PB_%d_HF", this.name(), piece);
        else if (step == startStep + 1)
            name = String.format("%s_PB_%d_F", this.name(), piece);
        else
            name = String.format("%s_PB_%d_C", this.name(), piece);

        String emojiName = name;
        return emojis.stream()
            .filter(emoji -> emoji.getName().equalsIgnoreCase(emojiName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Emoji not found: '%s'".formatted(emojiName)));
    }

}
