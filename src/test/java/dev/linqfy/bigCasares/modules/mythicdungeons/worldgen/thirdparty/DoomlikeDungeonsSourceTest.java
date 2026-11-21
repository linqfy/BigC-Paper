package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonTheme;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicExtraPiece;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicExtraPiece.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoomlikeDungeonsSourceTest {

    @Test
    void piecesAreDeterministicAndShapedForTheContract() {
        DoomlikeDungeonsSource source = new DoomlikeDungeonsSource();
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            List<MythicExtraPiece> first = source.piecesFor(theme.culture());
            List<MythicExtraPiece> second = new DoomlikeDungeonsSource().piecesFor(theme.culture());
            assertEquals(2 * DoomlikeDungeonsSource.VARIANTS, first.size(), theme.culture());
            for (int index = 0; index < first.size(); index++) {
                assertArrayEquals(first.get(index).template().toBytes(), second.get(index).template().toBytes(),
                    theme.culture() + " " + first.get(index).name());
            }
            for (MythicExtraPiece piece : first) {
                int[] size = PackHarness.size(piece.template());
                for (int axis : size) {
                    assertTrue(axis >= 1 && axis <= 48, piece.name());
                }
                List<PackHarness.Jigsaw> jigsaws = PackHarness.jigsaws(piece.template());
                List<String> blocks = piece.template().paletteBlocks();
                assertTrue(blocks.contains("minecraft:spawner") || blocks.contains("minecraft:chest"),
                    theme.culture() + " " + piece.name() + " has no spawner or chest");
                if (piece.role() == Role.FULL_DUNGEON) {
                    List<PackHarness.Jigsaw> descents = jigsaws.stream()
                        .filter(jigsaw -> jigsaw.name().equals(ThirdPartyBlocks.DESCENT)).toList();
                    assertEquals(1, descents.size(), piece.name());
                    assertEquals("down", descents.getFirst().front());
                    assertEquals(0, descents.getFirst().y());
                    assertTrue(size[0] >= 12 && size[2] >= 12, theme.culture() + " " + piece.name() + " is a multi-room level");
                    assertTrue(hasGate(TemplateGrid.read(piece.template())), theme.culture() + " " + piece.name() + " has no gate");
                    for (PackHarness.Jigsaw jigsaw : jigsaws) {
                        if (jigsaw.name().equals(ThirdPartyBlocks.SIDE)) {
                            boolean onFace = jigsaw.x() == 0 || jigsaw.z() == 0 || jigsaw.x() == size[0] - 1
                                || jigsaw.z() == size[2] - 1;
                            assertTrue(onFace, piece.name() + " side doorway inside the template");
                        }
                    }
                } else {
                    assertEquals(Role.SIDE_ROOM, piece.role());
                    assertEquals(9, size[0]);
                    assertEquals(1, jigsaws.size());
                }
            }
        }
    }

    /** An open outer-wall cell at walking level (not a connector) lets players walk in from outside. */
    private static boolean hasGate(TemplateGrid grid) {
        int y = DoomlikeLayout.BASE;
        for (TemplateGrid.Cell cell : grid.cells()) {
            boolean face = cell.x() == 0 || cell.z() == 0 || cell.x() == grid.sizeX() - 1 || cell.z() == grid.sizeZ() - 1;
            if (face && cell.y() == y && cell.name().equals("minecraft:air") && "minecraft:air".equals(grid.name(cell.x(), y + 1, cell.z()))
                && !"minecraft:air".equals(grid.name(cell.x(), y - 1, cell.z()))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everyCultureHasADoomlikeThemeMapping() {
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            assertEquals(DoomlikeDungeonsSource.VARIANTS, DoomlikeTheme.BY_CULTURE.get(theme.culture()).size(), theme.culture());
        }
    }
}
