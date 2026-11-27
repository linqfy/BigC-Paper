package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.block.structure.StructureRotation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NoteBlockRotationsTest {
    @Test
    void facingsTurnClockwiseLikeVanilla() {
        assertEquals("east", NoteBlockRotations.turnVariant("bone_worms", "north", StructureRotation.CLOCKWISE_90));
        assertEquals("west", NoteBlockRotations.turnVariant("bone_worms", "north", StructureRotation.COUNTERCLOCKWISE_90));
        assertEquals("north", NoteBlockRotations.turnVariant("bone_worms", "south", StructureRotation.CLOCKWISE_180));
        assertEquals("south_true", NoteBlockRotations.turnVariant("bone_ribs", "east_true", StructureRotation.CLOCKWISE_90));
        assertEquals("west_3", NoteBlockRotations.turnVariant("mussel", "south_3", StructureRotation.CLOCKWISE_90));
    }

    @Test
    void axesSwapOnQuarterTurnsOnly() {
        assertEquals("z_2", NoteBlockRotations.turnVariant("thin_bone", "x_2", StructureRotation.CLOCKWISE_90));
        assertEquals("x_active", NoteBlockRotations.turnVariant("abyssmarine_pillar", "z_active", StructureRotation.COUNTERCLOCKWISE_90));
        assertEquals("x", NoteBlockRotations.turnVariant("hollow_bone", "x", StructureRotation.CLOCKWISE_180));
        assertNull(NoteBlockRotations.turnVariant("hollow_bone", "y", StructureRotation.CLOCKWISE_90));
        assertNull(NoteBlockRotations.turnVariant("bone_worms", "up", StructureRotation.CLOCKWISE_90));
    }

    @Test
    void baleenBoneSwapsItsXFlag() {
        assertEquals("x", NoteBlockRotations.turnVariant("baleen_bone", "default", StructureRotation.CLOCKWISE_90));
        assertEquals("default", NoteBlockRotations.turnVariant("baleen_bone", "x", StructureRotation.COUNTERCLOCKWISE_90));
        assertNull(NoteBlockRotations.turnVariant("baleen_bone", "x", StructureRotation.CLOCKWISE_180));
    }
}
