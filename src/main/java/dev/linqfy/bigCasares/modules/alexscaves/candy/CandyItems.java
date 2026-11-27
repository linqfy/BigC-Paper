package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Candy Cavity's tools (mod {@code ShotGumItem}, {@code CandyCaneHookItem}, {@code SackOfSatingItem}):
 * <ul>
 *   <li>Shot Gum: right click cranks it (5 ticks) and fires two bouncing gumballs (4 damage, 4 bounces); four shots a
 *   load, reloaded with a gumball pile from the inventory when empty.</li>
 *   <li>Candy Cane Hook: right click throws a hook that sticks to blocks and mobs; right click again reels in, flinging
 *   the player to the block or pulling the mob in. Hooked to a Gum Worm, it takes the player onto the worm to ride.</li>
 *   <li>Sack of Sating: right click food onto it in the inventory to store its whole stack's hunger; while carried it
 *   feeds 1 hunger every 100 ticks to a hungry player.</li>
 * </ul>
 */
public final class CandyItems implements Listener, AutoCloseable {
    static final NamespacedKey GUMBALLS = new NamespacedKey("bigcasares", "ac_shot_gum_gumballs");
    static final NamespacedKey SACK_HUNGER = new NamespacedKey("bigcasares", "ac_sack_hunger");
    static final int MAX_GUMBALLS = 4;
    private final ModMobService mobs;
    private final Map<UUID, Hook> hooks = new HashMap<>();
    private final Map<UUID, Long> shooting = new HashMap<>();
    private final BukkitTask sackTask;
    private boolean closed;

    public CandyItems(ModMobService mobs) {
        this.mobs = mobs;
        this.sackTask = new BukkitRunnable() {
            @Override
            public void run() {
                feedFromSacks();
            }
        }.runTaskTimer(mobs.plugin(), 100L, 100L);
    }

    private boolean is(ItemStack stack, String id) {
        return stack != null && !stack.getType().isAir() && mobs.itemId(stack).filter(id::equals).isPresent();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (is(held, "ac_shot_gum")) {
            event.setCancelled(true);
            shotGum(player, held, event.getHand());
        } else if (is(held, "ac_candy_cane_hook")) {
            event.setCancelled(true);
            hook(player, held, event.getHand());
        }
    }

    /** Right clicks on a Gum Worm segment (an interaction) still use the Shot Gum or the hook. */
    @EventHandler(ignoreCancelled = true)
    public void onUseOnSegment(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (CandyGumWorm.headOf(event.getRightClicked()).isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getHand() == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        if (is(held, "ac_shot_gum")) {
            event.setCancelled(true);
            shotGum(player, held, event.getHand());
        } else if (is(held, "ac_candy_cane_hook")) {
            event.setCancelled(true);
            hook(player, held, event.getHand());
        }
    }

    /** Getting off a Gum Worm: put the player on the surface, never inside the ground the worm swam through. */
    @EventHandler
    public void onDismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player) || CandyGumWorm.headOf(event.getDismounted()).isEmpty()) {
            return;
        }
        if (closed) {
            // The module is stopping: close() puts the rider on the surface once the dismount is over.
            return;
        }
        Location from = event.getDismounted().getLocation();
        Runnable surface = () -> surface(player, from);
        // Also when the module stops first: the player must not stay inside the ground.
        mobs.tasks().later(1L, surface, surface);
    }

    private static void surface(Player player, Location from) {
        if (!player.isOnline()) {
            return;
        }
        Location safe = from.clone();
        for (int i = 0; i < 24 && safe.getBlockY() < safe.getWorld().getMaxHeight() - 2; i++) {
            if (safe.getBlock().isPassable() && safe.clone().add(0, 1, 0).getBlock().isPassable()) {
                break;
            }
            safe.add(0, 1, 0);
        }
        safe.setDirection(player.getLocation().getDirection());
        player.teleport(safe);
    }

    // --- Shot Gum ---------------------------------------------------------------------------------------------------

    static int gumballs(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? MAX_GUMBALLS : meta.getPersistentDataContainer().getOrDefault(GUMBALLS, PersistentDataType.INTEGER, MAX_GUMBALLS);
    }

    static void setGumballs(ItemStack stack, int count) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(GUMBALLS, PersistentDataType.INTEGER, count);
        if (meta instanceof Damageable damageable) {
            // The durability bar shows the gumballs left.
            damageable.setDamage(MAX_GUMBALLS - count);
        }
        stack.setItemMeta(meta);
    }

    private void shotGum(Player player, ItemStack held, EquipmentSlot hand) {
        long now = mobs.plugin().getServer().getCurrentTick();
        if (now - shooting.getOrDefault(player.getUniqueId(), Long.MIN_VALUE / 2) < 10) {
            return;
        }
        if (gumballs(held) <= 0) {
            boolean creative = player.getGameMode() == GameMode.CREATIVE;
            ItemStack ammo = creative ? null : findAmmo(player);
            if (ammo != null || creative) {
                if (ammo != null) {
                    ammo.setAmount(ammo.getAmount() - 1);
                }
                setGumballs(held, MAX_GUMBALLS);
                player.getWorld().playSound(player.getLocation(), "alexscaves:shotgum_reload", SoundCategory.PLAYERS, 1.0f, 1.0f);
            } else {
                player.getWorld().playSound(player.getLocation(), "alexscaves:shotgum_empty", SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
            return;
        }
        shooting.put(player.getUniqueId(), now);
        // Mod: the crank turns for 5 ticks before it fires.
        mobs.tasks().later(5L, () -> fire(player, hand));
    }

    private void fire(Player player, EquipmentSlot hand) {
        if (closed || !player.isOnline() || player.isDead()) {
            return;
        }
        ItemStack held = hand == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        if (!is(held, "ac_shot_gum") || gumballs(held) <= 0) {
            return;
        }
        player.getWorld().playSound(player.getLocation(), "alexscaves:shotgum_shoot", SoundCategory.PLAYERS, 1.0f, 1.0f);
        boolean leftHand = hand == EquipmentSlot.OFF_HAND;
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        Vector side = look.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 2; i++) {
            double offset = (i < 1 ? 0.15 : -0.15) + (leftHand ? -0.35 : 0.35);
            Location from = player.getLocation().add(0, player.getHeight() * 0.8, 0).add(side.clone().multiply(offset))
                .add(look.clone().multiply(0.75));
            // Mod: speed 1.5, inaccuracy 5.
            Vector velocity = look.clone().add(new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian())
                .multiply(0.0075 * 5.0)).normalize().multiply(1.5);
            CandyMobListener.launchGumball(mobs, player, from, velocity, 4.0, 4);
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            setGumballs(held, Math.max(0, gumballs(held) - 1));
        }
    }

    private ItemStack findAmmo(Player player) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (is(stack, "ac_gumball_pile")) {
                return stack;
            }
        }
        return null;
    }

    // --- Candy Cane Hook --------------------------------------------------------------------------------------------

    private void hook(Player player, ItemStack held, EquipmentSlot hand) {
        Hook active = hooks.get(player.getUniqueId());
        if (active != null && !active.isCancelled()) {
            active.reel();
            if (player.getGameMode() != GameMode.CREATIVE && held.getItemMeta() instanceof Damageable damageable) {
                damageable.setDamage(damageable.getDamage() + 1);
                held.setItemMeta(damageable);
                if (damageable.getDamage() >= 200) {
                    held.setAmount(0);
                }
            }
            player.getWorld().playSound(player.getLocation(), "alexscaves:candy_cane_hook_reel", SoundCategory.PLAYERS, 1.0f, 1.0f);
            return;
        }
        player.getWorld().playSound(player.getLocation(), "alexscaves:candy_cane_hook_launch", SoundCategory.PLAYERS, 0.5f, 1.0f);
        Hook hook = new Hook(player, held.clone(), hand);
        hooks.put(player.getUniqueId(), hook);
        mobs.tasks().repeat(hook, 0L, 1L, hook::finish);
    }

    /** Mod {@code CandyCaneHookEntity}: flies, sticks, and pulls the player (or the hooked mob) when reeled. */
    final class Hook extends BukkitRunnable {
        private final Player owner;
        private final EquipmentSlot hand;
        private final ItemDisplay display;
        private Vector velocity;
        private Location stuck;
        private Entity hooked;
        private int age;

        Hook(Player owner, ItemStack look, EquipmentSlot hand) {
            this.owner = owner;
            this.hand = hand;
            Location from = owner.getEyeLocation();
            this.velocity = from.getDirection().multiply(1.6);
            this.display = from.getWorld().spawn(from, ItemDisplay.class, shown -> {
                shown.setItemStack(look);
                shown.setPersistent(false);
                shown.setTeleportDuration(1);
            });
        }

        void reel() {
            if (stuck != null && hooked == null) {
                // Mod fling: towards the hook, a fifth of the way, at most 1 a tick, and a little up.
                Vector delta = stuck.toVector().subtract(owner.getLocation().toVector()).multiply(0.2);
                if (delta.length() > 1.0) {
                    delta.normalize();
                }
                owner.setVelocity(owner.getVelocity().add(delta.add(new Vector(0, 0.2, 0))));
            } else if (hooked instanceof LivingEntity living && CandyGumWorm.headOf(living).isEmpty()
                && mobs.typeOf(living).filter(found -> found == CandyGumWorm.GUM_WORM).isEmpty()) {
                Vector delta = owner.getLocation().toVector().subtract(living.getLocation().toVector()).multiply(0.2);
                if (delta.length() > 1.0) {
                    delta.normalize();
                }
                living.setVelocity(living.getVelocity().add(delta.add(new Vector(0, 0.2, 0))));
            }
            finish();
        }

        private void finish() {
            display.remove();
            hooks.remove(owner.getUniqueId(), this);
            if (!isCancelled()) {
                cancel();
            }
        }

        private boolean holding() {
            ItemStack held = hand == EquipmentSlot.OFF_HAND ? owner.getInventory().getItemInOffHand() : owner.getInventory().getItemInMainHand();
            return is(held, "ac_candy_cane_hook");
        }

        @Override
        public void run() {
            age++;
            if (!owner.isOnline() || owner.isDead() || !display.isValid() || owner.getWorld() != display.getWorld()
                || !holding() || display.getLocation().distance(owner.getLocation()) > 64.0 || stuck == null && age > 100) {
                finish();
                return;
            }
            Location at = display.getLocation();
            if (hooked != null) {
                if (!hooked.isValid()) {
                    finish();
                    return;
                }
                at = hooked.getLocation().add(0, hooked.getHeight() * 0.5, 0);
                display.teleport(at);
                ride();
            } else if (stuck == null) {
                Location next = at.clone().add(velocity);
                velocity.multiply(0.99).add(new Vector(0, -0.05, 0));
                for (Entity near : at.getWorld().getNearbyEntities(next, 1.0, 1.0, 1.0)) {
                    // Players are never hooked (it would pull them out of protected areas), and the hook has to touch
                    // the mob, not reach it through a wall.
                    if (near != owner && near != display && !(near instanceof Player)
                        && (near instanceof LivingEntity || CandyGumWorm.headOf(near).isPresent())
                        && near.getBoundingBox().expand(0.3).contains(next.toVector())) {
                        hooked = near;
                        break;
                    }
                }
                if (hooked == null && !next.getBlock().isPassable()) {
                    stuck = at.clone();
                } else {
                    display.teleport(next);
                    at = next;
                }
            }
            if (age % 2 == 0) {
                line(owner.getLocation().add(0, 1.2, 0), at);
            }
        }

        /** Hooked to a Gum Worm: pulled to its riding segment and seated on it within 4 blocks (mod onMounted). */
        private void ride() {
            java.util.UUID headId = CandyGumWorm.headOf(hooked).orElse(
                mobs.typeOf(hooked).filter(found -> found == CandyGumWorm.GUM_WORM).isPresent() ? hooked.getUniqueId() : null);
            if (headId == null || owner.isInsideVehicle() || owner.isSneaking()) {
                return;
            }
            Entity head = owner.getServer().getEntity(headId);
            if (!(head instanceof LivingEntity living)
                || !(mobs.behaviourOf(living).orElse(null) instanceof CandyGumWorm.GumWorm worm)) {
                return;
            }
            Location seat = worm.ridingSegmentLocation();
            if (seat == null) {
                return;
            }
            Vector move = seat.toVector().subtract(owner.getLocation().toVector());
            if (move.length() < 4.0) {
                if (worm.mount(owner)) {
                    finish();
                }
            } else {
                owner.setVelocity(owner.getVelocity().multiply(0.8).add(move.normalize()));
            }
        }

        private void line(Location from, Location to) {
            Vector step = to.toVector().subtract(from.toVector());
            double length = step.length();
            if (length < 0.5) {
                return;
            }
            step.normalize().multiply(0.6);
            Location point = from.clone();
            for (double d = 0; d < length; d += 0.6) {
                point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(0xE8, 0x3A, 0x3A), 0.6f));
                point.add(step);
            }
        }
    }

    /** The rider of a Gum Worm bites by swinging (mod: the mouth forced open by the attack key). */
    @EventHandler(ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();
        if (vehicle == null) {
            return;
        }
        CandyGumWorm.headOf(vehicle).map(id -> event.getPlayer().getServer().getEntity(id))
            .filter(LivingEntity.class::isInstance)
            .flatMap(head -> mobs.behaviourOf(head))
            .filter(CandyGumWorm.GumWorm.class::isInstance)
            .ifPresent(worm -> ((CandyGumWorm.GumWorm) worm).forceBite());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shooting.remove(event.getPlayer().getUniqueId());
        Hook hook = hooks.remove(event.getPlayer().getUniqueId());
        if (hook != null) {
            hook.finish();
        }
    }

    // --- Sack of Sating ---------------------------------------------------------------------------------------------

    static int sackHunger(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? 0 : meta.getPersistentDataContainer().getOrDefault(SACK_HUNGER, PersistentDataType.INTEGER, 0);
    }

    static void setSackHunger(ItemStack stack, int hunger) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(SACK_HUNGER, PersistentDataType.INTEGER, hunger);
        meta.lore(List.of(Component.text("Alex's Caves", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Comida guardada: " + hunger, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        stack.setItemMeta(meta);
    }

    /** Mod {@code overrideOtherStackedOnMe}: right click a food stack onto the sack to store all of its hunger. */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onSackClick(InventoryClickEvent event) {
        ItemStack sack = event.getCurrentItem();
        ItemStack food = event.getCursor();
        if (event.getClick() != ClickType.RIGHT || !is(sack, "ac_sack_of_sating") || food == null || food.getType().isAir()
            || !(event.getWhoClicked() instanceof Player player)
            // Only the player's own inventory: never menus or other plugins' views.
            || event.getClickedInventory() != player.getInventory()) {
            return;
        }
        FoodProperties properties = food.getData(DataComponentTypes.FOOD);
        if (properties == null || properties.nutrition() <= 0) {
            return;
        }
        event.setCancelled(true);
        int hunger = properties.nutrition() * food.getAmount();
        Material leftover = food.getType() == Material.HONEY_BOTTLE ? Material.GLASS_BOTTLE
            : food.getType() == Material.MUSHROOM_STEW || food.getType() == Material.RABBIT_STEW
            || food.getType() == Material.BEETROOT_SOUP ? Material.BOWL : null;
        if (leftover != null) {
            ItemStack container = new ItemStack(leftover, food.getAmount());
            player.getInventory().addItem(container).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        }
        event.getView().setCursor(null);
        setSackHunger(sack, sackHunger(sack) + hunger);
        event.setCurrentItem(sack);
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.5f, 1.4f);
    }

    /** Mod {@code inventoryTick}: every 100 ticks a hungry player gets 1 hunger (and 0.1 saturation) from a sack. */
    private void feedFromSacks() {
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            // Mod: each carried sack feeds on its own while the player is hungry.
            for (ItemStack stack : player.getInventory().getContents()) {
                if (player.getFoodLevel() >= 20) {
                    break;
                }
                if (!is(stack, "ac_sack_of_sating") || sackHunger(stack) <= 0) {
                    continue;
                }
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
                player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 0.1f));
                setSackHunger(stack, sackHunger(stack) - 1);
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.5f, 1.0f);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        sackTask.cancel();
        // Before the worms are released: teleporting from inside the dismount would be undone by it.
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            Entity mount = player.getVehicle();
            if (mount != null && CandyGumWorm.headOf(mount).isPresent()) {
                Location from = mount.getLocation();
                player.leaveVehicle();
                surface(player, from);
            }
        }
        for (Hook hook : List.copyOf(hooks.values())) {
            hook.finish();
        }
        hooks.clear();
    }
}
