package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Vanilla block whose spare states draw module blocks. Must match tools/alexscaves/block_bases.py.
 * Note blocks hold the opaque cubes; tripwire (no collision, no occlusion) and chorus plant (partial
 * collision, no occlusion) hold the blocks that are not full cubes, so the client still draws the floor
 * and walls next to them.
 */
public enum BlockBase {
    /** {@code instrument * 50 + note * 2 + powered}; harp states (0-49) stay vanilla. */
    NOTE_BLOCK("note_block", 800),
    /** {@code attached*64 + disarmed*32 + powered*16 + north*8 + east*4 + south*2 + west}; only disarmed states. */
    TRIPWIRE("tripwire", 128),
    /** {@code down*32 + up*16 + north*8 + east*4 + south*2 + west}; only states End generation never makes. */
    CHORUS_PLANT("chorus_plant", 64);

    private final String id;
    private final int count;

    BlockBase(String id, int count) {
        this.id = id;
        this.count = count;
    }

    public String id() {
        return id;
    }

    public int count() {
        return count;
    }

    public static Optional<BlockBase> byId(String id) {
        for (BlockBase base : values()) {
            if (base.id.equals(id)) {
                return Optional.of(base);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a state may draw a module block. Tripwire: a vanilla string is only disarmed during the tick
     * shears break it. Chorus plant: End generation makes plants with {@code down=true}, or {@code down=false}
     * with exactly one horizontal link (a branch towards its stem); the default (all false) state stays vanilla.
     */
    public boolean allocatable(int index) {
        if (index < 0 || index >= count) {
            return false;
        }
        return switch (this) {
            case NOTE_BLOCK -> index >= NoteBlockState.FIRST_CUSTOM;
            case TRIPWIRE -> (index >> 5 & 1) == 1;
            case CHORUS_PLANT -> {
                int horizontal = Integer.bitCount(index & 0b1111);
                yield (index >> 5 & 1) == 0 && (horizontal >= 2 || horizontal == 0 && (index >> 4 & 1) == 1);
            }
        };
    }

    /** Properties of a state, sorted by name (the order of Java block data strings). */
    public Map<String, String> properties(int index) {
        if (index < 0 || index >= count) {
            throw new IllegalArgumentException(id + " state out of range: " + index);
        }
        Map<String, String> properties = new TreeMap<>();
        switch (this) {
            case NOTE_BLOCK -> {
                NoteBlockState note = new NoteBlockState(index);
                properties.put("instrument", note.instrument());
                properties.put("note", Integer.toString(note.note()));
                properties.put("powered", Boolean.toString(note.powered()));
            }
            case TRIPWIRE -> {
                properties.put("attached", bit(index, 6));
                properties.put("disarmed", bit(index, 5));
                properties.put("powered", bit(index, 4));
                sides(properties, index);
            }
            case CHORUS_PLANT -> {
                properties.put("down", bit(index, 5));
                properties.put("up", bit(index, 4));
                sides(properties, index);
            }
        }
        return properties;
    }

    /** Inverse of {@link #properties}: empty when a property is missing or out of range. */
    public Optional<Integer> index(Map<String, String> properties) {
        try {
            return switch (this) {
                case NOTE_BLOCK -> NoteBlockState.of(properties.get("instrument"),
                        Integer.parseInt(properties.getOrDefault("note", "-1")), flag(properties, "powered"))
                    .map(NoteBlockState::index);
                case TRIPWIRE -> Optional.of((flag(properties, "attached") ? 64 : 0) + (flag(properties, "disarmed") ? 32 : 0)
                    + (flag(properties, "powered") ? 16 : 0) + sides(properties));
                case CHORUS_PLANT -> Optional.of((flag(properties, "down") ? 32 : 0) + (flag(properties, "up") ? 16 : 0)
                    + sides(properties));
            };
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /** Block data string, e.g. {@code minecraft:tripwire[attached=false,disarmed=true,...]}. */
    public String blockDataString(int index) {
        StringBuilder out = new StringBuilder("minecraft:").append(id).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : properties(index).entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.append(']').toString();
    }

    /** Parses {@code minecraft:<base>[k=v,...]} (Bukkit's {@code BlockData#getAsString}). */
    public static Optional<CustomBlockState> parse(String blockData) {
        int open = blockData.indexOf('[');
        String name = (open < 0 ? blockData : blockData.substring(0, open)).toLowerCase(Locale.ROOT);
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        Optional<BlockBase> base = byId(name);
        if (base.isEmpty() || open < 0 || !blockData.endsWith("]")) {
            return Optional.empty();
        }
        Map<String, String> properties = new LinkedHashMap<>();
        for (String pair : blockData.substring(open + 1, blockData.length() - 1).split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                properties.put(pair.substring(0, equals), pair.substring(equals + 1));
            }
        }
        return base.get().index(properties).map(index -> new CustomBlockState(base.get(), index));
    }

    private static String bit(int index, int shift) {
        return Boolean.toString((index >> shift & 1) == 1);
    }

    private static void sides(Map<String, String> properties, int index) {
        properties.put("north", bit(index, 3));
        properties.put("east", bit(index, 2));
        properties.put("south", bit(index, 1));
        properties.put("west", bit(index, 0));
    }

    private static int sides(Map<String, String> properties) {
        return (flag(properties, "north") ? 8 : 0) + (flag(properties, "east") ? 4 : 0)
            + (flag(properties, "south") ? 2 : 0) + (flag(properties, "west") ? 1 : 0);
    }

    private static boolean flag(Map<String, String> properties, String name) {
        String value = properties.get(name);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException(name);
    }
}
