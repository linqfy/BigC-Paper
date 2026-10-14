package dev.linqfy.bigCasares.modules.cosmicniv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The equipment level model. Immutable and safe to share between threads; reading an entity's equipment is done
 * elsewhere, on the main thread.
 */
public final class CosmicNivService {
    private static final String MAIN = CosmicNivSettingsLoader.MAIN_FILE + " > ";
    private static final String VALUES = CosmicNivSettingsLoader.VALUES_FILE + " > ";
    private static final List<CosmicNivItemCategory> HELD_CATEGORIES =
        List.of(CosmicNivItemCategory.WEAPON, CosmicNivItemCategory.TOOL);

    private final CosmicNivSettings settings;
    private final Catalog catalog;
    private final double zeroArmorPower;
    private final double maxArmorPower;
    private final Map<CosmicNivItemCategory, Map<String, Double>> zeroHeldPower;
    private final Map<CosmicNivItemCategory, Map<String, Double>> maxHeldPower;

    public CosmicNivService(CosmicNivSettings settings) {
        List<String> problems = problems(settings);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        this.settings = settings;
        this.catalog = Catalog.of(settings);
        this.zeroArmorPower = catalog.armorPower(settings.zeroReference().armor());
        this.maxArmorPower = catalog.armorPower(settings.maxReference().armor());
        Map<CosmicNivItemCategory, Map<String, Double>> zero = new EnumMap<>(CosmicNivItemCategory.class);
        Map<CosmicNivItemCategory, Map<String, Double>> max = new EnumMap<>(CosmicNivItemCategory.class);
        for (CosmicNivItemCategory category : HELD_CATEGORIES) {
            Map<String, Double> zeroPowers = new LinkedHashMap<>();
            Map<String, Double> maxPowers = new LinkedHashMap<>();
            for (String family : catalog.families(category)) {
                zeroPowers.put(family, catalog.itemPower(settings.zeroReference().held(category).get(family)));
                maxPowers.put(family, catalog.itemPower(settings.maxReference().held(category).get(family)));
            }
            zero.put(category, Collections.unmodifiableMap(zeroPowers));
            max.put(category, Collections.unmodifiableMap(maxPowers));
        }
        this.zeroHeldPower = Collections.unmodifiableMap(zero);
        this.maxHeldPower = Collections.unmodifiableMap(max);
    }

    /**
     * Every reason why {@code settings} cannot describe a valid model, with the file and YAML path it comes from.
     * Empty when the settings are valid.
     */
    public static List<String> problems(CosmicNivSettings settings) {
        List<String> problems = new ArrayList<>();
        if (!(settings.maximum() > 0.0) || !Double.isFinite(settings.maximum())) {
            problems.add(MAIN + "output.maximum: must be greater than 0");
        }
        if (settings.decimals() < 0) {
            problems.add(MAIN + "output.decimals: must not be negative");
        } else if (settings.maximum() > 0.0 && Double.isFinite(settings.maximum())) {
            try {
                BigDecimal.valueOf(settings.maximum()).setScale(settings.decimals(), RoundingMode.UNNECESSARY);
            } catch (ArithmeticException notRepresentable) {
                problems.add(MAIN + "output.maximum: " + settings.maximum() + " cannot be shown with output.decimals "
                    + settings.decimals() + "; the level could round above the maximum or never reach it");
            }
        }
        double totalWeight = settings.armorWeight() + settings.weaponWeight() + settings.toolWeight();
        if (settings.armorWeight() < 0.0 || settings.weaponWeight() < 0.0 || settings.toolWeight() < 0.0
            || !(totalWeight > 0.0) || !Double.isFinite(totalWeight)) {
            problems.add(MAIN + "weights: armor, weapon and tool must not be negative and must add up to more than 0");
        }

        checkLimits(settings, problems);
        Catalog catalog = Catalog.collect(settings, problems);
        checkReference(settings, catalog, settings.zeroReference(), "references.zero", problems);
        checkReference(settings, catalog, settings.maxReference(), "references.max", problems);
        if (settings.armorWeight() > 0.0 && catalog.armorValues.isEmpty()) {
            problems.add(VALUES + "armor: at least one armor value is required while weights.armor is above 0");
        }
        for (CosmicNivItemCategory category : HELD_CATEGORIES) {
            String section = section(category);
            if (settings.weight(category) <= 0.0) {
                continue;
            }
            if (catalog.families(category).isEmpty()) {
                problems.add(VALUES + section + ": at least one family is required while weights."
                    + category.configKey() + " is above 0");
            }
            for (String family : catalog.families(category)) {
                if (!settings.maxReference().held(category).containsKey(family)) {
                    problems.add(MAIN + "references.max." + section + "." + family
                        + ": missing; every " + category.configKey() + " family needs a max reference");
                }
            }
        }
        if (!problems.isEmpty()) {
            return List.copyOf(problems);
        }

        if (settings.armorWeight() > 0.0) {
            double zero = catalog.armorPower(settings.zeroReference().armor());
            double max = catalog.armorPower(settings.maxReference().armor());
            checkAbove(max, zero, "references.max.armor", "references.zero.armor", problems);
        }
        for (CosmicNivItemCategory category : HELD_CATEGORIES) {
            if (settings.weight(category) <= 0.0) {
                continue;
            }
            String section = section(category);
            for (String family : catalog.families(category)) {
                double zero = catalog.itemPower(settings.zeroReference().held(category).get(family));
                double max = catalog.itemPower(settings.maxReference().held(category).get(family));
                checkAbove(max, zero, "references.max." + section + "." + family,
                    "references.zero." + section + "." + family, problems);
            }
        }
        return List.copyOf(problems);
    }

    public CosmicNivSettings settings() {
        return settings;
    }

    public double maximum() {
        return settings.maximum();
    }

    /**
     * Turns what a stack looks like into the item the model values: the first custom item whose match fits, or the
     * plain material otherwise. Enchantments are kept either way.
     */
    public CosmicNivItem identify(CosmicNivItemIdentity identity) {
        for (CosmicNivCustomItem custom : settings.customItems()) {
            if (custom.match().matches(identity)) {
                return CosmicNivItem.of(custom.key(), identity.enchantments());
            }
        }
        return CosmicNivItem.of(identity.material(), identity.enchantments());
    }

    /**
     * Persistent data keys that custom item matches read, so the Bukkit reader only looks up those.
     */
    public Set<String> persistentDataKeys() {
        return catalog.persistentDataKeys;
    }

    /**
     * Base value plus enchantment points; 0 for items that have no value in {@code cosmicniv_valores.yml}.
     */
    public double itemPower(CosmicNivItem item) {
        return catalog.itemPower(item);
    }

    /**
     * Where {@code item} counts, or {@code null} when it has no value.
     */
    /**
     * The armor slot {@code item} is worn in, or {@code null} when it is not armor or fits any slot. A piece only
     * counts in its own slot.
     */
    public CosmicNivArmorSlot armorSlotOf(CosmicNivItem item) {
        return item == null ? null : catalog.armorSlots.get(item.key());
    }

    public CosmicNivItemCategory categoryOf(CosmicNivItem item) {
        if (item == null) {
            return null;
        }
        if (catalog.armorValues.containsKey(item.key())) {
            return CosmicNivItemCategory.ARMOR;
        }
        Owner owner = catalog.heldOwners.get(item.key());
        return owner == null ? null : owner.category();
    }

    /**
     * Weapon or tool family of {@code item}, or {@code null} when it is not a weapon or a tool.
     */
    public String familyOf(CosmicNivItem item) {
        Owner owner = item == null ? null : catalog.heldOwners.get(item.key());
        return owner == null ? null : owner.family();
    }

    /**
     * Score of a weapon or tool against its own family's references, between 0 and 1; 0 for anything else.
     */
    public double heldScore(CosmicNivItem item) {
        Owner owner = item == null ? null : catalog.heldOwners.get(item.key());
        if (owner == null) {
            return 0.0;
        }
        return normalize(catalog.itemPower(item), zeroHeldPower.get(owner.category()).get(owner.family()),
            maxHeldPower.get(owner.category()).get(owner.family()));
    }

    public CosmicNivResult evaluate(CosmicNivLoadout loadout) {
        CosmicNivLoadout safeLoadout = loadout == null ? CosmicNivLoadout.empty() : loadout;
        Map<CosmicNivArmorSlot, CosmicNivItem> armorPieces = catalog.bestArmor(safeLoadout.armor());
        double armorPower = catalog.armorPower(armorPieces);
        double armorScore = normalize(armorPower, zeroArmorPower, maxArmorPower);
        CosmicNivResult.Held weapon = best(CosmicNivItemCategory.WEAPON, safeLoadout.weapons());
        CosmicNivResult.Held tool = best(CosmicNivItemCategory.TOOL, safeLoadout.tools());

        double totalWeight = settings.armorWeight() + settings.weaponWeight() + settings.toolWeight();
        double weighted = weighted(settings.armorWeight(), armorScore)
            + weighted(settings.weaponWeight(), weapon.score())
            + weighted(settings.toolWeight(), tool.score());
        double progress = Math.clamp(weighted / totalWeight, 0.0, 1.0);
        double curved = settings.curve().apply(progress);
        double exact = settings.maximum() * curved;
        double rounded = BigDecimal.valueOf(exact).setScale(settings.decimals(), RoundingMode.HALF_UP).doubleValue();
        return new CosmicNivResult(rounded, exact, settings.maximum(), progress, curved, settings.curve().type(),
            armorScore, armorPower, armorPieces, weapon, tool);
    }

    private CosmicNivResult.Held best(CosmicNivItemCategory category, List<CosmicNivItem> held) {
        CosmicNivResult.Held best = CosmicNivResult.Held.NONE;
        for (CosmicNivItem item : held) {
            Owner owner = catalog.heldOwners.get(item.key());
            if (owner == null || owner.category() != category) {
                continue;
            }
            double power = catalog.itemPower(item);
            double score = normalize(power, zeroHeldPower.get(category).get(owner.family()),
                maxHeldPower.get(category).get(owner.family()));
            if (!best.present() || score > best.score()) {
                best = new CosmicNivResult.Held(owner.family(), item, power, score);
            }
        }
        return best;
    }

    private static double weighted(double weight, double score) {
        return weight > 0.0 && Double.isFinite(score) ? weight * score : 0.0;
    }

    private static double normalize(double power, double zero, double max) {
        if (!(max > zero)) {
            return 0.0;
        }
        return Math.clamp((power - zero) / (max - zero), 0.0, 1.0);
    }

    private static void checkAbove(double max, double zero, String maxPath, String zeroPath, List<String> problems) {
        if (!Double.isFinite(zero) || !Double.isFinite(max)) {
            problems.add(MAIN + maxPath + ": powers are too large to compute");
        } else if (!(max > zero)) {
            problems.add(MAIN + maxPath + ": power " + max + " must be greater than " + zeroPath + " power " + zero);
        }
    }

    private static void checkLimits(CosmicNivSettings settings, List<String> problems) {
        double limit = CosmicNivSettingsLoader.MAXIMUM_ABSOLUTE_VALUE;
        settings.armorValues().forEach((key, value) -> {
            if (!(Math.abs(value) <= limit)) {
                problems.add(VALUES + "armor." + key + ": must be between -" + limit + " and " + limit);
            }
        });
        for (CosmicNivItemCategory category : HELD_CATEGORIES) {
            settings.families(category).forEach((family, members) -> members.forEach((key, value) -> {
                if (!(Math.abs(value) <= limit)) {
                    problems.add(VALUES + section(category) + "." + family + "." + key + ": must be between -" + limit
                        + " and " + limit);
                }
            }));
        }
        settings.enchantmentValues().forEach((key, value) -> {
            if (!(Math.abs(value.pointsPerLevel()) <= limit)) {
                problems.add(VALUES + "enchantments." + key + ": points-per-level must be between -" + limit + " and "
                    + limit);
            }
        });
        for (CosmicNivCustomItem custom : settings.customItems()) {
            if (!(Math.abs(custom.value()) <= limit)) {
                problems.add(VALUES + "custom-items." + custom.id() + ".value: must be between -" + limit + " and "
                    + limit);
            }
        }
    }

    private static void checkReference(CosmicNivSettings settings, Catalog catalog, CosmicNivReference reference,
                                       String path, List<String> problems) {
        checkHeldReferences(settings, catalog, reference, path, problems);
        if (settings.armorWeight() <= 0.0) {
            return;
        }
        reference.armor().forEach((slot, item) -> {
            String itemPath = MAIN + path + ".armor." + slot.configKey() + ": " + item.key();
            if (!catalog.armorValues.containsKey(item.key())) {
                Owner owner = catalog.heldOwners.get(item.key());
                problems.add(owner == null
                    ? itemPath + " has no armor value in " + CosmicNivSettingsLoader.VALUES_FILE
                    : itemPath + " is a " + owner.category().configKey() + " (" + section(owner.category()) + "."
                        + owner.family() + "); only armor counts in armor slots");
            } else if (!catalog.fits(item, slot)) {
                problems.add(itemPath + " is worn as " + catalog.armorSlots.get(item.key()).configKey() + ", not "
                    + slot.configKey());
            }
        });
    }

    private static void checkHeldReferences(CosmicNivSettings settings, Catalog catalog, CosmicNivReference reference,
                                            String path, List<String> problems) {
        for (CosmicNivItemCategory category : HELD_CATEGORIES) {
            if (settings.weight(category) <= 0.0) {
                continue;
            }
            String section = section(category);
            reference.held(category).forEach((family, item) -> {
                String itemPath = MAIN + path + "." + section + "." + family;
                if (!settings.families(category).containsKey(family)) {
                    problems.add(itemPath + ": unknown " + category.configKey() + " family " + family
                        + " (declare it under " + section + ": in " + CosmicNivSettingsLoader.VALUES_FILE + ")");
                    return;
                }
                Owner owner = catalog.heldOwners.get(item.key());
                if (owner == null) {
                    problems.add(itemPath + ": " + item.key() + " is not a " + category.configKey()
                        + " in " + CosmicNivSettingsLoader.VALUES_FILE);
                } else if (owner.category() != category || !owner.family().equals(family)) {
                    problems.add(itemPath + ": " + item.key() + " belongs to " + section(owner.category()) + "."
                        + owner.family() + ", not " + section + "." + family);
                }
            });
        }
    }

    private static String section(CosmicNivItemCategory category) {
        return switch (category) {
            case ARMOR -> "armor";
            case WEAPON -> "weapons";
            case TOOL -> "tools";
        };
    }

    private record Owner(CosmicNivItemCategory category, String family) {
    }

    private static final class Catalog {
        private final CosmicNivSettings settings;
        private final Map<String, Double> armorValues = new LinkedHashMap<>();
        private final Map<String, CosmicNivArmorSlot> armorSlots = new LinkedHashMap<>();
        private final Map<String, Double> heldValues = new LinkedHashMap<>();
        private final Map<String, Owner> heldOwners = new LinkedHashMap<>();
        private final Map<CosmicNivItemCategory, List<String>> families = new EnumMap<>(CosmicNivItemCategory.class);
        private final Set<String> persistentDataKeys;

        private Catalog(CosmicNivSettings settings, List<String> problems) {
            this.settings = settings;
            Map<String, String> places = new LinkedHashMap<>();
            settings.armorValues().forEach((key, value) -> {
                place(places, key, "armor." + key, problems);
                armorValues.put(key, value);
                CosmicNivArmorSlot.ofItemKey(key).ifPresent(slot -> armorSlots.put(key, slot));
            });
            for (CosmicNivItemCategory category : HELD_CATEGORIES) {
                List<String> names = new ArrayList<>();
                settings.families(category).forEach((family, members) -> {
                    names.add(family);
                    members.forEach((key, value) -> {
                        if (place(places, key, section(category) + "." + family + "." + key, problems)) {
                            heldValues.put(key, value);
                            heldOwners.put(key, new Owner(category, family));
                        }
                    });
                });
                families.put(category, List.copyOf(names));
            }
            for (String family : settings.toolFamilies().keySet()) {
                if (settings.weaponFamilies().containsKey(family)) {
                    problems.add(VALUES + "tools." + family + ": family name already used under weapons");
                }
            }
            Set<String> dataKeys = new LinkedHashSet<>();
            for (CosmicNivCustomItem custom : settings.customItems()) {
                String path = "custom-items." + custom.id();
                if (custom.match().persistentDataKey() != null) {
                    dataKeys.add(custom.match().persistentDataKey());
                }
                if (!place(places, custom.key(), path, problems)) {
                    continue;
                }
                if (custom.category() == CosmicNivItemCategory.ARMOR) {
                    armorValues.put(custom.key(), custom.value());
                    if (custom.armorSlot() != null) {
                        armorSlots.put(custom.key(), custom.armorSlot());
                    }
                } else if (!settings.families(custom.category()).containsKey(custom.family())) {
                    problems.add(VALUES + path + ".family: unknown " + custom.category().configKey() + " family "
                        + custom.family() + " (use one declared under " + section(custom.category()) + ":)");
                } else {
                    heldValues.put(custom.key(), custom.value());
                    heldOwners.put(custom.key(), new Owner(custom.category(), custom.family()));
                }
            }
            this.persistentDataKeys = Collections.unmodifiableSet(dataKeys);
        }

        static Catalog of(CosmicNivSettings settings) {
            return new Catalog(settings, new ArrayList<>());
        }

        static Catalog collect(CosmicNivSettings settings, List<String> problems) {
            return new Catalog(settings, problems);
        }

        List<String> families(CosmicNivItemCategory category) {
            return families.get(category);
        }

        Map<CosmicNivArmorSlot, CosmicNivItem> bestArmor(Map<CosmicNivArmorSlot, List<CosmicNivItem>> candidates) {
            Map<CosmicNivArmorSlot, CosmicNivItem> best = new EnumMap<>(CosmicNivArmorSlot.class);
            candidates.forEach((slot, pieces) -> {
                CosmicNivItem chosen = null;
                double chosenPower = 0.0;
                for (CosmicNivItem piece : pieces) {
                    if (!armorValues.containsKey(piece.key()) || !fits(piece, slot)) {
                        continue;
                    }
                    double power = itemPower(piece);
                    if (power < 0.0) {
                        // An empty slot is worth 0, so a net-negative piece (e.g. cursed leather) is never the best.
                        continue;
                    }
                    if (chosen == null || power > chosenPower) {
                        chosen = piece;
                        chosenPower = power;
                    }
                }
                if (chosen != null) {
                    best.put(slot, chosen);
                }
            });
            return best;
        }

        boolean fits(CosmicNivItem piece, CosmicNivArmorSlot slot) {
            CosmicNivArmorSlot own = armorSlots.get(piece.key());
            return own == null || own == slot;
        }

        double armorPower(Map<CosmicNivArmorSlot, CosmicNivItem> armor) {
            double total = 0.0;
            for (Map.Entry<CosmicNivArmorSlot, CosmicNivItem> entry : armor.entrySet()) {
                if (armorValues.containsKey(entry.getValue().key()) && fits(entry.getValue(), entry.getKey())) {
                    total += itemPower(entry.getValue());
                }
            }
            return total;
        }

        double itemPower(CosmicNivItem item) {
            if (item == null) {
                return 0.0;
            }
            Double base = armorValues.get(item.key());
            if (base == null) {
                base = heldValues.get(item.key());
            }
            if (base == null) {
                return 0.0;
            }
            double power = base;
            for (String enchantment : new TreeSet<>(item.enchantments().keySet())) {
                CosmicNivEnchantmentValue value = settings.enchantmentValues().get(enchantment);
                if (value != null) {
                    power += value.points(item.enchantments().get(enchantment));
                }
            }
            return power;
        }

        private static boolean place(Map<String, String> places, String key, String path, List<String> problems) {
            String previous = places.putIfAbsent(key, path);
            if (previous != null) {
                problems.add(VALUES + path + ": " + key + " is already listed under " + previous
                    + "; an item can only be in one place");
                return false;
            }
            return true;
        }
    }
}
