package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Per-instance logic of a module mob (abilities, special movement, animation
 * choice). Ticked every few ticks by {@link ModMobService} while the entity is
 * loaded; the base vanilla AI handles plain pathing.
 */
public interface ModMobBehaviour {
    /** @param ticks ticks since the last call */
    default void tick(LivingEntity entity, ModMobContext context, int ticks) {
    }

    default void onDamaged(LivingEntity entity, ModMobContext context, EntityDamageEvent event) {
    }

    default void onAttack(LivingEntity entity, ModMobContext context, LivingEntity target) {
    }

    /**
     * Sound event to play for a mob sound ({@code idle}, {@code hurt}, {@code death}) as {@code alexscaves:<mob>_<result>},
     * for mobs with variants (the Hullbreaker's land sounds); null stays silent.
     */
    default String soundSuffix(LivingEntity entity, String suffix) {
        return suffix;
    }

    /** Volume of the mob's idle, hurt and death sounds (the mod's {@code getSoundVolume}); above 1 carries farther. */
    default float soundVolume() {
        return 1.0F;
    }

    /** Experience dropped on death, given the base mob's own ({@code vanilla}). */
    default int experience(LivingEntity entity, int vanilla) {
        return vanilla;
    }

    /** Last say on the drops of a dying mob, after the loot table: a mob that already dropped its loot clears them. */
    /**
     * Animation the model plays when the mob dies (the body stays about 20 ticks, as in vanilla, before it is removed),
     * or null when the behaviour already played its own death sequence.
     */
    default String deathAnimation(LivingEntity entity) {
        return "death";
    }

    default void onDeathDrops(LivingEntity entity, java.util.List<org.bukkit.inventory.ItemStack> drops) {
    }

    /**
     * Called once when the entity dies or unloads; release anything the behaviour spawned.
     * {@code entity} is null when it was already gone from the world.
     */
    default void close(LivingEntity entity, ModMobContext context) {
    }

    @FunctionalInterface
    interface Factory {
        ModMobBehaviour create(LivingEntity entity, ModMobType type);
    }

    ModMobBehaviour NONE = new ModMobBehaviour() {
    };
}
