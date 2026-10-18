package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.dimension.runtime.DimensionExtension;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Runtime behaviour of The Sift: Ichor contact effects, tide effects, creature
 * conversion and the Java-side decoration populator.
 *
 * <p>Ichor is water inside the Sift (biome-tinted cyan). Like the mod it grants
 * Regeneration I on contact and does not form infinite sources (game rule).
 * Tide effects are original interpretations of the Flow/Endure/Thrive cycle.</p>
 */
final class SiftRuntime implements DimensionExtension, Listener {

    private static final long PULSE_TICKS = 20L;

    private final JavaPlugin plugin;
    private final SiftSettings settings;
    private final java.util.function.Supplier<List<SiftWorldgen.CustomPlacement>> customPlacements;
    private final dev.linqfy.bigCasares.modules.sift.feature.SiftFeatures features;
    private final SiftCreatures creatures;
    private final SiftRifts rifts;
    private final SiftSifters sifters;
    private final SiftIchorSnowfall snowfall;
    private final SiftSingers singers;
    private final SiftEchoGolems golems;
    private final SiftDarkSniffers darkSniffers;
    private final java.util.function.BiFunction<String, Integer, java.util.Optional<org.bukkit.inventory.ItemStack>> items;
    private final org.bukkit.NamespacedKey endureHealthKey;
    private volatile String tide = "flow";
    private ActiveDimension dimension;

    SiftRuntime(JavaPlugin plugin, SiftSettings settings, java.util.function.Supplier<List<SiftWorldgen.CustomPlacement>> customPlacements,
                dev.linqfy.bigCasares.modules.sift.feature.SiftFeatures features, SiftCreatures creatures, SiftRifts rifts,
                SiftSifters sifters, SiftSingers singers, SiftEchoGolems golems, SiftDarkSniffers darkSniffers,
                java.util.function.BiFunction<String, Integer, java.util.Optional<org.bukkit.inventory.ItemStack>> items,
                SiftIchorSnowfall snowfall) {
        this.items = items;
        this.snowfall = snowfall;
        this.plugin = plugin;
        this.settings = settings;
        this.customPlacements = customPlacements;
        this.features = features;
        this.creatures = creatures;
        this.rifts = rifts;
        this.sifters = sifters;
        this.singers = singers;
        this.golems = golems;
        this.darkSniffers = darkSniffers;
        this.endureHealthKey = new org.bukkit.NamespacedKey(plugin, "sift_endure_health");
    }

    @Override
    public void onActivate(ActiveDimension active) {
        this.dimension = active;
        active.registrations().registerListener("sift-runtime-listener", this);
        if (settings.blubs()) {
            creatures.activate(active);
        }
        if (settings.rifts()) {
            rifts.activate(active);
        }
        if (settings.sifters()) {
            sifters.activate(active);
        }
        if (settings.darkSniffers()) {
            darkSniffers.activate(active);
        }
        if (settings.singers()) {
            singers.activate(active);
        }
        if (settings.echoGolems()) {
            golems.activate(active);
        }
        snowfall.activate(active);
        active.snapshot(SiftDimension.TIDES).ifPresent(snapshot -> tide = snapshot.phase().id());
    }

    @Override
    public void onDeactivate(ActiveDimension active) {
        this.dimension = null;
    }

    @Override
    public List<BlockPopulator> populators(ActiveDimension active) {
        return List.of(new SiftPopulator(customPlacements.get(), features, items, active.logger()));
    }

    @Override
    public long pulseIntervalTicks() {
        return PULSE_TICKS;
    }

    @Override
    public void onPulse(ActiveDimension active, Collection<Player> players) {
        for (Player player : players) {
            if (settings.ichor().regeneration() && touchesIchor(player)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 70, 0, true, true, true));
                if (settings.ichor().slowness()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, true, false, false));
                }
            }
            if ("thrive".equals(tide)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 70, 0, true, false, true));
            }
        }
    }

    /** Ichor is the water of this dimension; touching it means feet or eyes in water. */
    static boolean touchesIchor(Player player) {
        Block feet = player.getLocation().getBlock();
        return feet.getType() == Material.WATER || feet.getType() == Material.BUBBLE_COLUMN
            || player.getEyeLocation().getBlock().getType() == Material.WATER;
    }

    @Override
    public void onPhaseChange(ActiveDimension active, EnvironmentCycle cycle, EnvironmentCycle.Snapshot from,
                              EnvironmentCycle.Snapshot to) {
        if (!cycle.id().equals(SiftDimension.TIDES)) {
            return;
        }
        tide = to.phase().id();
        if (from == null) {
            return;
        }
        Component title = switch (tide) {
            case "endure" -> Component.text("Marea: Resistir", NamedTextColor.LIGHT_PURPLE);
            case "thrive" -> Component.text("Marea: Prosperar", NamedTextColor.GOLD);
            default -> Component.text("Marea: Fluir", NamedTextColor.AQUA);
        };
        Component subtitle = switch (tide) {
            case "endure" -> Component.text("Las criaturas se endurecen y no regenerás de forma natural", NamedTextColor.GRAY);
            case "thrive" -> Component.text("Regenerás más rápido y excavás con prisa", NamedTextColor.GRAY);
            default -> Component.text("La Grieta vuelve a la calma", NamedTextColor.GRAY);
        };
        Title shown = Title.title(title, subtitle,
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(800)));
        for (Player player : active.players()) {
            player.showTitle(shown);
        }
    }

    private boolean inSift(World world) {
        ActiveDimension active = dimension;
        return active != null && active.world().getUID().equals(world.getUID());
    }

    /** Endure: no passive regeneration. Thrive: natural regeneration is 50% stronger. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || !inSift(player.getWorld())) {
            return;
        }
        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        if (reason != EntityRegainHealthEvent.RegainReason.SATIATED && reason != EntityRegainHealthEvent.RegainReason.REGEN) {
            return;
        }
        if ("endure".equals(tide) && reason == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        } else if ("thrive".equals(tide)) {
            event.setAmount(event.getAmount() * 1.5);
        }
    }

    /** Endure: hostile creatures spawning in the Sift get 25% more health. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (!inSift(entity.getWorld()) || !"endure".equals(tide) || !(entity instanceof Enemy)) {
            return;
        }
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health == null || health.getModifier(endureHealthKey) != null) {
            return;
        }
        health.addModifier(new AttributeModifier(endureHealthKey, 0.25, AttributeModifier.Operation.MULTIPLY_SCALAR_1,
            EquipmentSlotGroup.ANY));
        entity.setHealth(health.getValue());
    }

    Optional<ActiveDimension> active() {
        return Optional.ofNullable(dimension);
    }
}
