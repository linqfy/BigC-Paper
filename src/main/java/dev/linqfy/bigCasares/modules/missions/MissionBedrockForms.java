package dev.linqfy.bigCasares.modules.missions;

import java.util.List;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Bedrock forms for the missions (through Geyser). */
public interface MissionBedrockForms {
    /** Shows a button form; false when it cannot (no Geyser, forms off, not a Bedrock client): the caller falls back. */
    boolean send(UUID playerId, String title, String content, List<String> buttons, IntConsumer clicked);

    /** Whether Bedrock clients get BigCasares' pack, whose glyphs draw the progress bars. */
    boolean glyphs();
}
