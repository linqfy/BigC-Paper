package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.CavePaintings;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructurePaintingMarksTest {
    @Test
    void marksParseFromTheIndex() {
        List<CavePaintings.Mark> marks = StructureTemplates.parseMarks(JsonParser.parseString(
            "[[0, 3, 6, \"subterranodon_ride\", \"east\"], [4, 1, 2, \"dark\", \"down\"]]").getAsJsonArray());
        assertEquals(List.of(new CavePaintings.Mark(0, 3, 6, "subterranodon_ride", BlockFace.EAST),
            new CavePaintings.Mark(4, 1, 2, "dark", BlockFace.DOWN)), marks);
        assertEquals(List.of(), StructureTemplates.parseMarks(null));
        assertThrows(IllegalStateException.class, () -> StructureTemplates.parseMarks(JsonParser.parseString(
            "[[0, 0, 0, \"dark\", \"sideways\"]]").getAsJsonArray()));
    }
}
