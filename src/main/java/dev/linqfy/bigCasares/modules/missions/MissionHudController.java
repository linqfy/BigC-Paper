package dev.linqfy.bigCasares.modules.missions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MissionHudController implements Listener {

    private static final int INVENTORY_SIZE = 54;
    private static final int DAILY_SLOT = 20;
    private static final int WEEKLY_SLOT = 24;
    private static final int CLAIM_SLOT = 31;
    private static final int BACK_SLOT = 49;

    private final MissionModule module;
    private final VaultEconomyGateway economyGateway;

    public MissionHudController(MissionModule module, VaultEconomyGateway economyGateway) {
        this.module = module;
        this.economyGateway = economyGateway;
    }

    public void open(Player player, MissionHudView view) {
        MissionPlayerState state = module.getOrCreateState(player);
        Inventory inventory = Bukkit.createInventory(new MissionMenuHolder(view), INVENTORY_SIZE, MissionTexts.title(view));

        switch (view) {
            case MAIN_MENU -> populateMainMenu(inventory, state);
            case DAILY_LIST -> populateAssignmentList(inventory, state.dailyAssignments(), state.dailyResetsAt(), "Volver");
            case WEEKLY_LIST -> populateAssignmentList(inventory, state.weeklyAssignments(), state.weeklyResetsAt(), "Volver");
            case CLAIMABLES -> populateClaimables(inventory, state);
        }

        player.openInventory(inventory);
    }

    public void closeOpenViews() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MissionMenuHolder) {
                player.closeInventory();
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof MissionMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (holder.view() == MissionHudView.MAIN_MENU) {
            if (slot == DAILY_SLOT) {
                open(player, MissionHudView.DAILY_LIST);
            } else if (slot == WEEKLY_SLOT) {
                open(player, MissionHudView.WEEKLY_LIST);
            } else if (slot == CLAIM_SLOT) {
                module.claimAll(player);
                open(player, MissionHudView.CLAIMABLES);
            }
            return;
        }

        if (slot == BACK_SLOT) {
            open(player, MissionHudView.MAIN_MENU);
        } else if (holder.view() == MissionHudView.CLAIMABLES && slot == CLAIM_SLOT) {
            module.claimAll(player);
            open(player, MissionHudView.CLAIMABLES);
        }
    }

    private void populateMainMenu(Inventory inventory, MissionPlayerState state) {
        inventory.setItem(DAILY_SLOT, createMenuItem(Material.CLOCK, ChatColor.GOLD + "Misiones diarias", List.of(
            ChatColor.GRAY + "Activas: " + state.dailyAssignments().size(),
            ChatColor.GRAY + "Reinicio: " + MissionTexts.formatTimeRemaining(Duration.between(Instant.now(), state.dailyResetsAt()))
        )));
        inventory.setItem(WEEKLY_SLOT, createMenuItem(Material.COMPASS, ChatColor.AQUA + "Misiones semanales", List.of(
            ChatColor.GRAY + "Activas: " + state.weeklyAssignments().size(),
            ChatColor.GRAY + "Reinicio: " + MissionTexts.formatTimeRemaining(Duration.between(Instant.now(), state.weeklyResetsAt()))
        )));
        inventory.setItem(CLAIM_SLOT, createMenuItem(Material.GOLD_INGOT, ChatColor.GREEN + "Reclamar recompensas", List.of(
            ChatColor.GRAY + "Pendientes: " + module.countClaimables(state),
            ChatColor.GRAY + "Click para cobrar todo"
        )));
    }

    private void populateAssignmentList(Inventory inventory, Map<String, MissionAssignment> assignments, Instant resetsAt, String backLabel) {
        int slot = 0;
        for (MissionAssignment assignment : assignments.values()) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, createAssignmentItem(assignment, resetsAt));
        }
        inventory.setItem(BACK_SLOT, createMenuItem(Material.BARRIER, ChatColor.RED + backLabel, List.of(ChatColor.GRAY + "Volver al menu principal")));
    }

    private void populateClaimables(Inventory inventory, MissionPlayerState state) {
        int slot = 0;
        for (MissionAssignment assignment : module.claimableAssignments(state)) {
            if (slot >= 45) {
                break;
            }
            inventory.setItem(slot++, createAssignmentItem(assignment, assignment.definition().scope() == MissionScope.DAILY ? state.dailyResetsAt() : state.weeklyResetsAt()));
        }

        inventory.setItem(CLAIM_SLOT, createMenuItem(Material.EMERALD, ChatColor.GREEN + "Cobrar todo", List.of(
            ChatColor.GRAY + "Saldo pendiente: " + economyGateway.format(module.sumClaimables(state)),
            ChatColor.GRAY + "Click para depositar via Vault"
        )));
        inventory.setItem(BACK_SLOT, createMenuItem(Material.BARRIER, ChatColor.RED + "Volver", List.of(ChatColor.GRAY + "Volver al menu principal")));
    }

    private ItemStack createAssignmentItem(MissionAssignment assignment, Instant resetsAt) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + assignment.definition().description());
        lore.add(ChatColor.DARK_GRAY + "Tipo: " + assignment.definition().type().name());
        lore.add(ChatColor.YELLOW + "Progreso: " + assignment.snapshot().progress() + "/" + assignment.definition().goal());
        lore.add(ChatColor.GREEN + "Recompensa: " + economyGateway.format(assignment.definition().reward()));
        lore.add(MissionTexts.status(assignment));
        lore.add(ChatColor.GRAY + "Reinicio: " + MissionTexts.formatTimeRemaining(Duration.between(Instant.now(), resetsAt)));
        return createMenuItem(iconFor(assignment), ChatColor.WHITE + assignment.definition().title(), lore);
    }

    private ItemStack createMenuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material iconFor(MissionAssignment assignment) {
        return switch (assignment.definition().scope()) {
            case DAILY -> Material.PAPER;
            case WEEKLY -> Material.BOOK;
        };
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MissionMenuHolder) {
            event.setCancelled(true);
        }
    }

    /** Identifies mission menus by holder: titles are plain text and can be spoofed or collide. */
    private record MissionMenuHolder(MissionHudView view) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
