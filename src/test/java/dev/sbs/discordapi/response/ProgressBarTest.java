package dev.sbs.discordapi.response;

import dev.sbs.discordapi.util.ProgressBar;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProgressBarTest {

    private static ConcurrentList<Emoji> emojis;

    @BeforeAll
    static void buildEmojiList() {
        AtomicLong idSeq = new AtomicLong(1);

        // Empty variants (HF/F/C duplicates + originals)
        Stream<String> emptyNames = Stream.of(
            "EMPTY_PB_1_HF", "EMPTY_PB_1_F", "EMPTY_PB_1_C",
            "EMPTY_PB_2_HF", "EMPTY_PB_2_F", "EMPTY_PB_2_C",
            "EMPTY_PB_3_HF", "EMPTY_PB_3_F"
        );

        // Colored variants for each color
        Stream<String> coloredNames = Stream.of("BLUE", "EMPTY", "GRAY", "GREEN", "RED")
            .flatMap(color -> Stream.of(
                "%s_PB_1_HF", "%s_PB_1_F", "%s_PB_1_C",
                "%s_PB_2_HF", "%s_PB_2_F", "%s_PB_2_C",
                "%s_PB_3_HF", "%s_PB_3_F"
            ).map(fmt -> String.format(fmt, color)));

        emojis = Stream.concat(emptyNames, coloredNames)
            .distinct()
            .map(name -> Emoji.of(idSeq.getAndIncrement(), name))
            .collect(Concurrent.toList());
    }

    @Test
    void atZeroPercent_allEmpty() {
        ConcurrentList<Emoji> bar = ProgressBar.BLUE.render(0, emojis);
        List<String> names = bar.stream().map(Emoji::getName).toList();

        assertEquals(List.of(
            "EMPTY_PB_1_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_3_HF"
        ), names);
    }

    @Test
    void atTenPercent_firstHalfFilled() {
        ConcurrentList<Emoji> bar = ProgressBar.BLUE.render(10, emojis);
        List<String> names = bar.stream().map(Emoji::getName).toList();

        assertEquals(List.of(
            "BLUE_PB_1_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_3_HF"
        ), names);
    }

    @Test
    void atTwentyPercent_firstFilled() {
        ConcurrentList<Emoji> bar = ProgressBar.BLUE.render(20, emojis);
        List<String> names = bar.stream().map(Emoji::getName).toList();

        assertEquals(List.of(
            "BLUE_PB_1_F",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_3_HF"
        ), names);
    }

    @Test
    void atThirtyPercent_firstComplete_secondHalfFilled() {
        ConcurrentList<Emoji> bar = ProgressBar.BLUE.render(30, emojis);
        List<String> names = bar.stream().map(Emoji::getName).toList();

        assertEquals(List.of(
            "BLUE_PB_1_C",
            "BLUE_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_3_HF"
        ), names);
    }

    @Test
    void atFiftyPercent_halfFilled() {
        ConcurrentList<Emoji> bar = ProgressBar.BLUE.render(50, emojis);
        List<String> names = bar.stream().map(Emoji::getName).toList();

        assertEquals(List.of(
            "BLUE_PB_1_C",
            "BLUE_PB_2_C",
            "BLUE_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_3_HF"
        ), names);
    }

    @Test
    void atHundredPercent_allFilled() {
        ConcurrentList<Emoji> bar = ProgressBar.BLUE.render(100, emojis);
        List<String> names = bar.stream().map(Emoji::getName).toList();

        assertEquals(List.of(
            "BLUE_PB_1_C",
            "BLUE_PB_2_C",
            "BLUE_PB_2_C",
            "BLUE_PB_2_C",
            "BLUE_PB_3_F"
        ), names);
    }

    @Test
    void atNinetyPercent_lastHalfFilled() {
        ConcurrentList<Emoji> bar = ProgressBar.BLUE.render(90, emojis);
        List<String> names = bar.stream().map(Emoji::getName).toList();

        assertEquals(List.of(
            "BLUE_PB_1_C",
            "BLUE_PB_2_C",
            "BLUE_PB_2_C",
            "BLUE_PB_2_C",
            "BLUE_PB_3_HF"
        ), names);
    }

    @Test
    void emptyColor_alwaysEmpty() {
        ConcurrentList<Emoji> bar = ProgressBar.EMPTY.render(50, emojis);
        List<String> names = bar.stream().map(Emoji::getName).toList();

        assertEquals(List.of(
            "EMPTY_PB_1_C",
            "EMPTY_PB_2_C",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_2_HF",
            "EMPTY_PB_3_HF"
        ), names);
    }

    @Test
    void negativePercentage_clampsToZero() {
        ConcurrentList<Emoji> bar = ProgressBar.BLUE.render(-10, emojis);
        ConcurrentList<Emoji> barZero = ProgressBar.BLUE.render(0, emojis);

        List<String> names = bar.stream().map(Emoji::getName).toList();
        List<String> namesZero = barZero.stream().map(Emoji::getName).toList();

        assertEquals(namesZero, names);
    }

    @Test
    void overHundredPercent_clampsToHundred() {
        ConcurrentList<Emoji> bar = ProgressBar.BLUE.render(200, emojis);
        ConcurrentList<Emoji> barMax = ProgressBar.BLUE.render(100, emojis);

        List<String> names = bar.stream().map(Emoji::getName).toList();
        List<String> namesMax = barMax.stream().map(Emoji::getName).toList();

        assertEquals(namesMax, names);
    }

    @Test
    void allColors_renderWithoutError() {
        for (ProgressBar color : ProgressBar.values()) {
            for (int pct = 0; pct <= 100; pct += 10) {
                ConcurrentList<Emoji> bar = color.render(pct, emojis);
                assertEquals(5, bar.size(), "%s at %d%%".formatted(color, pct));
            }
        }
    }

}
