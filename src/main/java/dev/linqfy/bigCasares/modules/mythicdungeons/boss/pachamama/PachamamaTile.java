package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

/** One square of the floor chessboard, by column and row of the grid ({@link PachamamaArena#tileAt}). */
public record PachamamaTile(int column, int row) {

    /** The chessboard colour of the tile: 0 or 1. */
    public int parity() {
        return Math.floorMod(column + row, 2);
    }
}
