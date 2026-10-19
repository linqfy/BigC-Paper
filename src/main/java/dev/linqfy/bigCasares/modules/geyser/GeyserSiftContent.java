package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.modules.sift.SiftItems;
import dev.linqfy.bigCasares.modules.sift.SiftModels;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * What Bedrock players see of the Sift. Its pieces come from tools/export_sift_bedrock.py, which writes the
 * Bedrock pack's textures, icons, creature models and sounds plus the block list read here.
 *
 * <ul>
 *   <li>Items: every Sift content item with a Sift model, matched by that model, with the icon the tool drew.</li>
 *   <li>Blocks: the mushroom-block states that draw Sift blocks, as Bedrock custom blocks.</li>
 *   <li>Creatures and rifts: the vanilla body a Sift model is dressed on becomes a Bedrock custom entity
 *   with that model. Geyser does not show BetterModel's display entities, so without this a Bedrock player
 *   sees a plain wolf, silverfish, wandering trader or sniffer, and no rift at all.</li>
 * </ul>
 */
final class GeyserSiftContent {

    static final String BLOCKS = "geyser/the_sift_blocks.json";
    private static final String SIFT_NAMESPACE = "the_sift";

    private GeyserSiftContent() {
    }

    /**
     * Items drawn with a Sift model. A few stand-ins wear a vanilla item's model (the Ichor glass is light blue
     * stained glass); Bedrock shows those as that vanilla item already, and mapping their vanilla model would
     * catch every plain stack of it too.
     */
    static List<GeyserCustomItemDefinition> items() {
        return SiftItems.items().stream()
            .filter(item -> item.itemModel().namespace().equals(SIFT_NAMESPACE))
            .map(GeyserSiftContent::item)
            .toList();
    }

    static GeyserCustomItemDefinition item(ContentItem item) {
        return new GeyserCustomItemDefinition(
            "minecraft:" + item.material().toLowerCase(Locale.ROOT),
            item.itemModel().asString(),
            item.id().asString(),
            item.itemModel().namespace() + "." + item.itemModel().path(),
            item.fallbackName());
    }

    /**
     * Bodies by Java type and model: the marker key is the model the body wears (see
     * {@code ClientPresentedModelGateway}). Rifts and the ritual's Singer hang on invisible armor stands, which
     * Bedrock does not draw, so they are shown on a Bedrock-only stand that wears the same model key.
     */
    static List<GeyserCustomEntityDefinition> entities() {
        return List.of(
            new GeyserCustomEntityDefinition("WOLF", "the_sift:blub", SiftModels.BLUB),
            new GeyserCustomEntityDefinition("WOLF", "the_sift:blub", SiftModels.BLUB_TAMED),
            new GeyserCustomEntityDefinition("SILVERFISH", "the_sift:sifter", SiftModels.SIFTER),
            new GeyserCustomEntityDefinition("WANDERING_TRADER", "the_sift:singer", SiftModels.SINGER),
            new GeyserCustomEntityDefinition("ARMOR_STAND", "the_sift:singer", SiftModels.SINGER),
            new GeyserCustomEntityDefinition("WOLF", "the_sift:echo_golem", SiftModels.ECHO_GOLEM),
            new GeyserCustomEntityDefinition("SNIFFER", "the_sift:dark_sniffer", SiftModels.DARK_SNIFFER),
            new GeyserCustomEntityDefinition("ARMOR_STAND", "the_sift:rift", SiftModels.RIFT),
            new GeyserCustomEntityDefinition("ARMOR_STAND", "the_sift:rift_overworld", SiftModels.RIFT_OVERWORLD));
    }

    static List<GeyserCustomBlockDefinition> blocks(InputStream resource) throws IOException {
        return resource == null ? List.of() : GeyserCustomBlockDefinition.read(resource);
    }
}
