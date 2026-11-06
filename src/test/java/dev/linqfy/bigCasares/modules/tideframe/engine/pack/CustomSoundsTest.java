package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** plugins/BigCasares/tideframe/engine/sounds: Ogg Vorbis files become tideframe:NAME in the pack; anything else is left out with a reason. */
class CustomSoundsTest {
    /** The start of an Ogg Vorbis file: one Ogg page holding the Vorbis identification header ({@code channels}). */
    static byte[] vorbis(int channels, int size) {
        byte[] out = new byte[Math.max(size, 64)];
        byte[] head = "OggS".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(head, 0, out, 0, 4);
        out[5] = 2; out[26] = 1; out[27] = 30; // one segment of 30 bytes
        out[28] = 1;
        System.arraycopy("vorbis".getBytes(StandardCharsets.US_ASCII), 0, out, 29, 6);
        out[28 + 11] = (byte) channels;
        return out;
    }

    @Test void onlyOggVorbisIsASound() {
        assertEquals(1, CustomSounds.vorbisChannels(vorbis(1, 64)));
        assertEquals(2, CustomSounds.vorbisChannels(vorbis(2, 64)));
        byte[] opus = vorbis(1, 64);
        System.arraycopy("OpusHe".getBytes(StandardCharsets.US_ASCII), 0, opus, 28, 6);
        assertEquals(0, CustomSounds.vorbisChannels(opus), "Ogg Opus does not play in Minecraft");
        assertEquals(0, CustomSounds.vorbisChannels("ID3 an mp3 file with plenty of bytes to pass the length check......".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(0, CustomSounds.vorbisChannels(new byte[10]));
        assertEquals(0, CustomSounds.vorbisChannels(vorbis(0, 64)));
    }
    @Test void theFolderGivesSoundsAndReasons(@TempDir Path folder) throws Exception {
        assertEquals(CustomSounds.Loaded.NONE, CustomSounds.load(folder.resolve("missing")), "no folder: no sounds");
        Files.write(folder.resolve("click.ogg"), vorbis(1, 200));
        Files.write(folder.resolve("level_up.ogg"), vorbis(2, 300));
        Files.write(folder.resolve("Bad Name.ogg"), vorbis(1, 100));
        Files.write(folder.resolve("song.ogg"), "not audio at all, just text that is long enough to be read.......".getBytes(StandardCharsets.US_ASCII));
        Files.write(folder.resolve("huge.ogg"), vorbis(1, CustomSounds.MAX_BYTES + 1));
        Files.write(folder.resolve("notes.txt"), new byte[]{1, 2, 3});
        Files.createDirectory(folder.resolve("folder.ogg"));
        var loaded = CustomSounds.load(folder);
        assertEquals(List.of("tideframe:click", "tideframe:level_up"), loaded.sounds().stream().map(CustomSounds.Sound::id).toList());
        assertEquals(2, loaded.sounds().get(1).channels());
        assertEquals(4, loaded.problems().size(), loaded.problems().toString());
        assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("song.ogg") && p.contains("Ogg Vorbis")));
        assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("huge.ogg") && p.contains("2 MiB")));
        assertTrue(loaded.problems().stream().anyMatch(p -> p.contains("Bad Name.ogg")));
        assertThrows(IllegalArgumentException.class, () -> new CustomSounds.Sound("ok", new byte[64], 1));
    }
    @Test void thePackCarriesTheFilesAndTheirNames(@TempDir Path folder) throws Exception {
        var sounds = List.of(new CustomSounds.Sound("click", vorbis(1, 128), 1), new CustomSounds.Sound("win", vorbis(2, 96), 2));
        var entries = CustomSounds.entries(sounds);
        String json = new String(entries.get("assets/tideframe/sounds.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"click\": {\"sounds\": [{\"name\": \"tideframe:custom/click\"}]}"), json);
        assertTrue(json.contains("\"win\""));
        assertEquals(128, entries.get("assets/tideframe/sounds/custom/click.ogg").length);
        assertTrue(CustomSounds.entries(List.of()).isEmpty(), "no sounds: no sounds.json");
        var artifact = PackGenerator.generate(folder, ImageGlyphs.empty(), HudAssets.empty(), null, sounds);
        try (var zip = new ZipFile(artifact.path().toFile())) {
            assertNotNull(zip.getEntry("assets/tideframe/sounds/custom/win.ogg"));
            assertNotNull(zip.getEntry("assets/tideframe/sounds.json"));
            String provenance = new String(zip.getInputStream(zip.getEntry("ASSET_PROVENANCE.txt")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(provenance.contains("tideframe:win: 96 bytes, 2 channels"), provenance);
        }
        var plain = PackGenerator.generate(folder.resolve("plain"), ImageGlyphs.empty(), HudAssets.empty(), null);
        try (var zip = new ZipFile(plain.path().toFile())) { assertNull(zip.getEntry("assets/tideframe/sounds.json"), "without sounds the pack is as before"); }
    }
}
