package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Forlorn Hollows items and interactions: moth dust (points mobs at what it hits), the vesper wing (hunger when
 * eaten), moth balls (stack up to five per block), and the watcher's hold on a player.
 */
public final class ForlornListener implements Listener {
    static final String MOTH_DUST = "ac_moth_dust";
    static final String MOTH_BALL = "ac_moth_ball";
    static final String VESPER_WING = "ac_vesper_wing";
    /** Mod {@code MothDustItem}: it reaches 5 blocks at full charge (the item here is used at once, at full charge). */
    static final double MOTH_DUST_REACH = 5.0;
    /** Mod tag {@code moth_dust_enrages} reach: mobs within 32 blocks of what was hit. */
    static final double ENRAGE_RADIUS = 32.0;

    private final ModMobService mobs;
    private final Map<BlockData, Integer> balls = new HashMap<>();
    private final ModBlockPalette palette;

    public ForlornListener(ModMobService mobs, ModBlockPalette palette) {
        this.mobs = mobs;
        this.palette = palette;
        for (String variant : palette.registry().variants("moth_ball")) {
            try {
                balls.put(palette.get("moth_ball", variant), Integer.parseInt(variant));
            } catch (NumberFormatException ignored) {
                // Not a ball count.
            }
        }
    }

    private boolean ours(Entity entity) {
        return mobs.typeOf(entity).filter(ForlornMobs.ALL::contains).isPresent();
    }

    /** The animals under the mobs must not take food, saddles, leads or shears. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (ours(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    // --- items ---------------------------------------------------------------------------------------------

    /** Mod {@code ACFoods.VESPER_WING}: eating it gives hunger for a minute, always. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEat(PlayerItemConsumeEvent event) {
        if (mobs.itemId(event.getItem()).filter(VESPER_WING::equals).isPresent()) {
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 1200, 0));
        }
    }

    /**
     * Moth dust and moth balls. A protection plugin that denies the use of the item (which is what cancelling the click
     * sets) stops both; the block-use result is not read, because the module's own blocks always deny it.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() == null || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        Optional<String> id = mobs.itemId(held);
        if (id.isEmpty()) {
            return;
        }
        if (event.getAction().isRightClick() && id.get().equals(MOTH_DUST)) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()
                && clicked.getType() != Material.NOTE_BLOCK) {
                return;
            }
            event.setCancelled(true);
            throwMothDust(player, held);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && id.get().equals(MOTH_BALL)
            && event.getClickedBlock() != null && !player.isSneaking()) {
            Integer count = balls.get(event.getClickedBlock().getBlockData());
            if (count != null) {
                event.setCancelled(true);
                if (count < 5) {
                    event.getClickedBlock().setBlockData(palette.get("moth_ball", String.valueOf(count + 1)), false);
                    event.getClickedBlock().getWorld().playSound(event.getClickedBlock().getLocation(), "alexscaves:moth_ball_place", 1.0F, 1.0F);
                    consume(player, held);
                }
            }
        }
    }

    /** Mod {@code canSurvive}: a moth ball stands on a full, sturdy top. */
    private static boolean supported(Block ball) {
        return ball.getRelative(0, -1, 0).getBlockData().isFaceSturdy(BlockFace.UP, BlockSupport.FULL);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlaceBall(BlockPlaceEvent event) {
        BlockScan.touched(event.getBlockPlaced().getWorld(), event.getBlockPlaced().getX(), event.getBlockPlaced().getZ());
        if (mobs.itemId(event.getItemInHand()).filter(MOTH_BALL::equals).isEmpty()) {
            return;
        }
        if (!supported(event.getBlockPlaced())) {
            event.setCancelled(true);
            return;
        }
        event.getBlockPlaced().getWorld().playSound(event.getBlockPlaced().getLocation(), "alexscaves:moth_ball_place", 1.0F, 1.0F);
    }

    /** A block changed where torches or moth balls could be: the scans there are stale. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (BlockScan.relevant(block.getType())) {
            BlockScan.touched(block.getWorld(), block.getX(), block.getZ());
        }
    }

    /**
     * Mod {@code updateShape}: a moth ball whose support went away drops its balls. The module cancels the physics of
     * note blocks, but the event still reaches monitors, and the block under a ball changing sends one to the ball.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK || !balls.containsKey(block.getBlockData()) || supported(block)) {
            return;
        }
        mobs.plugin().getServer().getScheduler().runTask(mobs.plugin(), () -> {
            Integer count = balls.get(block.getBlockData());
            if (count != null && !supported(block)) {
                block.setType(Material.AIR, false);
                mobs.createItem(MOTH_BALL, count).ifPresent(stack ->
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.2, 0.5), stack));
                BlockScan.touched(block.getWorld(), block.getX(), block.getZ());
            }
        });
    }

    /** A rider cannot be held (no move events for passengers), so a possessed player cannot get on a mount. */
    @EventHandler(ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && Watcher.POSSESSED_PLAYERS.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private static void consume(Player player, ItemStack held) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
    }

    /**
     * Mod {@code MothDustItem.releaseUsing}: a cloud flies up to 5 blocks ahead; whatever living thing it hits becomes the
     * target of every vesper, corrodent, underzealot and watcher within 32 blocks of it.
     */
    private void throwMothDust(Player player, ItemStack held) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult hit = player.getWorld().rayTraceEntities(eye, direction, MOTH_DUST_REACH, 0.3,
            entity -> entity instanceof LivingEntity && entity != player);
        double reach = hit == null ? MOTH_DUST_REACH : eye.toVector().distance(hit.getHitPosition());
        for (int i = 0; i < Math.ceil(MOTH_DUST_REACH * 3); i++) {
            Location at = eye.clone().add(direction.clone().multiply(Math.min(reach, 0.3 + i * reach / 15.0)));
            player.getWorld().spawnParticle(Particle.DUST, at, 1, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(0x6b5a8f), 1.4F));
        }
        if (hit != null && hit.getHitEntity() instanceof LivingEntity victim) {
            for (Entity near : player.getWorld().getNearbyEntities(victim.getLocation(), ENRAGE_RADIUS, ENRAGE_RADIUS, ENRAGE_RADIUS)) {
                if (near != victim && mobs.typeOf(near).filter(ForlornMobs.ALL::contains).isPresent()
                    && !mobs.typeOf(near).equals(mobs.typeOf(victim))
                    && mobs.behaviourOf(near).orElse(null) instanceof ForlornMobs.Enrageable enrageable) {
                    enrageable.enrage(victim);
                }
            }
        }
        consume(player, held);
    }

    // --- the watcher's hold --------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent event) {
        if (!Watcher.POSSESSED_PLAYERS.containsKey(event.getPlayer().getUniqueId()) || event.getTo() == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        to.setX(from.getX());
        to.setZ(from.getZ());
        if (to.getY() > from.getY()) {
            to.setY(from.getY());
        }
    }

    /** Mod: a possessed player is hurt by nothing but the watcher (and what ignores invulnerability). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !Watcher.POSSESSED_PLAYERS.containsKey(player.getUniqueId())) {
            return;
        }
        switch (event.getCause()) {
            case VOID, KILL, SUICIDE, WORLD_BORDER -> {
                return;
            }
            default -> {
            }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
            && mobs.typeOf(byEntity.getDamager()).filter(ForlornMobs.WATCHER::equals).isPresent()) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Watcher.POSSESSED_PLAYERS.remove(event.getPlayer().getUniqueId());
    }
}
