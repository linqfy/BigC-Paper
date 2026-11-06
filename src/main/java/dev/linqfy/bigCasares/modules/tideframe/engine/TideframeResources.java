package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.io.InputStream;

/** Bundled engine assets have their own root and never shadow BigCasares configuration. */
public final class TideframeResources {
    private TideframeResources() { }

    public static InputStream open(String name) {
        if (name.startsWith("/") || name.contains("..")) {
            throw new IllegalArgumentException("Expected a relative engine resource");
        }
        return TideframeResources.class.getResourceAsStream("/tideframe/engine/" + name);
    }
}
