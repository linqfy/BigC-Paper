package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Custom sounds (0.23): Ogg Vorbis files in plugins/BigCasares/tideframe/engine/sounds go into the resource pack as {@code tideframe:NAME},
 * for the sound action, the menu's hover and click sounds and any plugin. Only real Ogg Vorbis files are taken (the
 * only audio the game plays), named a-z, 0-9 and _ (up to 32), at most 2 MiB each, 16 MiB and 64 files together;
 * anything else is left out with a reason. Mono files play from the player; stereo ones play the same everywhere.
 * CPU and disk work: call on a worker.
 */
public final class CustomSounds {
    private CustomSounds() { }
    public static final int MAX_BYTES = 2 * 1024 * 1024, MAX_TOTAL = 16 * 1024 * 1024, MAX_FILES = 64;
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");

    /** One sound: its name ({@code tideframe:name} in the game), the file and its channel count. */
    public record Sound(String name, byte[] ogg, int channels) {
        public Sound {
            if (name == null || !NAME.matcher(name).matches()) throw new IllegalArgumentException("Sound names use a-z, 0-9 and _ (up to 32)");
            ogg = ogg.clone();
            if (vorbisChannels(ogg) <= 0) throw new IllegalArgumentException("Sound " + name + " is not an Ogg Vorbis file");
        }
        @Override public byte[] ogg() { return ogg.clone(); }
        public String id() { return "tideframe:" + name; }
    }
    /** The sounds taken, in name order, and why other files were left out. */
    public record Loaded(List<Sound> sounds, List<String> problems) {
        public static final Loaded NONE = new Loaded(List.of(), List.of());
        public Loaded { sounds = List.copyOf(sounds); problems = List.copyOf(problems); }
    }

    /** Reads {@code folder} (missing is fine: no sounds). Links, folders and other files are skipped. */
    public static Loaded load(Path folder) throws IOException {
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) return Loaded.NONE;
        var files = new ArrayList<Path>();
        try (var listing = Files.list(folder)) { listing.filter(p -> p.getFileName().toString().endsWith(".ogg")).sorted().forEach(files::add); }
        var sounds = new ArrayList<Sound>();
        var problems = new ArrayList<String>();
        long total = 0;
        for (var file : files) {
            String fileName = file.getFileName().toString(), name = fileName.substring(0, fileName.length() - 4);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { problems.add("sounds/" + fileName + " is not a plain file; skipped"); continue; }
            if (!NAME.matcher(name).matches()) { problems.add("sounds/" + fileName + ": names use a-z, 0-9 and _ (up to 32); skipped"); continue; }
            if (sounds.size() >= MAX_FILES) { problems.add("sounds/" + fileName + ": the sounds folder already holds 64 files or 16 MiB; skipped"); continue; }
            // Read what was actually there, never more than the caps allow plus one byte (a file being copied or swapped
            // while the pack is built cannot pass a size checked earlier), without following a link put in its place.
            byte[] bytes;
            try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = in.readNBytes((int) Math.min(MAX_BYTES, MAX_TOTAL - total) + 1); }
            if (bytes.length > MAX_BYTES) { problems.add("sounds/" + fileName + " is larger than 2 MiB; skipped"); continue; }
            if (total + bytes.length > MAX_TOTAL) { problems.add("sounds/" + fileName + ": the sounds folder already holds 64 files or 16 MiB; skipped"); continue; }
            int channels = vorbisChannels(bytes);
            if (channels <= 0) { problems.add("sounds/" + fileName + " is not an Ogg Vorbis file (the only audio Minecraft plays); skipped"); continue; }
            sounds.add(new Sound(name, bytes, channels));
            total += bytes.length;
        }
        return new Loaded(sounds, problems);
    }

    /**
     * Channels of an Ogg Vorbis file (1 mono, 2 stereo...), or 0 when the bytes are not one: an Ogg page ("OggS") whose
     * first packet is the Vorbis identification header ("\1vorbis").
     */
    public static int vorbisChannels(byte[] data) {
        if (data == null || data.length < 58 || data[0] != 'O' || data[1] != 'g' || data[2] != 'g' || data[3] != 'S' || data[4] != 0) return 0;
        int segments = data[26] & 0xff, packet = 27 + segments;
        if (data.length < packet + 30 || data[packet] != 1) return 0;
        if (!new String(data, packet + 1, 6, StandardCharsets.US_ASCII).equals("vorbis")) return 0;
        int channels = data[packet + 11] & 0xff;
        return channels >= 1 && channels <= 8 ? channels : 0;
    }

    /** Pack entries: assets/tideframe/sounds/custom/NAME.ogg and the sounds.json naming each tideframe:NAME. */
    public static Map<String, byte[]> entries(List<Sound> sounds) {
        var out = new LinkedHashMap<String, byte[]>();
        if (sounds.isEmpty()) return out;
        var json = new StringBuilder("{\n");
        for (int i = 0; i < sounds.size(); i++) {
            var sound = sounds.get(i);
            json.append("  \"").append(sound.name()).append("\": {\"sounds\": [{\"name\": \"tideframe:custom/").append(sound.name()).append("\"}]}")
                .append(i + 1 < sounds.size() ? ",\n" : "\n");
            out.put("assets/tideframe/sounds/custom/" + sound.name() + ".ogg", sound.ogg());
        }
        out.put("assets/tideframe/sounds.json", json.append("}\n").toString().getBytes(StandardCharsets.UTF_8));
        return out;
    }
}
