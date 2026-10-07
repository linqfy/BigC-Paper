package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Casino de Don Pollo Fino: apuesta un objeto a rojo, negro o verde y gira la ruleta. */
final class DonPollosRouletteMenu implements Listener {

    private static final int BET_SIZE = 45;
    private static final int BET_SLOT = 13;
    private static final int SPIN_BUTTON = 22;
    private static final int CLOSE_BUTTON = 44;
    private static final int INFO = 36;
    private static final Map<Integer, DonPollosRouletteColor> COLOR_BUTTONS = Map.of(
        29, DonPollosRouletteColor.RED, 31, DonPollosRouletteColor.BLACK, 33, DonPollosRouletteColor.GREEN);

    private static final int WHEEL_SIZE = 54;
    /** Casillas del cofre de 6 filas que forman el anillo, en sentido horario desde arriba a la izquierda. */
    private static final int[] RING = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 26, 35, 44, 53, 52, 51, 50, 49, 48, 47, 46, 45, 36, 27, 18, 9
    };
    private static final int WHEEL_BET = 22;
    private static final int WHEEL_STATUS = 31;
    private static final int WHEEL_CHOICE = 21;
    private static final int WHEEL_AGAIN = 39;
    private static final int WHEEL_CLOSE = 41;

    private final JavaPlugin plugin;
    private final DonPollosRoulette roulette;
    private final String title;
    private final Map<UUID, DonPollosRouletteColor> choices = new HashMap<>();
    private final Map<UUID, Spin> spins = new HashMap<>();

    DonPollosRouletteMenu(JavaPlugin plugin, DonPollosRoulette roulette, String title) {
        this.plugin = plugin;
        this.roulette = roulette;
        this.title = title;
    }

    // ------------------------------------------------------------ menu de apuesta

    void openBet(Player player) {
        if (spins.containsKey(player.getUniqueId())) {
            Spin spin = spins.get(player.getUniqueId());
            player.openInventory(spin.inventory());
            return;
        }
        Inventory inventory = Bukkit.createInventory(new BetHolder(), BET_SIZE, title);
        ItemStack border1 = pane(Material.WHITE_STAINED_GLASS_PANE);
        ItemStack border2 = pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemStack inside = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack gold = pane(Material.YELLOW_STAINED_GLASS_PANE);
        for (int slot = 0; slot < BET_SIZE; slot++) {
            boolean border = slot < 9 || slot >= 36 || slot % 9 == 0 || slot % 9 == 8;
            inventory.setItem(slot, border ? (slot % 2 == 0 ? border1 : border2) : inside);
        }
        inventory.setItem(BET_SLOT - 1, gold);
        inventory.setItem(BET_SLOT + 1, gold);
        inventory.setItem(BET_SLOT - 9, item(Material.GOLD_INGOT, "§6§l♠ Casino de Don Pollo Fino ♠", List.of(
            "§71. Pone un objeto en el hueco de abajo.",
            "§72. Elegi §cROJO§7, §8NEGRO §7o §aVERDE§7.",
            "§73. Toca §6GIRAR§7.",
            "§7Si sale tu color, te llevas el doble.")));
        inventory.setItem(BET_SLOT, null);
        inventory.setItem(INFO, item(Material.BOOK, "§eChances", List.of(
            "§cRojo: §f" + roulette.weight(DonPollosRouletteColor.RED) + "/" + roulette.totalWeight(),
            "§8Negro: §f" + roulette.weight(DonPollosRouletteColor.BLACK) + "/" + roulette.totalWeight(),
            "§aVerde: §f" + roulette.weight(DonPollosRouletteColor.GREEN) + "/" + roulette.totalWeight())));
        inventory.setItem(CLOSE_BUTTON, item(Material.BARRIER, "§cCerrar", List.of("§7Te devuelve tu apuesta.")));
        refreshBetButtons(player, inventory);
        player.openInventory(inventory);
    }

    private void refreshBetButtons(Player player, Inventory inventory) {
        DonPollosRouletteColor chosen = choices.get(player.getUniqueId());
        COLOR_BUTTONS.forEach((slot, color) -> inventory.setItem(slot, colorButton(color, color == chosen)));
        boolean ready = chosen != null;
        inventory.setItem(SPIN_BUTTON, item(ready ? Material.NETHER_STAR : Material.CLOCK, "§6§l⟳ GIRAR", List.of(
            ready ? "§7Apostas a " + chosen.displayName() : "§cPrimero elegi un color abajo.",
            "§7Pone el objeto a apostar arriba.")));
    }

    private ItemStack colorButton(DonPollosRouletteColor color, boolean selected) {
        Material material = switch (color) {
            case RED -> Material.RED_CONCRETE;
            case BLACK -> Material.BLACK_CONCRETE;
            case GREEN -> Material.LIME_CONCRETE;
        };
        List<String> lore = new ArrayList<>();
        lore.add("§7Chance: §f" + roulette.weight(color) + "/" + roulette.totalWeight());
        lore.add("§7Paga: §fx" + roulette.multiplier(color));
        lore.add(selected ? "§e✔ Elegido" : "§7Click para elegir");
        ItemStack stack = item(material, color.displayName(), lore);
        if (selected) {
            stack.editMeta(meta -> meta.addEnchant(Enchantment.UNBREAKING, 1, true));
        }
        return stack;
    }

    private void handleBetClick(InventoryClickEvent event, Player player) {
        Inventory top = event.getView().getTopInventory();
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != top) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && !clicked.getType().isAir() && isEmpty(top.getItem(BET_SLOT))) {
                    top.setItem(BET_SLOT, clicked.clone());
                    event.setCurrentItem(null);
                }
            }
            return;
        }
        int slot = event.getSlot();
        if (slot == BET_SLOT) {
            return;
        }
        event.setCancelled(true);
        if (COLOR_BUTTONS.containsKey(slot)) {
            choices.put(player.getUniqueId(), COLOR_BUTTONS.get(slot));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            refreshBetButtons(player, top);
        } else if (slot == SPIN_BUTTON) {
            startSpin(player, top);
        } else if (slot == CLOSE_BUTTON) {
            player.closeInventory();
        }
    }

    // ------------------------------------------------------------ ruleta

    private void startSpin(Player player, Inventory betInventory) {
        DonPollosRouletteColor chosen = choices.get(player.getUniqueId());
        ItemStack bet = betInventory.getItem(BET_SLOT);
        if (chosen == null) {
            fail(player, "Elegi un color: rojo, negro o verde.");
            return;
        }
        if (isEmpty(bet)) {
            fail(player, "Pone un objeto para apostar.");
            return;
        }
        betInventory.setItem(BET_SLOT, null);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        DonPollosRouletteColor result = roulette.spin(random::nextInt);
        int stop = roulette.stopPocket(result, random::nextInt);

        Inventory wheel = Bukkit.createInventory(new WheelHolder(), WHEEL_SIZE, title);
        ItemStack inside = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < WHEEL_SIZE; slot++) {
            wheel.setItem(slot, inside);
        }
        for (int index = 0; index < RING.length; index++) {
            wheel.setItem(RING[index], pocket(roulette.wheel().get(index)));
        }
        ItemStack shown = bet.clone();
        shown.editMeta(meta -> meta.setLore(List.of("§7Tu apuesta")));
        wheel.setItem(WHEEL_BET, shown);
        wheel.setItem(WHEEL_CHOICE, colorButton(chosen, true));
        wheel.setItem(WHEEL_STATUS, item(Material.CLOCK, "§e§lGirando...", List.of("§7Suerte!")));

        Spin spin = new Spin(player.getUniqueId(), bet.clone(), chosen, result, wheel);
        spins.put(player.getUniqueId(), spin);
        player.openInventory(wheel);
        step(spin, DonPollosRoulette.stepDelays(stop), 0);
    }

    private void step(Spin spin, List<Integer> delays, int index) {
        if (spins.get(spin.playerId()) != spin) {
            return;
        }
        if (index >= delays.size()) {
            finish(spin);
            return;
        }
        spin.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int from = spin.ball;
            spin.ball = (spin.ball + 1) % RING.length;
            spin.inventory().setItem(RING[from], pocket(roulette.wheel().get(from)));
            spin.inventory().setItem(RING[spin.ball], ball(roulette.wheel().get(spin.ball)));
            Player player = Bukkit.getPlayer(spin.playerId());
            if (player != null) {
                float pitch = 0.8f + 1.2f * (1f - index / (float) delays.size());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitch);
            }
            step(spin, delays, index + 1);
        }, delays.get(index));
    }

    private void finish(Spin spin) {
        spins.remove(spin.playerId());
        Player player = Bukkit.getPlayer(spin.playerId());
        int payout = roulette.payout(spin.bet().getAmount(), spin.chosen(), spin.result());
        if (player == null) {
            return;
        }
        if (payout > 0) {
            give(player, spin.bet(), payout);
            player.sendMessage("§a¡Salio " + spin.result().displayName() + "§a! Ganaste §f" + payout + "x "
                + name(spin.bet()) + "§a.");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            if (spin.result() == DonPollosRouletteColor.GREEN) {
                Bukkit.broadcastMessage("§a§l¡" + player.getName() + " le pego al VERDE en el casino de Don Pollo Fino!");
            }
        } else {
            player.sendMessage("§cSalio " + spin.result().displayName() + "§c. Perdiste tu apuesta.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        }
        Inventory wheel = spin.inventory();
        wheel.setItem(WHEEL_STATUS, payout > 0
            ? item(Material.EMERALD_BLOCK, "§a§l¡GANASTE!", List.of("§7Salio " + spin.result().displayName(),
                "§7Recibiste §f" + payout + "x " + name(spin.bet())))
            : item(Material.REDSTONE_BLOCK, "§c§lPERDISTE", List.of("§7Salio " + spin.result().displayName())));
        wheel.setItem(WHEEL_BET, null);
        wheel.setItem(WHEEL_AGAIN, item(Material.NETHER_STAR, "§6§lJugar otra vez", List.of()));
        wheel.setItem(WHEEL_CLOSE, item(Material.BARRIER, "§cCerrar", List.of()));
    }

    private void handleWheelClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        boolean spinning = spins.containsKey(player.getUniqueId());
        if (!spinning && event.getSlot() == WHEEL_AGAIN) {
            openBet(player);
        } else if (!spinning && event.getSlot() == WHEEL_CLOSE) {
            player.closeInventory();
        }
    }

    // ------------------------------------------------------------ eventos

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (holder instanceof BetHolder) {
            handleBetClick(event, player);
        } else if (holder instanceof WheelHolder) {
            handleWheelClick(event, player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        int topSize = event.getView().getTopInventory().getSize();
        if (holder instanceof WheelHolder) {
            event.setCancelled(true);
        } else if (holder instanceof BetHolder) {
            for (int raw : event.getRawSlots()) {
                if (raw < topSize && raw != BET_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private java.util.function.Consumer<Player> closed = player -> { };

    /** Se llama al cerrar el casino (para la frase de despedida del Fino). */
    void onMenuClosed(java.util.function.Consumer<Player> listener) {
        this.closed = listener;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if ((event.getInventory().getHolder() instanceof BetHolder || event.getInventory().getHolder() instanceof WheelHolder)
            && event.getPlayer() instanceof Player player) {
            closed.accept(player);
        }
        if (event.getInventory().getHolder() instanceof BetHolder && event.getPlayer() instanceof Player player) {
            ItemStack bet = event.getInventory().getItem(BET_SLOT);
            event.getInventory().setItem(BET_SLOT, null);
            if (!isEmpty(bet)) {
                give(player, bet, bet.getAmount());
            }
        }
    }

    /** Si el jugador se va en medio del giro, se le devuelve la apuesta. */
    void playerQuit(Player player) {
        Spin spin = spins.remove(player.getUniqueId());
        if (spin != null) {
            if (spin.task != null) {
                spin.task.cancel();
            }
            give(player, spin.bet(), spin.bet().getAmount());
        }
        choices.remove(player.getUniqueId());
    }

    void shutdown() {
        for (Spin spin : new ArrayList<>(spins.values())) {
            Player player = Bukkit.getPlayer(spin.playerId());
            if (player != null) {
                playerQuit(player);
                player.closeInventory();
            }
        }
        spins.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BetHolder) {
                player.closeInventory();
            }
        }
    }

    // ------------------------------------------------------------ utilidades

    private static void give(Player player, ItemStack template, int amount) {
        int max = Math.max(1, template.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(max, remaining));
            remaining -= stack.getAmount();
            player.getInventory().addItem(stack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
    }

    private static void fail(Player player, String message) {
        player.sendMessage("§c" + message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() < 1;
    }

    private static String name(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return stack.getItemMeta().getDisplayName();
        }
        return stack.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private static ItemStack pocket(DonPollosRouletteColor color) {
        Material material = switch (color) {
            case RED -> Material.RED_CONCRETE;
            case BLACK -> Material.BLACK_CONCRETE;
            case GREEN -> Material.LIME_CONCRETE;
        };
        return item(material, color.displayName(), List.of());
    }

    private static ItemStack ball(DonPollosRouletteColor color) {
        ItemStack stack = item(Material.SNOWBALL, "§f§l● " + color.displayName(), List.of());
        stack.editMeta(meta -> meta.addEnchant(Enchantment.UNBREAKING, 1, true));
        return stack;
    }

    private static ItemStack pane(Material material) {
        return item(material, " ", List.of());
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }

    private static final class BetHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class WheelHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final class Spin {
        private final UUID playerId;
        private final ItemStack bet;
        private final DonPollosRouletteColor chosen;
        private final DonPollosRouletteColor result;
        private final Inventory inventory;
        private int ball;
        private BukkitTask task;

        private Spin(UUID playerId, ItemStack bet, DonPollosRouletteColor chosen,
                     DonPollosRouletteColor result, Inventory inventory) {
            this.playerId = playerId;
            this.bet = bet;
            this.chosen = chosen;
            this.result = result;
            this.inventory = inventory;
        }

        UUID playerId() { return playerId; }
        ItemStack bet() { return bet; }
        DonPollosRouletteColor chosen() { return chosen; }
        DonPollosRouletteColor result() { return result; }
        Inventory inventory() { return inventory; }
    }
}
