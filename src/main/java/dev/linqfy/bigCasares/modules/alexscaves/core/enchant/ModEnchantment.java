package dev.linqfy.bigCasares.modules.alexscaves.core.enchant;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The 51 weapon enchantments of Alex's Caves ({@code ACEnchantmentRegistry}).
 * Each one only applies to one module item. Enchantability follows
 * {@code ACWeaponEnchantment}: minimum {@code 1 + (level - 1) * minCost}.
 */
public enum ModEnchantment {
    FIELD_EXTENSION("field_extension", "ac_galena_gauntlet", Rarity.COMMON, 4, 6),
    CRYSTALLIZATION("crystallization", "ac_galena_gauntlet", Rarity.RARE, 1, 15),
    FERROUS_HASTE("ferrous_haste", "ac_galena_gauntlet", Rarity.RARE, 1, 15),
    ARROW_INDUCTING("arrow_inducting", "ac_resistor_shield", Rarity.RARE, 1, 18),
    HEAVY_SLAM("heavy_slam", "ac_resistor_shield", Rarity.COMMON, 3, 6),
    SWIFTWOOD("swiftwood", "ac_primitive_club", Rarity.RARE, 3, 8),
    BONKING("bonking", "ac_primitive_club", Rarity.VERY_RARE, 1, 18),
    DAZING_SWEEP("dazing_sweep", "ac_primitive_club", Rarity.RARE, 2, 10),
    PLUMMETING_FLIGHT("plummeting_flight", "ac_extinction_spear", Rarity.RARE, 3, 13),
    HERD_PHALANX("herd_phalanx", "ac_extinction_spear", Rarity.RARE, 3, 13),
    CHOMPING_SPIRIT("chomping_spirit", "ac_extinction_spear", Rarity.RARE, 2, 10),
    ENERGY_EFFICIENCY("energy_efficiency", "ac_raygun", Rarity.COMMON, 3, 5),
    SOLAR("solar", "ac_raygun", Rarity.COMMON, 1, 14),
    X_RAY("x_ray", "ac_raygun", Rarity.COMMON, 1, 12),
    GAMMA_RAY("gamma_ray", "ac_raygun", Rarity.RARE, 1, 18),
    SECOND_WAVE("second_wave", "ac_ortholance", Rarity.RARE, 1, 12),
    FLINGING("flinging", "ac_ortholance", Rarity.COMMON, 3, 8),
    SEA_SWING("sea_swing", "ac_ortholance", Rarity.RARE, 1, 10),
    TSUNAMI("tsunami", "ac_ortholance", Rarity.VERY_RARE, 1, 20),
    CHARTING_CALL("charting_call", "ac_magic_conch", Rarity.COMMON, 4, 7),
    LASTING_MORALE("lasting_morale", "ac_magic_conch", Rarity.RARE, 3, 8),
    TAXING_BELLOW("taxing_bellow", "ac_magic_conch", Rarity.RARE, 1, 19),
    ENVELOPING_BUBBLE("enveloping_bubble", "ac_sea_staff", Rarity.RARE, 1, 13),
    BOUNCING_BOLT("bouncing_bolt", "ac_sea_staff", Rarity.RARE, 1, 13),
    SEAPAIRING("seapairing", "ac_sea_staff", Rarity.VERY_RARE, 1, 10),
    TRIPLE_SPLASH("triple_splash", "ac_sea_staff", Rarity.RARE, 1, 15),
    SOAK_SEEKING("soak_seeking", "ac_sea_staff", Rarity.COMMON, 3, 5),
    DETONATING_DEATH("detonating_death", "ac_totem_of_possession", Rarity.RARE, 1, 11),
    RAPID_POSSESSION("rapid_possession", "ac_totem_of_possession", Rarity.COMMON, 3, 5),
    SIGHTLESS("sightless", "ac_totem_of_possession", Rarity.RARE, 1, 13),
    ASTRAL_TRANSFERRING("astral_transferring", "ac_totem_of_possession", Rarity.RARE, 1, 12),
    IMPENDING_STAB("impending_stab", "ac_desolate_dagger", Rarity.COMMON, 3, 6),
    SATED_BLADE("sated_blade", "ac_desolate_dagger", Rarity.COMMON, 2, 11),
    DOUBLE_STAB("double_stab", "ac_desolate_dagger", Rarity.RARE, 1, 12),
    PRECISE_VOLLEY("precise_volley", "ac_dreadbow", Rarity.RARE, 1, 18),
    DARK_NOCK("dark_nock", "ac_dreadbow", Rarity.RARE, 3, 10),
    RELENTLESS_DARKNESS("relentless_darkness", "ac_dreadbow", Rarity.VERY_RARE, 1, 20),
    TWILIGHT_PERFECTION("twilight_perfection", "ac_dreadbow", Rarity.RARE, 3, 7),
    SHADED_RESPITE("shaded_respite", "ac_dreadbow", Rarity.VERY_RARE, 1, 9),
    TARGETED_RICOCHET("targeted_ricochet", "ac_shot_gum", Rarity.RARE, 1, 16),
    TRIPLE_SPLIT("triple_split", "ac_shot_gum", Rarity.RARE, 1, 12),
    BOUNCY_BALL("bouncy_ball", "ac_shot_gum", Rarity.COMMON, 3, 7),
    EXPLOSIVE_FLAVOR("explosive_flavor", "ac_shot_gum", Rarity.VERY_RARE, 1, 16),
    FAR_FLUNG("far_flung", "ac_candy_cane_hook", Rarity.COMMON, 3, 6),
    SHARP_CANE("sharp_cane", "ac_candy_cane_hook", Rarity.COMMON, 2, 8),
    STRAIGHT_HOOK("straight_hook", "ac_candy_cane_hook", Rarity.RARE, 1, 12),
    SPELL_LASTING("spell_lasting", "ac_sugar_staff", Rarity.COMMON, 3, 8),
    PEPPERMINT_PUNTING("peppermint_punting", "ac_sugar_staff", Rarity.RARE, 1, 12),
    HUMUNGOUS_HEX("humungous_hex", "ac_sugar_staff", Rarity.UNCOMMON, 2, 9),
    MULTIPLE_MINT("multiple_mint", "ac_sugar_staff", Rarity.UNCOMMON, 2, 9),
    SEEKCANDY("seekcandy", "ac_sugar_staff", Rarity.RARE, 1, 16);

    public static final String NAMESPACE = "alexscaves";
    private static final Map<ModEnchantment, Set<ModEnchantment>> INCOMPATIBLE = new EnumMap<>(ModEnchantment.class);

    static {
        for (ModEnchantment enchantment : values()) {
            INCOMPATIBLE.put(enchantment, EnumSet.noneOf(ModEnchantment.class));
        }
        incompatible(BOUNCING_BOLT, TRIPLE_SPLASH);
        incompatible(DETONATING_DEATH, ASTRAL_TRANSFERRING);
        incompatible(IMPENDING_STAB, DOUBLE_STAB);
        incompatible(RELENTLESS_DARKNESS, DARK_NOCK);
        incompatible(RELENTLESS_DARKNESS, PRECISE_VOLLEY);
        incompatible(RELENTLESS_DARKNESS, TWILIGHT_PERFECTION);
        incompatible(SECOND_WAVE, TSUNAMI);
        incompatible(TARGETED_RICOCHET, TRIPLE_SPLIT);
        incompatible(X_RAY, GAMMA_RAY);
    }

    private final String id;
    private final String itemId;
    private final Rarity rarity;
    private final int maxLevel;
    private final int minCost;

    ModEnchantment(String id, String itemId, Rarity rarity, int maxLevel, int minCost) {
        this.id = id;
        this.itemId = itemId;
        this.rarity = rarity;
        this.maxLevel = maxLevel;
        this.minCost = minCost;
    }

    public String id() {
        return id;
    }

    public String key() {
        return NAMESPACE + ":" + id;
    }

    /** Catalog id of the only item that can carry this enchantment. */
    public String itemId() {
        return itemId;
    }

    public Rarity rarity() {
        return rarity;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public int minCost() {
        return minCost;
    }

    public int minEnchantability(int level) {
        return 1 + (Math.max(1, level) - 1) * minCost;
    }

    public boolean isCompatibleWith(ModEnchantment other) {
        return other != this && !INCOMPATIBLE.get(this).contains(other);
    }

    /** Taxing Bellow cannot be combined with these vanilla enchantments. */
    public Set<String> incompatibleVanilla() {
        return this == TAXING_BELLOW ? Set.of("minecraft:mending", "minecraft:unbreaking") : Set.of();
    }

    public static Optional<ModEnchantment> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(NAMESPACE + ":")) {
            normalized = normalized.substring(NAMESPACE.length() + 1);
        }
        for (ModEnchantment enchantment : values()) {
            if (enchantment.id.equals(normalized)) {
                return Optional.of(enchantment);
            }
        }
        return Optional.empty();
    }

    private static void incompatible(ModEnchantment first, ModEnchantment second) {
        INCOMPATIBLE.get(first).add(second);
        INCOMPATIBLE.get(second).add(first);
    }

    /** Vanilla rarity weights used for anvil costs (common 1, uncommon 2, rare 4, very rare 8). */
    public enum Rarity {
        COMMON(1, 10),
        UNCOMMON(2, 5),
        RARE(4, 2),
        VERY_RARE(8, 1);

        private final int anvilCost;
        private final int weight;

        Rarity(int anvilCost, int weight) {
            this.anvilCost = anvilCost;
            this.weight = weight;
        }

        public int anvilCost() {
            return anvilCost;
        }

        /** Selection weight in the enchanting table, as vanilla rarity weights. */
        public int weight() {
            return weight;
        }
    }
}
