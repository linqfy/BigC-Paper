package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.BigCasares;

import net.kyori.adventure.text.Component;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.IntSupplier;
import java.util.logging.Level;

/** Independently implemented mechanics inspired by public Altar SMP ability descriptions. */
final class LegendaryAltarAbilities implements Listener, Runnable {
    private record Key(UUID owner, LegendaryWeapon weapon, boolean secondary) {}

    private static final class Session {
        final Key key;
        final LegendaryAbilityLevels spec;
        final LegendaryPotionEffects effects = new LegendaryPotionEffects();
        final List<Entity> visuals = new ArrayList<>();
        final java.util.Set<UUID> hit = new java.util.HashSet<>();
        final List<Dagger> daggers = new ArrayList<>();
        Location center;
        Vector forward;
        int age, endAt;
        boolean done;
        Pose previousPose;
        boolean previousFixed;

        Session(Key key, LegendaryAbilityLevels spec, Location center, int endAt) {
            this.key = key;
            this.spec = spec;
            this.center = center;
            this.endAt = endAt;
        }
    }

    private static final class Dagger {
        Location point;
        final Vector velocity;
        final ItemDisplay model;
        double travelled;
        boolean done;

        Dagger(Location point, Vector velocity, ItemDisplay model) {
            this.point = point;
            this.velocity = velocity;
            this.model = model;
        }
    }

    private record Bleed(UUID owner, LivingEntity target, int until, int next) {}

    private record Passive(LegendaryWeapon weapon, int level, LegendaryPotionEffects effects) {}

    private final BigCasares plugin;
    private final LegendaryStateStore store;
    private final LegendaryPvpLevels levels;
    private final LegendaryPvpProgression progression;
    private final LegendaryModelAnimations animations;
    private final Predicate<Player> blocked;
    private final IntSupplier activeEffects;
    private final LegendaryElementalContext context;
    private final LegendaryEarthShock shock;
    private final LegendaryPaladinGuard guard;
    private final Map<Key, Session> sessions = new HashMap<>();
    private final Map<UUID, Passive> passives = new HashMap<>();
    private final Map<UUID, Integer> infectionCooldown = new HashMap<>();
    private final List<Bleed> bleeds = new ArrayList<>();
    private int tick;
    private boolean stopped;

    LegendaryAltarAbilities(
            BigCasares plugin,
            LegendaryStateStore store,
            LegendaryPvpLevels levels,
            LegendaryPvpProgression progression,
            LegendaryModelAnimations animations,
            Predicate<Player> blocked,
            IntSupplier activeEffects,
            BukkitRuntimeRegistrations registrations) {
        this.plugin = plugin;
        this.store = store;
        this.levels = levels;
        this.progression = progression;
        this.animations = animations;
        this.blocked = blocked;
        this.activeEffects = activeEffects;
        context =
                registrations.registerListener(
                        "legendary-altar-damage",
                        new LegendaryElementalContext(plugin, progression));
        shock = registrations.registerListener("legendary-earth-shock", new LegendaryEarthShock());
        guard =
                registrations.registerListener(
                        "legendary-paladin-guard",
                        new LegendaryPaladinGuard(
                                registrations,
                                (owner, target) ->
                                        context.allowed(owner, target)
                                                && context.clear(
                                                        owner.getEyeLocation(),
                                                        target.getEyeLocation()),
                                (owner, target, amount) ->
                                        context.damage(
                                                owner,
                                                LegendaryWeapon.EARTHBREAKER,
                                                target,
                                                amount)));
        registrations.registerListener(
                "legendary-life-steal",
                new LegendaryLifeSteal(this::bloodAge, context.policy::affect));
    }

    private LegendaryWeapon held(Player player) {
        return plugin.getCustomItemRegistry()
                .resolveItemId(player.getInventory().getItemInMainHand())
                .flatMap(LegendaryWeapon::find)
                .orElse(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swap(PlayerSwapHandItemsEvent event) {
        if (stopped || blocked.test(event.getPlayer())) return;
        LegendaryWeapon weapon = held(event.getPlayer());
        if (weapon != LegendaryWeapon.BLOODLUST
                && weapon != LegendaryWeapon.SHADOW_BLADE
                && weapon != LegendaryWeapon.EARTHBREAKER) return;
        event.setCancelled(true);
        cast(event.getPlayer(), weapon, event.getPlayer().isSneaking());
    }

    void cast(Player player, LegendaryWeapon weapon, boolean secondary) {
        if (stopped || blocked.test(player) || held(player) != weapon || player.isDead()
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        activate(player, weapon, secondary);
    }

    static int requiredLevel(LegendaryWeapon weapon, boolean secondary) {
        return weapon == LegendaryWeapon.BLOODLUST ? (secondary ? 3 : 5)
                : secondary ? (weapon == LegendaryWeapon.EARTHBREAKER ? 3 : 2) : 1;
    }

    private void activate(Player player, LegendaryWeapon weapon, boolean secondary) {
        Key key = new Key(player.getUniqueId(), weapon, secondary);
        Session existing = sessions.get(key);
        if (existing != null) {
            if (weapon == LegendaryWeapon.SHADOW_BLADE && secondary) landLeap(player, existing);
            if (weapon == LegendaryWeapon.BLOODLUST && secondary) finish(existing);
            return;
        }
        int level = levels.level(player.getUniqueId(), weapon), required = requiredLevel(weapon, secondary);
        if (level < required) {
            player.sendActionBar(
                    Component.text("Necesitas nivel " + required + " de " + weapon.displayName));
            return;
        }
        if (activeEffects.getAsInt() >= Math.clamp(
                    plugin.getConfig().getInt("legendary-weapons.max-active-effects", 32), 1, 32)
                || !context.policy.allowed(
                        player, player.getLocation(), null, LegendaryProtectionEvent.Action.MOVE))
            return;
        long now = System.currentTimeMillis(),
                remaining = store.remaining(player.getUniqueId(), weapon, secondary, now);
        if (remaining > 0) {
            player.sendActionBar(
                    Component.text("Habilidad — " + ((remaining + 999) / 1000) + " s"));
            return;
        }
        var spec = LegendaryAbilityLevels.at(level);
        int duration =
                switch (weapon) {
                    case BLOODLUST -> secondary ? 300 : 100;
                    case SHADOW_BLADE -> secondary ? 20 : 80;
                    case EARTHBREAKER -> secondary ? spec.guardDurationTicks() : 40;
                    default -> 40;
                };
        Session session = new Session(key, spec, player.getLocation(), duration);
        session.forward = player.getEyeLocation().getDirection();
        if (weapon == LegendaryWeapon.EARTHBREAKER && !secondary && !player.isOnGround()) {
            player.sendActionBar(Component.text("Acércate al suelo para Earthshatter."));
            return;
        }
        try {
            if (!store.beginCooldown(
                    player.getUniqueId(),
                    weapon,
                    secondary,
                    now,
                    LegendaryAbilityLevels.cooldown(weapon, secondary))) return;
            sessions.put(key, session);
            switch (weapon) {
                case BLOODLUST -> {
                    if (secondary) {
                        session.previousPose = player.getPose();
                        session.previousFixed = player.hasFixedPose();
                        player.setPose(Pose.SWIMMING, true);
                        session.effects.apply(player, PotionEffectType.INVISIBILITY, 300, 0, 0);
                        session.effects.apply(player, PotionEffectType.SPEED, 300, 2, 0);
                    } else session.center = player.getEyeLocation();
                }
                case SHADOW_BLADE -> {
                    if (secondary) {
                        session.center = player.getLocation();
                        session.effects.apply(player, PotionEffectType.INVISIBILITY, 22, 0, 0);
                    } else
                        for (int index = 0; index < spec.daggers(); index++) {
                            Vector velocity =
                                    session.forward
                                            .clone()
                                            .rotateAroundY(
                                                    Math.toRadians(
                                                            (index - (spec.daggers() - 1) / 2.0)
                                                                    * 8))
                                            .multiply(1.1);
                            Location start = player.getEyeLocation();
                            ItemDisplay model = display(start, weapon);
                            session.visuals.add(model);
                            session.daggers.add(new Dagger(start, velocity, model));
                        }
                }
                case EARTHBREAKER -> {
                    if (secondary) {
                        guard.start(player, spec);
                        session.effects.apply(player, PotionEffectType.RESISTANCE, duration, 0, 0);
                    } else {
                        ItemDisplay model = display(player.getLocation().add(0, 3, 0), weapon);
                        var transform = model.getTransformation();
                        transform.getScale().set(3);
                        model.setTransformation(transform);
                        session.visuals.add(model);
                    }
                }
                default -> {}
            }
            animations.ability(player, weapon);
            player.getWorld()
                    .playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, .7f, .8f);
        } catch (IOException failure) {
            finish(session);
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar el cooldown.", failure);
        }
    }

    private ItemDisplay display(Location location, LegendaryWeapon weapon) {
        return location.getWorld()
                .spawn(
                        location,
                        ItemDisplay.class,
                        model -> {
                            model.setItemStack(
                                    plugin.getCustomItemRegistry().findById(weapon.id).orElseThrow().createItemStack(1));
                            model.setPersistent(false);
                            model.setTeleportDuration(1);
                            model.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                            model.getPersistentDataContainer()
                                    .set(
                                            new NamespacedKey("bigcasares", "legendary_temporary"),
                                            PersistentDataType.BYTE,
                                            (byte) 1);
                        });
    }

    public void run() {
        if (stopped) return;
        tick++;
        if (tick % 10 == 0)
            for (Player player : plugin.getServer().getOnlinePlayers()) passive(player);
        for (Session session : new ArrayList<>(sessions.values())) {
            Player player = plugin.getServer().getPlayer(session.key.owner());
            session.age++;
            if (player == null
                    || !player.isOnline()
                    || player.isDead()
                    || !player.getWorld().equals(session.center.getWorld())
                    || session.age > session.endAt) {
                finish(session);
                continue;
            }
            try {
                session.effects.prune(session.age);
                switch (session.key.weapon()) {
                    case BLOODLUST -> blood(player, session);
                    case SHADOW_BLADE -> {
                        if (session.key.secondary()) leap(player, session);
                        else daggers(player, session);
                    }
                    case EARTHBREAKER -> earth(player, session);
                    default -> finish(session);
                }
            } catch (RuntimeException failure) {
                finish(session);
                plugin.getLogger().log(Level.SEVERE, "Habilidad Altar cancelada.", failure);
            }
        }
        for (Bleed bleed : new ArrayList<>(bleeds)) {
            Player owner = plugin.getServer().getPlayer(bleed.owner());
            if (owner == null
                    || owner.isDead()
                    || !owner.isOnline()
                    || !bleed.target().isValid()
                    || bleed.target().isDead()
                    || !owner.getWorld().equals(bleed.target().getWorld())
                    || tick > bleed.until()) {
                bleeds.remove(bleed);
                continue;
            }
            if (tick >= bleed.next()) {
                bleeds.remove(bleed);
                if (context.damage(owner, LegendaryWeapon.BLOODLUST, bleed.target(), 1))
                    context.dust(bleed.target().getLocation().add(0, 1, 0), Color.RED, 6);
                bleeds.add(new Bleed(bleed.owner(), bleed.target(), bleed.until(), tick + 20));
            }
        }
        context.prune();
    }

    private void blood(Player player, Session session) {
        if (session.key.secondary()) {
            if (session.age % 5 == 0) context.ring(player.getLocation(), .9, Color.RED);
            return;
        }
        if (session.done) return;
        Location next = session.center.clone().add(session.forward.clone().multiply(1.2));
        if (session.age * 1.2 > session.spec.bloodRange()
                || !context.clear(session.center, next)
                || !context.pathAllowed(
                        player, session.center, next, LegendaryProtectionEvent.Action.DAMAGE)) {
            session.done = true;
            return;
        }
        var collision =
                session.center
                        .getWorld()
                        .rayTrace(
                                session.center,
                                session.forward,
                                1.2,
                                FluidCollisionMode.NEVER,
                                true,
                                .35,
                                e ->
                                        e instanceof LivingEntity living
                                                && context.allowed(player, living));
        context.line(player.getEyeLocation(), next, Color.RED);
        session.center = next;
        if (collision != null) {
            session.done = true;
            if (collision.getHitEntity() instanceof LivingEntity target
                    && context.damage(player, LegendaryWeapon.BLOODLUST, target, 4))
                pull(player, target, session.spec.bloodPull());
        }
    }

    private void daggers(Player player, Session session) {
        for (Dagger dagger : session.daggers) {
            if (dagger.done) continue;
            double step = Math.min(1.1, session.spec.shadowRange() - dagger.travelled);
            Location next =
                    dagger.point.clone().add(dagger.velocity.clone().normalize().multiply(step));
            if (step <= 0
                    || !context.pathAllowed(
                            player, dagger.point, next, LegendaryProtectionEvent.Action.DAMAGE)) {
                dagger.done = true;
                dagger.model.remove();
                continue;
            }
            var collision =
                    dagger.point
                            .getWorld()
                            .rayTrace(
                                    dagger.point,
                                    dagger.velocity.clone().normalize(),
                                    step,
                                    FluidCollisionMode.NEVER,
                                    true,
                                    .25,
                                    e ->
                                            e instanceof LivingEntity target
                                                    && context.allowed(player, target));
            if (collision != null) {
                dagger.done = true;
                dagger.model.remove();
                if (collision.getHitEntity() instanceof LivingEntity target
                        && session.hit.add(target.getUniqueId())
                        && context.damage(
                                player,
                                LegendaryWeapon.SHADOW_BLADE,
                                target,
                                session.spec.shadowDamage())) {
                    session.effects.apply(
                            target,
                            PotionEffectType.SLOWNESS,
                            session.spec.shadowEffectTicks(),
                            0,
                            session.age);
                    session.effects.apply(
                            target,
                            PotionEffectType.BLINDNESS,
                            session.spec.shadowEffectTicks(),
                            0,
                            session.age);
                    session.endAt =
                            Math.max(session.endAt, session.age + session.spec.shadowEffectTicks());
                }
            } else {
                dagger.point = next;
                dagger.travelled += step;
                dagger.model.teleport(next);
                context.dust(next, Color.fromRGB(35, 12, 50), 3);
            }
        }
    }

    private void leap(Player player, Session session) {
        if (session.done) return;
        Vector step = session.forward.clone().multiply(.6 + session.spec.level() * .08);
        Location next = session.center.clone().add(step);
        if (!context.moveAllowed(
                        player, player, next.toVector().subtract(player.getLocation().toVector()))
                || session.age >= 20) {
            landLeap(player, session);
            return;
        }
        session.center = next;
        context.dust(next.clone().add(0, 1, 0), Color.fromRGB(50, 10, 70), 10);
    }

    private void landLeap(Player player, Session session) {
        if (!session.done
                && context.moveAllowed(
                        player,
                        player,
                        session.center.toVector().subtract(player.getLocation().toVector())))
            player.teleport(
                    session.center,
                    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        session.done = true;
        finish(session);
    }

    private void earth(Player player, Session session) {
        if (session.key.secondary()) {
            if (session.age % 5 == 0) context.ring(player.getLocation(), 1.4, Color.YELLOW);
            return;
        }
        if (session.age <= 8 && !session.visuals.isEmpty()) {
            Entity display = session.visuals.getFirst();
            Location point = session.center.clone().add(0, 3 - session.age * .3, 0);
            display.teleport(point);
        }
        if (session.age < 8) return;
        double radius = Math.min(8, 2 + session.spec.level());
        if (session.age % 3 == 0)
            context.ring(
                    session.center,
                    Math.min(radius, (session.age - 7) * .5),
                    Color.fromRGB(210, 165, 40));
        if (session.done) return;
        session.done = true;
        player.getWorld().playSound(session.center, Sound.ENTITY_GENERIC_EXPLODE, 1, .7f);
        progression.attribute(
                player,
                LegendaryWeapon.EARTHBREAKER,
                () -> {
                    shock.pulse(
                            player,
                            session.center,
                            Math.clamp(plugin.getConfig().getInt("legendary-weapons.max-targets-per-pulse", 12), 1, 12),
                            target ->
                                    context.allowed(player, target)
                                            && target.getLocation().distanceSquared(session.center)
                                                    <= radius * radius
                                            && context.clear(
                                                    session.center.clone().add(0, 1, 0),
                                                    target.getEyeLocation())
                                            && context.pathAllowed(
                                                    player,
                                                    session.center,
                                                    target.getLocation(),
                                                    LegendaryProtectionEvent.Action.DAMAGE),
                            target -> {
                                Vector push =
                                        target.getLocation()
                                                .toVector()
                                                .subtract(session.center.toVector())
                                                .setY(0);
                                if (push.lengthSquared() < .01) push.setX(1);
                                push.normalize().multiply(.3).setY(.3);
                                if (context.moveAllowed(player, target, push))
                                    target.setVelocity(push);
                            });
                    return null;
                });
    }

    private void pull(Player owner, LivingEntity target, double amount) {
        Vector velocity = owner.getLocation().toVector().subtract(target.getLocation().toVector());
        if (velocity.lengthSquared() > 1) {
            velocity.normalize().multiply(amount).setY(.2);
            if (context.moveAllowed(owner, target, velocity)) target.setVelocity(velocity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void melee(EntityDamageByEntityEvent event) {
        if (progression.inAbilityDamage()
                || event.getFinalDamage() <= 0
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity target)
                || !context.allowed(player, target)) return;
        LegendaryWeapon weapon = held(player);
        if (weapon == LegendaryWeapon.BLOODLUST
                && tick >= infectionCooldown.getOrDefault(player.getUniqueId(), 0)
                && bleeds.size() < 64
                && ThreadLocalRandom.current().nextDouble() < .15) {
            infectionCooldown.put(player.getUniqueId(), tick + 300);
            bleeds.add(new Bleed(player.getUniqueId(), target, tick + 100, tick + 20));
        }
        if (weapon == LegendaryWeapon.SHADOW_BLADE
                && levels.level(player.getUniqueId(), weapon) > 0
                && ThreadLocalRandom.current().nextDouble() < .3) {
            Vector
                    behind =
                            player.getLocation()
                                    .toVector()
                                    .subtract(target.getLocation().toVector())
                                    .setY(0),
                    facing = target.getLocation().getDirection().setY(0);
            if (behind.lengthSquared() > .01
                    && facing.lengthSquared() > .01
                    && behind.normalize().dot(facing.normalize()) < -.5) pull(player, target, .8);
        }
    }

    private int bloodAge(Player player) {
        if (stopped || progression.inAbilityDamage() || held(player) != LegendaryWeapon.BLOODLUST)
            return -1;
        return sessions.keySet().stream()
                        .anyMatch(
                                key ->
                                        key.owner().equals(player.getUniqueId())
                                                && key.weapon() == LegendaryWeapon.BLOODLUST)
                ? 0
                : -1;
    }

    private void passive(Player player) {
        LegendaryWeapon weapon = held(player);
        int level = weapon == null ? 0 : levels.level(player.getUniqueId(), weapon);
        UUID id = player.getUniqueId();
        Passive previous = passives.get(id);
        boolean eligible =
                !player.isDead()
                        && !blocked.test(player)
                        && level > 0
                        && (weapon == LegendaryWeapon.BLOODLUST
                                || weapon == LegendaryWeapon.SHADOW_BLADE);
        if (previous != null
                && (!eligible || previous.weapon() != weapon || previous.level() != level)) {
            previous.effects().cleanup(tick);
            passives.remove(id);
            previous = null;
        }
        if (!eligible) return;
        if (previous == null) {
            previous = new Passive(weapon, level, new LegendaryPotionEffects());
            passives.put(id, previous);
        }
        previous.effects().apply(player, PotionEffectType.SPEED, 30, 1, tick);
        if (weapon == LegendaryWeapon.BLOODLUST && level >= 4)
            previous.effects().apply(player, PotionEffectType.STRENGTH, 30, 0, tick);
        if (weapon == LegendaryWeapon.BLOODLUST && level >= 2 && tick % 40 == 0) {
            Location origin = player.getLocation();
            for (Player target : LegendaryTargets.select(player.getWorld().getPlayers(), Player::getUniqueId,
                    candidate -> origin.distanceSquared(candidate.getLocation()),
                    candidate -> origin.distanceSquared(candidate.getLocation()) <= 24 * 24
                            && context.allowed(player, candidate), 16)) {
                Vector line =
                        target.getEyeLocation()
                                .toVector()
                                .subtract(player.getEyeLocation().toVector());
                for (int point = 1; point <= 12; point++)
                    player.spawnParticle(
                            Particle.DUST,
                            player.getEyeLocation().add(line.clone().multiply(point / 12.0)),
                            1,
                            0,
                            0,
                            0,
                            0,
                            new Particle.DustOptions(Color.RED, .7f));
            }
        }
    }

    private void finish(Session session) {
        sessions.remove(session.key);
        Player owner = plugin.getServer().getPlayer(session.key.owner());
        session.visuals.forEach(Entity::remove);
        session.effects.cleanup(session.age);
        if (session.key.weapon() == LegendaryWeapon.EARTHBREAKER && session.key.secondary())
            guard.end(session.key.owner());
        if (owner != null
                && session.previousPose != null
                && owner.getPose() == Pose.SWIMMING
                && owner.hasFixedPose()) owner.setPose(session.previousPose, session.previousFixed);
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler
    public void death(PlayerDeathEvent event) {
        clear(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameMode(org.bukkit.event.player.PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == org.bukkit.GameMode.CREATIVE
                || event.getNewGameMode() == org.bukkit.GameMode.SPECTATOR) clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void milk(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == org.bukkit.Material.MILK_BUCKET) {
            sessions.values().forEach(s -> s.effects.forget(event.getPlayer().getUniqueId()));
            Passive passive = passives.get(event.getPlayer().getUniqueId());
            if (passive != null) passive.effects().forget(event.getPlayer().getUniqueId());
        }
    }

    private void clear(Player player) {
        UUID id = player.getUniqueId();
        for (Session session : new ArrayList<>(sessions.values()))
            if (session.key.owner().equals(id)) finish(session);
            else session.effects.forget(id);
        Passive passive = passives.remove(id);
        if (passive != null) passive.effects().cleanup(tick);
        bleeds.removeIf(
                bleed -> bleed.owner().equals(id) || bleed.target().getUniqueId().equals(id));
        infectionCooldown.remove(id);
        context.clean(id);
    }

    void shutdown() {
        stopped = true;
        for (Session session : new ArrayList<>(sessions.values())) finish(session);
        passives.values().forEach(p -> p.effects().cleanup(tick));
        passives.clear();
        bleeds.clear();
        infectionCooldown.clear();
        context.close();
    }

    int activeEffects() { return sessions.size(); }
}
