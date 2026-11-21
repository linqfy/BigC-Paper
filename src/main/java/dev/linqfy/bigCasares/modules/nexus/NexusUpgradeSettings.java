package dev.linqfy.bigCasares.modules.nexus;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ajustes de las mejoras de base.
 *
 * @param prices          precio en la economía de Vault de cada mejora
 * @param intervalTicks   cada cuánto se aplican los efectos
 * @param repulsorRadius  radio en bloques del repulsor de mobs hostiles
 * @param repulsorStrength velocidad horizontal con la que se empuja a los mobs
 * @param saturationRadius radio en bloques del aura de saturación
 * @param foodPerPulse    puntos de hambre que recupera cada pulso del aura
 */
public record NexusUpgradeSettings(
    Map<NexusUpgrade, Double> prices,
    long intervalTicks,
    double repulsorRadius,
    double repulsorStrength,
    double saturationRadius,
    int foodPerPulse
) {

    public NexusUpgradeSettings {
        EnumMap<NexusUpgrade, Double> copy = new EnumMap<>(NexusUpgrade.class);
        for (NexusUpgrade upgrade : NexusUpgrade.values()) {
            double price = prices.getOrDefault(upgrade, 0.0);
            if (!Double.isFinite(price) || price < 0.0) {
                throw new IllegalArgumentException("Precio inválido para " + upgrade.id());
            }
            copy.put(upgrade, price);
        }
        prices = Map.copyOf(copy);
        if (intervalTicks <= 0L) {
            throw new IllegalArgumentException("interval-ticks debe ser mayor a cero.");
        }
        if (!(repulsorRadius > 0.0) || !(saturationRadius > 0.0) || repulsorRadius > 64.0 || saturationRadius > 64.0) {
            throw new IllegalArgumentException("Los radios de las mejoras deben estar entre 0 y 64.");
        }
        if (!(repulsorStrength >= 0.0) || foodPerPulse < 0) {
            throw new IllegalArgumentException("La fuerza del repulsor y la comida por pulso no pueden ser negativas.");
        }
    }

    public static NexusUpgradeSettings defaults() {
        return new NexusUpgradeSettings(
            Map.of(NexusUpgrade.MOB_REPULSOR, 25000.0, NexusUpgrade.SATURATION_AURA, 15000.0),
            40L, 24.0, 0.8, 16.0, 1);
    }

    public static NexusUpgradeSettings fromConfig(ConfigurationSection root) {
        NexusUpgradeSettings defaults = defaults();
        if (root == null) {
            return defaults;
        }
        String base = "nexus-system.upgrades.";
        try {
            EnumMap<NexusUpgrade, Double> prices = new EnumMap<>(NexusUpgrade.class);
            for (NexusUpgrade upgrade : NexusUpgrade.values()) {
                prices.put(upgrade, root.getDouble(base + upgrade.id() + ".price", defaults.price(upgrade)));
            }
            return new NexusUpgradeSettings(
                prices,
                root.getLong(base + "interval-ticks", defaults.intervalTicks()),
                root.getDouble(base + "repulsor.radius", defaults.repulsorRadius()),
                root.getDouble(base + "repulsor.push-strength", defaults.repulsorStrength()),
                root.getDouble(base + "saturacion.radius", defaults.saturationRadius()),
                root.getInt(base + "saturacion.food-per-pulse", defaults.foodPerPulse()));
        } catch (IllegalArgumentException invalid) {
            return defaults;
        }
    }

    public double price(NexusUpgrade upgrade) {
        return prices.getOrDefault(upgrade, 0.0);
    }

    /** Vector horizontal (x, z) que aleja del Nexus un mob en el desplazamiento (dx, dz). */
    public static double[] pushAway(double dx, double dz, double strength) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-6) {
            return new double[] {strength, 0.0};
        }
        return new double[] {dx / length * strength, dz / length * strength};
    }

    /** Nuevo nivel de hambre y saturación tras un pulso del aura; la saturación nunca supera al hambre. */
    public static float[] feed(int foodLevel, float saturation, int foodPerPulse) {
        int food = Math.min(20, Math.max(0, foodLevel) + foodPerPulse);
        float sat = Math.min(food, Math.max(0.0f, saturation) + foodPerPulse);
        return new float[] {food, sat};
    }
}
