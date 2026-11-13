package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.modules.legendary.LegendaryWeapon;
import dev.linqfy.bigCasares.items.CustomItemRegistry;

import java.util.List;
import java.util.Locale;

final class GeyserLegendaryItems {
    static List<GeyserCustomItemDefinition> definitions(CustomItemRegistry registry) {
        return LegendaryWeapon.available().stream().filter(weapon -> registry.findById(weapon.id).isPresent())
                .map(GeyserLegendaryItems::definition).toList();
    }

    static GeyserCustomItemDefinition definition(LegendaryWeapon weapon) {
        return new GeyserCustomItemDefinition(
                "minecraft:" + weapon.itemMaterial().toLowerCase(Locale.ROOT), "bigcasares:" + weapon.id,
                "bigcasares:" + weapon.id, "bigcasares.legendary." + weapon.id, weapon.displayName);
    }
}
