package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Taming, following, sitting and riding of the Primordial dinosaurs, after the
 * mod's {@code DinosaurEntity} and each mob's interaction code. The vanilla
 * bases are not tameable animals, so the owner lives in the entity's PDC.
 * <ul>
 *   <li>Subterranodon: trilocaris tail (raw or cooked), 1 in 3 tames. Rideable, flies.</li>
 *   <li>Vallumraptor: a dinosaur nugget relaxes it for 10-20 s (mod: a nugget looted from a
 *       chest), then a serene salad tames it.</li>
 *   <li>Tremorsaurus: a serene salad while stunned (primitive club). Tames from the 4th try on a
 *       coin flip, always on the 9th. Rideable.</li>
 * </ul>
 * Owners sneak + right-click to make a tamed dinosaur sit or follow. Tamed dinosaurs help their
 * owner fight and never pick a player on their own.
 */
public final class PrimordialTaming implements Listener, Runnable, AutoCloseable {
    static final double FOLLOW_START = 8.0;
    static final double TELEPORT_DISTANCE = 28.0;
    static final double ASSIST_RADIUS = 16.0;
    static final double FOLLOW_SEARCH = 48.0;

    private final ModMobService mobs;
    private final NamespacedKey ownerKey;
    private final NamespacedKey sittingKey;
    private final NamespacedKey attemptsKey;
    private final NamespacedKey babyKey;
    private final Map<UUID, Long> relaxedUntil = new HashMap<>();
    private long tick;

    public PrimordialTaming(ModMobService mobs) {
        this.mobs = mobs;
        this.ownerKey = new NamespacedKey(mobs.plugin(), TamingRules.OWNER_KEY);
        this.sittingKey = new NamespacedKey(mobs.plugin(), TamingRules.SITTING_KEY);
        this.babyKey = new NamespacedKey(mobs.plugin(), TamingRules.BABY_UNTIL_KEY);
        this.attemptsKey = new NamespacedKey(mobs.plugin(), "alexscaves_tame_attempts");
    }

    // --- State ------------------------------------------------------------------------------------

    Optional<UUID> owner(Entity entity) {
        String value = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException broken) {
            return Optional.empty();
        }
    }

    private boolean sitting(Entity entity) {
        Byte value = entity.getPersistentDataContainer().get(sittingKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }

    private void tame(LivingEntity entity, Player player, boolean sit) {
        entity.getPersistentDataContainer().remove(attemptsKey);
        markTamed(mobs.plugin(), entity, player.getUniqueId(), sit);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§a¡Domaste al dinosaurio!"));
    }

    /** Owner, sitting state and persistence of a tamed dinosaur (also used for hatched babies). */
    static void markTamed(Plugin plugin, LivingEntity entity, UUID owner, boolean sit) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(new NamespacedKey(plugin, TamingRules.OWNER_KEY), PersistentDataType.STRING, owner.toString());
        data.set(new NamespacedKey(plugin, TamingRules.SITTING_KEY), PersistentDataType.BYTE, (byte) (sit ? 1 : 0));
        if (entity instanceof Mob mob) {
            // Tamed dinosaurs stay; wild ones despawn like other module mobs.
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            mob.setTarget(null);
            mob.setAware(!sit);
        }
        entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, entity.getHeight(), 0), 7, 0.5, 0.5, 0.5);
    }

    /** Tells the entity's animation behaviour about a state change (the raptor's relaxed loop). */
    private void animate(LivingEntity entity, java.util.function.Consumer<PrimordialDinosaur> change) {
        PrimordialDinosaur dinosaur = PrimordialDinosaur.of(mobs, entity);
        if (dinosaur != null) {
            change.accept(dinosaur);
        }
    }

    private static void failed(LivingEntity entity) {
        entity.getWorld().spawnParticle(Particle.SMOKE, entity.getLocation().add(0, entity.getHeight(), 0), 7, 0.4, 0.4, 0.4, 0.02);
    }

    // --- Interaction ------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof LivingEntity entity)) {
            return;
        }
        Optional<ModMobType> type = mobs.typeOf(entity);
        if (type.isEmpty() || type.get().biome() != PrimordialMobs.SUBTERRANODON.biome()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<String> heldId = mobs.itemId(held);
        Optional<UUID> owner = owner(entity);
        if (owner.isPresent()) {
            if (!owner.get().equals(player.getUniqueId())) {
                return;
            }
            if (player.isSneaking()) {
                event.setCancelled(true);
                boolean sit = !sitting(entity);
                entity.getPersistentDataContainer().set(sittingKey, PersistentDataType.BYTE, (byte) (sit ? 1 : 0));
                if (entity instanceof Mob mob) {
                    mob.setAware(!sit);
                    mob.setTarget(null);
                }
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(sit ? "§eQuieto" : "§eTe sigue"));
            } else if (TamingRules.rideable(type.get()) && held.getType().isAir() && entity.getPassengers().isEmpty()
                && !entity.getPersistentDataContainer().has(babyKey, PersistentDataType.LONG)) {
                event.setCancelled(true);
                entity.addPassenger(player);
            }
            return;
        }
        if (heldId.isEmpty()) {
            return;
        }
        String item = heldId.get();
        ModMobType mob = type.get();
        if (mob == PrimordialMobs.SUBTERRANODON && (item.equals("ac_trilocaris_tail") || item.equals("ac_cooked_trilocaris_tail"))) {
            event.setCancelled(true);
            consume(player, held);
            if (ThreadLocalRandom.current().nextInt(3) == 0) {
                tame(entity, player, false);
            } else {
                failed(entity);
            }
        } else if (mob == PrimordialMobs.VALLUMRAPTOR && item.equals("ac_dinosaur_nugget")) {
            event.setCancelled(true);
            consume(player, held);
            int relaxedFor = 200 + ThreadLocalRandom.current().nextInt(200);
            relaxedUntil.put(entity.getUniqueId(), tick + relaxedFor);
            animate(entity, dinosaur -> dinosaur.relax(relaxedFor));
            if (entity instanceof Mob raptor) {
                raptor.setTarget(null);
            }
            entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0, entity.getHeight(), 0), 5, 0.4, 0.4, 0.4);
        } else if (mob == PrimordialMobs.VALLUMRAPTOR && item.equals("ac_serene_salad")) {
            event.setCancelled(true);
            if (relaxedUntil.getOrDefault(entity.getUniqueId(), 0L) > tick) {
                consumeBowl(player, held);
                relaxedUntil.remove(entity.getUniqueId());
                animate(entity, dinosaur -> dinosaur.relax(0));
                entity.setHealth(Math.min(entity.getHealth() + 5, maxHealth(entity)));
                tame(entity, player, true);
            } else {
                failed(entity);
            }
        } else if (mob == PrimordialMobs.RELICHEIRUS && item.equals("ac_primordial_soup")) {
            // Mod RelicheirusEntity.onFeedMixture: it pushes trees over for 1200 ticks.
            event.setCancelled(true);
            consumeBowl(player, held);
            animate(entity, dinosaur -> dinosaur.pushTrees(1200));
            entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0, entity.getHeight(), 0), 5, 0.4, 0.4, 0.4);
        } else if (mob == PrimordialMobs.TREMORSAURUS && item.equals("ac_serene_salad")) {
            event.setCancelled(true);
            if (!mobs.effects().has(entity, ModEffectType.STUNNED)) {
                failed(entity);
                return;
            }
            consumeBowl(player, held);
            mobs.effects().remove(entity, ModEffectType.STUNNED);
            PersistentDataContainer data = entity.getPersistentDataContainer();
            int attempts = data.getOrDefault(attemptsKey, PersistentDataType.INTEGER, 0) + 1;
            data.set(attemptsKey, PersistentDataType.INTEGER, attempts);
            if (TamingRules.tremorsaurusTames(attempts, ThreadLocalRandom.current().nextBoolean())) {
                tame(entity, player, false);
            } else {
                failed(entity);
            }
        }
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? entity.getHealth() : attribute.getValue();
    }

    private static void consume(Player player, ItemStack held) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract();
        }
    }

    /** Mixtures are bowl foods: the bowl comes back, as in the mod. */
    private static void consumeBowl(Player player, ItemStack held) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        held.subtract();
        player.getInventory().addItem(new ItemStack(Material.BOWL))
            .values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /** Primitive club: 80 % chance to stun for 7.5-15 s (mod {@code PrimitiveClubItem.hurtEnemy}). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onClub(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (mobs.itemId(player.getInventory().getItemInMainHand()).filter("ac_primitive_club"::equals).isEmpty()) {
            return;
        }
        if (ThreadLocalRandom.current().nextFloat() < 0.8f) {
            mobs.effects().apply(target, ModEffectType.STUNNED, 0, 150 + ThreadLocalRandom.current().nextInt(150));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
        } else {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 1.0f, 0.8f);
        }
    }

    // --- Loyalty ----------------------------------------------------------------------------------

    /** Tamed dinosaurs never pick a player on their own (and never their owner); the owner's fights are theirs. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Optional<UUID> owner = owner(event.getEntity());
        if (owner.isEmpty() || event.getTarget() == null) {
            return;
        }
        if (event.getTarget().getUniqueId().equals(owner.get())
            || owner(event.getTarget()).filter(owner.get()::equals).isPresent()
            || (event.getReason() != EntityTargetEvent.TargetReason.CUSTOM && !(event.getTarget() instanceof Enemy)
                && !(event.getReason() == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY && !(event.getTarget() instanceof Player)))) {
            // The zombie and ravager bases would otherwise go for villagers, golems and players.
            event.setCancelled(true);
        }
    }

    /** Vanilla counts tamed zombie/ravager bases as monsters near the bed. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBed(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.NOT_SAFE) {
            return;
        }
        Location bed = event.getBed().getLocation();
        for (Entity nearby : bed.getWorld().getNearbyEntities(bed, 8, 5, 8)) {
            // The tremorzilla is an animal in the mod (its giant base is a monster): it never keeps anyone awake.
            if (nearby instanceof Monster && owner(nearby).isEmpty()
                && mobs.typeOf(nearby).filter(type -> type.id().equals(Tremorzilla.ID)).isEmpty()) {
                return;
            }
        }
        event.setUseBed(Event.Result.ALLOW);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onOwnerFight(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
            ? shooter : event.getDamager();
        Player owner = damager instanceof Player player ? player : null;
        LivingEntity target = event.getEntity() instanceof LivingEntity living ? living : null;
        Player attacked = event.getEntity() instanceof Player player ? player : null;
        if (owner != null && target != null && (target instanceof Enemy || target instanceof Player)) {
            assist(owner, target);
        } else if (attacked != null && damager instanceof LivingEntity attacker) {
            assist(attacked, attacker);
        }
    }

    private void assist(Player owner, LivingEntity enemy) {
        if (enemy.equals(owner) || owner(enemy).filter(owner.getUniqueId()::equals).isPresent()) {
            return;
        }
        for (Entity nearby : owner.getNearbyEntities(ASSIST_RADIUS, ASSIST_RADIUS, ASSIST_RADIUS)) {
            if (nearby instanceof Mob mob && !nearby.equals(enemy) && !sitting(nearby) && nearby.getPassengers().isEmpty()
                && owner(nearby).filter(owner.getUniqueId()::equals).isPresent()) {
                mob.setTarget(enemy);
            }
        }
    }

    /** A server stop while riding would save the mount with its AI off; fix it when it loads again. */
    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            restoreAware(entity);
        }
    }

    private void restoreAware(Entity entity) {
        if (entity instanceof Mob mob && owner(mob).isPresent()) {
            mob.setAware(!sitting(mob) && mob.getPassengers().isEmpty());
        }
    }

    /** Module shutdown: riders get off and every mount gets its AI back. */
    @Override
    public void close() {
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            if (player.getVehicle() instanceof Mob mount && owner(mount).isPresent()) {
                mount.removePassenger(player);
                restoreAware(mount);
            }
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof Mob mob && owner(mob).isPresent()) {
            mob.setAware(!sitting(mob));
        }
    }

    // --- Tick: riding and following ---------------------------------------------------------------

    @Override
    public void run() {
        tick++;
        if (tick == 1) {
            for (World world : mobs.plugin().getServer().getWorlds()) {
                for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                    restoreAware(mob);
                }
            }
        }
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            if (player.getVehicle() instanceof Mob mount && owner(mount).filter(player.getUniqueId()::equals).isPresent()) {
                ride(player, mount);
            }
            if (tick % 10 == 0) {
                follow(player);
            }
        }
        if (tick % 200 == 0) {
            relaxedUntil.values().removeIf(until -> until <= tick);
        }
    }

    private void ride(Player player, Mob mount) {
        Optional<ModMobType> type = mobs.typeOf(mount);
        if (type.isEmpty() || !TamingRules.rideable(type.get())) {
            return;
        }
        mount.setAware(false);
        boolean flying = type.get() == PrimordialMobs.SUBTERRANODON;
        Input input = player.getCurrentInput();
        AttributeInstance speedAttribute = mount.getAttribute(Attribute.MOVEMENT_SPEED);
        double speed = (speedAttribute == null ? 0.25 : speedAttribute.getValue()) * (flying ? 3.0 : 1.6)
            * (input.isSprint() && !flying ? 1.3 : 1.0);
        Location look = player.getLocation();
        double[] v = TamingRules.mountVelocity(look.getYaw(), look.getPitch(), input.isForward(), input.isBackward(), input.isLeft(),
            input.isRight(), input.isJump(), flying && input.isSprint(), speed, flying, mount.getVelocity().getY(),
            mount.isOnGround());
        mount.setRotation(look.getYaw(), 0);
        mount.setBodyYaw(look.getYaw());
        mount.setVelocity(new Vector(v[0], v[1], v[2]));
        mount.setFallDistance(0);
    }

    private void follow(Player owner) {
        for (Entity nearby : owner.getNearbyEntities(FOLLOW_SEARCH, FOLLOW_SEARCH, FOLLOW_SEARCH)) {
            if (!(nearby instanceof Mob mob) || !nearby.getPassengers().isEmpty() || sitting(nearby)
                || owner(nearby).filter(owner.getUniqueId()::equals).isEmpty()
                || mobs.typeOf(nearby).filter(type -> type.id().equals(Tremorzilla.ID)).isPresent()) {
                // The tremorzilla follows by its own command (Tremorzilla.followOwner).
                continue;
            }
            LivingEntity target = mob.getTarget();
            if (target != null && (!target.isValid() || target.isDead() || !target.getWorld().equals(mob.getWorld()))) {
                mob.setTarget(null);
                target = null;
            }
            if (target != null) {
                continue;
            }
            double distance = nearby.getLocation().distance(owner.getLocation());
            if (distance > TELEPORT_DISTANCE) {
                nearby.teleport(owner.getLocation());
            } else if (distance > FOLLOW_START) {
                if (mob instanceof Phantom) {
                    Vector toOwner = owner.getLocation().add(0, 2, 0).toVector().subtract(mob.getLocation().toVector());
                    mob.setVelocity(toOwner.normalize().multiply(0.45));
                } else {
                    mob.getPathfinder().moveTo(owner, 1.2);
                }
            }
        }
    }
}
