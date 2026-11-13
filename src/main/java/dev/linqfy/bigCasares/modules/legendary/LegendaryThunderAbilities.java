package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The five electrical abilities use visual bolts; damage is dispatched separately through Paper.
 */
final class LegendaryThunderAbilities {
    private LegendaryThunderAbilities() {}

    static LegendaryAbilityCast create(
            Player player,
            LegendaryElementalSkill skill,
            LegendaryElementalSkill.Settings settings,
            LegendaryElementalContext context) {
        return switch (skill) {
            case LIGHTNING_DASH -> new Dash(player, skill, settings, false);
            case THUNDER_MARK -> new Mark(player, skill, settings);
            case CHAIN_LIGHTNING -> {
                var ray = context.ray(player, settings.range());
                yield ray != null && ray.getHitEntity() instanceof LivingEntity first
                        ? new Chain(player, skill, settings, first)
                        : null;
            }
            case THUNDERSTORM -> {
                Location center = context.groundAim(player, settings.range());
                yield center == null ? null : new Storm(player, skill, settings, center);
            }
            case WRATH_OF_ZEUS -> new Zeus(player, skill, settings);
            default -> throw new IllegalArgumentException("Not a thunder skill");
        };
    }

    static final class Dash extends LegendaryAbilityCast {
        final Vector direction;
        final boolean wind;
        Location previous;
        double travelled;
        final LegendaryFlightWindow flight = new LegendaryFlightWindow();

        Dash(
                Player player,
                LegendaryElementalSkill skill,
                LegendaryElementalSkill.Settings settings,
                boolean wind) {
            super(player, skill, settings);
            this.wind = wind;
            direction = player.getEyeLocation().getDirection();
            if (!wind) direction.setY(0);
            if (direction.lengthSquared() < .001) direction.setZ(1);
            direction.normalize();
            previous = player.getLocation();
            endAt = wind ? 200 : settings.durationTicks() + 40;
        }

        void tick(Player player, LegendaryElementalContext context) {
            if (flight.update(age, player.isOnGround(), actionDone) && wind) {
                actionDone = true;
                endAt = age + 1;
            }
            if (actionDone) return;
            Location here = player.getLocation();
            travelled += here.distance(previous);
            previous = here;
            double speed = Math.min(1.5, settings.range() - travelled);
            if (speed <= .05
                    || age > settings.durationTicks()
                    || !context.moveAllowed(player, player, direction.clone().multiply(speed))) {
                actionDone = true;
                player.setVelocity(new Vector(0, Math.min(0, player.getVelocity().getY()), 0));
                return;
            }
            player.setVelocity(direction.clone().multiply(speed));
            if (wind) player.setFallDistance(0);
            context.dust(here.clone().add(0, 1, 0), wind ? Color.WHITE : Color.AQUA, 8);
            if (!wind)
                for (var target :
                        context.targets(
                                player,
                                here.clone().add(direction.clone().multiply(speed / 2)),
                                1.4)) {
                    if (hit.add(target.getUniqueId())
                            && context.damage(player, skill.weapon, target, settings.damage()))
                        potion(target, PotionEffectType.SLOWNESS, 40, 0);
                }
        }

        boolean fallProtected() {
            return wind && flight.protectsFall();
        }

        void fall(Player player, LegendaryElementalContext context) {
            flight.land();
            actionDone = true;
            endAt = age + 1;
        }
    }

    static final class Mark extends LegendaryAbilityCast {
        UUID marked;
        int charges;
        LivingEntity discharge;
        boolean consumed;

        Mark(
                Player player,
                LegendaryElementalSkill skill,
                LegendaryElementalSkill.Settings settings) {
            super(player, skill, settings);
        }

        void melee(LivingEntity target) {
            if (consumed || age >= settings.durationTicks()) return;
            if (marked == null) marked = target.getUniqueId();
            if (!marked.equals(target.getUniqueId())) return;
            if (++charges >= 3) {
                consumed = true;
                discharge = target;
            }
        }

        void tick(Player player, LegendaryElementalContext context) {
            if (age % 5 == 0) context.dust(player.getLocation().add(0, 1, 0), Color.AQUA, 3);
            if (discharge == null) return;
            LivingEntity main = discharge;
            discharge = null;
            if (!main.isValid()
                    || main.isDead()
                    || !main.getWorld().equals(player.getWorld())
                    || !context.allowed(player, main)
                    || !context.clear(player.getEyeLocation(), main.getEyeLocation())) return;
            Location center = main.getLocation();
            context.lightning(player, center);
            context.damage(player, skill.weapon, main, settings.damage());
            for (var target : context.targets(player, center, settings.radius()))
                if (!target.equals(main))
                    context.damage(
                            player,
                            skill.weapon,
                            target,
                            settings.number("secondary-damage", 3, 40));
        }
    }

    private static final class Chain extends LegendaryAbilityCast {
        final LivingEntity first;

        Chain(
                Player player,
                LegendaryElementalSkill skill,
                LegendaryElementalSkill.Settings settings,
                LivingEntity first) {
            super(player, skill, settings);
            this.first = first;
        }

        void tick(Player player, LegendaryElementalContext context) {
            if (actionDone) return;
            actionDone = true;
            LivingEntity target = first;
            Location from = player.getEyeLocation();
            for (int jump = 0; jump < 4 && target != null; jump++) {
                if (!target.isValid()
                        || !target.getWorld().equals(player.getWorld())
                        || !context.allowed(player, target)
                        || !context.clear(from, target.getEyeLocation())
                        || !context.pathAllowed(
                                player,
                                from,
                                target.getLocation(),
                                LegendaryProtectionEvent.Action.DAMAGE)) break;
                hit.add(target.getUniqueId());
                context.line(from, target.getEyeLocation(), Color.AQUA);
                context.lightning(player, target.getLocation());
                context.damage(
                        player,
                        skill.weapon,
                        target,
                        LegendaryElementalSkill.chainDamage(settings.damage(), jump));
                from = target.getEyeLocation();
                List<LivingEntity> nearby =
                        new ArrayList<>(
                                context.targets(player, target.getLocation(), settings.radius()));
                target =
                        nearby.stream()
                                .filter(candidate -> !hit.contains(candidate.getUniqueId()))
                                .findFirst()
                                .orElse(null);
            }
        }
    }

    private static final class Storm extends LegendaryAbilityCast {
        final Location center;

        Storm(
                Player player,
                LegendaryElementalSkill skill,
                LegendaryElementalSkill.Settings settings,
                Location center) {
            super(player, skill, settings);
            this.center = center;
        }

        void tick(Player player, LegendaryElementalContext context) {
            if (age % 5 == 0) {
                context.ring(center, settings.radius(), Color.AQUA);
                center.getWorld()
                        .spawnParticle(
                                Particle.CLOUD,
                                center.clone().add(0, 4, 0),
                                6,
                                settings.radius() / 2,
                                .2,
                                settings.radius() / 2,
                                0);
            }
            if ((age - 1) % 40 != 0 || age > settings.durationTicks()) return;
            var targets = context.targets(player, center, settings.radius());
            if (targets.isEmpty()) return;
            LivingEntity target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
            if (!context.clear(target.getEyeLocation(), target.getEyeLocation().add(0, 4, 0)))
                return;
            context.lightning(player, target.getLocation());
            context.damage(player, skill.weapon, target, settings.damage());
        }
    }

    private static final class Zeus extends LegendaryAbilityCast {
        Zeus(
                Player player,
                LegendaryElementalSkill skill,
                LegendaryElementalSkill.Settings settings) {
            super(player, skill, settings);
            endAt = settings.durationTicks() + 61;
        }

        void tick(Player player, LegendaryElementalContext context) {
            if (actionDone) return;
            context.dust(player.getEyeLocation(), age % 2 == 0 ? Color.YELLOW : Color.AQUA, 6);
            if (age < settings.durationTicks()) return;
            actionDone = true;
            var ray = context.ray(player, settings.range());
            if (ray == null || !(ray.getHitEntity() instanceof LivingEntity main)) return;
            context.line(player.getEyeLocation(), main.getEyeLocation(), Color.YELLOW);
            context.lightning(player, main.getLocation());
            if (context.damage(player, skill.weapon, main, settings.damage()))
                potion(main, PotionEffectType.SLOWNESS, 60, 1);
            for (var target : context.targets(player, main.getLocation(), settings.radius()))
                if (!target.equals(main)
                        && context.damage(
                                player,
                                skill.weapon,
                                target,
                                settings.number("secondary-damage", 5, 40)))
                    potion(target, PotionEffectType.SLOWNESS, 60, 1);
        }
    }
}
