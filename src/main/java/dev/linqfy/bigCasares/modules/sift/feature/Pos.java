package dev.linqfy.bigCasares.modules.sift.feature;

/** Immutable block position for feature ports (the mod's {@code BlockPos}). */
record Pos(int x, int y, int z) {

    Pos above() {
        return new Pos(x, y + 1, z);
    }

    Pos below() {
        return new Pos(x, y - 1, z);
    }

    Pos offset(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }
}
