package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class LegendaryPotionEffects {
    private final Map<UUID, LivingEntity> targets = new HashMap<>();
    private final LegendaryEffectOwnership leases =
            new LegendaryEffectOwnership(
                    new LegendaryEffectOwnership.Gateway() {
                        private PotionEffectType type(String key) {
                            return PotionEffectType.getByKey(NamespacedKey.fromString(key));
                        }

                        public Optional<LegendaryEffectOwnership.Effect> current(
                                UUID target, String key) {
                            LivingEntity entity = targets.get(target);
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
                            LivingEntity entity = targets.get(target);
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
                            LivingEntity entity = targets.get(target);
                            if (entity != null && entity.isValid())
                                entity.removePotionEffect(type(key));
                        }
                    });

    void apply(LivingEntity target, PotionEffectType type, int ticks, int amplifier, int age) {
        targets.put(target.getUniqueId(), target);
        leases.apply(
                target.getUniqueId(),
                type.getKey().toString(),
                new LegendaryEffectOwnership.Effect(ticks, amplifier, false, true, true),
                age);
    }

    void prune(int age) {
        leases.prune(age);
    }

    void cleanup(int age) {
        leases.cleanup(age);
        targets.clear();
    }

    void forget(UUID target) {
        leases.forget(target);
        targets.remove(target);
    }
}
