package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La musica compartida de cada dimension: pedazos en orden, silencio entre canciones y nada de repetir seguido. */
class DimensionMusicTest {

    private static final class Fixed extends Random {
        private final int value;

        Fixed(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return Math.min(value, bound - 1);
        }
    }

    private static final List<DimensionMusic.Track> TWO = List.of(
        new DimensionMusic.Track("a", 3, 100),
        new DimensionMusic.Track("b", 1, 50));

    /** Corre la musica tick a tick y anota en que tick sono cada pedazo. */
    private static List<String> run(DimensionMusic music, long from, long to, Random random) {
        List<String> out = new ArrayList<>();
        for (long t = from; t <= to; t++) {
            String event = music.tick(t, random);
            if (event != null) {
                out.add(t + " " + event);
            }
        }
        return out;
    }

    @Test
    void theFirstSongStartsSoonThenItsPiecesFollowEveryFifteenSeconds() {
        DimensionMusic music = new DimensionMusic(TWO);
        int seg = DimensionMusic.SEGMENT_TICKS;
        int first = DimensionMusic.FIRST_MIN_TICKS;
        List<String> played = run(music, 0, first + 2 * seg + 99, new Fixed(0));
        assertEquals(List.of(
            first + " caichet:music.a.1",
            (first + seg) + " caichet:music.a.2",
            (first + 2 * seg) + " caichet:music.a.3"), played);
    }

    @Test
    void afterASongThereIsSilenceAndThenADifferentOne() {
        DimensionMusic music = new DimensionMusic(TWO);
        int seg = DimensionMusic.SEGMENT_TICKS;
        long end = DimensionMusic.FIRST_MIN_TICKS + 2L * seg + 100; // termina el ultimo pedazo de "a"
        run(music, 0, end - 1, new Fixed(0));
        assertNull(music.tick(end, new Fixed(0)), "termina la cancion: silencio");
        assertNull(music.playing());
        long next = end + DimensionMusic.GAP_MIN_TICKS;
        assertNull(music.tick(next - 1, new Fixed(0)));
        assertEquals("caichet:music.b.1", music.tick(next, new Fixed(0)), "no repite la que acaba de sonar");
    }

    @Test
    void whenEveryoneLeavesItStopsAndStartsAgainWhenSomeoneArrives() {
        DimensionMusic music = new DimensionMusic(TWO);
        run(music, 0, DimensionMusic.FIRST_MIN_TICKS, new Fixed(0));
        assertEquals("a", music.playing().name());
        music.reset();
        assertNull(music.playing());
        assertNull(music.tick(10_000, new Fixed(0)), "al volver solo agenda");
        String event = music.tick(10_000 + DimensionMusic.FIRST_MIN_TICKS, new Fixed(0));
        assertEquals("caichet:music.b.1", event, "y no arranca con la que se corto");
    }

    @Test
    void theRealSongsMatchTheirSoundsAndAreNeverRepeatedBackToBack() {
        for (List<DimensionMusic.Track> tracks : List.of(CaiChetMusic.HOTEL_TRACKS, CaiChetMusic.DREAM_TRACKS)) {
            assertEquals(3, tracks.size());
            DimensionMusic music = new DimensionMusic(tracks);
            Random random = new Random(7);
            String previous = null;
            for (long t = 0; t < 20 * 60 * 60; t++) {
                String event = music.tick(t, random);
                if (event != null && event.endsWith(".1")) {
                    assertNotEquals(previous, event, "la misma cancion dos veces seguidas");
                    previous = event;
                }
            }
            for (DimensionMusic.Track track : tracks) {
                assertTrue(track.lastTicks() > 0 && track.lastTicks() <= DimensionMusic.SEGMENT_TICKS, track.name());
            }
        }
        assertTrue(CaiChetSounds.ALL.containsAll(CaiChetMusic.events()));
        assertEquals(4 + 15 + 14 + 13 + 5 + 5, CaiChetMusic.events().size());
    }
}
