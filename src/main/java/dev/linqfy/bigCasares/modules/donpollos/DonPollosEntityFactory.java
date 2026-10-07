package dev.linqfy.bigCasares.modules.donpollos;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;

public final class DonPollosEntityFactory {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final double VILLAGER_SPEED = 0.5;

    private final NamespacedKey variantKey;

    public DonPollosEntityFactory(JavaPlugin plugin) {
        this.variantKey = new NamespacedKey(plugin, "don_pollo_variant");
    }

    public Villager spawn(Location location, DonPollosVariantSettings settings) {
        Objects.requireNonNull(location.getWorld(), "location world");
        return location.getWorld().spawn(location, Villager.class, villager -> {
            villager.getPersistentDataContainer().set(variantKey, PersistentDataType.STRING, settings.variant().id());
            applyBehaviour(villager, settings);
        });
    }

    public void applyBehaviour(Villager villager, DonPollosVariantSettings settings) {
        villager.setProfession(Registry.VILLAGER_PROFESSION.getOrThrow(NamespacedKey.minecraft("nitwit")));
        // en Java no se ve (lo tapa el modelo); en Bedrock, que ve el aldeano, cada Don Pollo tiene otra ropa
        variant(villager).ifPresent(variant -> villager.setVillagerType(
            Registry.VILLAGER_TYPE.getOrThrow(NamespacedKey.minecraft(DonPollosBedrock.villagerLook(variant)))));
        villager.setAdult();
        villager.setBreed(false);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCanPickupItems(false);
        villager.setAI(settings.wander());
        org.bukkit.attribute.AttributeInstance movement = villager.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (movement != null) {
            movement.setBaseValue(VILLAGER_SPEED * settings.speed());
        }
        villager.customName(LEGACY.deserialize(settings.displayName()));
        villager.setCustomNameVisible(true);
    }

    public Optional<DonPolloVariant> variant(Entity entity) {
        if (!(entity instanceof Villager)) {
            return Optional.empty();
        }
        return DonPolloVariant.fromId(entity.getPersistentDataContainer().get(variantKey, PersistentDataType.STRING));
    }

    public boolean isDonPollo(Entity entity) {
        return variant(entity).isPresent();
    }
}
