package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.BigCasares;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared protection, collision, damage attribution and temporary visual leases. */
final class LegendaryElementalContext implements Listener {
    final BigCasares plugin;
    final LegendaryCombatPolicy policy;
    final LegendaryPvpProgression progression;
    private LivingEntity observing;
    private boolean accepted;
    private final Map<UUID, List<Entity>> visuals = new HashMap<>();
    private final NamespacedKey temporary = new NamespacedKey("bigcasares", "legendary_temporary");

    LegendaryElementalContext(BigCasares plugin, LegendaryPvpProgression progression) {
        this.plugin = plugin;
        this.policy = new LegendaryCombatPolicy(plugin);
        this.progression = progression;
    }

    boolean allowed(Player owner, LivingEntity target) {
        return policy.target(owner, target);
    }

    boolean clear(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) return false;
        Vector delta = to.toVector().subtract(from.toVector());
        double distance = delta.length();
        for (int step = 0; step <= Math.ceil(distance * 2); step++) {
            Location point =
                    from.clone()
                            .add(
                                    delta.clone()
                                            .multiply(step / Math.max(1, Math.ceil(distance * 2))));
            if (!point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4))
                return false;
        }
        return distance < .05
                || from.getWorld()
                                .rayTraceBlocks(
                                        from,
                                        delta.normalize(),
                                        distance,
                                        FluidCollisionMode.NEVER,
                                        true)
                        == null;
    }

    boolean pathAllowed(
            Player owner, Location from, Location to, LegendaryProtectionEvent.Action action) {
        if (!from.getWorld().equals(to.getWorld())) return false;
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.max(1, (int) Math.ceil(delta.length() * 2));
        for (int step = 0; step <= steps; step++) {
            Location point = from.clone().add(delta.clone().multiply(step / (double) steps));
            if (!point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)
                    || !point.getWorld().getWorldBorder().isInside(point)
                    || !policy.allowed(owner, point, null, action)) return false;
        }
        return true;
    }

    List<LivingEntity> targets(Player owner, Location center, double radius) {
        if (!center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4))
            return List.of();
        var candidates =
                center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                        .filter(e -> e instanceof LivingEntity)
                        .map(e -> (LivingEntity) e)
                        .toList();
        return LegendaryTargets.select(
                candidates,
                LivingEntity::getUniqueId,
                e -> e.getLocation().distanceSquared(center),
                e ->
                        e.getLocation().distanceSquared(center) <= radius * radius
                                && allowed(owner, e)
                                && clear(center.clone().add(0, .25, 0), e.getEyeLocation())
                                && pathAllowed(
                                        owner,
                                        center,
                                        e.getLocation(),
                                        LegendaryProtectionEvent.Action.DAMAGE),
                16);
    }

    org.bukkit.util.RayTraceResult ray(Player owner, double range) {
        if (!pathAllowed(
                owner,
                owner.getEyeLocation(),
                owner.getEyeLocation().add(owner.getEyeLocation().getDirection().multiply(range)),
                LegendaryProtectionEvent.Action.DAMAGE)) return null;
        return owner.getWorld()
                .rayTrace(
                        owner.getEyeLocation(),
                        owner.getEyeLocation().getDirection(),
                        range,
                        FluidCollisionMode.NEVER,
                        true,
                        .3,
                        e -> e instanceof LivingEntity living && allowed(owner, living));
    }

    Location aimed(Player owner, double range) {
        Location end =
                owner.getEyeLocation().add(owner.getEyeLocation().getDirection().multiply(range));
        if (!pathAllowed(
                owner, owner.getEyeLocation(), end, LegendaryProtectionEvent.Action.DAMAGE))
            return null;
        var hit =
                owner.getWorld()
                        .rayTraceBlocks(
                                owner.getEyeLocation(),
                                owner.getEyeLocation().getDirection(),
                                range,
                                FluidCollisionMode.NEVER,
                                true);
        return hit == null
                ? end
                : hit.getHitPosition()
                        .toLocation(owner.getWorld())
                        .subtract(owner.getEyeLocation().getDirection().multiply(.15));
    }

    Location groundAim(Player owner, double range) {
        Vector direction = owner.getLocation().getDirection();
        if (direction.getY() > 0) direction.setY(0);
        if (direction.lengthSquared() < .001) direction = new Vector(0, 0, 1);
        Location end = owner.getEyeLocation().add(direction.normalize().multiply(range));
        if (!pathAllowed(
                owner, owner.getEyeLocation(), end, LegendaryProtectionEvent.Action.DAMAGE))
            return null;
        var hit =
                owner.getWorld()
                        .rayTraceBlocks(
                                owner.getEyeLocation(),
                                direction,
                                range,
                                FluidCollisionMode.NEVER,
                                true);
        Location point =
                hit == null
                        ? end
                        : hit.getHitPosition()
                                .toLocation(owner.getWorld())
                                .subtract(direction.clone().multiply(.1));
        var floor =
                point.getWorld()
                        .rayTraceBlocks(
                                point, new Vector(0, -1, 0), 32, FluidCollisionMode.NEVER, true);
        return floor == null
                ? null
                : floor.getHitPosition().toLocation(owner.getWorld()).add(0, .1, 0);
    }

    boolean moveAllowed(Player owner, LivingEntity target, Vector velocity) {
        Location start = target.getLocation(), end = start.clone().add(velocity);
        for (double x : new double[] {-.29, .29})
            for (double z : new double[] {-.29, .29})
                for (double y : new double[] {.1, 1.7})
                    if (!clear(start.clone().add(x, y, z), end.clone().add(x, y, z))) return false;
        return clear(start.clone().add(0, .5, 0), end.clone().add(0, .5, 0))
                && clear(start.clone().add(0, 1.5, 0), end.clone().add(0, 1.5, 0))
                && pathAllowed(owner, start, end, LegendaryProtectionEvent.Action.MOVE)
                && end.getY() > end.getWorld().getMinHeight()
                && end.getY() < end.getWorld().getMaxHeight() - 2
                && end.getWorld().isChunkLoaded(end.getBlockX() >> 4, end.getBlockZ() >> 4)
                && end.getWorld().getWorldBorder().isInside(end)
                && policy.allowed(owner, start, target, LegendaryProtectionEvent.Action.MOVE)
                && policy.allowed(owner, end, target, LegendaryProtectionEvent.Action.MOVE);
    }

    void ring(Location center, double radius, Color color) {
        for (int index = 0; index < 32; index++) {
            double angle = index * Math.PI / 16;
            dust(
                    center.clone().add(Math.cos(angle) * radius, .1, Math.sin(angle) * radius),
                    color,
                    1);
        }
    }

    boolean damage(Player owner, LegendaryWeapon weapon, LivingEntity target, double amount) {
        if (amount <= 0 || !allowed(owner, target) || target.isInvulnerable()) return false;
        LivingEntity previous = observing;
        boolean prior = accepted;
        observing = target;
        accepted = false;
        int immunity = target.getNoDamageTicks();
        try {
            return progression.attribute(
                    owner,
                    weapon,
                    () -> {
                        target.setNoDamageTicks(0);
                        target.damage(
                                amount,
                                DamageSource.builder(DamageType.PLAYER_ATTACK)
                                        .withCausingEntity(owner)
                                        .withDirectEntity(owner)
                                        .build());
                        return accepted;
                    });
        } finally {
            if (target.isValid() && !target.isDead())
                target.setNoDamageTicks(Math.max(immunity, target.getNoDamageTicks()));
            observing = previous;
            accepted = prior;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void damageOutcome(EntityDamageByEntityEvent event) {
        if (event.getEntity().equals(observing))
            accepted = !event.isCancelled() && event.getFinalDamage() > 0;
    }

    void dust(Location point, Color color, int count) {
        point.getWorld()
                .spawnParticle(
                        Particle.DUST,
                        point,
                        count,
                        .15,
                        .15,
                        .15,
                        0,
                        new Particle.DustOptions(color, 1));
    }

    void line(Location from, Location to, Color color) {
        Vector delta = to.toVector().subtract(from.toVector());
        int points = Math.max(1, Math.min(40, (int) (delta.length() * 3)));
        for (int index = 0; index <= points; index++)
            dust(from.clone().add(delta.clone().multiply(index / (double) points)), color, 1);
    }

    void lightning(Player owner, Location point) {
        Entity bolt = point.getWorld().strikeLightningEffect(point);
        bolt.setPersistent(false);
        bolt.getPersistentDataContainer().set(temporary, PersistentDataType.BYTE, (byte) 1);
        visuals.computeIfAbsent(owner.getUniqueId(), ignored -> new ArrayList<>()).add(bolt);
        point.getWorld().playSound(point, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, .5f, 1.3f);
    }

    void clean(UUID owner) {
        var list = visuals.remove(owner);
        if (list != null) list.forEach(Entity::remove);
    }

    void prune() {
        visuals.values().forEach(list -> list.removeIf(e -> !e.isValid()));
    }

    void close() {
        visuals.keySet().stream().toList().forEach(this::clean);
    }
}
