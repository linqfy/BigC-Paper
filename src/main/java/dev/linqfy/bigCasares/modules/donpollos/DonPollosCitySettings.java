package dev.linqfy.bigCasares.modules.donpollos;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Ciudades de Don Pollo en la generacion del mundo. */
public record DonPollosCitySettings(
    boolean enabled,
    List<String> worlds,
    int spacing,
    double chance,
    int minDistanceFromSpawn,
    int blocksPerTick,
    Map<DonPolloVariant, Integer> pollos
) {

    public DonPollosCitySettings {
        worlds = List.copyOf(worlds == null ? List.of() : worlds);
        Map<DonPolloVariant, Integer> copy = new EnumMap<>(DonPolloVariant.class);
        if (pollos != null) {
            copy.putAll(pollos);
        }
        for (Map.Entry<DonPolloVariant, Integer> entry : copy.entrySet()) {
            if (entry.getValue() < 0 || entry.getValue() > 30) {
                throw new IllegalArgumentException("ciudades.don-pollos." + entry.getKey().id() + " tiene que estar entre 0 y 30");
            }
        }
        pollos = Map.copyOf(copy);
        if (blocksPerTick < 100) {
            throw new IllegalArgumentException("ciudades.blocks-per-tick tiene que ser al menos 100");
        }
    }

    public static DonPollosCitySettings defaults() {
        Map<DonPolloVariant, Integer> pollos = new EnumMap<>(DonPolloVariant.class);
        pollos.put(DonPolloVariant.COMUN, 4);
        pollos.put(DonPolloVariant.GORDITO, 2);
        pollos.put(DonPolloVariant.SALSERO, 2);
        pollos.put(DonPolloVariant.AURA_67, 2);
        pollos.put(DonPolloVariant.FINO, 2);
        return new DonPollosCitySettings(true, List.of("world"), 1400, 1.0, 500, 6000, pollos);
    }

    public int count(DonPolloVariant variant) {
        return pollos.getOrDefault(variant, 0);
    }
}
