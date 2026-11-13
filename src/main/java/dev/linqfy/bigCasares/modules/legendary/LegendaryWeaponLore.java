package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.BigCasares;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class LegendaryWeaponLore implements Runnable {
    private static final Map<LegendaryWeapon, List<String>> ABILITIES = Map.of(
        LegendaryWeapon.BLOODLUST, List.of("Velocidad II / Infection", "Blood Tracker — pasiva, 24 bloques",
                "Blood Trail — Shift + F/G", "Fuerza I", "Blood Hook — F/G"),
        LegendaryWeapon.SHADOW_BLADE, List.of("Shadow Daggers — F/G", "Shadow Leap — Shift + F/G",
                "Mejora de dagas y efectos", "Mejora de alcance y daño", "Máxima potencia de Shadow Blade"),
        LegendaryWeapon.WINDWEAVER, List.of("Wind Dash — Shift + clic izquierdo al aire",
                "Skybreaker — clic derecho", "Tornado — Shift + clic derecho",
                "Wind Prison — Shift + clic izquierdo al suelo", "Heaven's Fall — Shift + F/G"),
        LegendaryWeapon.EARTHBREAKER, List.of("Earthshatter — F/G", "Mejora de alcance y onda",
                "Stalwart Absorption — Shift + F/G", "Mejora de duración y reflejo",
                "Máxima potencia de Earthbreaker"),
        LegendaryWeapon.THUNDER_SPEAR, List.of("Lightning Dash — Shift + clic izquierdo al aire",
                "Thunder Mark — clic derecho", "Chain Lightning — Shift + clic derecho",
                "Thunderstorm — Shift + clic izquierdo mirando arriba", "Wrath of Zeus — Shift + F/G")
    );
    private final BigCasares plugin;
    private final LegendaryPvpLevels levels;

    LegendaryWeaponLore(BigCasares plugin, LegendaryPvpLevels levels) {
        this.plugin = plugin;
        this.levels = levels;
    }

    @Override public void run() {
        plugin.getServer().getOnlinePlayers().forEach(this::refresh);
    }

    private void refresh(Player player) {
        LegendaryItemCarrier.inventory(player);
        for (var item : player.getInventory().getContents()) {
            var weapon = plugin.getCustomItemRegistry().resolveItemId(item)
                    .flatMap(LegendaryWeapon::find).orElse(null);
            if (weapon == null) continue;
            var meta = item.getItemMeta();
            boolean changed = LegendaryCombatAttributes.initialize(meta, weapon);
            var name = weapon.coloredName(meta.displayName());
            if (!name.equals(meta.displayName())) {
                meta.displayName(name);
                changed = true;
            }
            var lore = describe(weapon, levels.level(player.getUniqueId(), weapon));
            if (!lore.equals(meta.getLore())) {
                meta.setLore(lore);
                changed = true;
            }
            if (changed) item.setItemMeta(meta);
        }
    }

    static List<String> describe(LegendaryWeapon weapon, int level) {
        var lore = new ArrayList<String>();
        lore.add("§d" + weapon.displayName);
        lore.add("§6Nivel " + level + "/5");
        lore.add("§7Habilidades (verde = desbloqueada):");
        var abilities = ABILITIES.getOrDefault(weapon, List.of());
        for (int i = 0; i < abilities.size(); i++) {
            lore.add((i < level ? "§a  ✓ " : "§8  🔒 ") + "N" + (i + 1) + ": " + abilities.get(i));
        }
        return List.copyOf(lore);
    }
}
