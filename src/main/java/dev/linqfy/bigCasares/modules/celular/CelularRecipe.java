package dev.linqfy.bigCasares.modules.celular;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Eight iron ingots around one glass block. */
public final class CelularRecipe {

    public static final String KEY = "celular";
    public static final List<String> SHAPE = List.of("HHH", "HVH", "HHH");
    public static final Map<Character, String> INGREDIENTS = Map.of(
        'H', "IRON_INGOT",
        'V', "GLASS"
    );

    private CelularRecipe() {
    }

    public static Map<String, Integer> materialCount() {
        Map<String, Integer> count = new TreeMap<>();
        for (String row : SHAPE) {
            for (char symbol : row.toCharArray()) {
                count.merge(INGREDIENTS.get(symbol), 1, Integer::sum);
            }
        }
        return count;
    }
}
