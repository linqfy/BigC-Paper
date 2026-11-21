package dev.linqfy.bigCasares.modules.trimabilities;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Passive bonus granted by a full set of armor sharing the same trim pattern. Bonuses are deliberately
 * mild: they reward completing a set without competing with netherite or enchantments.
 */
enum TrimAbility {

    SENTRY("sentry", "Centinela", "-15% de daño de proyectiles", Perk.reduce(DamageKind.PROJECTILE, 0.15)),
    DUNE("dune", "Duna", "-25% de daño de explosiones", Perk.reduce(DamageKind.EXPLOSION, 0.25)),
    COAST("coast", "Costa", "Gracia de delfín en el agua", Perk.effect("dolphins_grace", true)),
    WILD("wild", "Salvaje", "Salto I", Perk.effect("jump_boost", false)),
    WARD("ward", "Guardián", "-10% de daño cuerpo a cuerpo", Perk.reduce(DamageKind.MELEE, 0.10)),
    EYE("eye", "Ojo", "Visión nocturna", Perk.effect("night_vision", false)),
    VEX("vex", "Vex", "8% de esquivar golpes cuerpo a cuerpo", Perk.dodge(0.08)),
    TIDE("tide", "Marea", "Poder del conducto en el agua", Perk.effect("conduit_power", true)),
    SNOUT("snout", "Hocico", "Resistencia al fuego", Perk.effect("fire_resistance", false)),
    RIB("rib", "Costilla", "Inmune al efecto Wither", Perk.immunity("wither")),
    SPIRE("spire", "Aguja", "-40% de daño de caída", Perk.reduce(DamageKind.FALL, 0.40)),
    WAYFINDER("wayfinder", "Explorador", "+8% de velocidad al caminar",
        Perk.attribute("movement_speed", 0.08, true)),
    SHAPER("shaper", "Modelador", "Prisa minera I", Perk.effect("haste", false)),
    SILENCE("silence", "Silencio", "Inmune a Oscuridad y Ceguera", Perk.immunity("darkness", "blindness")),
    RAISER("raiser", "Criador", "+2 corazones de vida máxima", Perk.attribute("max_health", 4.0, false)),
    HOST("host", "Anfitrión", "Gasta 30% menos hambre", Perk.hunger(0.30)),
    FLOW("flow", "Flujo", "+30% de resistencia al empuje", Perk.attribute("knockback_resistance", 0.3, false)),
    BOLT("bolt", "Rayo", "+10% de velocidad de ataque", Perk.attribute("attack_speed", 0.10, true));

    private final String patternId;
    private final String displayName;
    private final String description;
    private final Perk perk;

    TrimAbility(String patternId, String displayName, String description, Perk perk) {
        this.patternId = patternId;
        this.displayName = displayName;
        this.description = description;
        this.perk = perk;
    }

    String patternId() {
        return patternId;
    }

    String displayName() {
        return displayName;
    }

    String description() {
        return description;
    }

    Perk perk() {
        return perk;
    }

    static Optional<TrimAbility> byPattern(String patternId) {
        if (patternId == null) {
            return Optional.empty();
        }
        String normalized = patternId.toLowerCase(Locale.ROOT);
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            normalized = normalized.substring(colon + 1);
        }
        for (TrimAbility ability : values()) {
            if (ability.patternId.equals(normalized)) {
                return Optional.of(ability);
            }
        }
        return Optional.empty();
    }

    enum DamageKind {
        PROJECTILE, EXPLOSION, MELEE, FALL
    }

    enum PerkType {
        DAMAGE_REDUCTION, EFFECT, IMMUNITY, DODGE, HUNGER, ATTRIBUTE
    }

    /**
     * What the ability does. Bukkit types are referenced by key so the table stays testable without a
     * live registry; the runtime resolves them.
     *
     * @param keys      potion effect or attribute keys (vanilla, without namespace)
     * @param amount    reduction/chance fraction, or the attribute amount
     * @param waterOnly effect applies only while the player is in water
     * @param multiply  attribute amount is a multiplier of the base value instead of a flat bonus
     */
    record Perk(PerkType type, DamageKind damageKind, List<String> keys, double amount,
                boolean waterOnly, boolean multiply) {

        static Perk reduce(DamageKind kind, double fraction) {
            return new Perk(PerkType.DAMAGE_REDUCTION, kind, List.of(), fraction, false, false);
        }

        static Perk effect(String effect, boolean waterOnly) {
            return new Perk(PerkType.EFFECT, null, List.of(effect), 0.0, waterOnly, false);
        }

        static Perk immunity(String... effects) {
            return new Perk(PerkType.IMMUNITY, null, List.of(effects), 0.0, false, false);
        }

        static Perk dodge(double chance) {
            return new Perk(PerkType.DODGE, DamageKind.MELEE, List.of(), chance, false, false);
        }

        static Perk hunger(double chance) {
            return new Perk(PerkType.HUNGER, null, List.of(), chance, false, false);
        }

        static Perk attribute(String attribute, double amount, boolean multiply) {
            return new Perk(PerkType.ATTRIBUTE, null, List.of(attribute), amount, false, multiply);
        }

        /** Damage after this perk, for a hit of the given kind; {@code roll} is uniform in [0, 1). */
        double adjustDamage(DamageKind hitKind, double damage, double roll) {
            if (hitKind == null || hitKind != damageKind) {
                return damage;
            }
            return switch (type) {
                case DAMAGE_REDUCTION -> damage * (1.0 - amount);
                case DODGE -> roll < amount ? 0.0 : damage;
                default -> damage;
            };
        }
    }
}
