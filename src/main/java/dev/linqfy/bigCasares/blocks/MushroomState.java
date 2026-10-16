package dev.linqfy.bigCasares.blocks;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One of the 192 vanilla mushroom-block states, used as the server-side identity
 * of a custom full-cube block.
 *
 * <p>Vanilla only generates mushroom caps and stems with {@code down=false}, and
 * the framework normalises player-placed mushroom blocks, so the 31 states per
 * block with {@code down=true} (all-true excluded, it is the item default) are a
 * safe reserved pool: 93 custom blocks that clients render through a resource
 * pack and that world generation, saving and multiplayer treat as plain blocks.</p>
 */
public record MushroomState(Base base, boolean north, boolean east, boolean south, boolean west, boolean up, boolean down) {

    public enum Base {
        BROWN("minecraft:brown_mushroom_block", "minecraft:block/brown_mushroom_block"),
        RED("minecraft:red_mushroom_block", "minecraft:block/red_mushroom_block"),
        STEM("minecraft:mushroom_stem", "minecraft:block/mushroom_stem");

        private final String blockId;
        private final String capModel;

        Base(String blockId, String capModel) {
            this.blockId = blockId;
            this.capModel = capModel;
        }

        public String blockId() {
            return blockId;
        }

        public String capModel() {
            return capModel;
        }

        /** Bukkit material name, e.g. {@code BROWN_MUSHROOM_BLOCK}. */
        public String material() {
            return blockId.substring("minecraft:".length()).toUpperCase(Locale.ROOT);
        }
    }

    public static MushroomState of(Base base, boolean north, boolean east, boolean south, boolean west, boolean up, boolean down) {
        return new MushroomState(base, north, east, south, west, up, down);
    }

    /**
     * Parses the compact form {@code brown:NESWUD} where each letter is present
     * when that face is {@code true}, e.g. {@code stem:D} or {@code red:NESD}.
     */
    public static MushroomState parse(String compact) {
        String[] parts = compact.trim().toLowerCase(Locale.ROOT).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected <base>:<faces>, got " + compact);
        }
        Base base = Base.valueOf(parts[0].toUpperCase(Locale.ROOT));
        String faces = parts[1];
        for (char face : faces.toCharArray()) {
            if ("neswud".indexOf(face) < 0) {
                throw new IllegalArgumentException("Unknown face '" + face + "' in " + compact);
            }
        }
        return new MushroomState(base, faces.indexOf('n') >= 0, faces.indexOf('e') >= 0, faces.indexOf('s') >= 0,
            faces.indexOf('w') >= 0, faces.indexOf('u') >= 0, faces.indexOf('d') >= 0);
    }

    /** All 64 states of a base in a stable order. */
    public static List<MushroomState> all(Base base) {
        List<MushroomState> states = new ArrayList<>(64);
        for (int bits = 0; bits < 64; bits++) {
            states.add(new MushroomState(base, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                (bits & 8) != 0, (bits & 16) != 0, (bits & 32) != 0));
        }
        return states;
    }

    /** True for states in the reserved pool (see class documentation). */
    public boolean isReservable() {
        return down && !isAllTrue();
    }

    public boolean isAllTrue() {
        return north && east && south && west && up && down;
    }

    public boolean face(String name) {
        return switch (name) {
            case "north" -> north;
            case "east" -> east;
            case "south" -> south;
            case "west" -> west;
            case "up" -> up;
            case "down" -> down;
            default -> throw new IllegalArgumentException("Unknown face " + name);
        };
    }

    public Map<String, String> properties() {
        TreeMap<String, String> properties = new TreeMap<>();
        properties.put("down", Boolean.toString(down));
        properties.put("east", Boolean.toString(east));
        properties.put("north", Boolean.toString(north));
        properties.put("south", Boolean.toString(south));
        properties.put("up", Boolean.toString(up));
        properties.put("west", Boolean.toString(west));
        return properties;
    }

    public BlockStateSpec toSpec() {
        return BlockStateSpec.of(base.blockId(), properties());
    }

    /** Bukkit {@code createBlockData} syntax. */
    public String asString() {
        return toSpec().asString();
    }

    public String compact() {
        StringBuilder faces = new StringBuilder();
        if (north) faces.append('N');
        if (east) faces.append('E');
        if (south) faces.append('S');
        if (west) faces.append('W');
        if (up) faces.append('U');
        if (down) faces.append('D');
        return base.name().toLowerCase(Locale.ROOT) + ":" + faces;
    }

    @Override
    public String toString() {
        return compact();
    }
}
