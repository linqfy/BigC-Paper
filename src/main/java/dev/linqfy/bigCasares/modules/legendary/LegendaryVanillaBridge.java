package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.damage.DamageSource;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** Narrow 26.2 adapter: reuse native enchantment rolls and mace hooks without exposing a native held item. */
final class LegendaryVanillaBridge {
    private final Object mace;
    private final Method entityHandle, worldHandle, menuHandle, sourceHandle, stackCopy;
    private final Method registryAccess, enchantmentList, enchantment, enchantmentLevel, bukkitEnchantment;
    private final Method rename, maceBonus, maceHit, maceAfterHit;
    private final Method strength, baseScale, movement, horizontalDistance, speed, sweep;

    LegendaryVanillaBridge() {
        try {
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> living = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> stack = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> source = Class.forName("net.minecraft.world.damagesource.DamageSource");
            Class<?> registry = Class.forName("net.minecraft.core.RegistryAccess");
            entityHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftLivingEntity").getMethod("getHandle");
            worldHandle = Class.forName("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
            menuHandle = Class.forName("org.bukkit.craftbukkit.inventory.CraftInventoryView").getMethod("getHandle");
            sourceHandle = Class.forName("org.bukkit.craftbukkit.damage.CraftDamageSource").getMethod("getHandle");
            stackCopy = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
                    .getMethod("asNMSCopy", ItemStack.class);
            registryAccess = Class.forName("net.minecraft.world.level.Level").getMethod("registryAccess");
            enchantmentList = Class.forName("net.minecraft.world.inventory.EnchantmentMenu")
                    .getDeclaredMethod("getEnchantmentList", registry, stack, int.class, int.class);
            enchantmentList.setAccessible(true);
            Class<?> instance = Class.forName("net.minecraft.world.item.enchantment.EnchantmentInstance");
            enchantment = instance.getMethod("enchantment");
            enchantmentLevel = instance.getMethod("level");
            bukkitEnchantment = Class.forName("org.bukkit.craftbukkit.enchantments.CraftEnchantment")
                    .getMethod("minecraftHolderToBukkit", Class.forName("net.minecraft.core.Holder"));
            rename = Class.forName("net.minecraft.world.inventory.AnvilMenu").getMethod("setItemName", String.class);
            mace = Class.forName("net.minecraft.world.item.Items").getField("MACE").get(null);
            Class<?> maceType = Class.forName("net.minecraft.world.item.MaceItem");
            maceBonus = maceType.getMethod("getAttackDamageBonus", entity, float.class, source);
            maceHit = maceType.getMethod("hurtEnemy", stack, living, living);
            maceAfterHit = maceType.getMethod("postHurtEnemy", stack, living, living);
            Class<?> player = Class.forName("net.minecraft.world.entity.player.Player");
            strength = player.getMethod("getAttackStrengthScale", float.class);
            baseScale = player.getDeclaredMethod("baseDamageScaleFactor");
            baseScale.setAccessible(true);
            movement = entity.getMethod("getKnownMovement");
            horizontalDistance = Class.forName("net.minecraft.world.phys.Vec3").getMethod("horizontalDistanceSqr");
            speed = living.getMethod("getSpeed");
            sweep = player.getDeclaredMethod("doSweepAttack", entity, float.class, source, float.class);
            sweep.setAccessible(true);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("No se pudo conectar el adaptador de armas de Paper 26.2", failure);
        }
    }

    Map<Enchantment, Integer> roll(InventoryView mirror, ItemStack family, int button, int cost) {
        Object level = call(worldHandle, mirror.getPlayer().getWorld());
        Object registry = call(registryAccess, level);
        var rolled = (Iterable<?>) call(enchantmentList, call(menuHandle, mirror), registry,
                call(stackCopy, null, family), button, cost);
        var result = new LinkedHashMap<Enchantment, Integer>();
        for (Object entry : rolled) {
            result.put((Enchantment) call(bukkitEnchantment, null, call(enchantment, entry)),
                    (int) call(enchantmentLevel, entry));
        }
        return result;
    }

    void rename(InventoryView mirror, String name) {
        if (name != null) call(rename, call(menuHandle, mirror), name);
    }

    double smashBonus(LivingEntity victim, DamageSource source) {
        return (float) call(maceBonus, mace, call(entityHandle, victim), 0.0F, call(sourceHandle, source));
    }

    void smashEffects(ItemStack weapon, LivingEntity victim, LivingEntity attacker) {
        call(maceHit, mace, call(stackCopy, null, weapon), call(entityHandle, victim), call(entityHandle, attacker));
    }

    void finishSmash(ItemStack weapon, LivingEntity victim, LivingEntity attacker) {
        call(maceAfterHit, mace, call(stackCopy, null, weapon), call(entityHandle, victim), call(entityHandle, attacker));
    }

    float attackStrength(LivingEntity player) {
        return (float) call(strength, call(entityHandle, player), 0.5F);
    }

    float baseDamageScale(LivingEntity player) {
        return (float) call(baseScale, call(entityHandle, player));
    }

    boolean slowEnoughToSweep(LivingEntity player) {
        Object handle = call(entityHandle, player);
        double distance = (double) call(horizontalDistance, call(movement, handle));
        double limit = (float) call(speed, handle) * 2.5;
        return distance < limit * limit;
    }

    void sweep(LivingEntity attacker, LivingEntity victim, float damage, DamageSource source, float charge) {
        call(sweep, call(entityHandle, attacker), call(entityHandle, victim), damage, call(sourceHandle, source), charge);
    }

    private static Object call(Method method, Object receiver, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
            throw new IllegalStateException("Falló el adaptador de armas: " + method.getName(), cause);
        }
    }
}
