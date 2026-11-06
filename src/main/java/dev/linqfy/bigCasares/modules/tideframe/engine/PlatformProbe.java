package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.regex.Pattern;

public final class PlatformProbe {
    private static final Pattern OLD = Pattern.compile("1\\.21(?:\\.(?:[1-9]|10|11))?");
    private static final Pattern NEW = Pattern.compile("26\\.[123](?:\\.[1-9][0-9]*)?");
    private PlatformProbe() {}
    public static boolean supports(String version) {
        return version != null && (OLD.matcher(version).matches() || NEW.matcher(version).matches());
    }
    public static int minimumJava(String version) {
        if (!supports(version)) throw new IllegalArgumentException("Untargeted Minecraft release: " + version);
        return version.startsWith("26.") ? 25 : 21;
    }
    public static boolean folia() {
        try { Class.forName("io.papermc.paper.threadedregions.RegionizedServer"); return true; }
        catch (ClassNotFoundException ignored) { return false; }
    }
}
