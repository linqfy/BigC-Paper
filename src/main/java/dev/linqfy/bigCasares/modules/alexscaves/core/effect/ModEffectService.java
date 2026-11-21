package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings.EffectSettings;
import dev.linqfy.bigCasares.modules.alexscaves.core.damage.ModDamageTypes;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToIntFunction;

/**
 * Bukkit side of the module effects: ticks the ledger, applies their gameplay
 * and shows them in the action bar. Effects of offline players are stored in
 * their PDC and restored on join.
 */
public final class ModEffectService {
    private static final String STUN_MODIFIER = "alexscaves_stunned";

    private final Plugin plugin;
    private final EffectSettings settings;
    private final ModDamageTypes damageTypes;
    private final ToIntFunction<LivingEntity> hazmatPieces;
    private final ModEffectLedger ledger = new ModEffectLedger();
    private final NamespacedKey storageKey;
    private final NamespacedKey stunKey;
    private long tick;

    public ModEffectService(Plugin plugin, EffectSettings settings, ModDamageTypes damageTypes,
                            ToIntFunction<LivingEntity> hazmatPieces) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.settings = settings;
        this.damageTypes = damageTypes;
        this.hazmatPieces = hazmatPieces;
        this.storageKey = new NamespacedKey(plugin, "alexscaves_effects");
        this.stunKey = new NamespacedKey(plugin, STUN_MODIFIER);
    }

    public boolean apply(LivingEntity entity, ModEffectType type, int amplifier, int durationTicks) {
        if (type == ModEffectType.IRRADIATED && !settings.irradiatedEnabled()) {
            return false;
        }
        boolean had = ledger.has(entity.getUniqueId(), type);
        boolean applied = ledger.apply(entity.getUniqueId(), type, amplifier, durationTicks);
        if (applied && type == ModEffectType.STUNNED) {
            addStun(entity);
        }
        if (applied && !had && type == ModEffectType.DARKNESS_INCARNATE) {
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:darkness_incarnate_enter", SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        return applied;
    }

    public boolean has(Entity entity, ModEffectType type) {
        return ledger.has(entity.getUniqueId(), type);
    }

    public Optional<ModEffectLedger.ActiveEffect> get(Entity entity, ModEffectType type) {
        return ledger.get(entity.getUniqueId(), type);
    }

    public void remove(LivingEntity entity, ModEffectType type) {
        if (ledger.remove(entity.getUniqueId(), type)) {
            onExpired(entity, type);
        }
    }

    public void cureWithMilk(LivingEntity entity) {
        for (ModEffectType type : ledger.cureWithMilk(entity.getUniqueId())) {
            onExpired(entity, type);
        }
    }

    /** Runs every tick. */
    public void tick() {
        tick++;
        Set<UUID> loaded = new HashSet<>();
        for (UUID id : ledger.entities()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                continue;
            }
            loaded.add(id);
            for (ModEffectLedger.ActiveEffect effect : ledger.effectsOf(id)) {
                applyTick(living, effect);
            }
        }
        for (ModEffectLedger.ExpiredEffect expired : ledger.tick(1, loaded::contains)) {
            if (plugin.getServer().getEntity(expired.entity()) instanceof LivingEntity living) {
                onExpired(living, expired.effect().type());
            }
        }
        if (settings.hud() && tick % 20 == 0) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                showHud(player);
            }
        }
    }

    private void applyTick(LivingEntity entity, ModEffectLedger.ActiveEffect effect) {
        if (effect.type() == ModEffectType.DARKNESS_INCARNATE) {
            tickDarknessIncarnate(entity);
            return;
        }
        if (effect.type() == ModEffectType.IRRADIATED) {
            if (!RadiationFormula.procsOnTick(effect.elapsedTicks(), effect.amplifier())) {
                return;
            }
            int pieces = hazmatPieces.applyAsInt(entity);
            if (entity instanceof Player player && RadiationFormula.causesExhaustion(pieces)) {
                player.setExhaustion(player.getExhaustion() + RadiationFormula.UNPROTECTED_EXHAUSTION);
            }
            if (ThreadLocalRandom.current().nextDouble() < RadiationFormula.damageChance(pieces)) {
                double damage = RadiationFormula.damage(pieces);
                if (damage > 0) {
                    entity.damage(damage, damageTypes.source(ModDamageTypes.RADIATION));
                }
            }
            entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, entity.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0);
        }
    }

    /**
     * Mod {@code DarknessIncarnateEffect.applyEffectTick} and {@code CommonEvents.livingTick}: flight, a lift off the
     * ground, an idle sound about every 50 ticks, and the end of the effect in the light (checked every 5 ticks).
     */
    private void tickDarknessIncarnate(LivingEntity entity) {
        DarknessIncarnate.toggleFlight(entity, true, false);
        if (entity.isOnGround()) {
            entity.setVelocity(entity.getVelocity().add(new Vector(0.0, 0.1, 0.0)));
        }
        int age = entity.getTicksLived();
        if ((age + entity.getEntityId() * 5) % 50 == 0 && ThreadLocalRandom.current().nextInt(2) == 0) {
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:darkness_incarnate_idle", SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        if (age % 5 == 0 && DarknessIncarnate.isInLight(entity, DarknessIncarnate.LIGHT_THRESHOLD)) {
            remove(entity, ModEffectType.DARKNESS_INCARNATE);
        }
    }

    private void onExpired(LivingEntity entity, ModEffectType type) {
        if (type == ModEffectType.STUNNED) {
            removeStun(entity);
        }
        if (type == ModEffectType.DARKNESS_INCARNATE) {
            DarknessIncarnate.toggleFlight(entity, false, true);
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:darkness_incarnate_exit", SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }

    private void addStun(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null || speed.getModifier(stunKey) != null) {
            return;
        }
        // Mod: MULTIPLY_BASE -1 on movement speed.
        speed.addModifier(new AttributeModifier(stunKey, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void removeStun(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(stunKey) != null) {
            speed.removeModifier(stunKey);
        }
    }

    private void showHud(Player player) {
        List<ModEffectLedger.ActiveEffect> effects = ledger.effectsOf(player.getUniqueId());
        if (effects.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (ModEffectLedger.ActiveEffect effect : effects) {
            if (!text.isEmpty()) {
                text.append("  ");
            }
            text.append(effect.type().harmful() ? "§c" : "§b").append(effect.type().displayName());
            if (effect.amplifier() > 0) {
                text.append(' ').append(roman(effect.amplifier() + 1));
            }
            int seconds = effect.remainingTicks() / 20;
            text.append(" §7").append(seconds / 60).append(':').append(String.format("%02d", seconds % 60));
        }
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text.toString()));
    }

    public void save(Player player) {
        List<ModEffectLedger.ActiveEffect> effects = ledger.effectsOf(player.getUniqueId());
        if (effects.isEmpty()) {
            player.getPersistentDataContainer().remove(storageKey);
        } else {
            player.getPersistentDataContainer().set(storageKey, PersistentDataType.STRING, ModEffectCodec.encode(effects));
        }
    }

    public void load(Player player) {
        // The modifier lives in the entity NBT; after a crash it may outlive the stored effect.
        removeStun(player);
        String stored = player.getPersistentDataContainer().get(storageKey, PersistentDataType.STRING);
        // The ledger owns the effects from here on; a copy left in the PDC would revive them after a crash.
        player.getPersistentDataContainer().remove(storageKey);
        for (ModEffectLedger.ActiveEffect effect : ModEffectCodec.decode(stored)) {
            ledger.restore(player.getUniqueId(), effect);
            if (effect.type() == ModEffectType.STUNNED) {
                addStun(player);
            }
        }
        // A crash while flying leaves the borrowed flight behind: put it back when the effect is no longer running.
        if (!ledger.has(player.getUniqueId(), ModEffectType.DARKNESS_INCARNATE) && DarknessIncarnate.hasStoredFlight(player)) {
            DarknessIncarnate.restoreFlight(player);
        }
    }

    /** Removes a stun modifier left in the NBT of a mob that unloaded while stunned. */
    public void clearStaleStun(LivingEntity entity) {
        if (!ledger.has(entity.getUniqueId(), ModEffectType.STUNNED)) {
            removeStun(entity);
        }
    }

    public void forget(Entity entity) {
        if (entity instanceof LivingEntity living) {
            removeStun(living);
            if (ledger.has(entity.getUniqueId(), ModEffectType.DARKNESS_INCARNATE)) {
                DarknessIncarnate.toggleFlight(living, false, false);
            }
        }
        ledger.forget(entity.getUniqueId());
    }

    /** Saves online players and removes transient modifiers; called on disable. */
    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            save(player);
            removeStun(player);
            if (ledger.has(player.getUniqueId(), ModEffectType.DARKNESS_INCARNATE)) {
                // The stored effect brings the flight back on the next join; do not leave it on without one.
                DarknessIncarnate.toggleFlight(player, false, false);
            }
        }
        for (UUID id : ledger.entities()) {
            if (plugin.getServer().getEntity(id) instanceof LivingEntity living) {
                removeStun(living);
            }
        }
    }

    static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(value);
        };
    }
}
