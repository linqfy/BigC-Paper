package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.datapack.DatapackBuilder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The mod's advancement tree ({@code the_sift:story/*} and {@code the_sift:the_sift/*}),
 * translated for vanilla clients and written into the dimension datapack.
 *
 * <p>Only advancements the port can grant are written: an advancement whose
 * mechanic is not ported (Sculkflower, Charoite enchanting, ...)
 * would sit in the tab forever. Children of a left-out advancement hang from its
 * nearest written ancestor instead. Native criteria (dimension change, biome and
 * structure location) work unchanged because the port keeps the mod's ids; item
 * criteria match the content id stored on content items; the mod's
 * {@code minecraft:impossible} criteria are awarded from code with
 * {@link #award(Player, String)}, at the same moments as in the mod. Entity
 * conditions are rewritten from the mod's 26.3 form to the list form 26.2 parses.</p>
 */
public final class SiftAdvancements {

    public static final String SONG_OF_THE_PAST = "story/song_of_the_past";
    public static final String BLUB = "the_sift/blub";
    public static final String RIFTER = "the_sift/rifter";
    public static final String RIFT_RESONANCE = "the_sift/rift_resonance";
    public static final String KINDA_PURPLEISH = "the_sift/kinda_purpleish";
    public static final String SIFTED_NUGGET = "the_sift/sifted_nugget";
    public static final String SPICEWOOD = "the_sift/spicewood";
    public static final String KEEP_INVENTORY = "the_sift/keep_inventory";
    public static final String FALSE_ALARM = "the_sift/false_alarm";
    public static final String LIKE_FATHER_AND_SON = "the_sift/like_father_and_son";
    public static final String FRIEND_OF_THE_SIFT = "the_sift/friend_of_the_sift";
    public static final String PITCH_PERFECT = "the_sift/pitch_perfect";
    public static final String PLANTING_THE_DARK = "the_sift/planting_the_dark";
    public static final String FOOD_CHAIN = "the_sift/food_chain";
    public static final String SMELLS_CREEPY = "the_sift/smells_creepy";

    /** Advancements with the mod's {@code impossible} criterion that the port awards from code. */
    static final Set<String> AWARDED_FROM_CODE = Set.of(SONG_OF_THE_PAST, BLUB, RIFTER, RIFT_RESONANCE, KINDA_PURPLEISH, SIFTED_NUGGET,
        SPICEWOOD, KEEP_INVENTORY, SiftCharoite.BETTER_ENCHANTER, FALSE_ALARM, LIKE_FATHER_AND_SON, FRIEND_OF_THE_SIFT,
        PITCH_PERFECT, PLANTING_THE_DARK, FOOD_CHAIN);

    /** Every advancement of the mod except recipe unlocks. */
    static final List<String> MOD_ADVANCEMENTS = List.of(
        "story/fallen_civilization", "story/song_of_the_past", "story/brave_the_unknown",
        "the_sift/root", "the_sift/better_enchanter", "the_sift/blood_of_the_gods", "the_sift/blub",
        "the_sift/certified_sifter", "the_sift/cover_me_in_siftite", "the_sift/false_alarm", "the_sift/food_chain",
        "the_sift/friend_of_the_sift", "the_sift/keep_inventory", "the_sift/kinda_purpleish",
        "the_sift/like_father_and_son", "the_sift/pitch_perfect", "the_sift/planting_the_dark",
        "the_sift/rift_resonance", "the_sift/rifter", "the_sift/sifted_nugget", "the_sift/smells_creepy",
        "the_sift/spicewood");

    /** Icons without a content item: vanilla item shown with the mod's item model. */
    private static final Map<String, String> ICON_STAND_INS = Map.of("sift_rift", "minecraft:ender_eye");

    private static final String CUSTOM_DATA = "minecraft:custom_data";
    private static final String BUKKIT_VALUES = "PublicBukkitValues";

    private SiftAdvancements() {
    }

    /** Advancements the port can grant with these settings. */
    static Set<String> granted(SiftSettings settings) {
        Set<String> granted = new LinkedHashSet<>(List.of(
            "story/fallen_civilization", "story/brave_the_unknown", "the_sift/root",
            "the_sift/blood_of_the_gods", "the_sift/certified_sifter", "the_sift/kinda_purpleish",
            "the_sift/sifted_nugget", "the_sift/spicewood", "the_sift/cover_me_in_siftite", KEEP_INVENTORY,
            SiftCharoite.BETTER_ENCHANTER, FALSE_ALARM));
        if (settings.ancientCityRitual()) {
            granted.add(SONG_OF_THE_PAST);
        }
        if (settings.blubs()) {
            granted.add(BLUB);
        }
        if (settings.rifts()) {
            granted.add(RIFTER);
            // The Rift disc (a vanilla disc in the mod's clothes, see SiftItems) played near an open rift.
            granted.add(RIFT_RESONANCE);
        }
        // An Echo Golem hands its Soul Block to a Singer (father and son) or to the player it likes (friend).
        if (settings.echoGolems() && settings.singers()) {
            granted.add(LIKE_FATHER_AND_SON);
        }
        if (settings.echoGolems()) {
            granted.add(FRIEND_OF_THE_SIFT);
        }
        if (settings.singers()) {
            granted.add(PITCH_PERFECT);
        }
        // The Dark Sniffer is the only source of Sculkflower Seeds in survival.
        if (settings.darkSniffers()) {
            granted.add(SMELLS_CREEPY);
            if (settings.sculkflowers()) {
                granted.add(PLANTING_THE_DARK);
            }
            if (settings.blubs()) {
                granted.add(FOOD_CHAIN);
            }
        }
        return granted;
    }

    static void contribute(SiftData data, SiftSettings settings, DatapackBuilder datapack) {
        translate(data, granted(settings), SiftItems.items()).forEach((path, json) ->
            datapack.json(DatapackBuilder.Registry.ADVANCEMENT, NamespacedId.of(SiftBlocks.NS, path), json));
    }

    /** The granted advancements, translated, keyed by path. */
    static Map<String, JsonObject> translate(SiftData data, Set<String> granted, List<ContentItem> contentItems) {
        Map<String, JsonObject> mod = new LinkedHashMap<>();
        for (String path : MOD_ADVANCEMENTS) {
            mod.put(path, data.json("advancement/" + path + ".json"));
        }
        Map<NamespacedId, ContentItem> items = new LinkedHashMap<>();
        contentItems.forEach(item -> items.put(item.id(), item));
        JsonObject texts = data.optionalJson("lang/advancements_en_us.json").orElseGet(JsonObject::new);
        Map<String, JsonObject> translated = new LinkedHashMap<>();
        for (String path : MOD_ADVANCEMENTS) {
            if (!granted.contains(path)) {
                continue;
            }
            JsonObject source = mod.get(path);
            JsonObject json = new JsonObject();
            parent(path, mod, granted).ifPresent(parent -> json.addProperty("parent", parent));
            json.add("display", display(source.getAsJsonObject("display"), items, texts, path));
            json.add("criteria", criteria(source.getAsJsonObject("criteria"), items, path));
            json.add("requirements", source.get("requirements").deepCopy());
            translated.put(path, json);
        }
        return translated;
    }

    /** The nearest granted ancestor, or a parent outside the mod (vanilla advancements). */
    static Optional<String> parent(String path, Map<String, JsonObject> mod, Set<String> granted) {
        JsonElement parent = mod.get(path).get("parent");
        while (parent != null) {
            NamespacedId id = NamespacedId.parse(parent.getAsString());
            if (!id.namespace().equals(SiftBlocks.NS) || granted.contains(id.path())) {
                return Optional.of(id.asString());
            }
            JsonObject ancestor = mod.get(id.path());
            if (ancestor == null) {
                throw new IllegalStateException("Advancement " + path + " has unknown ancestor " + id);
            }
            parent = ancestor.get("parent");
        }
        return Optional.empty();
    }

    private static JsonObject display(JsonObject source, Map<NamespacedId, ContentItem> items, JsonObject texts, String path) {
        JsonObject display = source.deepCopy();
        display.add("icon", icon(source.getAsJsonObject("icon"), items, path));
        for (String text : List.of("title", "description")) {
            JsonObject component = display.getAsJsonObject(text);
            JsonElement key = component.get("translate");
            if (key != null && texts.has(key.getAsString())) {
                component.addProperty("fallback", texts.get(key.getAsString()).getAsString());
            }
        }
        return display;
    }

    /** Mod item icons become their content item's material showing the mod's item model. */
    static JsonObject icon(JsonObject source, Map<NamespacedId, ContentItem> items, String path) {
        NamespacedId id = NamespacedId.parse(source.get("id").getAsString());
        if (id.namespace().equals("minecraft")) {
            return source.deepCopy();
        }
        ContentItem item = items.get(id);
        String material = item != null ? materialId(item) : ICON_STAND_INS.get(id.path());
        if (material == null) {
            throw new IllegalStateException("Advancement " + path + " uses icon " + id + " without a content item");
        }
        JsonObject components = new JsonObject();
        components.addProperty("minecraft:item_model", item != null ? item.itemModel().asString() : id.asString());
        JsonObject icon = new JsonObject();
        icon.addProperty("id", material);
        icon.add("components", components);
        return icon;
    }

    private static JsonObject criteria(JsonObject source, Map<NamespacedId, ContentItem> items, String path) {
        JsonObject criteria = source.deepCopy();
        for (Map.Entry<String, JsonElement> entry : criteria.entrySet()) {
            JsonObject criterion = entry.getValue().getAsJsonObject();
            JsonObject conditions = criterion.getAsJsonObject("conditions");
            if (conditions == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> condition : conditions.entrySet()) {
                if (isSingleLootCondition(condition.getValue())) {
                    condition.setValue(lootConditions(condition.getValue().getAsJsonObject()));
                }
            }
            if (!conditions.has("items")) {
                continue;
            }
            JsonArray predicates = new JsonArray();
            for (JsonElement predicate : conditions.getAsJsonArray("items")) {
                predicates.add(itemPredicate(predicate.getAsJsonObject(), items, path));
            }
            conditions.add("items", predicates);
        }
        return criteria;
    }

    /** A 26.3 entity condition: one loot condition object whose kind is under {@code type}. */
    private static boolean isSingleLootCondition(JsonElement value) {
        return value.isJsonObject() && value.getAsJsonObject().has("type") && value.getAsJsonObject().has("entity")
            && !value.getAsJsonObject().has("condition");
    }

    /** 26.2 reads entity conditions as a list of loot conditions whose kind is under {@code condition}. */
    static JsonArray lootConditions(JsonObject single) {
        JsonObject condition = new JsonObject();
        condition.add("condition", single.get("type").deepCopy());
        single.entrySet().stream().filter(entry -> !entry.getKey().equals("type"))
            .forEach(entry -> condition.add(entry.getKey(), entry.getValue().deepCopy()));
        JsonArray list = new JsonArray();
        list.add(condition);
        return list;
    }

    /** {@code {"items": "the_sift:x"}} matches content item x by its stored content id. */
    static JsonObject itemPredicate(JsonObject source, Map<NamespacedId, ContentItem> items, String path) {
        JsonElement value = source.get("items");
        if (value == null || !value.isJsonPrimitive() || !value.getAsString().startsWith(SiftBlocks.NS + ":")) {
            return source.deepCopy();
        }
        NamespacedId id = NamespacedId.parse(value.getAsString());
        ContentItem item = items.get(id);
        if (item == null) {
            throw new IllegalStateException("Advancement " + path + " needs item " + id + ", which is not a content item");
        }
        // SNBT: the custom_data predicate parses a string on every 26.x version.
        String customData = "{" + BUKKIT_VALUES + ":{\"" + ContentItemFactory.CONTENT_ID.asString() + "\":\""
            + id.asString() + "\"}}";
        JsonObject subPredicates = new JsonObject();
        subPredicates.addProperty(CUSTOM_DATA, customData);
        JsonObject predicate = source.deepCopy();
        predicate.addProperty("items", materialId(item));
        predicate.add("predicates", subPredicates);
        return predicate;
    }

    private static String materialId(ContentItem item) {
        return "minecraft:" + item.material().toLowerCase(Locale.ROOT);
    }

    /** Grants an advancement the mod awards from code (its {@code event} criterion). */
    public static void award(Player player, String path) {
        Advancement advancement = Bukkit.getAdvancement(new NamespacedKey(SiftBlocks.NS, path));
        if (advancement != null) {
            player.getAdvancementProgress(advancement).awardCriteria("event");
        }
    }
}
