package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The web editor and the plugin talk through two small files in plugins/BigCasares/tideframe/engine (the editor's server sits next to
 * the screens folder). editor-status.json, written once a load is live: its screens, their warnings and the time of
 * each screen file it read (the editor waits for the time of the file it published), or why the load failed; plus
 * the answer to the last "open in game" request, after that request's id. editor-open.json: the editor's request to
 * open a screen for a player; the plugin reads it once, deletes it, and only ever opens a screen (never runs anything else).
 */
final class EditorBridge {
    private EditorBridge() { }
    static final String STATUS = "editor-status.json", OPEN = "editor-open.json";

    /** A request read from editor-open.json; {@code player} and {@code request} (its id) are null when the editor sent none. */
    record OpenRequest(String screen, String player, String request) { }

    /** The status document; {@code problems}: screen id → warnings; {@code stamps}: screen id → file time in ms. */
    static String status(long time, boolean ok, String error, Iterable<String> screens, Map<String, List<String>> problems,
                         Map<String, Long> stamps, String open) {
        var out = new StringBuilder("{\"time\":").append(time).append(",\"ok\":").append(ok).append(",\"error\":").append(error == null ? "null" : quote(error));
        out.append(",\"screens\":[");
        boolean first = true;
        for (String id : screens) { if (!first) out.append(','); first = false; out.append(quote(id)); }
        out.append("],\"problems\":{");
        first = true;
        for (var entry : new TreeMap<>(problems).entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            if (!first) out.append(','); first = false;
            out.append(quote(entry.getKey())).append(":[");
            for (int i = 0; i < entry.getValue().size(); i++) { if (i > 0) out.append(','); out.append(quote(entry.getValue().get(i))); }
            out.append(']');
        }
        out.append("},\"stamps\":{");
        first = true;
        for (var entry : new TreeMap<>(stamps).entrySet()) {
            if (!first) out.append(','); first = false;
            out.append(quote(entry.getKey())).append(':').append(entry.getValue().longValue());
        }
        return out.append("},\"open\":").append(open == null ? "null" : quote(open)).append('}').toString();
    }
    /** Writes the status atomically (a temporary file renamed over the old one). */
    static void writeStatus(Path dataFolder, String json) throws IOException {
        Files.createDirectories(dataFolder);
        var temporary = Files.createTempFile(dataFolder, "editor-status", ".tmp");
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            Files.move(temporary, dataFolder.resolve(STATUS), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }
    /** The request in {@code raw}, or null when it is not one ({"screen":"id","player":"Name","request":"id"}). */
    static OpenRequest openRequest(String raw) {
        try {
            if (!(dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec.parse(raw) instanceof Map<?, ?> map)) return null;
            Object screen = map.get("screen"), player = map.get("player"), request = map.get("request");
            if (!(screen instanceof String id) || !id.matches("[a-z][a-z0-9_-]{0,31}")) return null;
            String name = player instanceof String text && text.matches("[A-Za-z0-9_]{1,16}") ? text : null;
            String requestId = request instanceof String text && text.matches("[A-Za-z0-9]{1,32}") ? text : null;
            return new OpenRequest(id, name, requestId);
        } catch (RuntimeException unreadable) { return null; }
    }
    private static String quote(String text) {
        var out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
