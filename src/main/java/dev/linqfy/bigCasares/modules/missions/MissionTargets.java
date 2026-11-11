package dev.linqfy.bigCasares.modules.missions;

import org.bukkit.Material;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

public final class MissionTargets {

    private static final Set<String> WHISPER_COMMANDS = Set.of("msg", "tell", "w", "whisper", "m", "pm", "dm");

    private MissionTargets() {
    }

    /** Lowercases, removes legacy colour codes and drops everything but letters and digits. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutColours = raw.replaceAll("(?i)[§&][0-9a-fk-orx]", "");
        return withoutColours.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static boolean matches(Object expected, Collection<String> candidates) {
        String target = normalize(expected == null ? null : String.valueOf(expected));
        if (target.isEmpty()) {
            return false;
        }
        for (String candidate : candidates) {
            if (target.equals(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHelmet(Material material) {
        return material != null && (material.name().endsWith("_HELMET") || material == Material.TURTLE_HELMET);
    }

    /** Returns the player name a private-message command is aimed at, or null when the line is not a whisper. */
    public static String whisperTarget(String commandLine) {
        if (commandLine == null) {
            return null;
        }
        String[] parts = commandLine.trim().split("\\s+");
        if (parts.length < 3 || !parts[0].startsWith("/")) {
            return null;
        }
        String label = parts[0].substring(1).toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }
        return WHISPER_COMMANDS.contains(label) ? parts[1] : null;
    }
}
