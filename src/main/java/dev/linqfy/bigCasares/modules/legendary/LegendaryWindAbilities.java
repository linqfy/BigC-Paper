package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Wind motion has finite leases. No terrain edits or permanent flight flags are used. */
final class LegendaryWindAbilities {
    private LegendaryWindAbilities() {}

    static LegendaryAbilityCast create(
            Player player,
            LegendaryElementalSkill skill,
            LegendaryElementalSkill.Settings settings,
            LegendaryElementalContext context) {
        return switch (skill) {
            case WIND_DASH -> new LegendaryThunderAbilities.Dash(player, skill, settings, true);
            case SKYBREAKER, HEAVENS_FALL -> new Flight(player, skill, settings);
            case TORNADO -> new Tornado(player, skill, settings);
            case WIND_PRISON -> {
                Location center = context.groundAim(player, settings.range());
                yield center == null ? null : new Prison(player, skill, settings, center);
            }
            default -> throw new IllegalArgumentException("Not a wind skill");
        };
    }

    static final class Flight extends LegendaryAbilityCast {
        final boolean heaven;
        final double startY;
        final boolean previousGravity;
        boolean airborne, descending, hovering, gravityOwned, impacted;
        int hoverAge;
        Location destination;

        Flight(
                Player player,
                LegendaryElementalSkill skill,
                LegendaryElementalSkill.Settings settings) {
            super(player, skill, settings);
            heaven = skill == LegendaryElementalSkill.HEAVENS_FALL;
            startY = player.getY();
            previousGravity = player.hasGravity();
        }

        void tick(Player player, LegendaryElementalContext context) {
            if (impacted) return;
            if (age == 1) {
                Vector velocity =
                        player.getVelocity().setY(Math.sqrt(.16 * settings.range()) + .12);
                if (!context.moveAllowed(player, player, velocity)) {
                    actionDone = true;
                    endAt = age;
                    return;
                }
                player.setVelocity(velocity);
                player.setFallDistance(0);
                player.getWorld()
                        .playSound(
                                player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1, .8f);
            }
            if (!player.isOnGround()) airborne = true;
            if (age > 3 && airborne && player.isOnGround()) {
                impact(player, context);
                return;
            }
            player.setFallDistance(0);
            if (age % 2 == 0) context.dust(player.getLocation(), Color.WHITE, 5);
            if (!descending && !hovering && player.getY() - startY >= settings.range())
                player.setVelocity(player.getVelocity().setY(0));
            if (heaven
                    && !descending
                    && airborne
                    && (player.getVelocity().getY() <= .05
                            || player.getY() - startY >= settings.range())) {
                hovering = true;
                if (!gravityOwned) {
                    player.setGravity(false);
                    gravityOwned = true;
                }
                player.setVelocity(player.getVelocity().setY(0));
                context.ring(player.getLocation(), 1.5, Color.AQUA);
                if (++hoverAge >= 40) descending = true;
            }
            if (descending) {
                restoreGravity(player);
                if (destination == null) {
                    destination = context.groundAim(player, settings.range());
                    if (destination == null) destination = player.getLocation().subtract(0, 1, 0);
                }
                Vector horizontal =
                        destination.toVector().subtract(player.getLocation().toVector()).setY(0);
                if (horizontal.lengthSquared() > 1) horizontal.normalize();
                horizontal.multiply(heaven ? .65 : .35).setY(-1.8);
                if (context.moveAllowed(player, player, horizontal)) player.setVelocity(horizontal);
                else
                    player.setVelocity(
                            new Vector(0, Math.min(-.3, player.getVelocity().getY()), 0));
            }
        }

        void recast(Player player) {
            if (!impacted) {
                descending = true;
                destination = null;
            }
        }

        boolean fallProtected() {
            return !impacted;
        }

        void fall(Player player, LegendaryElementalContext context) {
            if (airborne) impact(player, context);
        }

        private void impact(Player player, LegendaryElementalContext context) {
            if (impacted) return;
            impacted = true;
            restoreGravity(player);
            player.setFallDistance(0);
            Location center = player.getLocation();
            double radius = settings.radius();
            context.ring(center, radius, Color.WHITE);
            context.ring(center, radius * .5, Color.AQUA);
            player.getWorld()
                    .spawnParticle(Particle.GUST, center, 8, radius / 3, .3, radius / 3, 0);
            player.getWorld().playSound(center, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, .5f);
            for (LivingEntity target : context.targets(player, center, radius)) {
                double distance = target.getLocation().distance(center);
                double amount =
                        distance <= radius * .45
                                ? settings.damage()
                                : settings.number("outer-damage", heaven ? 6 : 4, 40);
                if (!hit.add(target.getUniqueId())
                        || !context.damage(player, skill.weapon, target, amount)) continue;
                Vector push = target.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (push.lengthSquared() < .001) push.setX(1);
                push.normalize().multiply(heaven ? 1.05 : .7).setY(heaven ? .35 : .65);
                if (context.moveAllowed(player, target, push)) target.setVelocity(push);
                if (heaven) potion(target, PotionEffectType.LEVITATION, 20, 0);
            }
            endAt = age + (heaven ? 22 : 2);
        }

        private void restoreGravity(Player player) {
            if (gravityOwned && !player.hasGravity()) player.setGravity(previousGravity);
            gravityOwned = false;
        }

        void close(Player player) {
            if (player != null) restoreGravity(player);
            super.close(player);
        }
    }

    private static final class Tornado extends LegendaryAbilityCast {
        Location center;
        final Vector velocity;
        final LegendaryHitBudget budget;

        Tornado(
                Player player,
                LegendaryElementalSkill skill,
                LegendaryElementalSkill.Settings settings) {
            super(player, skill, settings);
            center = player.getLocation().add(0, .2, 0);
            velocity = player.getEyeLocation().getDirection().setY(0);
            if (velocity.lengthSquared() < .001) velocity.setZ(1);
            velocity.normalize().multiply(settings.number("speed", 3, 10) / 20);
            budget = new LegendaryHitBudget(settings.number("max-damage", 6, 40));
        }

        void tick(Player player, LegendaryElementalContext context) {
            Location next = center.clone().add(velocity);
            if (!context.clear(center.clone().add(0, .5, 0), next.clone().add(0, .5, 0))
                    || !context.pathAllowed(
                            player, center, next, LegendaryProtectionEvent.Action.DAMAGE)) {
                actionDone = true;
                endAt = age;
                return;
            }
            center = next;
            for (int index = 0; index < 16; index++) {
                double y = index * .16, angle = age * .4 + index * .7, radius = .3 + y * .35;
                center.getWorld()
                        .spawnParticle(
                                Particle.CLOUD,
                                center.clone()
                                        .add(Math.cos(angle) * radius, y, Math.sin(angle) * radius),
                                1,
                                0,
                                0,
                                0,
                                0);
            }
            for (var target : context.targets(player, center, settings.radius())) {
                Vector pull = center.toVector().subtract(target.getLocation().toVector()).setY(0);
                if (pull.lengthSquared() > .05) pull.normalize().multiply(.18);
                pull.add(velocity).setY(target.getY() < center.getY() + 1.4 ? .15 : -.05);
                if (context.moveAllowed(player, target, pull)) target.setVelocity(pull);
                if ((age - 1) % 20 == 0) {
                    double amount = budget.reserve(target.getUniqueId(), settings.damage());
                    if (amount > 0) context.damage(player, skill.weapon, target, amount);
                }
            }
        }
    }

    private static final class Prison extends LegendaryAbilityCast {
        final Location center;
        final int duration;

        Prison(
                Player player,
                LegendaryElementalSkill skill,
                LegendaryElementalSkill.Settings settings,
                Location center) {
            super(player, skill, settings);
            this.center = center;
            duration = settings.durationTicks();
        }

        void tick(Player player, LegendaryElementalContext context) {
            if (age > duration) return;
            if (age % 3 == 0)
                context.ring(center, settings.radius(), age % 2 == 0 ? Color.WHITE : Color.AQUA);
            for (var target : context.targets(player, center, settings.radius())) {
                if (age % 10 == 1) potion(target, PotionEffectType.SLOWNESS, 12, 1);
                Vector pull = center.toVector().subtract(target.getLocation().toVector()).setY(0);
                if (pull.lengthSquared() > .25) {
                    pull.normalize().multiply(.08).setY(target.getVelocity().getY());
                    Vector velocity =
                            target.getVelocity()
                                    .multiply(.85)
                                    .add(pull)
                                    .setY(target.getVelocity().getY());
                    if (context.moveAllowed(player, target, velocity)) target.setVelocity(velocity);
                }
            }
        }
    }
}
