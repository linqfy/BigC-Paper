package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A finite, owner-scoped effect. Cleanup never strips effects installed by another plugin. */
abstract class LegendaryAbilityCast {
    final UUID owner;
    final UUID world;
    final LegendaryElementalSkill skill;
    final LegendaryElementalSkill.Settings settings;
    final Map<UUID, LivingEntity> affected = new HashMap<>();
    final Set<UUID> hit = new HashSet<>();
    final List<Entity> visuals = new ArrayList<>();
    int age;
    int endAt;
    boolean actionDone;
    final LegendaryEffectOwnership effects =
            new LegendaryEffectOwnership(
                    new LegendaryEffectOwnership.Gateway() {
                        private PotionEffectType type(String key) {
                            return PotionEffectType.getByKey(NamespacedKey.fromString(key));
                        }

                        public Optional<LegendaryEffectOwnership.Effect> current(
                                UUID target, String key) {
                            LivingEntity entity = affected.get(target);
                            if (entity == null || !entity.isValid()) return Optional.empty();
                            PotionEffect effect = entity.getPotionEffect(type(key));
                            return effect == null
                                    ? Optional.empty()
                                    : Optional.of(
                                            new LegendaryEffectOwnership.Effect(
                                                    effect.getDuration(),
                                                    effect.getAmplifier(),
                                                    effect.isAmbient(),
                                                    effect.hasParticles(),
                                                    effect.hasIcon()));
                        }

                        public boolean apply(
                                UUID target, String key, LegendaryEffectOwnership.Effect effect) {
                            LivingEntity entity = affected.get(target);
                            return entity != null
                                    && entity.isValid()
                                    && entity.addPotionEffect(
                                            new PotionEffect(
                                                    type(key),
                                                    effect.duration(),
                                                    effect.amplifier(),
                                                    effect.ambient(),
                                                    effect.particles(),
                                                    effect.icon()));
                        }

                        public void remove(UUID target, String key) {
                            LivingEntity entity = affected.get(target);
                            if (entity != null && entity.isValid())
                                entity.removePotionEffect(type(key));
                        }
                    });

    LegendaryAbilityCast(
            Player player,
            LegendaryElementalSkill skill,
            LegendaryElementalSkill.Settings settings) {
        owner = player.getUniqueId();
        world = player.getWorld().getUID();
        this.skill = skill;
        this.settings = settings;
        endAt = settings.durationTicks();
    }

    void potion(LivingEntity target, PotionEffectType type, int ticks, int amplifier) {
        affected.put(target.getUniqueId(), target);
        effects.apply(
                target.getUniqueId(),
                type.getKey().toString(),
                new LegendaryEffectOwnership.Effect(ticks, amplifier, false, true, true),
                age);
        endAt = Math.max(endAt, age + ticks);
    }

    abstract void tick(Player player, LegendaryElementalContext context);

    void close(Player player) {
        effects.cleanup(age);
        visuals.forEach(Entity::remove);
    }

    void targetGone(UUID target) {
        effects.forget(target);
        affected.remove(target);
    }

    boolean fallProtected() {
        return false;
    }

    void fall(Player player, LegendaryElementalContext context) {}

    void recast(Player player) {}
}
