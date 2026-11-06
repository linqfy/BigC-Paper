package dev.linqfy.bigCasares.modules.mutango;

import java.util.ArrayList;
import java.util.List;

/**
 * Armadura de Lef, Lihuen, Lican, Neguechen y los animales que invoca, segun cuantas veces ya le gano quien empezo la
 * pelea (sin Bukkit), como DonPollosMinionArmor en BigC. Primera vez: sin armadura. Despues cobre, hierro, diamante y
 * netherite; y con netherite, cada vez mejores encantamientos (Proteccion hasta V y despues Espinas hasta III).
 * <p>
 * Se ve en el modelo (hay una version de cada modelo por material) y protege de verdad:
 * puntos de armadura y dureza como un juego completo de ese material, y la Proteccion y las Espinas como si cada pieza
 * las tuviera.
 */
final class MutangoArmor {

    enum Tier {
        NONE(null, 0, 0, 0),
        COBRE("cobre", 10, 0, 0),
        HIERRO("hierro", 15, 0, 0),
        DIAMANTE("diamante", 20, 8, 0),
        NETHERITE("netherite", 20, 12, 0.4);

        /** Como termina el modelo con esta armadura (bigcasares_boss_mutango_cobre...). */
        final String suffix;
        /** Lo que da un juego completo (casco, pechera, pantalones y botas) de ese material. */
        final double armor;
        final double toughness;
        final double knockbackResistance;

        Tier(String suffix, double armor, double toughness, double knockbackResistance) {
            this.suffix = suffix;
            this.armor = armor;
            this.toughness = toughness;
            this.knockbackResistance = knockbackResistance;
        }
    }

    static final int MAX_PROTECTION = 5;
    static final int MAX_THORNS = 3;
    /** Piezas de un juego completo: la Proteccion y las Espinas cuentan una vez por pieza, como en Minecraft. */
    static final int PIECES = 4;

    /** Modelos que tienen version con armadura. */
    static final List<String> ARMORED_BASE_MODELS = List.of("bigcasares_boss_mutango", "bigcasares_boss_mutango_2ojos",
        "bigcasares_boss_mutango_3ojos", "bigcasares_neguechen");

    private MutangoArmor() {
    }

    static Tier tier(int level) {
        Tier[] tiers = Tier.values();
        return tiers[Math.max(0, Math.min(level, tiers.length - 1))];
    }

    /** Proteccion de cada pieza: arranca cuando la armadura ya es de netherite. */
    static int protection(int level) {
        return Math.max(0, Math.min(MAX_PROTECTION, level - Tier.NETHERITE.ordinal()));
    }

    /** Espinas de cada pieza: despues de Proteccion V. */
    static int thorns(int level) {
        return Math.max(0, Math.min(MAX_THORNS, level - Tier.NETHERITE.ordinal() - MAX_PROTECTION));
    }

    /**
     * Cuanto del golpe pasa con la Proteccion (como en Minecraft: 4% por nivel y por pieza, hasta 80%). 1 = todo.
     */
    static double protectionMultiplier(int level) {
        int points = Math.min(20, protection(level) * PIECES);
        return 1 - points * 0.04;
    }

    /** Probabilidad de que las Espinas te devuelvan el golpe (15% por nivel, por cada pieza). */
    static double thornsChance(int level) {
        double perPiece = Math.min(1, 0.15 * thorns(level));
        return 1 - Math.pow(1 - perPiece, PIECES);
    }

    /** Modelo con la armadura de su nivel si tiene version con armadura; si no, el de siempre. */
    static String model(String baseModel, int level) {
        Tier tier = tier(level);
        return tier == Tier.NONE || !ARMORED_BASE_MODELS.contains(baseModel) ? baseModel : baseModel + "_" + tier.suffix;
    }

    static List<String> armoredModels() {
        List<String> models = new ArrayList<>();
        for (String base : ARMORED_BASE_MODELS) {
            for (Tier tier : Tier.values()) {
                if (tier != Tier.NONE) {
                    models.add(base + "_" + tier.suffix);
                }
            }
        }
        return models;
    }

    /** La armadura de caballo de cada material (el caballo y el burro la muestran); null si no hay. */
    static String horseArmor(int level) {
        return switch (tier(level)) {
            case NONE -> null;
            case COBRE -> "COPPER_HORSE_ARMOR";
            case HIERRO -> "IRON_HORSE_ARMOR";
            case DIAMANTE -> "DIAMOND_HORSE_ARMOR";
            case NETHERITE -> "NETHERITE_HORSE_ARMOR";
        };
    }
}
