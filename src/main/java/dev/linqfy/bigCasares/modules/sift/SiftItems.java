package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.MushroomState;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.dimension.NamespacedId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Content items of the Sift port. Block items use their backing mushroom block
 * as material so placement is a normal vanilla placement that the custom block
 * service rewrites to the exact backing state. Item models and names come from
 * the imported mod assets ({@code the_sift:<id>} item definitions and lang keys).
 *
 * <p>The Siftite gear ({@link SiftiteGear}) and the Siftite upgrade smithing template are based on their
 * netherite counterparts. The gear gets its stats from {@link SiftiteGearComponents} each time a stack is
 * created. Differences: the template is a netherite upgrade template with another name, model and content
 * id, so the client still prints the netherite template's "Applies to" and "Ingredients" lines (Diamond
 * Equipment, Netherite Ingot) and its smithing table slot hints, and vanilla's netherite upgrade recipe
 * accepts it as a template. The Siftite smithing recipes themselves are not defined here.</p>
 */
public final class SiftItems {

    private SiftItems() {
    }

    public static List<ContentItem> items() {
        List<ContentItem> items = new ArrayList<>();
        Set<NamespacedId> blockItems = new LinkedHashSet<>();
        for (CustomBlockDefinition block : SiftBlocks.definitions()) {
            NamespacedId itemId = itemFor(block);
            if (!blockItems.add(itemId)) {
                continue;
            }
            CustomBlockDefinition placed = SiftBlocks.custom(itemId.path()).orElse(block);
            items.add(ContentItem.builder(itemId, material(placed.state().base()))
                .name("block.the_sift." + itemId.path(), pretty(itemId.path()))
                .places(placed.id())
                .build());
        }
        items.add(simple("charoite", "AMETHYST_SHARD"));
        items.add(simple("siftite_nugget", "IRON_NUGGET"));
        items.add(simple("siftite_ingot", "NETHERITE_INGOT"));
        items.addAll(gear());
        items.add(ContentItem.builder(id("siftite_upgrade_smithing_template"), "NETHERITE_UPGRADE_SMITHING_TEMPLATE")
            .name("item.the_sift.siftite_upgrade_smithing_template", "Siftite Upgrade").build());
        items.add(simple("ichor_shard", "PRISMARINE_SHARD"));
        items.add(ContentItem.builder(id("raw_sifter_meat"), "MUTTON").name("item.the_sift.raw_sifter_meat", "Raw Sifter Meat")
            .food(2, 1.2F, false).build());
        items.add(ContentItem.builder(id("cooked_sifter_meat"), "COOKED_MUTTON").name("item.the_sift.cooked_sifter_meat",
            "Cooked Sifter Meat").food(7, 9.6F, false).build());
        items.add(ContentItem.builder(id("ichor_bottle"), "POTION").name("item.the_sift.ichor_bottle", "Ichor Bottle")
            .maxStack(16).build());
        items.add(ContentItem.builder(id("ichor_bucket"), "WATER_BUCKET").name("item.the_sift.ichor_bucket", "Ichor Bucket")
            .maxStack(1).build());
        items.add(simple("overgrown_willow_sapling", "MANGROVE_PROPAGULE"));
        return items;
    }

    /** One item per piece of {@link SiftiteGear}: a single stack, with the stats applied on creation. */
    static List<ContentItem> gear() {
        List<ContentItem> gear = new ArrayList<>();
        for (SiftiteGear piece : SiftiteGear.values()) {
            gear.add(ContentItem.builder(id(piece.path()), piece.baseMaterial())
                .name("item.the_sift." + piece.path(), pretty(piece.path()))
                .maxStack(1)
                .customize(stack -> SiftiteGearComponents.apply(piece, stack))
                .build());
        }
        return gear;
    }

    /** Item that places a block: axis/state variants share their base block's item. */
    static NamespacedId itemFor(CustomBlockDefinition block) {
        String path = block.id().path();
        for (String suffix : List.of("_x", "_z", "_note", "_snowy")) {
            if (path.endsWith(suffix)) {
                return id(path.substring(0, path.length() - suffix.length()));
            }
        }
        return block.id();
    }

    private static ContentItem simple(String path, String material) {
        return ContentItem.builder(id(path), material).name("item.the_sift." + path, pretty(path)).build();
    }

    private static NamespacedId id(String path) {
        return NamespacedId.of(SiftBlocks.NS, path);
    }

    private static String material(MushroomState.Base base) {
        return base.material();
    }

    static String pretty(String path) {
        StringBuilder name = new StringBuilder();
        for (String word : path.split("_")) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }
}
