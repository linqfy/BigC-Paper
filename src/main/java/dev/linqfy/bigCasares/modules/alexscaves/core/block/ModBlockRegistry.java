package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Vanilla states of the module blocks, read from {@code alexscaves/blocks.yml}. The file is generated and
 * append-only: worlds store these states, so a broken or duplicated entry is a packaging error that fails
 * the enable. Lines are {@code variant: index} (note block) or {@code variant: base index [legacy index]};
 * a legacy note block state belongs to a block that moved to another base and is migrated when its chunk loads.
 * <p>
 * The tripwire and chorus plant bases are only safe when Paper's {@code disable-tripwire-updates} and
 * {@code disable-chorus-plant-updates} are on (vanilla would otherwise rewrite those states from their neighbours).
 * Without them ({@code shapedBasesActive} false) a block that moved keeps its legacy note block state as its only
 * state, the tripwire and chorus plant states are plain vanilla ones, and nothing is migrated.
 */
public final class ModBlockRegistry {
    private final Map<String, Map<String, CustomBlockState>> blocks;
    private final Map<CustomBlockState, Placed> byState;
    private final Map<CustomBlockState, CustomBlockState> legacy;
    private final boolean shapedBasesActive;

    private ModBlockRegistry(Map<String, Map<String, CustomBlockState>> blocks, Map<CustomBlockState, Placed> byState,
                             Map<CustomBlockState, CustomBlockState> legacy, boolean shapedBasesActive) {
        this.blocks = blocks;
        this.byState = byState;
        this.legacy = legacy;
        this.shapedBasesActive = shapedBasesActive;
    }

    /** The registry with the tripwire and chorus plant bases in use. */
    public static ModBlockRegistry parse(String text) {
        return parse(text, true);
    }

    /**
     * @param shapedBasesActive whether blocks that moved to tripwire / chorus plant use their new state; when false
     *                          they keep the note block state they had before and the new states are not module states
     */
    public static ModBlockRegistry parse(String text, boolean shapedBasesActive) {
        Map<String, Map<String, CustomBlockState>> blocks = new LinkedHashMap<>();
        Map<CustomBlockState, Placed> byState = new HashMap<>();
        Map<CustomBlockState, CustomBlockState> legacy = new HashMap<>();
        String current = null;
        int lineNumber = 0;
        for (String line : text.split("\\R")) {
            lineNumber++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (!line.startsWith(" ")) {
                current = line.trim();
                if (!current.endsWith(":") || !current.substring(0, current.length() - 1).matches("[a-z0-9_]+")) {
                    throw new IllegalStateException("blocks.yml línea " + lineNumber + ": bloque inválido");
                }
                current = current.substring(0, current.length() - 1);
                if (blocks.putIfAbsent(current, new LinkedHashMap<>()) != null) {
                    throw new IllegalStateException("blocks.yml: bloque duplicado " + current);
                }
                continue;
            }
            if (current == null) {
                throw new IllegalStateException("blocks.yml línea " + lineNumber + ": variante sin bloque");
            }
            String[] parts = line.trim().split(":");
            if (parts.length != 2) {
                throw new IllegalStateException("blocks.yml línea " + lineNumber + ": formato inválido");
            }
            String variant = parts[0].trim();
            String[] tokens = parts[1].trim().split("\\s+");
            CustomBlockState state;
            CustomBlockState old = null;
            try {
                if (tokens.length == 1) {
                    state = CustomBlockState.note(Integer.parseInt(tokens[0]));
                } else if (tokens.length == 2 || tokens.length == 4 && tokens[2].equals("legacy")) {
                    BlockBase base = BlockBase.byId(tokens[0])
                        .orElseThrow(() -> new IllegalArgumentException("base desconocida"));
                    state = new CustomBlockState(base, Integer.parseInt(tokens[1]));
                    if (tokens.length == 4) {
                        old = CustomBlockState.note(Integer.parseInt(tokens[3]));
                    }
                } else {
                    throw new IllegalArgumentException("formato");
                }
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("blocks.yml línea " + lineNumber + ": estado inválido", invalid);
            }
            if (!shapedBasesActive && state.base() != BlockBase.NOTE_BLOCK) {
                if (old == null) {
                    throw new IllegalStateException("blocks.yml línea " + lineNumber + ": " + state
                        + " no tiene estado legacy de note block al que volver");
                }
                state = old;
                old = null;
            }
            for (CustomBlockState used : old == null ? List.of(state) : List.of(state, old)) {
                if (!used.allocatable()) {
                    throw new IllegalStateException("blocks.yml línea " + lineNumber + ": " + used
                        + " es un estado vanilla reservado");
                }
                if (byState.putIfAbsent(used, new Placed(current, variant)) != null) {
                    throw new IllegalStateException("blocks.yml: estado " + used + " usado dos veces");
                }
            }
            if (old != null) {
                legacy.put(old, state);
            }
            blocks.get(current).put(variant, state);
        }
        Map<String, Map<String, CustomBlockState>> frozen = new LinkedHashMap<>();
        blocks.forEach((id, variants) -> frozen.put(id, Map.copyOf(variants)));
        return new ModBlockRegistry(Map.copyOf(frozen), Map.copyOf(byState), Map.copyOf(legacy),
            shapedBasesActive);
    }

    /** Whether blocks that are not full cubes use tripwire and chorus plant states (see the class comment). */
    public boolean shapedBasesActive() {
        return shapedBasesActive;
    }

    public boolean contains(String blockId) {
        return blocks.containsKey(blockId);
    }

    public Set<String> variants(String blockId) {
        Map<String, CustomBlockState> variants = blocks.get(blockId);
        return variants == null ? Set.of() : variants.keySet();
    }

    /** The current state of a variant (never a legacy one). */
    public Optional<CustomBlockState> state(String blockId, String variant) {
        Map<String, CustomBlockState> variants = blocks.get(blockId);
        return variants == null ? Optional.empty() : Optional.ofNullable(variants.get(variant));
    }

    /** The module block drawn by a state, current or legacy. */
    public Optional<Placed> at(CustomBlockState state) {
        return Optional.ofNullable(byState.get(state));
    }

    /** Every state that draws a module block, legacy ones included. */
    public Collection<CustomBlockState> usedStates() {
        return byState.keySet();
    }

    /** Legacy note block state -> the state it migrates to. */
    public Map<CustomBlockState, CustomBlockState> legacy() {
        return legacy;
    }

    public Set<String> blockIds() {
        return blocks.keySet();
    }

    /** A module block and the variant drawn by one state. */
    public record Placed(String blockId, String variant) {
    }
}
