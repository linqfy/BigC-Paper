package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.FacingDisplays;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Placed dinosaur chops (mod {@code DinosaurChopBlock}): the chop items are placed on a block face, facing the player,
 * and eaten in four bites like a cake (raw: 3 food, 0.2 saturation modifier each, plus the primordial armor bonus;
 * cooked: 7 and 0.35); after the last bite a thin bone stays, along the chop's axis. Without a block state of its
 * own the chop is an {@code ItemDisplay} with the mod's model for its bites over an {@code Interaction} box (right
 * click eats, a hit removes it; the mod's loot table is empty, so nothing drops). The bites live in the display's
 * persistent data.
 */
public final class DinosaurChops implements Listener {
    static final int MAX_BITES = 3;
    private static final String RAW = "dinosaur_chop";
    private static final String COOKED = "cooked_dinosaur_chop";

    private final ModMobService mobs;
    /** Thin bone along x, y, z. */
    private final BlockData[] thinBone;
    private final NamespacedKey chopKey;
    private final NamespacedKey bitesKey;
    private final NamespacedKey facingKey;
    private final NamespacedKey linkKey;

    public DinosaurChops(ModMobService mobs, BlockData[] thinBone) {
        this.mobs = mobs;
        this.thinBone = thinBone.clone();
        this.chopKey = new NamespacedKey("bigcasares", "ac_dinosaur_chop");
        this.bitesKey = new NamespacedKey("bigcasares", "ac_dinosaur_chop_bites");
        this.facingKey = new NamespacedKey("bigcasares", "ac_dinosaur_chop_facing");
        this.linkKey = new NamespacedKey("bigcasares", "ac_dinosaur_chop_link");
    }

    /** One bite: food and saturation modifier (mod {@code eat}, {@code PrimordialArmorItem.getExtraSaturationFromArmor}). */
    record Bite(int nutrition, float modifier) {
    }

    static Bite bite(boolean raw, int armorPieces) {
        if (raw) {
            return new Bite(3 + armorPieces, 0.2f + armorPieces * 0.125f);
        }
        return new Bite(7, 0.35f);
    }

    /** Vanilla {@code FoodData.eat}: {food, saturation} after eating. */
    static float[] eat(int food, float saturation, Bite bite) {
        int newFood = Math.min(20, food + bite.nutrition());
        float newSaturation = Math.min(saturation + bite.nutrition() * bite.modifier() * 2.0f, newFood);
        return new float[] {newFood, newSaturation};
    }

    /** Item model of a chop's display. */
    static String displayModel(String chop, int bites) {
        return "display/" + chop + "_" + bites;
    }

    /** Thin bone index (x 0, y 1, z 2) of the chop's facing axis. */
    static int axis(BlockFace facing) {
        return facing.getModX() != 0 ? 0 : facing.getModY() != 0 ? 1 : 2;
    }

    // --- Placing ----------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || held == null
            || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        String chop = mobs.itemId(held).map(id -> id.substring(3)).filter(id -> id.equals(RAW) || id.equals(COOKED))
            .orElse(null);
        Player player = event.getPlayer();
        if (chop == null || clicked.getType().isInteractable() && !player.isSneaking()) {
            return;
        }
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
        if (!target.isEmpty() || target.getY() >= target.getWorld().getMaxHeight() || chopAt(target) != null) {
            // Mod BlockItem: when the chop cannot be placed the item is used normally (eaten).
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        BlockPlaceEvent check = new BlockPlaceEvent(target, target.getState(), clicked, held, player, true,
            event.getHand());
        if (!check.callEvent()) {
            return;
        }
        Vector look = player.getLocation().getDirection();
        BlockFace facing = FacingDisplays.placedFacing(look.getX(), look.getY(), look.getZ());
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay display = target.getWorld().spawn(center, ItemDisplay.class, spawned -> {
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setTransformation(new Transformation(new Vector3f(), FacingDisplays.leftRotation(facing),
                new Vector3f(1, 1, 1), new Quaternionf()));
            spawned.setItemStack(shown(chop, 0));
            spawned.setPersistent(true);
            PersistentDataContainer data = spawned.getPersistentDataContainer();
            data.set(chopKey, PersistentDataType.STRING, chop);
            data.set(bitesKey, PersistentDataType.INTEGER, 0);
            data.set(facingKey, PersistentDataType.STRING, facing.name());
        });
        Interaction box = target.getWorld().spawn(target.getLocation().add(0.5, 0, 0.5), Interaction.class, spawned -> {
            spawned.setInteractionWidth(1.0f);
            spawned.setInteractionHeight(1.0f);
            spawned.setPersistent(true);
            spawned.getPersistentDataContainer().set(linkKey, PersistentDataType.STRING, display.getUniqueId().toString());
        });
        display.getPersistentDataContainer().set(linkKey, PersistentDataType.STRING, box.getUniqueId().toString());
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
        player.swingHand(event.getHand());
        target.getWorld().playSound(center, Sound.BLOCK_MUD_PLACE, 1.0f, 1.0f);
    }

    private static ItemStack shown(String chop, int bites) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("alexscaves", displayModel(chop, bites)));
        return stack;
    }

    private ItemDisplay chopAt(Block block) {
        for (Entity nearby : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 0.4, 0.4, 0.4)) {
            if (nearby instanceof ItemDisplay display && display.getPersistentDataContainer().has(chopKey)) {
                return display;
            }
        }
        return null;
    }

    // --- Eating and breaking ----------------------------------------------------------------------

    /**
     * Only {@code PlayerInteractEntityEvent} itself is handled ({@code PlayerInteractAtEntityEvent} has its own
     * handler list), once per hand: with the main hand only, one right click is one bite.
     */
    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEntityEvent event) {
        ItemDisplay chop = linkedChop(event.getRightClicked());
        if (chop == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        boolean creative = player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
        if (!creative && player.getFoodLevel() >= 20) {
            // Mod: Player.canEat(false).
            return;
        }
        Block block = chop.getLocation().getBlock();
        // Protection plugins: using the chop is using its block.
        PlayerInteractEvent use = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP,
            EquipmentSlot.HAND);
        use.callEvent();
        if (use.useInteractedBlock() == Event.Result.DENY) {
            return;
        }
        PersistentDataContainer data = chop.getPersistentDataContainer();
        boolean raw = RAW.equals(data.get(chopKey, PersistentDataType.STRING));
        float[] fed = eat(player.getFoodLevel(), player.getSaturation(), bite(raw, raw ? armorPieces(player) : 0));
        FoodLevelChangeEvent hunger = new FoodLevelChangeEvent(player, (int) fed[0]);
        if (!hunger.callEvent()) {
            return;
        }
        int food = Math.max(0, Math.min(20, hunger.getFoodLevel()));
        player.setFoodLevel(food);
        player.setSaturation(Math.min(fed[1], food));
        player.incrementStatistic(Statistic.CAKE_SLICES_EATEN);
        player.swingHand(EquipmentSlot.HAND);
        chop.getWorld().playSound(chop.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
        int bites = data.getOrDefault(bitesKey, PersistentDataType.INTEGER, 0);
        if (bites < MAX_BITES) {
            data.set(bitesKey, PersistentDataType.INTEGER, bites + 1);
            chop.setItemStack(shown(raw ? RAW : COOKED, bites + 1));
            return;
        }
        // Last bite: the thin bone stays where the chop was.
        BlockFace facing = parseFacing(data.get(facingKey, PersistentDataType.STRING));
        remove(chop, event.getRightClicked());
        if (block.isEmpty()) {
            block.setBlockData(thinBone[axis(facing)], false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(PrePlayerAttackEntityEvent event) {
        ItemDisplay chop = linkedChop(event.getAttacked());
        if (chop == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer().getGameMode() == GameMode.ADVENTURE) {
            return;
        }
        // Protection plugins: hitting the chop is breaking its block.
        if (!new BlockBreakEvent(chop.getLocation().getBlock(), event.getPlayer()).callEvent()) {
            return;
        }
        chop.getWorld().playSound(chop.getLocation(), Sound.BLOCK_MUD_BREAK, 1.0f, 1.0f);
        remove(chop, event.getAttacked());
    }

    /** A chop display or box left alone (the other was killed by a command or plugin) goes away when it loads. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        java.util.Map<UUID, Entity> loaded = new java.util.HashMap<>();
        for (Entity entity : event.getEntities()) {
            loaded.put(entity.getUniqueId(), entity);
        }
        for (Entity entity : event.getEntities()) {
            boolean display = entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(chopKey);
            boolean box = entity instanceof Interaction && entity.getPersistentDataContainer().has(linkKey);
            if (!display && !box) {
                continue;
            }
            String link = entity.getPersistentDataContainer().get(linkKey, PersistentDataType.STRING);
            Entity other = null;
            try {
                if (link != null) {
                    UUID id = UUID.fromString(link);
                    other = loaded.containsKey(id) ? loaded.get(id) : mobs.plugin().getServer().getEntity(id);
                }
            } catch (IllegalArgumentException broken) {
                other = null;
            }
            if (other == null || !other.isValid() && !loaded.containsKey(other.getUniqueId())) {
                entity.remove();
            }
        }
    }

    private ItemDisplay linkedChop(Entity clicked) {
        if (!(clicked instanceof Interaction box)) {
            return null;
        }
        String link = box.getPersistentDataContainer().get(linkKey, PersistentDataType.STRING);
        if (link == null) {
            return null;
        }
        Entity linked;
        try {
            linked = mobs.plugin().getServer().getEntity(UUID.fromString(link));
        } catch (IllegalArgumentException broken) {
            linked = null;
        }
        if (linked instanceof ItemDisplay display && display.getPersistentDataContainer().has(chopKey)) {
            return display;
        }
        // A box left without its chop goes away on its first touch.
        box.remove();
        return null;
    }

    private static void remove(ItemDisplay chop, Entity box) {
        chop.remove();
        box.remove();
    }

    private int armorPieces(Player player) {
        int pieces = 0;
        for (ItemStack worn : player.getInventory().getArmorContents()) {
            if (mobs.itemId(worn).filter(PrimordialArmor.PIECES::contains).isPresent()) {
                pieces++;
            }
        }
        return pieces;
    }

    private static BlockFace parseFacing(String name) {
        BlockFace facing = name == null ? null : FacingDisplays.parse(name);
        return facing == null ? BlockFace.UP : facing;
    }
}
