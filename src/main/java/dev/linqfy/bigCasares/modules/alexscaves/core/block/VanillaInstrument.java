package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import java.util.List;
import java.util.Locale;

/**
 * Instrument a vanilla note block would use for the block below it (or a mob
 * head above it). Vanilla note blocks are kept on {@code instrument=harp} so they
 * never collide with module states; the real instrument is restored only for the
 * sound.
 */
public final class VanillaInstrument {
    private static final List<String> BASS_DRUM_HINTS = List.of(
        "STONE", "DEEPSLATE", "_ORE", "BRICK", "OBSIDIAN", "NETHERRACK", "QUARTZ", "BASALT", "TUFF",
        "CALCITE", "DRIPSTONE", "PRISMARINE", "TERRACOTTA", "NYLIUM", "COBBLE", "ANDESITE", "DIORITE",
        "GRANITE", "BEDROCK", "END_STONE", "PURPUR", "CORAL_BLOCK", "MAGMA");
    /** Wood species: every solid block made of them uses the wood instrument (bass). */
    private static final List<String> WOOD_SPECIES = List.of(
        "OAK_", "SPRUCE_", "BIRCH_", "JUNGLE_", "ACACIA_", "DARK_OAK_", "MANGROVE_", "CHERRY_", "BAMBOO_",
        "CRIMSON_", "WARPED_", "PALE_OAK_");
    private static final List<String> NOT_WOOD = List.of(
        "LEAVES", "SAPLING", "PROPAGULE", "NYLIUM", "FUNGUS", "ROOTS", "SPROUTS", "VINES", "WART_BLOCK", "MOSS");
    private static final List<String> WOOD_BLOCKS = List.of(
        "NOTE_BLOCK", "BOOKSHELF", "CHISELED_BOOKSHELF", "CHEST", "TRAPPED_CHEST", "CRAFTING_TABLE", "JUKEBOX",
        "BARREL", "LECTERN", "COMPOSTER", "LOOM", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE", "SMITHING_TABLE",
        "BEEHIVE", "BEE_NEST", "DAYLIGHT_DETECTOR", "BROWN_MUSHROOM_BLOCK", "RED_MUSHROOM_BLOCK", "MUSHROOM_STEM",
        "BAMBOO_BLOCK", "STRIPPED_BAMBOO_BLOCK", "CREAKING_HEART");

    private VanillaInstrument() {
    }

    /** Name of the {@code org.bukkit.Instrument} constant for a material below the note block. */
    public static String below(String material) {
        String name = material.toUpperCase(Locale.ROOT);
        if (name.endsWith("_WOOL")) {
            return "GUITAR";
        }
        if (name.endsWith("CONCRETE_POWDER") || name.equals("SAND") || name.equals("RED_SAND")
            || name.equals("GRAVEL") || name.equals("SUSPICIOUS_SAND") || name.equals("SUSPICIOUS_GRAVEL")) {
            return "SNARE_DRUM";
        }
        if (name.contains("GLASS") || name.equals("SEA_LANTERN") || name.equals("BEACON")) {
            return "STICKS";
        }
        switch (name) {
            case "GOLD_BLOCK": return "BELL";
            case "CLAY": return "FLUTE";
            case "PACKED_ICE": return "CHIME";
            case "BONE_BLOCK": return "XYLOPHONE";
            case "IRON_BLOCK": return "IRON_XYLOPHONE";
            case "SOUL_SAND": return "COW_BELL";
            case "PUMPKIN", "CARVED_PUMPKIN", "JACK_O_LANTERN": return "DIDGERIDOO";
            case "EMERALD_BLOCK": return "BIT";
            case "HAY_BLOCK": return "BANJO";
            case "GLOWSTONE": return "PLING";
            default: break;
        }
        if (isWood(name)) {
            return "BASS_GUITAR";
        }
        for (String hint : BASS_DRUM_HINTS) {
            if (name.contains(hint)) {
                return "BASS_DRUM";
            }
        }
        if (name.endsWith("CONCRETE")) {
            return "BASS_DRUM";
        }
        return "PIANO";
    }

    /** Mob head instrument for the block above the note block, or {@code null} when it is not a mob head. */
    public static String above(String material) {
        return switch (material.toUpperCase(Locale.ROOT)) {
            case "ZOMBIE_HEAD" -> "ZOMBIE";
            case "SKELETON_SKULL" -> "SKELETON";
            case "CREEPER_HEAD" -> "CREEPER";
            case "DRAGON_HEAD" -> "DRAGON";
            case "WITHER_SKELETON_SKULL" -> "WITHER_SKELETON";
            case "PIGLIN_HEAD" -> "PIGLIN";
            case "PLAYER_HEAD" -> "CUSTOM_HEAD";
            default -> null;
        };
    }

    private static boolean isWood(String name) {
        if (WOOD_BLOCKS.contains(name) || name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD")
            || name.endsWith("_STEM") || name.endsWith("_HYPHAE")) {
            return true;
        }
        for (String excluded : NOT_WOOD) {
            if (name.contains(excluded)) {
                return false;
            }
        }
        String species = name.startsWith("STRIPPED_") ? name.substring("STRIPPED_".length()) : name;
        for (String prefix : WOOD_SPECIES) {
            if (species.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
