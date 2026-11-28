package dev.linqfy.bigCasares.modules.toffy;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Reads {@code toffy_complementaries.yml} (how the level is computed) and {@code toffy_valores.yml} (what every item
 * and enchantment is worth). Every error starts with the file and the YAML path it comes from.
 */
public final class ToffySettingsLoader {
    public static final String MAIN_FILE = "toffy_complementaries.yml";
    public static final String VALUES_FILE = "toffy_valores.yml";
    public static final double DEFAULT_MAXIMUM = 200.0;
    public static final int DEFAULT_DECIMALS = 1;
    public static final double DEFAULT_ARMOR_WEIGHT = 0.6;
    public static final double DEFAULT_WEAPON_WEIGHT = 0.4;
    public static final double DEFAULT_TOOL_WEIGHT = 0.0;
    public static final double DEFAULT_STRENGTH = 3.0;
    public static final double DEFAULT_EXPONENT = 2.0;
    public static final double DEFAULT_MIDPOINT = 0.5;
    public static final double MAXIMUM_ABSOLUTE_VALUE = 1_000_000.0;
    public static final double MAXIMUM_FORGET_MINUTES = 525_600.0;

    private static final Pattern FAMILY_NAME = Pattern.compile("[a-z0-9_-]+");

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private ToffySettingsLoader() {
    }

    public static ToffyConfigLoadResult load(ConfigurationSection main, ConfigurationSection values) {
        return new ToffySettingsLoader().parse(
            main == null ? Map.of() : plain(main),
            values == null ? Map.of() : plain(values));
    }

    private ToffyConfigLoadResult parse(Map<String, Object> main, Map<String, Object> values) {
        checkKeys(main, MAIN_FILE, "", List.of("output", "weights", "armor-search", "weapon-search", "tool-search",
            "memory", "curve", "references"));
        checkKeys(values, VALUES_FILE, "", List.of("armor", "weapons", "tools", "enchantments", "custom-items"));
        Map<String, Object> output = optionalMap(main, "output", MAIN_FILE, "output");
        checkKeys(output, MAIN_FILE, "output", List.of("maximum", "decimals"));
        double maximum = readDouble(output, "maximum", "output.maximum", DEFAULT_MAXIMUM, 0.001, MAXIMUM_ABSOLUTE_VALUE);
        int decimals = readInt(output, "decimals", "output.decimals", DEFAULT_DECIMALS, 0, 6);

        Map<String, Object> weights = optionalMap(main, "weights", MAIN_FILE, "weights");
        checkKeys(weights, MAIN_FILE, "weights", List.of("armor", "weapon", "tool"));
        double armorWeight = readDouble(weights, "armor", "weights.armor", DEFAULT_ARMOR_WEIGHT, 0.0, 1_000.0);
        double weaponWeight = readDouble(weights, "weapon", "weights.weapon", DEFAULT_WEAPON_WEIGHT, 0.0, 1_000.0);
        double toolWeight = readDouble(weights, "tool", "weights.tool", DEFAULT_TOOL_WEIGHT, 0.0, 1_000.0);
        if (armorWeight + weaponWeight + toolWeight <= 0.0) {
            mainError("weights", "armor + weapon + tool must be greater than 0");
        }

        ToffySearch search = new ToffySearch(
            readSearch(main.get("armor-search"), "armor-search", ToffyArmorSearch.class,
                ToffySearch.DEFAULT.armor()),
            readSearch(main.get("weapon-search"), "weapon-search", ToffyHeldSearch.class,
                ToffySearch.DEFAULT.weapon()),
            readSearch(main.get("tool-search"), "tool-search", ToffyHeldSearch.class,
                ToffySearch.DEFAULT.tool()));
        if (search.weapon() == ToffyHeldSearch.MAIN_HAND && search.tool() == ToffyHeldSearch.MAIN_HAND
            && weaponWeight > 0.0 && toolWeight > 0.0) {
            warnings.add(MAIN_FILE + " > weapon-search / tool-search: both are MAIN_HAND, so a weapon and a tool never"
                + " count together and the maximum level cannot be reached");
        }
        Map<String, Object> memorySection = optionalMap(main, "memory", MAIN_FILE, "memory");
        checkKeys(memorySection, MAIN_FILE, "memory",
            List.of("enabled", "forget-after-minutes", "forget-on-death", "check-interval-ticks"));
        ToffyMemorySettings memory = new ToffyMemorySettings(
            readBoolean(memorySection, "enabled", "memory.enabled", ToffyMemorySettings.DEFAULT.enabled()),
            Math.round(readDouble(memorySection, "forget-after-minutes", "memory.forget-after-minutes",
                ToffyMemorySettings.DEFAULT_FORGET_AFTER_MINUTES, 0.0, MAXIMUM_FORGET_MINUTES) * 60_000.0),
            readBoolean(memorySection, "forget-on-death", "memory.forget-on-death",
                ToffyMemorySettings.DEFAULT.forgetOnDeath()),
            readInt(memorySection, "check-interval-ticks", "memory.check-interval-ticks",
                ToffyMemorySettings.DEFAULT_CHECK_INTERVAL_TICKS, 1, 1_200));

        Map<String, Object> curveSection = optionalMap(main, "curve", MAIN_FILE, "curve");
        checkKeys(curveSection, MAIN_FILE, "curve", List.of("type", "strength", "exponent", "midpoint", "points"));
        ToffyCurve curve = readCurve(curveSection);

        Map<String, Double> armor = readValues(values.get("armor"), "armor", false);
        Map<String, Map<String, Double>> weapons = readFamilies(values.get("weapons"), "weapons");
        Map<String, Map<String, Double>> tools = readFamilies(values.get("tools"), "tools");
        Map<String, ToffyEnchantmentValue> enchantments = readEnchantments(values.get("enchantments"));
        List<ToffyCustomItem> customItems = readCustomItems(values.get("custom-items"));

        Map<String, Object> references = optionalMap(main, "references", MAIN_FILE, "references");
        checkKeys(references, MAIN_FILE, "references", List.of("zero", "max"));
        ToffyReference zero = references.get("zero") == null
            ? ToffyReference.empty()
            : readReference(references.get("zero"), "references.zero");
        ToffyReference max = null;
        if (references.get("max") == null) {
            mainError("references.max", "section is required");
        } else {
            max = readReference(references.get("max"), "references.max");
        }

        if (!errors.isEmpty() || zero == null || max == null) {
            return new ToffyConfigLoadResult(null, errors, warnings);
        }
        ToffySettings settings = new ToffySettings(maximum, decimals, armorWeight, weaponWeight, toolWeight,
            search, curve, zero, max, armor, weapons, tools, enchantments, customItems, memory);
        errors.addAll(ToffyService.problems(settings));
        warnUnvaluedReferenceEnchantments(settings);
        warnZeroGaps(settings);
        return new ToffyConfigLoadResult(errors.isEmpty() ? settings : null, errors, warnings);
    }

    private <E extends Enum<E>> E readSearch(Object raw, String path, Class<E> type, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            List<String> options = new ArrayList<>();
            for (E option : type.getEnumConstants()) {
                options.add(option.name());
            }
            mainError(path, "must be " + String.join(", ", options) + ", got " + raw);
            return fallback;
        }
    }

    private ToffyCurve readCurve(Map<String, Object> curve) {
        Object rawType = curve.get("type");
        ToffyCurveType type;
        try {
            type = rawType == null
                ? ToffyCurveType.LINEAR
                : ToffyCurveType.valueOf(String.valueOf(rawType).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            mainError("curve.type", "must be LINEAR, EXPONENTIAL, LOGARITHMIC, POWER, SIGMOID or POINTS, got " + rawType);
            return new ToffyCurve.Linear();
        }
        return switch (type) {
            case LINEAR -> new ToffyCurve.Linear();
            case EXPONENTIAL -> construct("curve.strength",
                () -> new ToffyCurve.Exponential(curveNumber(curve, "strength", DEFAULT_STRENGTH)));
            case LOGARITHMIC -> construct("curve.strength",
                () -> new ToffyCurve.Logarithmic(curveNumber(curve, "strength", DEFAULT_STRENGTH)));
            case POWER -> construct("curve.exponent",
                () -> new ToffyCurve.Power(curveNumber(curve, "exponent", DEFAULT_EXPONENT)));
            case SIGMOID -> sigmoid(curve);
            case POINTS -> construct("curve.points", () -> readPoints(curve.get("points")));
        };
    }

    /**
     * Builds the curve, reporting a rejected parameter under {@code path}, the exact key that holds it.
     */
    private ToffyCurve construct(String path, Supplier<ToffyCurve> build) {
        try {
            return build.get();
        } catch (IllegalArgumentException invalid) {
            mainError(path, invalid.getMessage());
            return new ToffyCurve.Linear();
        }
    }

    private ToffyCurve sigmoid(Map<String, Object> curve) {
        double strength = curveNumber(curve, "strength", DEFAULT_STRENGTH);
        double midpoint = curveNumber(curve, "midpoint", DEFAULT_MIDPOINT);
        try {
            return new ToffyCurve.Sigmoid(strength, midpoint);
        } catch (IllegalArgumentException invalid) {
            boolean strengthFits = sigmoidStrengthFits(strength);
            mainError(strengthFits ? "curve.midpoint" : "curve.strength", invalid.getMessage());
            return new ToffyCurve.Linear();
        }
    }

    private static boolean sigmoidStrengthFits(double strength) {
        try {
            new ToffyCurve.Sigmoid(strength, DEFAULT_MIDPOINT);
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * A numeric curve parameter, or {@code fallback} when it is missing. A value that is not a number is reported
     * under its own key.
     */
    private double curveNumber(Map<String, Object> curve, String key, double fallback) {
        Object raw = curve.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        mainError("curve." + key, "must be a number, got " + raw);
        return fallback;
    }

    private ToffyCurve readPoints(Object raw) {
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("POINTS needs a list like [[0, 0], [0.5, 0.3], [1, 1]]");
        }
        List<ToffyCurve.Point> points = new ArrayList<>();
        boolean malformed = false;
        for (int index = 0; index < list.size(); index++) {
            String path = "curve.points[" + index + "]";
            if (list.get(index) instanceof List<?> pair && pair.size() == 2
                && pair.get(0) instanceof Number x && pair.get(1) instanceof Number y) {
                try {
                    points.add(new ToffyCurve.Point(x.doubleValue(), y.doubleValue()));
                } catch (IllegalArgumentException invalid) {
                    mainError(path, invalid.getMessage());
                    malformed = true;
                }
            } else {
                mainError(path, "must be [t, value], got " + list.get(index));
                malformed = true;
            }
        }
        if (malformed) {
            return new ToffyCurve.Linear();
        }
        return new ToffyCurve.Points(points);
    }

    private Map<String, Double> readValues(Object raw, String path, boolean required) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (raw == null) {
            if (required) {
                valuesError(path, "section is required");
            }
            return values;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            valuesError(path, "must be a section of item: value");
            return values;
        }
        map.forEach((rawKey, rawValue) -> {
            String itemPath = path + "." + rawKey;
            String key = key(rawKey, VALUES_FILE, itemPath);
            if (key == null) {
                return;
            }
            if (!(rawValue instanceof Number number) || !withinLimits(number.doubleValue())) {
                valuesError(itemPath, "must be a number between -" + MAXIMUM_ABSOLUTE_VALUE + " and "
                    + MAXIMUM_ABSOLUTE_VALUE + ", got " + rawValue);
                return;
            }
            if (values.containsKey(key)) {
                valuesError(itemPath, key + " is listed twice");
                return;
            }
            values.put(key, number.doubleValue());
        });
        return values;
    }

    private Map<String, Map<String, Double>> readFamilies(Object raw, String path) {
        Map<String, Map<String, Double>> families = new LinkedHashMap<>();
        if (raw == null) {
            return families;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            valuesError(path, "must be a section of family: { item: value }");
            return families;
        }
        map.forEach((rawFamily, rawMembers) -> {
            String family = String.valueOf(rawFamily).trim().toLowerCase(Locale.ROOT);
            String familyPath = path + "." + rawFamily;
            if (!FAMILY_NAME.matcher(family).matches()) {
                valuesError(familyPath, "family names may only use a-z, 0-9, _ and -");
                return;
            }
            if (families.containsKey(family)) {
                valuesError(familyPath, "family " + family + " is declared twice");
                return;
            }
            Map<String, Double> members = readValues(rawMembers, familyPath, true);
            if (members.isEmpty() && rawMembers instanceof Map<?, ?>) {
                valuesError(familyPath, "must list at least one item with its value");
            }
            families.put(family, members);
        });
        return families;
    }

    private Map<String, ToffyEnchantmentValue> readEnchantments(Object raw) {
        Map<String, ToffyEnchantmentValue> enchantments = new LinkedHashMap<>();
        if (raw == null) {
            warnings.add(VALUES_FILE + " > enchantments: section missing, enchantments add nothing");
            return enchantments;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            valuesError("enchantments", "must be a section");
            return enchantments;
        }
        map.forEach((rawKey, rawValue) -> {
            String path = "enchantments." + rawKey;
            String key = key(rawKey, VALUES_FILE, path);
            if (key == null) {
                return;
            }
            if (rawValue instanceof Map<?, ?> entryMap) {
                checkKeys(entryMap, VALUES_FILE, path, List.of("points-per-level", "max-level"));
            }
            if (!(rawValue instanceof Map<?, ?> value)
                || !(value.get("points-per-level") instanceof Number points)
                || !(value.get("max-level") instanceof Number maxLevel)
                || !isWhole(maxLevel)) {
                valuesError(path, "must be { points-per-level: <number>, max-level: <whole level> }, got " + rawValue);
                return;
            }
            if (!withinLimits(points.doubleValue())) {
                valuesError(path, "points-per-level must be between -" + MAXIMUM_ABSOLUTE_VALUE + " and "
                    + MAXIMUM_ABSOLUTE_VALUE + ", got " + points);
                return;
            }
            if (enchantments.containsKey(key)) {
                valuesError(path, key + " is listed twice");
                return;
            }
            try {
                enchantments.put(key, new ToffyEnchantmentValue(points.doubleValue(), maxLevel.intValue()));
            } catch (IllegalArgumentException invalid) {
                valuesError(path, invalid.getMessage());
            }
        });
        return enchantments;
    }

    private List<ToffyCustomItem> readCustomItems(Object raw) {
        List<ToffyCustomItem> items = new ArrayList<>();
        if (raw == null) {
            return items;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            valuesError("custom-items", "must be a section");
            return items;
        }
        List<String> seen = new ArrayList<>();
        map.forEach((rawId, rawItem) -> {
            String path = "custom-items." + rawId;
            String id = String.valueOf(rawId).trim().toLowerCase(Locale.ROOT);
            if (seen.contains(id)) {
                valuesError(path, "custom item " + id + " is declared twice");
                return;
            }
            seen.add(id);
            if (!(rawItem instanceof Map<?, ?> item)) {
                valuesError(path, "must be a section with match:, category:, value: and (for weapons and tools) family:");
                return;
            }
            checkKeys(item, VALUES_FILE, path, List.of("match", "category", "family", "slot", "value"));
            if (item.get("family") != null && !(item.get("family") instanceof String)) {
                valuesError(path + ".family", "must be a family name, got " + item.get("family"));
                return;
            }
            ToffyArmorSlot slot = null;
            if (item.get("slot") != null) {
                slot = item.get("slot") instanceof String slotText
                    ? ToffyArmorSlot.fromConfigKey(slotText.trim()).orElse(null)
                    : null;
                if (slot == null) {
                    valuesError(path + ".slot", "must be helmet, chestplate, leggings or boots, got " + item.get("slot"));
                    return;
                }
            }
            ToffyCustomMatch match = readMatch(item.get("match"), path + ".match");
            ToffyItemCategory category = item.get("category") instanceof String text
                ? ToffyItemCategory.fromConfigKey(text).orElse(null)
                : null;
            if (category == null) {
                valuesError(path + ".category", "must be armor, weapon or tool, got " + item.get("category"));
            }
            Object rawValue = item.get("value");
            if (!(rawValue instanceof Number number) || !withinLimits(number.doubleValue())) {
                valuesError(path + ".value", "must be a number between -" + MAXIMUM_ABSOLUTE_VALUE + " and "
                    + MAXIMUM_ABSOLUTE_VALUE + ", got " + rawValue);
                return;
            }
            if (match == null || category == null) {
                return;
            }
            try {
                items.add(new ToffyCustomItem(id, match, category,
                    item.get("family") == null ? null : String.valueOf(item.get("family")), number.doubleValue(),
                    slot));
            } catch (IllegalArgumentException invalid) {
                valuesError(path, invalid.getMessage());
            }
        });
        return items;
    }

    private ToffyCustomMatch readMatch(Object raw, String path) {
        if (!(raw instanceof Map<?, ?> match)) {
            valuesError(path, "is required: use bigcasares-item, item-model, persistent-data and/or material");
            return null;
        }
        checkKeys(match, VALUES_FILE, path, List.of("material", "bigcasares-item", "item-model", "persistent-data"));
        for (String textKey : List.of("material", "bigcasares-item", "item-model")) {
            if (match.get(textKey) != null && !(match.get(textKey) instanceof String)) {
                valuesError(path + "." + textKey, "must be text, got " + match.get(textKey));
                return null;
            }
        }
        String material = match.get("material") == null ? null : key(match.get("material"), VALUES_FILE, path + ".material");
        String itemModel = match.get("item-model") == null
            ? null
            : key(match.get("item-model"), VALUES_FILE, path + ".item-model");
        Object catalog = match.get("bigcasares-item");
        String dataKey = null;
        String dataValue = null;
        Object rawData = match.get("persistent-data");
        if (rawData instanceof String text) {
            dataKey = key(text, VALUES_FILE, path + ".persistent-data");
        } else if (rawData instanceof Map<?, ?> data) {
            checkKeys(data, VALUES_FILE, path + ".persistent-data", List.of("key", "value"));
            if (data.get("value") != null && !(data.get("value") instanceof String)) {
                valuesError(path + ".persistent-data.value", "must be text in quotes, for example value: \"1\"; got "
                    + data.get("value"));
                return null;
            }
            dataKey = data.get("key") == null ? null : key(data.get("key"), VALUES_FILE, path + ".persistent-data.key");
            dataValue = (String) data.get("value");
            if (data.get("key") == null) {
                valuesError(path + ".persistent-data.key", "is required");
                return null;
            }
        } else if (rawData != null) {
            valuesError(path + ".persistent-data", "must be a key or { key: <key>, value: <text> }");
            return null;
        }
        try {
            return new ToffyCustomMatch(material, catalog == null ? null : String.valueOf(catalog), itemModel,
                dataKey, dataValue);
        } catch (IllegalArgumentException invalid) {
            valuesError(path, invalid.getMessage());
            return null;
        }
    }

    private ToffyReference readReference(Object raw, String path) {
        if (!(raw instanceof Map<?, ?> reference)) {
            mainError(path, "must be a section with armor:, weapons: and tools:");
            return null;
        }
        checkKeys(reference, MAIN_FILE, path, List.of("armor", "weapons", "tools"));
        EnumMap<ToffyArmorSlot, ToffyItem> armor = new EnumMap<>(ToffyArmorSlot.class);
        Object rawArmor = reference.get("armor");
        if (rawArmor instanceof Map<?, ?> armorMap) {
            armorMap.forEach((rawSlot, rawItem) -> {
                String slotPath = path + ".armor." + rawSlot;
                ToffyArmorSlot slot = ToffyArmorSlot.fromConfigKey(String.valueOf(rawSlot)).orElse(null);
                if (slot == null) {
                    mainError(slotPath, "unknown armor slot (use helmet, chestplate, leggings or boots)");
                    return;
                }
                if (armor.containsKey(slot)) {
                    mainError(slotPath, "slot " + slot.configKey() + " is listed twice");
                    return;
                }
                ToffyItem item = readItem(rawItem, slotPath);
                if (item != null) {
                    armor.put(slot, item);
                }
            });
        } else if (rawArmor != null) {
            mainError(path + ".armor", "must be a section");
        }
        return new ToffyReference(armor, readHeldReference(reference.get("weapons"), path + ".weapons"),
            readHeldReference(reference.get("tools"), path + ".tools"));
    }

    private Map<String, ToffyItem> readHeldReference(Object raw, String path) {
        Map<String, ToffyItem> items = new LinkedHashMap<>();
        if (raw == null) {
            return items;
        }
        if (!(raw instanceof Map<?, ?> map)) {
            mainError(path, "must be a section");
            return items;
        }
        map.forEach((rawFamily, rawItem) -> {
            String family = String.valueOf(rawFamily).trim().toLowerCase(Locale.ROOT);
            String familyPath = path + "." + rawFamily;
            if (items.containsKey(family)) {
                mainError(familyPath, "family " + family + " is listed twice");
                return;
            }
            ToffyItem item = readItem(rawItem, familyPath);
            if (item != null) {
                items.put(family, item);
            }
        });
        return items;
    }

    private ToffyItem readItem(Object raw, String path) {
        if (raw instanceof String) {
            String key = key(raw, MAIN_FILE, path);
            return key == null ? null : ToffyItem.of(key);
        }
        if (!(raw instanceof Map<?, ?> map)) {
            mainError(path, "must be an item key or { item: <key>, enchantments: { ... } }");
            return null;
        }
        checkKeys(map, MAIN_FILE, path, List.of("item", "enchantments"));
        if (map.get("item") == null) {
            mainError(path + ".item", "is required");
            return null;
        }
        String key = key(map.get("item"), MAIN_FILE, path + ".item");
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        boolean valid = key != null;
        Object rawEnchantments = map.get("enchantments");
        if (rawEnchantments instanceof Map<?, ?> enchantmentMap) {
            for (Map.Entry<?, ?> entry : enchantmentMap.entrySet()) {
                String enchantmentPath = path + ".enchantments." + entry.getKey();
                String enchantment = key(entry.getKey(), MAIN_FILE, enchantmentPath);
                if (enchantment == null) {
                    valid = false;
                    continue;
                }
                if (!(entry.getValue() instanceof Number level) || !isWhole(level) || level.intValue() < 1) {
                    mainError(enchantmentPath, "must be a whole level of 1 or more, got " + entry.getValue());
                    valid = false;
                    continue;
                }
                if (enchantments.containsKey(enchantment)) {
                    mainError(enchantmentPath, enchantment + " is listed twice");
                    valid = false;
                    continue;
                }
                enchantments.put(enchantment, level.intValue());
            }
        } else if (rawEnchantments != null) {
            mainError(path + ".enchantments", "must be a section like { sharpness: 5 }");
            valid = false;
        }
        return valid ? ToffyItem.of(key, enchantments) : null;
    }

    private void checkKeys(Map<?, ?> map, String file, String path, List<String> allowed) {
        for (Object rawKey : map.keySet()) {
            String key = String.valueOf(rawKey);
            if (!allowed.contains(key)) {
                errors.add(file + " > " + (path.isEmpty() ? "" : path + ".") + key + ": unknown key (use "
                    + String.join(", ", allowed) + ")");
            }
        }
    }

    private void warnUnvaluedReferenceEnchantments(ToffySettings settings) {
        for (ToffyReference reference : List.of(settings.zeroReference(), settings.maxReference())) {
            List<ToffyItem> items = new ArrayList<>(reference.armor().values());
            items.addAll(reference.weapons().values());
            items.addAll(reference.tools().values());
            for (ToffyItem item : items) {
                for (String enchantment : item.enchantments().keySet()) {
                    String warning = MAIN_FILE + " > references: " + enchantment + " on " + item.key()
                        + " has no value under enchantments in " + VALUES_FILE + ", so it adds 0";
                    if (!settings.enchantmentValues().containsKey(enchantment) && !warnings.contains(warning)) {
                        warnings.add(warning);
                    }
                }
            }
        }
    }

    /**
     * A zero reference that names some families or slots but not others: the missing ones count as 0 power, which
     * is only what the admin meant if they forgot them. An entirely empty zero reference is the normal "nothing"
     * and is not reported.
     */
    private void warnZeroGaps(ToffySettings settings) {
        ToffyReference zero = settings.zeroReference();
        if (settings.armorWeight() > 0.0 && !zero.armor().isEmpty()) {
            for (ToffyArmorSlot slot : ToffyArmorSlot.values()) {
                if (!zero.armor().containsKey(slot)) {
                    warnings.add(MAIN_FILE + " > references.zero.armor." + slot.configKey()
                        + ": missing, so this slot counts as 0 in the zero reference");
                }
            }
        }
        for (ToffyItemCategory category : List.of(ToffyItemCategory.WEAPON, ToffyItemCategory.TOOL)) {
            if (settings.weight(category) <= 0.0 || zero.held(category).isEmpty()) {
                continue;
            }
            String section = category == ToffyItemCategory.WEAPON ? "weapons" : "tools";
            for (String family : settings.families(category).keySet()) {
                if (!zero.held(category).containsKey(family)) {
                    warnings.add(MAIN_FILE + " > references.zero." + section + "." + family
                        + ": missing, so this family counts as 0 in the zero reference");
                }
            }
        }
    }

    private String key(Object raw, String file, String path) {
        try {
            return ToffyKeys.normalize(raw == null ? null : String.valueOf(raw));
        } catch (IllegalArgumentException invalid) {
            errors.add(file + " > " + path + ": invalid key " + raw);
            return null;
        }
    }

    private Map<String, Object> optionalMap(Map<String, Object> parent, String key, String file, String path) {
        Object raw = parent.get(key);
        if (raw == null) {
            return Map.of();
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((childKey, value) -> copy.put(String.valueOf(childKey), value));
            return copy;
        }
        errors.add(file + " > " + path + ": must be a section");
        return Map.of();
    }

    private double readDouble(Map<String, Object> section, String key, String path, double fallback,
                              double minimum, double maximum) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            mainError(path, "must be a number, got " + raw);
            return fallback;
        }
        double value = number.doubleValue();
        if (value < minimum || value > maximum) {
            mainError(path, "must be between " + minimum + " and " + maximum + ", got " + value);
            return fallback;
        }
        return value;
    }

    private boolean readBoolean(Map<String, Object> section, String key, String path, boolean fallback) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Boolean value)) {
            mainError(path, "must be true or false, got " + raw);
            return fallback;
        }
        return value;
    }

    private int readInt(Map<String, Object> section, String key, String path, int fallback, int minimum, int maximum) {
        Object raw = section.get(key);
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number) || !isWhole(number)) {
            mainError(path, "must be a whole number, got " + raw);
            return fallback;
        }
        int value = number.intValue();
        if (value < minimum || value > maximum) {
            mainError(path, "must be between " + minimum + " and " + maximum + ", got " + value);
            return fallback;
        }
        return value;
    }

    private static boolean withinLimits(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAXIMUM_ABSOLUTE_VALUE;
    }

    private static boolean isWhole(Number number) {
        double value = number.doubleValue();
        return Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE;
    }

    private void mainError(String path, String message) {
        errors.add(MAIN_FILE + " > " + path + ": " + message);
    }

    private void valuesError(String path, String message) {
        errors.add(VALUES_FILE + " > " + path + ": " + message);
    }

    private static Map<String, Object> plain(ConfigurationSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            values.put(key, plainValue(section.get(key)));
        }
        return values;
    }

    private static Object plainValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            return plain(section);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, child) -> values.put(String.valueOf(key), plainValue(child)));
            return values;
        }
        if (value instanceof List<?> list) {
            List<Object> values = new ArrayList<>(list.size());
            for (Object child : list) {
                values.add(plainValue(child));
            }
            return values;
        }
        return value;
    }
}
