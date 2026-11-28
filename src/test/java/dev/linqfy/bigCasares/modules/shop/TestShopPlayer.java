package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** A test account: a 36-slot storage inventory and a record of what was dropped at its feet. */
final class TestShopPlayer {
    final ItemStack[] storage = new ItemStack[36];
    final List<ItemStack> dropped = new ArrayList<>();
    final UUID id = UUID.randomUUID();
    boolean failDelivery;
    /** Adds the stacks, then throws, like an inventory that fails after a partial change. */
    boolean failAfterAdding;
    /** How many drops succeed before dropping throws; negative never throws. */
    int dropsBeforeFailure = -1;
    final Player player;

    TestShopPlayer() {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
            new Class<?>[] {PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getStorageContents" -> copy(storage);
                case "setStorageContents" -> {
                    ItemStack[] next = (ItemStack[]) args[0];
                    for (int i = 0; i < storage.length; i++) storage[i] = i < next.length && next[i] != null ? next[i].clone() : null;
                    yield null;
                }
                case "addItem" -> {
                    HashMap<Integer, ItemStack> left = add((ItemStack[]) args[0]);
                    if (failAfterAdding) throw new IllegalStateException("inventory failed after a change");
                    yield left;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[] {World.class},
            (proxy, method, args) -> {
                if (method.getName().equals("dropItemNaturally")) {
                    if (dropsBeforeFailure == dropped.size()) throw new IllegalStateException("world unavailable");
                    dropped.add(((ItemStack) args[1]).clone());
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getInventory" -> inventory;
                case "getUniqueId" -> id;
                case "getWorld" -> world;
                case "getLocation" -> new Location(null, 0, 64, 0);
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    int count(org.bukkit.Material material) {
        int total = 0;
        for (ItemStack stack : storage) if (stack != null && stack.getType() == material) total += stack.getAmount();
        return total;
    }

    private HashMap<Integer, ItemStack> add(ItemStack[] stacks) {
        if (failDelivery) throw new IllegalStateException("inventory unavailable");
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        for (int n = 0; n < stacks.length; n++) {
            ItemStack incoming = stacks[n].clone();
            int remaining = incoming.getAmount();
            int max = 64; // every product in these tests stacks to 64; Material.getMaxStackSize() needs a server
            for (int i = 0; i < storage.length && remaining > 0; i++) {
                ItemStack slot = storage[i];
                if (slot != null && slot.getType() == incoming.getType() && slot.getAmount() < max) {
                    int moved = Math.min(remaining, max - slot.getAmount());
                    slot.setAmount(slot.getAmount() + moved);
                    remaining -= moved;
                }
            }
            for (int i = 0; i < storage.length && remaining > 0; i++) {
                if (storage[i] == null) {
                    int moved = Math.min(remaining, max);
                    ItemStack placed = incoming.clone();
                    placed.setAmount(moved);
                    storage[i] = placed;
                    remaining -= moved;
                }
            }
            if (remaining > 0) {
                ItemStack left = incoming.clone();
                left.setAmount(remaining);
                leftovers.put(n, left);
            }
        }
        return leftovers;
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) copy[i] = contents[i] == null ? null : contents[i].clone();
        return copy;
    }
}
