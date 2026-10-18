package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SiftLandmarks.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftLandmarksTest {

    @Test
    void positionsPackLikeTheModsBlockPos() {
        for (SiftPos pos : List.of(new SiftPos(0, 0, 0), new SiftPos(-1, -64, -1), new SiftPos(1234, 319, -5678),
            new SiftPos(-30_000_000, 2047, 29_999_999), new SiftPos(33_554_431, -2048, -33_554_432))) {
            assertEquals(pos, SiftPos.of(pos.asLong()), pos.toString());
        }
        // BlockPos.asLong(1, 2, 3): x in the top 26 bits, z above the 12 bits of y.
        assertEquals(1L << 38 | 3L << 12 | 2L, new SiftPos(1, 2, 3).asLong());
    }

    @Test
    void recordingTwiceCountsOnceAndForgettingRemoves() {
        SiftLandmarks landmarks = new SiftLandmarks();
        SiftPos pos = new SiftPos(10, 20, 30);
        landmarks.record(Kind.SOUL_BLOCK, pos);
        landmarks.record(Kind.SOUL_BLOCK, pos);
        assertEquals(1, landmarks.count(Kind.SOUL_BLOCK));
        assertTrue(landmarks.contains(Kind.SOUL_BLOCK, pos));
        assertFalse(landmarks.contains(Kind.CANYON_ENTRANCE, pos), "kinds are separate");
        landmarks.forget(Kind.SOUL_BLOCK, pos);
        assertEquals(0, landmarks.count(Kind.SOUL_BLOCK));
        assertTrue(landmarks.soulBlocksNear(pos, 5, 5).isEmpty());
    }

    @Test
    void soulBlocksNearUsesTheHorizontalRadiusAndVerticalDistance() {
        SiftLandmarks landmarks = new SiftLandmarks();
        SiftPos centre = new SiftPos(100, 50, 100);
        SiftPos inside = new SiftPos(120, 60, 100);
        SiftPos tooFar = new SiftPos(137, 50, 100);
        SiftPos tooHigh = new SiftPos(100, 80, 100);
        SiftPos otherChunk = new SiftPos(80, 50, 120);
        for (SiftPos pos : List.of(inside, tooFar, tooHigh, otherChunk)) {
            landmarks.record(Kind.SOUL_BLOCK, pos);
        }
        List<SiftPos> near = landmarks.soulBlocksNear(centre, 36, 26);
        assertEquals(Set.of(inside, otherChunk), Set.copyOf(near));
    }

    @Test
    void nearestOtherIsHorizontalLimitedAndSkipsTheExcluded() {
        SiftLandmarks landmarks = new SiftLandmarks();
        SiftPos near = new SiftPos(10, 0, 0);
        SiftPos far = new SiftPos(50, 90, 0);
        landmarks.record(Kind.CANYON_ENTRANCE, near);
        landmarks.record(Kind.CANYON_ENTRANCE, far);
        SiftPos origin = new SiftPos(0, 0, 0);
        assertEquals(near, landmarks.nearestOther(Kind.CANYON_ENTRANCE, origin, 96.0, null).orElseThrow());
        assertEquals(far, landmarks.nearestOther(Kind.CANYON_ENTRANCE, origin, 96.0, near).orElseThrow());
        assertTrue(landmarks.nearestOther(Kind.CANYON_ENTRANCE, origin, 5.0, null).isEmpty());
        assertTrue(landmarks.nearestOther(Kind.CANYON_MIDPOINT, origin, 96.0, null).isEmpty());
    }

    @Test
    void nearestActiveClusterSkipsExcludedAndInactiveCanyons() {
        SiftLandmarks landmarks = new SiftLandmarks();
        SiftPos first = new SiftPos(20, 0, 0);
        SiftPos second = new SiftPos(-40, 0, 0);
        SiftPos third = new SiftPos(0, 0, 90);
        for (SiftPos pos : List.of(first, second, third)) {
            landmarks.record(Kind.CANYON_CLUSTER, pos);
        }
        SiftPos origin = new SiftPos(0, 0, 0);
        assertEquals(first, landmarks.nearestActiveCluster(origin, 1024.0, Set.of(), pos -> true).orElseThrow());
        assertEquals(second, landmarks.nearestActiveCluster(origin, 1024.0, Set.of(first.asLong()), pos -> true).orElseThrow());
        assertEquals(third, landmarks.nearestActiveCluster(origin, 1024.0, Set.of(), pos -> pos.equals(third)).orElseThrow());
        assertTrue(landmarks.nearestActiveCluster(origin, 10.0, Set.of(), pos -> true).isEmpty());
    }

    @Test
    void theRegistrySurvivesARestart(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("data/the-sift/landmarks.bin");
        SiftLandmarks landmarks = new SiftLandmarks();
        landmarks.record(Kind.SOUL_BLOCK, new SiftPos(-5, 10, 7));
        landmarks.record(Kind.CANYON_CLUSTER, new SiftPos(100, 20, -100));
        landmarks.record(Kind.GOLEM_PENDING, new SiftPos(1, 2, 3));
        assertTrue(landmarks.isDirty());
        assertTrue(landmarks.saveIfDirty(file));
        assertFalse(landmarks.isDirty());
        assertFalse(landmarks.saveIfDirty(file), "nothing changed since the last save");

        SiftLandmarks loaded = SiftLandmarks.load(file);
        assertFalse(loaded.isDirty());
        for (Kind kind : Kind.values()) {
            assertEquals(Set.copyOf(landmarks.snapshot(kind)), Set.copyOf(loaded.snapshot(kind)), kind.name());
        }
        assertEquals(List.of(new SiftPos(-5, 10, 7)), loaded.soulBlocksNear(new SiftPos(-5, 10, 7), 1, 1));
    }

    @Test
    void aMissingFileIsEmptyAndAForeignFileIsAnError(@TempDir Path directory) throws IOException {
        assertEquals(0, SiftLandmarks.load(directory.resolve("none.bin")).count(Kind.SOUL_BLOCK));
        Path foreign = directory.resolve("foreign.bin");
        Files.writeString(foreign, "not a landmark file at all");
        assertThrows(IOException.class, () -> SiftLandmarks.load(foreign));
    }

    @Test
    void generationThreadsCanRecordAtOnce() throws InterruptedException {
        SiftLandmarks landmarks = new SiftLandmarks();
        Thread[] threads = new Thread[4];
        for (int index = 0; index < threads.length; index++) {
            int offset = index;
            threads[index] = new Thread(() -> {
                for (int i = 0; i < 2000; i++) {
                    landmarks.record(Kind.SOUL_BLOCK, new SiftPos(i, offset, i % 7));
                }
            });
            threads[index].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(8000, landmarks.count(Kind.SOUL_BLOCK));
    }
}
