package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicTemplateSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The third-party template sources credited in {@link ThirdPartyCredits}. */
public final class ThirdPartySources {

    private static final String RESOURCE_ROOT = "/mythicdungeons/third_party/";

    private ThirdPartySources() {
    }

    /** YUNG's Better Dungeons rooms and Doomlike Dungeons layouts, in a fixed order. */
    public static List<MythicTemplateSource> all() {
        return List.of(new YungsBetterDungeonsSource(), new DoomlikeDungeonsSource());
    }

    /**
     * The licence files shipped under {@code mythicdungeons/third_party/<folder>/}, keyed
     * {@code <folder>/<name>}; a missing one fails the source (the pack must not ship without it).
     */
    static Map<String, byte[]> licenseFiles(String folder, List<String> names) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String name : names) {
            String resource = RESOURCE_ROOT + folder + "/" + name;
            try (InputStream input = ThirdPartySources.class.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IllegalStateException("Missing licence file " + resource);
                }
                files.put(folder + "/" + name, input.readAllBytes());
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        return files;
    }
}
