package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.function.Predicate;

/** Plans one ambush before spending its cooldown, keeping the same destination for execution. */
final class LegendaryShadowAmbush {
    static final int DURATION = 100;

    record Plan(Location destination, LivingEntity victim) {
        Plan { destination = destination.clone(); }
        @Override public Location destination() { return destination.clone(); }
    }

    record Buff(PotionEffectType type, int duration, int amplifier) {}

    private LegendaryShadowAmbush() {}

    static Plan prepare(Player attacker, double range, Predicate<LivingEntity> permitted) {
        Location eye = attacker.getEyeLocation();
        var collision = attacker.getWorld().rayTrace(eye, eye.getDirection(), range,
            FluidCollisionMode.NEVER, true, .4,
            entity -> entity instanceof LivingEntity living && permitted.test(living));
        LivingEntity victim = collision != null && collision.getHitEntity() instanceof LivingEntity living ? living : null;
        return new Plan(destination(attacker.getLocation(), victim == null ? null : victim.getLocation()), victim);
    }

    static Location destination(Location attacker, Location victim) {
        Location anchor = victim == null ? attacker : victim;
        double distance = victim == null ? 4 : -1.5;
        double yaw = Math.toRadians(anchor.getYaw());
        return anchor.clone().add(-Math.sin(yaw) * distance, 0, Math.cos(yaw) * distance);
    }

    static double damage(double configured) {
        if (!Double.isFinite(configured)) return 6;
        return Math.max(0, Math.min(configured, 6));
    }

    static List<Buff> casterBenefits() {
        return List.of(new Buff(PotionEffectType.INVISIBILITY, DURATION, 0),
            new Buff(PotionEffectType.SPEED, DURATION, 2));
    }

    static List<Buff> victimPenalties() {
        return List.of(new Buff(PotionEffectType.WEAKNESS, 80, 1),
            new Buff(PotionEffectType.DARKNESS, 60, 0));
    }
}
