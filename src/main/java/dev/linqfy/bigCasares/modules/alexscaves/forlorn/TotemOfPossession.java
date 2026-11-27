package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantment;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantments;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Totem of Possession (mod {@code TotemOfPossessionItem}). Hitting a creature with it binds that creature; holding
 * right click while looking at the bound creature (or from anywhere with Sightless) then drives it to where the
 * player's sight lands, and it attacks whatever it finds on the way. It wears one point every second tick.
 *
 * <p>Holding right click is detected from the client's repeated use of the item (every four ticks), so a possession
 * ends a few ticks after the player lets go.
 */
public final class TotemOfPossession implements Listener, Runnable, AutoCloseable {
    static final String TOTEM = "ac_totem_of_possession";
    /** The client repeats the use every 4 ticks while right click is held. */
    private static final int HOLD_GRACE_TICKS = 7;
    /** Durability is written to the item in batches: a change every other tick would restart its hand animation. */
    private static final int WEAR_BATCH_TICKS = 10;
    private static final double LOOK_DEGREES = 5.0;
    /** Mod tag {@code resists_totem_of_possession}: module mobs that are bosses. */
    private static final Set<String> RESISTANT_MOBS = Set.of("hullbreaker", "forsaken", "atlatitan", "tremorzilla");
    private static final Set<EntityType> RESISTANT_TYPES = Set.of(EntityType.WARDEN, EntityType.ARMOR_STAND);

    private final ModMobService mobs;
    private final ModEnchantments enchantments;
    private final NamespacedKey boundKey;
    private final NamespacedKey boundNameKey;
    private final Map<UUID, Possession> active = new HashMap<>();
    private final HarmVerdictCache verdicts = new HarmVerdictCache();
    private long tick;

    public TotemOfPossession(ModMobService mobs) {
        this.mobs = mobs;
        this.enchantments = ModEnchantments.lookup();
        this.boundKey = new NamespacedKey(mobs.plugin(), "alexscaves_totem_bound");
        this.boundNameKey = new NamespacedKey(mobs.plugin(), "alexscaves_totem_bound_type");
    }

    private boolean isTotem(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && mobs.itemId(stack).filter(TOTEM::equals).isPresent();
    }

    private boolean resists(Entity entity) {
        return entity instanceof Boss || RESISTANT_TYPES.contains(entity.getType())
            || mobs.typeOf(entity).filter(type -> RESISTANT_MOBS.contains(type.id())).isPresent()
            || entity instanceof Player && !mobs.settings().forlorn().possessPlayers();
    }

    // --- binding -------------------------------------------------------------------------------------------

    /** Mod {@code hurtEnemy}: a hit with the totem binds what it hits, unless the being is too powerful. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isTotem(held)) {
            return;
        }
        if (resists(target)) {
            player.sendActionBar(Component.text("Este ser es demasiado poderoso para ser poseído...", NamedTextColor.RED));
            return;
        }
        bind(player, held, target);
        player.getInventory().setItemInMainHand(held);
        player.getWorld().playSound(player.getLocation(), "alexscaves:totem_of_possession_use", SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    private void bind(Player player, ItemStack totem, LivingEntity target) {
        ItemMeta meta = totem.getItemMeta();
        meta.getPersistentDataContainer().set(boundKey, PersistentDataType.STRING, target.getUniqueId().toString());
        meta.getPersistentDataContainer().set(boundNameKey, PersistentDataType.STRING, target.getType().translationKey());
        List<Component> lore = baseLore(meta);
        lore.add(Component.translatable(target.getType().translationKey(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        totem.setItemMeta(meta);
        setFlags(totem, true, false);
    }

    private void reset(ItemStack totem) {
        ItemMeta meta = totem.getItemMeta();
        meta.getPersistentDataContainer().remove(boundKey);
        meta.getPersistentDataContainer().remove(boundNameKey);
        meta.lore(baseLore(meta));
        totem.setItemMeta(meta);
        setFlags(totem, false, false);
    }

    /** The catalog's lore line, without the name of a bound creature. */
    private List<Component> baseLore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        List<Component> base = new ArrayList<>();
        if (lore != null && !lore.isEmpty()) {
            base.add(lore.get(0));
        }
        return base;
    }

    /** Flag 0: bound, flag 1: being used (the item definition of the pack picks the texture from them). */
    private void setFlags(ItemStack totem, boolean bound, boolean using) {
        totem.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addFlag(bound).addFlag(using).build());
    }

    /** Mod: the bound creature is looked up in the player's own dimension only; elsewhere it counts as gone. */
    private Entity find(UUID id, Player player) {
        Entity entity = mobs.plugin().getServer().getEntity(id);
        return entity != null && entity.getWorld().equals(player.getWorld()) ? entity : null;
    }

    /** Whether the user could hurt the victim themselves: asks the protections with a stand-in hit of a neutral cause. */
    private boolean canHarm(Player user, LivingEntity victim) {
        return verdicts.allowed(user.getUniqueId(), victim.getUniqueId(), tick, () -> askProtections(user, victim));
    }

    private boolean askProtections(Player user, LivingEntity victim) {
        DamageSource source = DamageSource.builder(DamageType.GENERIC).withCausingEntity(user).withDirectEntity(user).build();
        EntityDamageByEntityEvent probe = new EntityDamageByEntityEvent(user, victim, EntityDamageEvent.DamageCause.CUSTOM, source, 1.0);
        return probe.callEvent();
    }

    private UUID boundOf(ItemStack totem) {
        String stored = totem.getItemMeta().getPersistentDataContainer().get(boundKey, PersistentDataType.STRING);
        if (stored == null) {
            return null;
        }
        try {
            return UUID.fromString(stored);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    // --- possessing ----------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getHand() == null || !isTotem(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        Possession current = active.get(player.getUniqueId());
        if (current != null) {
            current.lastUse = tick;
            return;
        }
        ItemStack totem = player.getInventory().getItem(event.getHand());
        UUID bound = boundOf(totem);
        Entity controlled = bound == null ? null : find(bound, player);
        if (bound != null && (controlled == null || controlled.isDead())) {
            reset(totem);
            player.getInventory().setItem(event.getHand(), totem);
            return;
        }
        if (bound == null || controlled == null) {
            return;
        }
        boolean sightless = enchantments.level(totem, ModEnchantment.SIGHTLESS) > 0;
        if (!sightless && !(controlled instanceof LivingEntity living && looking(player, living))) {
            return;
        }
        // Protection was only asked when the creature was bound: a claim may have appeared around it since.
        if (!(controlled instanceof LivingEntity victim) || resists(victim)
            || !verdicts.refresh(player.getUniqueId(), victim.getUniqueId(), tick, () -> askProtections(player, victim))) {
            return;
        }
        player.getWorld().playSound(player.getLocation(), "alexscaves:totem_of_possession_use", SoundCategory.PLAYERS, 1.0F, 1.0F);
        setFlags(totem, true, true);
        player.getInventory().setItem(event.getHand(), totem);
        active.put(player.getUniqueId(), new Possession(event.getHand(), tick, bound));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Possession possession = active.remove(event.getPlayer().getUniqueId());
        if (possession != null) {
            release(event.getPlayer(), possession);
        }
    }

    @Override
    public void run() {
        tick++;
        Iterator<Map.Entry<UUID, Possession>> iterator = active.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Possession> entry = iterator.next();
            Player player = mobs.plugin().getServer().getPlayer(entry.getKey());
            Possession possession = entry.getValue();
            ItemStack totem = player == null ? null : player.getInventory().getItem(possession.hand);
            if (player == null || !isTotem(totem) || tick - possession.lastUse > HOLD_GRACE_TICKS || !step(player, totem, possession)) {
                iterator.remove();
                if (player != null) {
                    release(player, possession);
                }
            }
        }
    }

    /** One tick of {@code onUseTick}; false ends the possession. */
    private boolean step(Player player, ItemStack totem, Possession possession) {
        Entity controlled = find(possession.target, player);
        int used = (int) (tick - possession.start);
        int max = totem.getType().getMaxDurability();
        Damageable meta = totem.getItemMeta() instanceof Damageable damageable ? damageable : null;
        int limit = meta != null && meta.hasMaxDamage() ? meta.getMaxDamage() : max;
        int worn = (meta == null ? 0 : meta.getDamage()) + possession.pendingWear;
        boolean alive = controlled != null && !controlled.isDead();
        if (!alive || worn >= limit) {
            if (controlled != null && enchantments.level(totem, ModEnchantment.DETONATING_DEATH) > 0) {
                explode(player, controlled);
            }
            possession.finished = true;
            return false;
        }
        boolean sightless = enchantments.level(totem, ModEnchantment.SIGHTLESS) > 0;
        if (!(controlled instanceof LivingEntity living) || !(sightless || looking(player, living)) || resists(living)) {
            return false;
        }
        if (player.getGameMode() != GameMode.CREATIVE && ForlornItemRules.totemWears(used)) {
            possession.pendingWear++;
            if (possession.pendingWear >= WEAR_BATCH_TICKS / 2) {
                flushWear(player, totem, possession);
            }
        }
        float ramp = ForlornItemRules.totemRamp(used);
        float speed = ForlornItemRules.totemSpeed(enchantments.level(totem, ModEnchantment.RAPID_POSSESSION));
        Location destination = destination(player, living, ForlornItemRules.totemReach(ramp));
        if (living instanceof Mob mob) {
            mob.getPathfinder().moveTo(destination, ramp * speed);
            if (sightless) {
                living.setGlowing(true);
                possession.glowing = true;
            }
        } else {
            push(living, destination, speed);
        }
        dust(living);
        attackNearby(player, totem, living, used, possession);
        return true;
    }

    private void flushWear(Player player, ItemStack totem, Possession possession) {
        if (possession.pendingWear <= 0) {
            return;
        }
        if (totem.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(damageable.getDamage() + possession.pendingWear);
            totem.setItemMeta(damageable);
            player.getInventory().setItem(possession.hand, totem);
        }
        possession.pendingWear = 0;
    }

    private void release(Player player, Possession possession) {
        ItemStack totem = player.getInventory().getItem(possession.hand);
        Entity controlled = mobs.plugin().getServer().getEntity(possession.target);
        if (controlled instanceof LivingEntity living && possession.glowing) {
            living.setGlowing(false);
        }
        if (!isTotem(totem)) {
            return;
        }
        flushWear(player, totem, possession);
        if (possession.finished) {
            reset(totem);
        }
        setFlags(totem, boundOf(totem) != null, false);
        Damageable meta = totem.getItemMeta() instanceof Damageable damageable ? damageable : null;
        int limit = meta != null && meta.hasMaxDamage() ? meta.getMaxDamage() : totem.getType().getMaxDurability();
        if (meta != null && limit > 0 && meta.getDamage() >= limit) {
            // Mod {@code releaseUsing}: a worn out totem is gone.
            player.getInventory().setItem(possession.hand, null);
            player.getWorld().playSound(player.getLocation(), "entity.item.break", SoundCategory.PLAYERS, 0.8F, 1.0F);
            return;
        }
        player.getInventory().setItem(possession.hand, totem);
    }

    /** Mod {@code isEntityLookingAt}: within 5 degrees (stretched with the distance) and in sight. */
    private boolean looking(Player looker, LivingEntity seen) {
        Location eye = looker.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector toSeen = new Vector(seen.getLocation().getX() - eye.getX(),
            seen.getBoundingBox().getMinY() + seen.getEyeHeight() - eye.getY(), seen.getLocation().getZ() - eye.getZ());
        double distance = toSeen.length();
        if (distance < 1.0e-4) {
            return true;
        }
        return ForlornItemRules.looksAt(look.dot(toSeen.normalize()), distance, LOOK_DEGREES) && looker.hasLineOfSight(seen);
    }

    /** Where the player's sight lands, up to {@code reach} blocks, not counting the possessed creature itself. */
    private Location destination(Player player, Entity controlled, double reach) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        RayTraceResult hit = world.rayTrace(eye, eye.getDirection(), reach, FluidCollisionMode.NEVER, true, 0.3,
            entity -> !entity.equals(player) && !entity.equals(controlled) && entity instanceof LivingEntity);
        Vector at = hit != null ? hit.getHitPosition() : eye.toVector().add(eye.getDirection().multiply(reach));
        return at.toLocation(world);
    }

    /** Mod, for creatures with no navigation (players, armor stands): a shove toward the destination. */
    private void push(LivingEntity living, Location destination, float speed) {
        boolean flies = living instanceof Ghast || living instanceof Phantom || living instanceof Bee || living instanceof Parrot;
        Vector toward = destination.toVector().subtract(living.getLocation().toVector());
        boolean jump = false;
        if (!flies && toward.getY() > 0) {
            Vector flat = new Vector(toward.getX(), 0, toward.getZ());
            if (living.isOnGround() && flat.lengthSquared() > 1.0e-4) {
                Location ahead = living.getLocation().add(flat.normalize().multiply(0.7));
                jump = !ahead.getBlock().isPassable();
            }
            toward = flat;
        }
        if (toward.length() > 1.0) {
            toward.normalize();
            float yaw = (float) -Math.toDegrees(Math.atan2(toward.getX(), toward.getZ()));
            living.setRotation(yaw, living.getLocation().getPitch());
        }
        Vector add = toward.multiply(0.15F * speed);
        if (jump) {
            add.add(new Vector(0, 0.6, 0));
        }
        living.setVelocity(living.getVelocity().multiply(0.8F).add(add));
    }

    private void dust(LivingEntity living) {
        BoundingBox box = living.getBoundingBox();
        int count = (int) (1.0F + living.getWidth() * 2.0F);
        living.getWorld().spawnParticle(Particle.DUST, living.getLocation().add(0, box.getHeight() * 0.5, 0), count,
            living.getWidth() * 0.75, box.getHeight() * 0.5, living.getWidth() * 0.75, 0.0, new Particle.DustOptions(Color.RED, 1.0F));
    }

    /** Mod: everything within 3 blocks of the possessed becomes its target (a player attacks it outright). */
    private void attackNearby(Player user, ItemStack totem, LivingEntity controlled, int used, Possession possession) {
        if (!(controlled instanceof Player) && !(controlled instanceof Mob)) {
            return;
        }
        for (Entity entity : controlled.getNearbyEntities(3, 3, 3)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(user) || entity.equals(controlled)
                || controlled.getPassengers().contains(entity) || entity.getPassengers().contains(controlled)
                || target instanceof Player spectator && spectator.getGameMode() == GameMode.SPECTATOR
                || target instanceof Tameable tame && controlled.equals(tame.getOwner())
                || controlled instanceof Tameable pet && target.equals(pet.getOwner())
                || !canHarm(user, target)) {
                continue;
            }
            if (controlled instanceof Mob mob) {
                mob.setTarget(target);
                if (used % 4 == 0 && target.getHealth() > mob.getHealth() && !resists(target)
                    && enchantments.level(totem, ModEnchantment.ASTRAL_TRANSFERRING) > 0) {
                    transfer(user, totem, target, possession);
                }
            } else if (controlled instanceof Player possessed) {
                possessed.attack(target);
                possessed.resetCooldown();
            }
        }
    }

    /** Mod Astral Transferring: the totem moves on to the stronger creature the possessed one is fighting. */
    private void transfer(Player user, ItemStack totem, LivingEntity target, Possession possession) {
        bind(user, totem, target);
        user.getInventory().setItem(possession.hand, totem);
        setFlags(totem, true, true);
        user.getInventory().setItem(possession.hand, totem);
        user.getWorld().playSound(user.getLocation(), "alexscaves:totem_of_possession_use", SoundCategory.PLAYERS, 1.0F, 1.0F);
        Entity previous = mobs.plugin().getServer().getEntity(possession.target);
        if (previous instanceof LivingEntity living && possession.glowing) {
            living.setGlowing(false);
            possession.glowing = false;
        }
        possession.target = target.getUniqueId();
    }

    /** Mod {@code TotemExplosion}: power 2 plus the whole width, no block damage, caused by the user. */
    private void explode(Player user, Entity controlled) {
        float power = ForlornItemRules.totemExplosion(controlled.getWidth());
        Location at = controlled.getLocation();
        World world = at.getWorld();
        world.spawnParticle(Particle.EXPLOSION, at, 1);
        world.playSound(at, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 4.0F, 1.0F);
        // Vanilla explosion damage, by hand so that only what the user could hurt on their own is hurt.
        double radius = power * 2.0;
        DamageSource source = DamageSource.builder(DamageType.PLAYER_EXPLOSION).withCausingEntity(user).withDirectEntity(user).build();
        for (Entity entity : world.getNearbyEntities(at, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity victim) || victim.getLocation().distance(at) > radius
                || !victim.equals(controlled) && !victim.equals(user) && !canHarm(user, victim)) {
                continue;
            }
            double impact = 1.0 - victim.getLocation().distance(at) / radius;
            victim.damage((int) ((impact * impact + impact) / 2.0 * 7.0 * power + 1.0), source);
        }
    }

    @Override
    public void close() {
        for (Map.Entry<UUID, Possession> entry : active.entrySet()) {
            Player player = mobs.plugin().getServer().getPlayer(entry.getKey());
            if (player != null) {
                release(player, entry.getValue());
            }
        }
        active.clear();
        verdicts.clear();
    }

    private static final class Possession {
        private final EquipmentSlot hand;
        private final long start;
        private UUID target;
        private long lastUse;
        private int pendingWear;
        private boolean glowing;
        private boolean finished;

        private Possession(EquipmentSlot hand, long start, UUID target) {
            this.hand = hand;
            this.start = start;
            this.lastUse = start;
            this.target = target;
        }
    }
}
