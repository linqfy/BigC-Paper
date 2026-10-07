package dev.linqfy.bigCasares.modules.celular;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelularVideosLoaderTest {

    @Test
    void readsIdTitleAndFrames() {
        List<CelularVideo> videos = CelularVideosLoader.load(new StringReader("""
            videos:
              - id: video_1
                titulo: "Hola"
                cuadros: 40
            """));
        assertEquals(List.of(new CelularVideo("video_1", "Hola", 40)), videos);
        assertEquals(4.0, videos.get(0).seconds());
    }

    @Test
    void rejectsBrokenOrEmptyLists() {
        assertThrows(IllegalArgumentException.class,
            () -> CelularVideosLoader.load(new StringReader("videos:\n  - id: video_1\n")));
        assertThrows(IllegalArgumentException.class, () -> CelularVideosLoader.load(new StringReader("videos: []\n")));
        assertThrows(IllegalArgumentException.class, () -> new CelularVideo("Video 1", "x", 1));
        assertThrows(IllegalArgumentException.class, () -> new CelularVideo("video_1", "x", 0));
    }

    @Test
    void packagedListHasUniqueVideosOfAtMostSixtySeconds() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(CelularVideosLoader.RESOURCE)) {
            assertNotNull(stream, CelularVideosLoader.RESOURCE + " must be packaged");
            List<CelularVideo> videos = CelularVideosLoader.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertFalse(videos.isEmpty());
            assertEquals(videos.size(), videos.stream().map(CelularVideo::id).distinct().count());
            for (CelularVideo video : videos) {
                assertTrue(video.seconds() <= 60.0, video.id() + " is longer than 60 seconds");
                assertFalse(video.title().isBlank(), video.id());
            }
        }
    }
}
