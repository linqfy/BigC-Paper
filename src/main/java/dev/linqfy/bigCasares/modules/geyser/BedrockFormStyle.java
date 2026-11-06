package dev.linqfy.bigCasares.modules.geyser;

/**
 * The BigCasares look on Bedrock: a form whose title starts with {@link #TAG} is drawn by the Bedrock pack
 * ({@code ui/bigc_forms.json}, built by {@code tools/tideframe-screens/build-bedrock-ui.mjs}) with the colours of the
 * Tideframe screens. Every other form keeps Bedrock's own look. The tag is made of colour codes, so it stays invisible
 * if the pack is missing.
 */
public final class BedrockFormStyle {
    public static final String TAG = "§b§g§c§r";

    private BedrockFormStyle() {
    }

    public static String styled(String title) {
        return TAG + (title == null ? "" : title);
    }
}
