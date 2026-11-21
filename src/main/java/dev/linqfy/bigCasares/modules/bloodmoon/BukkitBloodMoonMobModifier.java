package dev.linqfy.bigCasares.modules.bloodmoon;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class BukkitBloodMoonMobModifier {
    private final NamespacedKey healthKey;
    private final NamespacedKey speedKey;
    private final NamespacedKey customEntityKey;
    private final BloodMoonMobSettings settings;
    private final BloodMoonMobModifierService service;

    BukkitBloodMoonMobModifier(Plugin plugin, BloodMoonMobSettings settings) {
        Objects.requireNonNull(plugin, "plugin");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.healthKey = new NamespacedKey(plugin, "blood_moon_health");
        this.speedKey = new NamespacedKey(plugin, "blood_moon_speed");
        this.customEntityKey = new NamespacedKey(plugin, "custom_entity");
        this.service = new BloodMoonMobModifierService(
            settings.healthMultiplier(), settings.movementSpeedMultiplier());
    }

    boolean apply(LivingEntity entity) {
        return service.apply(new BukkitStats(entity));
    }

    boolean remove(LivingEntity entity) {
        return service.remove(new BukkitStats(entity));
    }

    boolean isEligible(LivingEntity entity) {
        if (entity == null || entity instanceof Player) {
            return false;
        }
        if (isAlexsCavesMob(entity.getPersistentDataContainer().get(customEntityKey, PersistentDataType.STRING))) {
            return false;
        }
        String enumName = entity.getType().name().toUpperCase(Locale.ROOT);
        String key = entity.getType().getKey().asString().toUpperCase(Locale.ROOT);
        if (matches(settings.blacklist(), enumName, key)) {
            return false;
        }
        if (!settings.whitelist().isEmpty()) {
            return matches(settings.whitelist(), enumName, key);
        }
        return entity instanceof Enemy;
    }

    /** Alex's Caves mobs keep the stats of the original mod, so Blood Moon leaves them alone. */
    static boolean isAlexsCavesMob(String customEntityTag) {
        return customEntityTag != null && customEntityTag.startsWith("alexscaves:");
    }

    private static boolean matches(Set<String> configured, String enumName, String key) {
        return configured.stream()
            .filter(Objects::nonNull)
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .anyMatch(value -> value.equals(enumName) || value.equals(key));
    }

    private final class BukkitStats implements BloodMoonMobStats {
        private final LivingEntity entity;

        private BukkitStats(LivingEntity entity) {
            this.entity = Objects.requireNonNull(entity, "entity");
        }

        @Override
        public boolean hostile() {
            return isEligible(entity);
        }

        @Override
        public boolean player() {
            return entity instanceof Player;
        }

        @Override
        public boolean hasModifier(BloodMoonAttribute attribute) {
            AttributeInstance instance = instance(attribute);
            return instance != null && instance.getModifier(key(attribute)) != null;
        }

        @Override
        public void addMultiplier(BloodMoonAttribute attribute, double multiplier) {
            AttributeInstance instance = instance(attribute);
            if (instance == null || instance.getModifier(key(attribute)) != null) {
                return;
            }
            instance.addModifier(new AttributeModifier(
                key(attribute), multiplier - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }

        @Override
        public void removeModifier(BloodMoonAttribute attribute) {
            AttributeInstance instance = instance(attribute);
            if (instance != null) {
                instance.removeModifier(key(attribute));
            }
        }

        @Override
        public double maxHealth() {
            AttributeInstance instance = entity.getAttribute(Attribute.MAX_HEALTH);
            return instance == null ? entity.getHealth() : instance.getValue();
        }

        @Override
        public double health() {
            return entity.getHealth();
        }

        @Override
        public void health(double health) {
            double clamped = Math.max(0.0, Math.min(maxHealth(), health));
            if (!entity.isDead() && clamped > 0.0) {
                entity.setHealth(clamped);
            }
        }

        private AttributeInstance instance(BloodMoonAttribute attribute) {
            return entity.getAttribute(attribute == BloodMoonAttribute.HEALTH
                ? Attribute.MAX_HEALTH
                : Attribute.MOVEMENT_SPEED);
        }

        private NamespacedKey key(BloodMoonAttribute attribute) {
            return attribute == BloodMoonAttribute.HEALTH ? healthKey : speedKey;
        }
    }
}
