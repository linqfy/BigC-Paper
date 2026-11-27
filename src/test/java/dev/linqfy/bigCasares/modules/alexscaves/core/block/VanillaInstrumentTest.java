package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import dev.linqfy.bigCasares.modules.alexscaves.core.item.AlexsCavesCraftGuardTestAccess;
import org.bukkit.ExplosionResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaInstrumentTest {

    @Test
    void followsTheVanillaBlockBelowRules() {
        assertEquals("GUITAR", VanillaInstrument.below("WHITE_WOOL"));
        assertEquals("SNARE_DRUM", VanillaInstrument.below("SAND"));
        assertEquals("SNARE_DRUM", VanillaInstrument.below("RED_CONCRETE_POWDER"));
        assertEquals("BASS_DRUM", VanillaInstrument.below("RED_CONCRETE"));
        assertEquals("STICKS", VanillaInstrument.below("GLASS"));
        assertEquals("BELL", VanillaInstrument.below("GOLD_BLOCK"));
        assertEquals("BASS_GUITAR", VanillaInstrument.below("OAK_PLANKS"));
        assertEquals("BASS_DRUM", VanillaInstrument.below("STONE"));
        assertEquals("BASS_DRUM", VanillaInstrument.below("DEEPSLATE_IRON_ORE"));
        assertEquals("PLING", VanillaInstrument.below("GLOWSTONE"));
        assertEquals("PIANO", VanillaInstrument.below("DIRT"));
        assertEquals("PIANO", VanillaInstrument.below("AIR"));
    }

    @Test
    void everyWoodenBlockIsBass() {
        for (String wood : new String[] {"OAK_SLAB", "SPRUCE_FENCE", "BIRCH_TRAPDOOR", "STRIPPED_CHERRY_LOG", "BARREL",
            "CRIMSON_STAIRS", "BAMBOO_MOSAIC", "PALE_OAK_PLANKS", "MUSHROOM_STEM", "LECTERN"}) {
            assertEquals("BASS_GUITAR", VanillaInstrument.below(wood), wood);
        }
        assertEquals("BASS_DRUM", VanillaInstrument.below("CRIMSON_NYLIUM"));
        assertEquals("PIANO", VanillaInstrument.below("OAK_LEAVES"));
        assertEquals("DIDGERIDOO", VanillaInstrument.below("CARVED_PUMPKIN"));
    }

    @Test
    void mobHeadsAboveSetTheInstrument() {
        assertEquals("ZOMBIE", VanillaInstrument.above("ZOMBIE_HEAD"));
        assertEquals("WITHER_SKELETON", VanillaInstrument.above("WITHER_SKELETON_SKULL"));
        assertEquals("CUSTOM_HEAD", VanillaInstrument.above("PLAYER_HEAD"));
        assertNull(VanillaInstrument.above("ZOMBIE_WALL_HEAD"), "wall heads are not on top of the block");
        assertNull(VanillaInstrument.above("AIR"));
    }

    @Test
    void onlyDestroyingExplosionsBreakModuleBlocks() {
        assertTrue(NoteBlockListener.destroysBlocks(ExplosionResult.DESTROY));
        assertTrue(NoteBlockListener.destroysBlocks(ExplosionResult.DESTROY_WITH_DECAY));
        assertFalse(NoteBlockListener.destroysBlocks(ExplosionResult.TRIGGER_BLOCK), "wind charges");
        assertFalse(NoteBlockListener.destroysBlocks(ExplosionResult.KEEP));
    }

    @Test
    void axesStripOnlyUnstrippedBlocks() {
        assertEquals("stripped_pewen_log", NoteBlockListener.strippedId("pewen_log"));
        assertEquals("", NoteBlockListener.strippedId("stripped_pewen_log"));
    }

    @Test
    void paperNoteblockUpdateSettingIsRead() {
        YamlConfiguration paper = new YamlConfiguration();
        assertFalse(NoteBlockService.noteblockUpdatesDisabled(paper));
        assertFalse(NoteBlockService.noteblockUpdatesDisabled(null));
        paper.set("block-updates.disable-noteblock-updates", true);
        assertTrue(NoteBlockService.noteblockUpdatesDisabled(paper));
    }

    @Test
    void onlyModuleCatalogRecipesMayUseModuleItems() {
        assertTrue(AlexsCavesCraftGuardTestAccess.isModuleRecipeKey("bigcasares", "ac_scarlet_magnet_recipe"));
        assertFalse(AlexsCavesCraftGuardTestAccess.isModuleRecipeKey("minecraft", "iron_ingot_from_nuggets"));
        assertFalse(AlexsCavesCraftGuardTestAccess.isModuleRecipeKey("bigcasares", "smoke_bomb_recipe"));
    }
}
