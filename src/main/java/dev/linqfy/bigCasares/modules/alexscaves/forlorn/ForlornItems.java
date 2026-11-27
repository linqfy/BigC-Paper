package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectLedger;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The simple Forlorn Hollows items: thrown guano (mod {@code GuanoEntity}, a snowball that does no damage), fertilizer
 * (mod {@code FertilizerItem}: bone meal that tries four times), the vesper stew and the darkened apple (mod
 * {@code ACFoods}, {@code DarkenedAppleItem}), the burrowing arrow (mod {@code BurrowingArrowEntity}) and the anvil
 * repairs of the darkness armor and the desolate dagger.
 */
public final class ForlornItems implements Listener, Runnable, AutoCloseable {
    static final String GUANO = "ac_guano";
    static final String FERTILIZER = "ac_fertilizer";
    static final String VESPER_STEW = "ac_vesper_stew";
    static final String DARKENED_APPLE = "ac_darkened_apple";
    static final String BURROWING_ARROW = "ac_burrowing_arrow";
    /** Mod {@code new ThrownProjectileItem(..., 0.0F, 1.0F, 1.0F)} against a snowball's throw speed of 1.5. */
    static final double GUANO_SPEED = 1.0 / 1.5;
    /** Mod {@code ACFoods.VESPER_SOUP}: night vision for two minutes. */
    static final int STEW_NIGHT_VISION_TICKS = 2400;
    /** Mod {@code BurrowingArrowEntity}: base damage 3.5, one more pierce, up to five blocks. */
    static final double BURROWING_DAMAGE = 3.5;
    static final int MAX_DUG = 5;
    /** Mod {@code BurrowingArrowEntity}: fifteen ticks per point of hardness (minimum 0.2). */
    static final int HARDNESS_TICKS = 15;
    private static final int SOUND_PERIOD = 38;

    /**
     * Mod hardness of the module blocks that live on a tripwire or chorus plant state, whose vanilla base has another
     * one (string 0, chorus plant 0.4): tube worm, bone ribs and baleen bone 2, mussel 1, guano layer 0.3; the rest
     * (bone worms, anemones, ping pong sponge, thornwood branch) break instantly in the mod.
     */
    static final Map<String, Float> MOD_HARDNESS = Map.ofEntries(Map.entry("tube_worm", 2.0F), Map.entry("bone_ribs", 2.0F),
        Map.entry("baleen_bone", 2.0F), Map.entry("mussel", 1.0F), Map.entry("guano_layer", 0.3F),
        Map.entry("bone_worms", 0.0F), Map.entry("dusk_anemone", 0.0F), Map.entry("midnight_anemone", 0.0F),
        Map.entry("twilight_anemone", 0.0F), Map.entry("ping_pong_sponge", 0.0F), Map.entry("thornwood_branch", 0.0F),
        Map.entry("curly_fern", 0.0F), Map.entry("flytrap", 0.0F), Map.entry("fiddlehead", 0.0F), Map.entry("underweed", 0.0F),
        Map.entry("sweet_puff", 0.0F), Map.entry("sundrop", 0.0F), Map.entry("lollipop_bunch", 0.0F),
        Map.entry("licoroot_sprout", 0.0F), Map.entry("cycad", 1.0F));

    private final ModMobService mobs;
    private final Map<BlockData, Float> moduleHardness = new HashMap<>();
    private final Map<UUID, Burrow> burrowing = new HashMap<>();
    private final ItemStack digTool = new ItemStack(Material.NETHERITE_PICKAXE);
    private Burrow inherited;

    public ForlornItems(ModMobService mobs, ModBlockPalette palette) {
        this.mobs = mobs;
        for (Map.Entry<String, Float> entry : MOD_HARDNESS.entrySet()) {
            for (String variant : palette.registry().variants(entry.getKey())) {
                BlockData data = palette.get(entry.getKey(), variant);
                if (data.getMaterial() == Material.TRIPWIRE || data.getMaterial() == Material.CHORUS_PLANT) {
                    moduleHardness.put(data, entry.getValue());
                }
            }
        }
    }

    /** The block's hardness: a module block on a tripwire or chorus plant state has the mod's, not its base's. */
    private float hardnessOf(Block block) {
        Material type = block.getType();
        if (type == Material.TRIPWIRE || type == Material.CHORUS_PLANT) {
            Float modded = moduleHardness.get(block.getBlockData());
            if (modded != null) {
                return modded;
            }
        }
        return type.getHardness();
    }

    private boolean is(ItemStack stack, String id) {
        return stack != null && !stack.getType().isAir() && mobs.itemId(stack).filter(id::equals).isPresent();
    }

    // --- guano ---------------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball snowball && is(snowball.getItem(), GUANO)) {
            snowball.setVelocity(snowball.getVelocity().multiply(GUANO_SPEED));
        }
    }

    // --- fertilizer ----------------------------------------------------------------------------------------

    /**
     * Mod {@code FertilizerItem.useOn}: bone meal that grows the block up to four times (stopping when it no longer
     * grows), or else grows what is under the water in front of the clicked face.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
            || event.getClickedBlock() == null || !is(event.getItem(), FERTILIZER)) {
            return;
        }
        if (event.useInteractedBlock() == Event.Result.DENY || event.useItemInHand() == Event.Result.DENY) {
            // A protection said no: neither grow it nor spend the item.
            return;
        }
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        BlockFace face = event.getBlockFace();
        boolean grew = false;
        for (int attempt = 0; attempt < 4 && block.applyBoneMeal(face); attempt++) {
            grew = true;
        }
        if (!grew && block.getType().isOccluding()) {
            grew = block.getRelative(face).applyBoneMeal(face);
        }
        if (grew) {
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.8, 0.5), 8, 0.3, 0.2, 0.3, 0.0);
            Player player = event.getPlayer();
            if (player.getGameMode() != GameMode.CREATIVE) {
                event.getItem().setAmount(event.getItem().getAmount() - 1);
            }
        }
    }

    // --- food ----------------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (is(event.getItem(), VESPER_STEW)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, STEW_NIGHT_VISION_TICKS, 0));
        } else if (is(event.getItem(), DARKENED_APPLE)) {
            Optional<ModEffectLedger.ActiveEffect> active = mobs.effects().get(player, ModEffectType.DARKNESS_INCARNATE);
            active.ifPresent(effect -> mobs.effects().apply(player, ModEffectType.DARKNESS_INCARNATE, effect.amplifier(),
                effect.remainingTicks() + ForlornItemRules.APPLE_EXTENSION_TICKS));
        }
    }

    // --- burrowing arrow -----------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow && is(event.getConsumable(), BURROWING_ARROW)) {
            ItemStack pickup = event.getConsumable().clone();
            pickup.setAmount(1);
            arrow.setItemStack(pickup);
            track(arrow);
        }
    }

    /** Arrows that other code spawns carrying the item (the dreadbow's volley) burrow too. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow && !burrowing.containsKey(arrow.getUniqueId())
            && is(arrow.getItemStack(), BURROWING_ARROW)) {
            track(arrow);
        }
    }

    private void track(AbstractArrow arrow) {
        if (burrowing.containsKey(arrow.getUniqueId())) {
            return;
        }
        if (inherited != null) {
            // The replacement of an arrow that went on through a block: it keeps the state of the one it replaces.
            burrowing.put(arrow.getUniqueId(), inherited);
            inherited = null;
            return;
        }
        arrow.setDamage(BURROWING_DAMAGE);
        arrow.setPierceLevel(arrow.getPierceLevel() + 1);
        burrowing.put(arrow.getUniqueId(), new Burrow(arrow.getVelocity()));
    }

    @Override
    public void run() {
        // A copy: digging may add the arrow that replaces the one that went on through a block.
        for (Map.Entry<UUID, Burrow> entry : new ArrayList<>(burrowing.entrySet())) {
            Burrow burrow = entry.getValue();
            if (!(mobs.plugin().getServer().getEntity(entry.getKey()) instanceof AbstractArrow arrow) || !arrow.isValid()) {
                burrow.clear(mobs.plugin().getServer().getOnlinePlayers());
                burrowing.remove(entry.getKey());
                continue;
            }
            burrow.id = arrow.getEntityId();
            if (!arrow.isInBlock()) {
                Vector velocity = arrow.getVelocity();
                if (velocity.lengthSquared() > 0.01) {
                    burrow.heading = velocity.clone();
                }
                burrow.clear(arrow.getWorld().getPlayers());
                continue;
            }
            dig(arrow, burrow);
        }
    }

    /** Mod {@code BurrowingArrowEntity.tick}: gnaw the block it is stuck in, then go on through it. */
    private void dig(AbstractArrow arrow, Burrow burrow) {
        Block block = arrow.getAttachedBlock();
        if (block == null || !(arrow.getShooter() instanceof Player owner) || !owner.isOnline() || !canMine(block, burrow)) {
            burrow.clear(arrow.getWorld().getPlayers());
            return;
        }
        if (!block.equals(burrow.block)) {
            burrow.clear(arrow.getWorld().getPlayers());
            burrow.block = block;
        }
        int hardness = hardnessTicks(hardnessOf(block));
        int progress = Math.min(9, (int) ((float) burrow.mining / hardness * 10));
        if (progress != burrow.progress) {
            burrow.progress = progress;
            for (Player near : arrow.getWorld().getPlayers()) {
                if (near.getLocation().distanceSquared(arrow.getLocation()) < 48 * 48) {
                    near.sendBlockDamage(block.getLocation(), progress / 10.0F, arrow.getEntityId());
                }
            }
        }
        if (burrow.mining % 8 == 0) {
            arrow.getWorld().playSound(arrow.getLocation(), block.getBlockData().getSoundGroup().getHitSound(), 1.0F, 1.0F);
        }
        if (burrow.sound-- <= 0) {
            burrow.sound = SOUND_PERIOD;
            arrow.getWorld().playSound(arrow.getLocation(), "alexscaves:corrodent_teeth", 1.0F, 1.0F);
        }
        if (burrow.mining++ <= hardness) {
            return;
        }
        burrow.clear(arrow.getWorld().getPlayers());
        if (block.getType() == Material.NOTE_BLOCK || block.getType() == Material.TRIPWIRE
            || block.getType() == Material.CHORUS_PLANT) {
            // A module block: the break path of the player fires the break event and hands out the module item.
            if (!owner.breakBlock(block)) {
                burrow.dug = MAX_DUG;
                return;
            }
        } else {
            BlockBreakEvent breakEvent = new BlockBreakEvent(block, owner);
            if (!breakEvent.callEvent()) {
                burrow.dug = MAX_DUG;
                return;
            }
            Location at = block.getLocation().add(0.5, 0.5, 0.5);
            if (breakEvent.isDropItems()) {
                block.breakNaturally(digTool);
            } else {
                block.setType(Material.AIR);
            }
            if (breakEvent.getExpToDrop() > 0) {
                int experience = breakEvent.getExpToDrop();
                at.getWorld().spawn(at, ExperienceOrb.class, orb -> orb.setExperience(experience));
            }
        }
        continueThrough(arrow, burrow);
    }

    /**
     * The mod pushes the stuck arrow on after the block breaks. A stuck arrow cannot be set flying again through the
     * API, so it is replaced by a copy that keeps going the way it came (the count of dug blocks moves with it).
     */
    private void continueThrough(AbstractArrow arrow, Burrow burrow) {
        Vector heading = burrow.heading.lengthSquared() < 1.0e-4 ? new Vector(0, -1, 0) : burrow.heading.clone().normalize();
        ItemStack pickup = arrow.getItemStack().clone();
        AbstractArrow.PickupStatus pickupStatus = arrow.getPickupStatus();
        double damage = arrow.getDamage();
        int pierce = arrow.getPierceLevel();
        var shooter = arrow.getShooter();
        Burrow carried = new Burrow(heading.clone().multiply(0.6));
        carried.dug = burrow.dug + 1;
        carried.sound = burrow.sound;
        inherited = carried;
        try {
            arrow.getWorld().spawn(arrow.getLocation(), Arrow.class, copy -> {
                copy.setShooter(shooter);
                copy.setItemStack(pickup);
                copy.setPickupStatus(pickupStatus);
                copy.setDamage(damage);
                copy.setPierceLevel(pierce);
                copy.setVelocity(heading.clone().multiply(0.6));
            });
        } finally {
            inherited = null;
        }
        arrow.remove();
    }

    private boolean canMine(Block block, Burrow burrow) {
        Material type = block.getType();
        boolean waterlogged = block.getBlockData() instanceof Waterlogged water && water.isWaterlogged();
        return burrow.dug < MAX_DUG && !type.isAir() && !block.isLiquid() && !waterlogged && type.getHardness() >= 0
            && !Tag.WITHER_IMMUNE.isTagged(type);
    }

    /** Mod: {@code (int) (max(hardness, 0.2) * 15)} ticks to chew through a block. */
    static int hardnessTicks(float hardness) {
        return (int) (Math.max(hardness, 0.2F) * HARDNESS_TICKS);
    }

    // --- anvil repairs -------------------------------------------------------------------------------------

    /**
     * Mod {@code isValidRepairItem}: dark tatters mend the darkness armor and pure darkness mends the desolate dagger,
     * a quarter of the item's durability each (the vanilla anvil only knows its own base materials).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getFirstItem();
        ItemStack second = inventory.getSecondItem();
        if (first == null || second == null || second.getType().isAir() || !(first.getItemMeta() instanceof Damageable damageable)
            || damageable.getDamage() <= 0) {
            return;
        }
        String repairWith = repairItem(first);
        if (repairWith == null || !is(second, repairWith)) {
            return;
        }
        int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : first.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        int unit = Math.max(1, max / 4);
        int used = 0;
        int damage = damageable.getDamage();
        while (used < second.getAmount() && damage > 0) {
            damage = Math.max(0, damage - unit);
            used++;
        }
        ItemStack result = first.clone();
        ItemMeta meta = result.getItemMeta();
        ((Damageable) meta).setDamage(damage);
        result.setItemMeta(meta);
        event.setResult(result);
        event.getView().setRepairCost(used);
        event.getView().setRepairItemCountCost(used);
    }

    private String repairItem(ItemStack stack) {
        Optional<String> id = mobs.itemId(stack);
        if (id.isEmpty()) {
            return null;
        }
        return switch (id.get()) {
            case "ac_hood_of_darkness", "ac_cloak_of_darkness" -> "ac_dark_tatters";
            case "ac_desolate_dagger" -> "ac_pure_darkness";
            default -> null;
        };
    }

    @Override
    public void close() {
        for (Map.Entry<UUID, Burrow> entry : burrowing.entrySet()) {
            entry.getValue().clear(mobs.plugin().getServer().getOnlinePlayers());
        }
        burrowing.clear();
    }

    /** What a burrowing arrow has done so far. */
    private static final class Burrow {
        private Vector heading;
        private int dug;
        private int mining;
        private int progress = -1;
        private int sound;
        private int id;
        private Block block;

        private Burrow(Vector heading) {
            this.heading = heading.clone();
        }

        /** Drops the crack of the block being chewed (mod: {@code destroyBlockProgress(id, pos, -1)}). */
        private void clear(Iterable<? extends Player> players) {
            if (block != null) {
                for (Player player : players) {
                    if (player.getWorld().equals(block.getWorld())) {
                        player.sendBlockDamage(block.getLocation(), 0.0F, id);
                    }
                }
            }
            block = null;
            mining = 0;
            progress = -1;
        }
    }
}
