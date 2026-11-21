package dev.linqfy.bigCasares.modules.duels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The ban/pick kit menu one player sees during the draft. */
final class DuelDraftMenu implements InventoryHolder {

    static final int SIZE = 36;
    static final int FIRST_KIT_SLOT = 9;
    private static final int INFO_SLOT = 4;

    private final DuelSession session;
    private final UUID viewer;
    private final Inventory inventory;

    DuelDraftMenu(DuelSession session, UUID viewer) {
        this.session = session;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Duelo: elegí el kit"));
    }

    DuelSession session() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Kit id for a clicked slot, or null. */
    String kitAt(int slot) {
        int index = slot - FIRST_KIT_SLOT;
        List<String> kits = session.draft().kits();
        return index >= 0 && index < kits.size() ? kits.get(index) : null;
    }

    void render(DuelSettings settings, DuelKitItems items, long secondsLeft) {
        inventory.clear();
        DuelKitDraft draft = session.draft();
        boolean banning = draft.phase() == DuelKitDraft.Phase.BAN;
        String mine = banning ? draft.banOf(viewer) : draft.pickOf(viewer);
        inventory.setItem(INFO_SLOT, item(Material.CLOCK,
            Component.text(banning ? "Fase de BANEO" : "Fase de ELECCIÓN", banning ? NamedTextColor.RED : NamedTextColor.GREEN),
            List.of(
                Component.text(banning ? "Click en un kit para banearlo." : "Click en un kit para elegirlo.", NamedTextColor.GRAY),
                Component.text(mine == null ? "Te quedan " + secondsLeft + "s." : "Esperando al rival...", NamedTextColor.YELLOW),
                Component.text("Si eligen distinto, se sortea entre los dos.", NamedTextColor.DARK_GRAY)
            )));
        List<String> kits = draft.kits();
        for (int i = 0; i < kits.size() && FIRST_KIT_SLOT + i < SIZE; i++) {
            DuelKit kit = settings.kits().get(kits.get(i));
            if (kit == null) {
                continue;
            }
            boolean banned = !banning && draft.isBanned(kit.id()) && !draft.available().contains(kit.id());
            List<Component> lore = new ArrayList<>();
            for (int line = 0; line < Math.min(6, kit.items().size()); line++) {
                DuelItemSpec spec = kit.items().get(line);
                lore.add(Component.text("• " + spec.material().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + (spec.amount() > 1 ? " x" + spec.amount() : ""), NamedTextColor.GRAY));
            }
            if (kit.items().size() > 6) {
                lore.add(Component.text("…", NamedTextColor.GRAY));
            }
            if (banned) {
                lore.add(Component.text("BANEADO", NamedTextColor.DARK_RED));
            } else if (kit.id().equals(mine)) {
                lore.add(Component.text(banning ? "Tu baneo" : "Tu elección", NamedTextColor.GOLD));
            }
            Material icon = banned ? Material.BARRIER : items.icon(kit);
            inventory.setItem(FIRST_KIT_SLOT + i, item(icon,
                Component.text(kit.displayName(), banned ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA), lore));
        }
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }
}
