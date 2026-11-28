package dev.linqfy.bigCasares.modules.toffy;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Warns about configured keys the server will never match: materials, enchantments, and the keys of custom item
 * matches. The catalog check needs the real BigCasares catalog, so the caller passes it in; the default accepts
 * every catalog id.
 */
final class BukkitToffyKeyAudit {
    private final Predicate<String> knownCatalogIds;

    BukkitToffyKeyAudit() {
        this(catalogId -> true);
    }

    /**
     * @param knownCatalogIds whether a {@code bigcasares-item} id exists in the BigCasares catalog
     */
    BukkitToffyKeyAudit(Predicate<String> knownCatalogIds) {
        this.knownCatalogIds = Objects.requireNonNull(knownCatalogIds, "knownCatalogIds");
    }

    /**
     * The audit with every catalog id accepted.
     */
    static List<String> unknownKeys(ToffySettings settings) {
        return new BukkitToffyKeyAudit().audit(settings);
    }

    List<String> audit(ToffySettings settings) {
        Set<String> items = new LinkedHashSet<>(settings.armorValues().keySet());
        settings.weaponFamilies().values().forEach(members -> items.addAll(members.keySet()));
        settings.toolFamilies().values().forEach(members -> items.addAll(members.keySet()));
        Set<String> enchantments = new LinkedHashSet<>(settings.enchantmentValues().keySet());
        for (ToffyReference reference : List.of(settings.zeroReference(), settings.maxReference())) {
            reference.armor().values().forEach(item -> enchantments.addAll(item.enchantments().keySet()));
            for (Map<String, ToffyItem> held : List.of(reference.weapons(), reference.tools())) {
                held.values().forEach(item -> enchantments.addAll(item.enchantments().keySet()));
            }
        }
        for (ToffyCustomItem custom : settings.customItems()) {
            if (custom.match().material() != null) {
                items.add(custom.match().material());
            }
        }

        List<String> warnings = new ArrayList<>();
        for (String item : items) {
            if (item.startsWith(ToffyCustomItem.NAMESPACE + ":")) {
                continue;
            }
            Material material = Material.matchMaterial(item);
            if (material == null || !material.isItem()) {
                warnings.add(item + " is not an item on this server; it will never match");
            }
        }
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (String enchantment : enchantments) {
            NamespacedKey key = NamespacedKey.fromString(enchantment);
            if (key == null || registry.get(key) == null) {
                warnings.add(enchantment + " is not an enchantment on this server; it will never match");
            }
        }
        warnings.addAll(customMatchWarnings(settings.customItems(), knownCatalogIds));
        return List.copyOf(warnings);
    }

    /**
     * Custom item matches whose keys can never match: a {@code bigcasares-item} that is not in the catalog, an
     * {@code item-model} or persistent data key that is not a valid namespaced key. Each warning starts with the
     * YAML path of the match.
     */
    static List<String> customMatchWarnings(List<ToffyCustomItem> customItems, Predicate<String> knownCatalogIds) {
        List<String> warnings = new ArrayList<>();
        for (ToffyCustomItem custom : customItems) {
            ToffyCustomMatch match = custom.match();
            String path = "custom-items." + custom.id() + ".match.";
            if (match.catalogId() != null && !knownCatalogIds.test(match.catalogId())) {
                warnings.add(path + "bigcasares-item: " + match.catalogId()
                    + " is not in the BigCasares catalog; it will never match");
            }
            if (match.itemModel() != null && NamespacedKey.fromString(match.itemModel()) == null) {
                warnings.add(path + "item-model: " + match.itemModel()
                    + " is not a valid namespaced key; it will never match");
            }
            if (match.persistentDataKey() != null && NamespacedKey.fromString(match.persistentDataKey()) == null) {
                warnings.add(path + "persistent-data: " + match.persistentDataKey()
                    + " is not a valid namespaced key; it will never match");
            }
        }
        return warnings;
    }
}
