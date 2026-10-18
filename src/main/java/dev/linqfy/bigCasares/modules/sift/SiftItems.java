package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.MushroomState;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
 *
 * <p>Spawn eggs of the mod's creatures sit on a vanilla egg of a similar mob. The vanilla spawn is always
 * cancelled and the real creature is spawned instead ({@link SiftSpawnEggs}). The two music discs are
 * stand-ins: the mod's recordings are third-party works and are not imported, so each disc sits on a vanilla
 * disc and plays that disc's song. The name is the mod's ("Music Disc") and one lore line names the mod's
 * track; the base disc's own tooltip line (its vanilla artist and title) is hidden because it would
 * misattribute the song. The client's "Now playing" text, which comes from the vanilla song, still shows the
 * vanilla title.</p>
 */
public final class SiftItems {

    static final int ICHOR_BOTTLE_COLOR = 5_909_123;
    static final NamespacedId SINGER_SPAWN_EGG = NamespacedId.of(SiftBlocks.NS, "singer_spawn_egg");
    static final NamespacedId ECHO_GOLEM_SPAWN_EGG = NamespacedId.of(SiftBlocks.NS, "echo_golem_spawn_egg");
    static final NamespacedId BLUB_SPAWN_EGG = NamespacedId.of(SiftBlocks.NS, "blub_spawn_egg");
    static final NamespacedId SIFTER_SPAWN_EGG = NamespacedId.of(SiftBlocks.NS, "sifter_spawn_egg");
    static final NamespacedId DARK_SNIFFER_SPAWN_EGG = NamespacedId.of(SiftBlocks.NS, "dark_sniffer_spawn_egg");
    static final NamespacedId MUSIC_DISC_RIFT = NamespacedId.of(SiftBlocks.NS, "music_disc_rift");
    static final NamespacedId MUSIC_DISC_WELLSPRING = NamespacedId.of(SiftBlocks.NS, "music_disc_wellspring");
    /** The vanilla discs that stand in for the mod's: the 5 (Warden, ancient city) and Otherside (caves), of similar length. */
    static final String RIFT_DISC_BASE = "MUSIC_DISC_5";
    static final String WELLSPRING_DISC_BASE = "MUSIC_DISC_OTHERSIDE";

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
        // Lapis underneath: the enchanting table's lapis slot is where Charoite is used (see SiftCharoite).
        items.add(simple("charoite", "LAPIS_LAZULI"));
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
            .maxStack(1).customize(SiftItems::ichorBottle).build());
        items.add(ContentItem.builder(id("ichor_snowball"), "SNOWBALL").name("item.the_sift.ichor_snowball", "Ichor Snowball")
            .maxStack(16).build());
        items.add(ContentItem.builder(id("ichor_bucket"), "WATER_BUCKET").name("item.the_sift.ichor_bucket", "Ichor Bucket")
            .maxStack(1).build());
        items.add(simple("overgrown_willow_sapling", "MANGROVE_PROPAGULE"));
        // Spawn eggs of the mod's Singer and Echo Golem; the use is in SiftSingers and SiftEchoGolems.
        items.add(ContentItem.builder(SINGER_SPAWN_EGG, "WANDERING_TRADER_SPAWN_EGG")
            .name("item.the_sift.singer_spawn_egg", "Singer Spawn Egg").build());
        items.add(ContentItem.builder(ECHO_GOLEM_SPAWN_EGG, "WOLF_SPAWN_EGG")
            .name("item.the_sift.echo_golem_spawn_egg", "Echo Golem Spawn Egg").build());
        // Spawn eggs of the Blub (a wolf), the Sifter (a silverfish) and the Dark Sniffer (a sniffer): see SiftSpawnEggs.
        items.add(ContentItem.builder(BLUB_SPAWN_EGG, "WOLF_SPAWN_EGG")
            .name("item.the_sift.blub_spawn_egg", "Blub Spawn Egg").build());
        items.add(ContentItem.builder(SIFTER_SPAWN_EGG, "SILVERFISH_SPAWN_EGG")
            .name("item.the_sift.sifter_spawn_egg", "Sifter Spawn Egg").build());
        items.add(ContentItem.builder(DARK_SNIFFER_SPAWN_EGG, "SNIFFER_SPAWN_EGG")
            .name("item.the_sift.dark_sniffer_spawn_egg", "Dark Sniffer Spawn Egg").build());
        items.add(musicDisc(MUSIC_DISC_RIFT, RIFT_DISC_BASE, "Rift"));
        items.add(musicDisc(MUSIC_DISC_WELLSPRING, WELLSPRING_DISC_BASE, "Wellspring"));
        // Planted by vanilla's own placement (see SiftSculkflowers): torchflower seeds place a torchflower crop,
        // a warped fungus a warped fungus, and the datapack lets both stand on sculk.
        items.add(simple("sculkflower_seeds", "TORCHFLOWER_SEEDS"));
        items.add(simple("sculkflower", "WARPED_FUNGUS"));
        // Slabs, stairs, doors, signs, boats, glass and plants: vanilla look-alikes (see SiftStandIns).
        Set<NamespacedId> present = new LinkedHashSet<>();
        items.forEach(item -> present.add(item.id()));
        SiftStandIns.items().stream().filter(item -> present.add(item.id())).forEach(items::add);
        return items;
    }

    /** A stand-in for one of the mod's discs: a vanilla disc under the mod's name, model and one track line. */
    private static ContentItem musicDisc(NamespacedId id, String baseDisc, String track) {
        return ContentItem.builder(id, baseDisc)
            .name("item." + SiftBlocks.NS + "." + id.path(), "Music Disc")
            .lore(List.of("The Sift - " + track))
            .maxStack(1)
            .customize(SiftItems::hideSongLine)
            .build();
    }

    /** Hides the vanilla song's "artist - title" line, which would credit the base disc's song to the mod's disc. */
    private static void hideSongLine(ItemStack stack) {
        stack.setData(DataComponentTypes.TOOLTIP_DISPLAY,
            TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.JUKEBOX_PLAYABLE).build());
    }

    /** The mod's Ichor bottle: a drink with its colour and three seconds of Regeneration. */
    private static void ichorBottle(ItemStack stack) {
        stack.editMeta(PotionMeta.class, meta -> {
            meta.setColor(Color.fromRGB(ICHOR_BOTTLE_COLOR));
            meta.addCustomEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0), true);
        });
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
