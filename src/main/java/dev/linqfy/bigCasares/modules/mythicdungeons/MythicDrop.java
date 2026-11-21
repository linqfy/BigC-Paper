package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.Material;

import java.util.Objects;

/** Un drop vanilla extra de un mob mitico: entre {@code min} y {@code max} unidades con probabilidad {@code chance}. */
public record MythicDrop(Material material, int min, int max, double chance) {

    public MythicDrop {
        Objects.requireNonNull(material, "material");
        if (min < 0 || max < min || chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("invalid mythic drop: " + material);
        }
    }

    /**
     * Cuantas unidades caen con dos tiradas uniformes en [0, 1): una decide si cae, la otra cuantas.
     */
    public int roll(double chanceRoll, double amountRoll) {
        if (chanceRoll >= chance) {
            return 0;
        }
        int span = max - min + 1;
        return min + Math.min(span - 1, (int) Math.floor(amountRoll * span));
    }
}
