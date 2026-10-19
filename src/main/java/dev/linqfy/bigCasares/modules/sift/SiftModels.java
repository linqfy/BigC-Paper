package dev.linqfy.bigCasares.modules.sift;

/**
 * BetterModel model keys of the Sift. The Geyser integration uses the same keys to give each dressed body its
 * Bedrock entity, so they are public; a key that changes here changes on both sides.
 */
public final class SiftModels {

    public static final String BLUB = "the_sift_blub";
    public static final String BLUB_TAMED = "the_sift_blub_tamed";
    public static final String SINGER = "the_sift_singer";
    public static final String SIFTER = "the_sift_sifter";
    public static final String ECHO_GOLEM = "the_sift_echo_golem";
    public static final String DARK_SNIFFER = "the_sift_dark_sniffer";
    public static final String RIFT = "the_sift_rift";
    public static final String RIFT_OVERWORLD = "the_sift_rift_overworld";

    private SiftModels() {
    }

    /** Whether a model key is one of the Sift's (they all start with the mod's namespace). */
    public static boolean isSift(String modelKey) {
        return modelKey.startsWith("the_sift_");
    }
}
