package dev.linqfy.bigCasares.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of custom blocks and generator of the client-side blockstate files
 * that render them.
 *
 * <p>Generated files replace {@code assets/minecraft/blockstates/<mushroom>.json}.
 * Vanilla parts are kept but restricted (with {@code OR} conditions) to states
 * that are not allocated, so ordinary mushroom blocks keep their vanilla look.</p>
 */
public final class CustomBlockRegistry {

    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};
    private static final int[][] CAP_ROTATIONS = {{0, 0}, {0, 90}, {0, 180}, {0, 270}, {270, 0}, {90, 0}};

    private final Map<NamespacedId, CustomBlockDefinition> byId = new LinkedHashMap<>();
    private final Map<MushroomState, CustomBlockDefinition> byState = new LinkedHashMap<>();

    public synchronized void register(CustomBlockDefinition definition) {
        if (byId.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate custom block id: " + definition.id());
        }
        CustomBlockDefinition clash = byState.get(definition.state());
        if (clash != null) {
            throw new IllegalArgumentException("Blocks " + clash.id() + " and " + definition.id()
                + " both use backing state " + definition.state());
        }
        byId.put(definition.id(), definition);
        byState.put(definition.state(), definition);
    }

    public synchronized Optional<CustomBlockDefinition> byId(NamespacedId id) {
        return Optional.ofNullable(byId.get(id));
    }

    public synchronized Optional<CustomBlockDefinition> byState(MushroomState state) {
        return Optional.ofNullable(byState.get(state));
    }

    public synchronized Collection<CustomBlockDefinition> definitions() {
        return List.copyOf(byId.values());
    }

    public synchronized boolean isAllocated(MushroomState state) {
        return byState.containsKey(state);
    }

    /** Blockstate JSON for one mushroom block, preserving vanilla rendering for unallocated states. */
    public synchronized JsonObject blockstate(MushroomState.Base base) {
        List<MushroomState> vanillaDownTrue = new ArrayList<>();
        for (MushroomState state : MushroomState.all(base)) {
            if (state.down() && !byState.containsKey(state)) {
                vanillaDownTrue.add(state);
            }
        }
        JsonArray multipart = new JsonArray();
        for (int faceIndex = 0; faceIndex < FACES.length; faceIndex++) {
            String face = FACES[faceIndex];
            multipart.add(vanillaPart(face, true, base.capModel(), CAP_ROTATIONS[faceIndex], true, vanillaDownTrue));
        }
        for (int faceIndex = 0; faceIndex < FACES.length; faceIndex++) {
            String face = FACES[faceIndex];
            multipart.add(vanillaPart(face, false, "minecraft:block/mushroom_block_inside", CAP_ROTATIONS[faceIndex],
                false, vanillaDownTrue));
        }
        for (CustomBlockDefinition definition : byId.values()) {
            if (definition.state().base() != base) {
                continue;
            }
            multipart.add(Json.object()
                .put("apply", definition.apply())
                .put("when", definition.state().properties())
                .build());
        }
        return Json.object().put("multipart", multipart).build();
    }

    private static JsonObject vanillaPart(
        String face, boolean value, String model, int[] rotation, boolean uvlock, List<MushroomState> vanillaDownTrue
    ) {
        Json.ObjectBuilder apply = Json.object().put("model", model);
        // Vanilla only sets uvlock on rotated cap faces.
        if (uvlock && (rotation[0] != 0 || rotation[1] != 0)) {
            apply.put("uvlock", true);
        }
        if (rotation[0] != 0) {
            apply.put("x", rotation[0]);
        }
        if (rotation[1] != 0) {
            apply.put("y", rotation[1]);
        }
        JsonArray or = new JsonArray();
        String expected = Boolean.toString(value);
        if (!face.equals("down")) {
            or.add(Json.object().put(face, expected).put("down", "false").build());
        } else if (!value) {
            or.add(Json.object().put("down", "false").build());
        }
        for (MushroomState state : vanillaDownTrue) {
            if (state.face(face) == value) {
                or.add(Json.of(state.properties()));
            }
        }
        return Json.object().put("apply", apply.build()).put("when", Json.object().put("OR", or).build()).build();
    }
}
