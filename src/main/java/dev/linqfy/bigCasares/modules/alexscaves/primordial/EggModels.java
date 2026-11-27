package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The BetterModel models of the placed dinosaur eggs, baked by {@code tools/alexscaves/block_model_to_bbmodel.py}: one
 * per species and hatch stage ({@code _cracked} and {@code _very_cracked} after the first). The Tremorzilla egg never
 * hatches by itself, so it has a single model.
 */
public final class EggModels {
    static final List<String> SPECIES = List.of("atlatitan", "grottoceratops", "relicheirus", "tremorsaurus",
        "subterranodon", "vallumraptor");
    private static final String[] SUFFIXES = {"", "_cracked", "_very_cracked"};
    private static final String PREFIX = "alexscaves_";
    private static final String TREMORZILLA = "alexscaves_tremorzilla_egg";

    private EggModels() {
    }

    /** The model of an egg of that mob at that hatch stage (0 to 2); empty for a mob without one. */
    public static Optional<String> key(String mobId, int stage) {
        if (Tremorzilla.ID.equals(mobId)) {
            return Optional.of(TREMORZILLA);
        }
        if (!SPECIES.contains(mobId)) {
            return Optional.empty();
        }
        return Optional.of(PREFIX + mobId + "_egg" + SUFFIXES[Math.max(0, Math.min(stage, SUFFIXES.length - 1))]);
    }

    /** Every egg model the module ships. */
    public static List<String> all() {
        List<String> keys = new ArrayList<>();
        for (String species : SPECIES) {
            for (int stage = 0; stage < SUFFIXES.length; stage++) {
                key(species, stage).ifPresent(keys::add);
            }
        }
        keys.add(TREMORZILLA);
        return List.copyOf(keys);
    }
}
