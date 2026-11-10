package dev.linqfy.bigCasares.modules.customcrossbow;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SniperCrossbowListenerTest {

    private final UUID playerId = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();
    private final CrossbowEnchantmentData data = new CrossbowEnchantmentData();
    private final CrossbowEnchantmentDataTest.TestCrossbow sniper = new CrossbowEnchantmentDataTest.TestCrossbow();
    private ItemStack main = sniper;
    private ItemStack offhand = new ItemStack(Material.SHIELD);
    private boolean sneaking;
    private long tick;
    private double health = 20;
    private final FakeView view = new FakeView();
    private final World world = proxy(World.class, (method, args) -> switch (method) {
        case "getUID" -> worldId;
        case "getGameTime" -> tick;
        default -> throw new UnsupportedOperationException(method);
    });
    private final PlayerInventory inventory = proxy(PlayerInventory.class, (method, args) -> switch (method) {
        case "getItem" -> args[0] == EquipmentSlot.HAND ? main : offhand;
        case "getHeldItemSlot" -> 0;
        default -> throw new UnsupportedOperationException(method);
    });
    private final Player player = proxy(Player.class, (method, args) -> switch (method) {
        case "getUniqueId" -> playerId;
        case "getInventory" -> inventory;
        case "getWorld" -> world;
        case "getGameMode" -> GameMode.SURVIVAL;
        case "isOnline" -> true;
        case "isDead", "hasCooldown" -> false;
        case "isSneaking" -> sneaking;
        case "getHealth" -> health;
        case "getCooldown" -> 0;
        case "getWalkSpeed" -> 0.2f;
        case "startUsingItem", "clearActiveItem", "updateInventory" -> null;
        case "setHealth" -> { health = (double) args[0]; yield null; }
        default -> throw new UnsupportedOperationException(method);
    });
    private final Server server = proxy(Server.class, (method, args) -> switch (method) {
        case "getOnlinePlayers" -> List.of(player);
        case "getPlayer" -> player;
        default -> throw new UnsupportedOperationException(method);
    });
    private final SniperCrossbowListener listener = new SniperCrossbowListener(server, data, new SniperDamageService(), view);

    SniperCrossbowListenerTest() {
        data.apply(sniper, CrossbowEnchantment.SNIPER);
    }

    @Test
    void crouchingScopesTheCrossbowWithoutASeparateSpyglassOrInventoryChanges() {
        ItemStack shield = offhand;
        crouch();
        assertTrue(view.zoomed.contains(playerId));
        assertSame(sniper, main);
        assertSame(shield, offhand);
        assertEquals(0.2f, player.getWalkSpeed());
        assertEquals(20, health);
    }

    @Test
    void idleCrouchingDoesNotChargeHearts() {
        crouch();
        tick = 180;
        listener.tick();
        assertEquals(0, view.hearts);
        assertEquals(20, health);
    }

    @Test
    void standingCancelsEvenAFullChargeWithoutSpendingHearts() {
        crouch();
        listener.onInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
            main, null, BlockFace.SELF, EquipmentSlot.HAND));
        tick = 180;
        listener.tick();
        assertEquals(6, view.hearts);

        sneaking = false;
        listener.onSneak(new PlayerToggleSneakEvent(player, false));
        assertFalse(view.zoomed.contains(playerId));
        assertEquals(20, health);

        crouch();
        tick = 185;
        listener.tick();
        assertEquals(0, view.hearts);
    }

    @Test
    void offhandCrossbowKeepsTheMainHandItemAndClosingRestoresTheCamera() {
        ItemStack shield = offhand;
        main = shield;
        offhand = sniper;
        crouch();
        assertTrue(view.zoomed.contains(playerId));

        listener.close();

        assertTrue(view.zoomed.isEmpty());
        assertSame(shield, main);
        assertSame(sniper, offhand);
        assertEquals(20, health);
    }

    @Test
    void changingTheHeldWeaponDiscardsChargeAndRestoresTheCamera() {
        crouch();
        main = new ItemStack(Material.STONE);
        listener.tick();
        assertFalse(view.zoomed.contains(playerId));
        assertEquals(20, health);
    }

    @Test
    void stoppingUseWithoutAReleaseDiscardsChargeEvenIfStillCrouching() {
        crouch();
        listener.onInteract(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
            main, null, BlockFace.SELF, EquipmentSlot.HAND));
        tick = 90;
        listener.tick();
        assertEquals(3, view.hearts);
        view.loading = false;
        tick = 95;
        listener.tick();
        assertEquals(0, view.hearts);
        listener.release(player, sniper);
        assertEquals(20, health);
    }

    @Test
    void queuedCreativeMoveCannotDeleteTheRealBowAfterItsProxyWasRejected() {
        crouch();
        InventoryView opened = proxy(InventoryView.class, (method, args) -> switch (method) {
            case "getPlayer" -> player;
            case "getInventory" -> inventory;
            case "convertSlot" -> (int) args[0] - 36;
            default -> throw new UnsupportedOperationException(method);
        });
        InventoryCreativeEvent destination = new InventoryCreativeEvent(opened,
            InventoryType.SlotType.QUICKBAR, 36, new ItemStack(Material.SPYGLASS));
        listener.onInventoryClick(destination);
        assertTrue(destination.isCancelled());

        InventoryCreativeEvent sourceRemoval = new InventoryCreativeEvent(opened,
            InventoryType.SlotType.QUICKBAR, 36, plainItem(Material.AIR));
        listener.onInventoryClick(sourceRemoval);
        assertTrue(sourceRemoval.isCancelled());
        assertSame(sniper, main);

        listener.onInventoryClose(new InventoryCloseEvent(opened));
        InventoryCreativeEvent subsequentEdit = new InventoryCreativeEvent(opened,
            InventoryType.SlotType.QUICKBAR, 36, plainItem(Material.STONE));
        listener.onInventoryClick(subsequentEdit);
        assertFalse(subsequentEdit.isCancelled());
    }

    @Test
    void scopedOffhandSniperTakesPriorityOverTheMainhandShield() {
        main = offhand;
        offhand = sniper;
        crouch();
        PlayerInteractEvent click = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
            main, null, BlockFace.SELF, EquipmentSlot.HAND);

        listener.onInteract(click);

        assertEquals(Event.Result.DENY, click.useItemInHand());
        assertEquals(Event.Result.DENY, click.useInteractedBlock());
        assertEquals(20, health);
        tick = 30;
        listener.tick();
        assertEquals(1, view.hearts);
    }

    private static ItemStack plainItem(Material material) {
        return new ItemStack(material) {
            @Override
            public boolean hasItemMeta() { return false; }
        };
    }

    private void crouch() {
        // Bukkit fires the event before updating Player.isSneaking().
        listener.onSneak(new PlayerToggleSneakEvent(player, true));
        sneaking = true;
    }

    private static final class FakeView implements SniperScopeView {
        private final Set<UUID> zoomed = new HashSet<>();
        private int hearts = -1;
        private boolean loading;

        @Override
        public boolean show(Player player, EquipmentSlot hand) { zoomed.add(player.getUniqueId()); return true; }
        @Override
        public void using(Player player, EquipmentSlot hand, boolean value) { loading = value; }
        @Override
        public boolean isUsing(Player player, EquipmentSlot hand) { return loading; }
        @Override
        public void hide(Player player) { zoomed.remove(player.getUniqueId()); }
        @Override
        public void charge(Player player, int value) { hearts = value; }
    }

    private interface Call { Object invoke(String method, Object[] args); }

    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) ->
            switch (method.getName()) {
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                case "toString" -> type.getSimpleName();
                default -> call.invoke(method.getName(), args);
            }));
    }
}
