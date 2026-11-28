package dev.linqfy.bigCasares.modules.caichet;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Art;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Los cuadros del Sueño: variantes propias de {@code painting_variant} (los JSON viajan en el datapack de {@link
 * CaiChetDatapack} y las texturas en {@code assets/caichet/textures/painting}). No estan en el tag {@code
 * #minecraft:placeable}, asi que un cuadro comun nunca sale con ellos: solo se consiguen con {@link #create}.
 *
 * <p>Al romper un cuadro, el servidor suelta siempre un cuadro comun sin variante (Painting.dropItem usa {@code
 * Items.PAINTING} a secas), asi que {@link #onBreak} lo reemplaza por el item del mismo cuadro.
 */
public final class DreamPaintings implements Listener {

    static final String NAMESPACE = "caichet";

    /** Un cuadro: el id del archivo, su tamaño en bloques y el titulo que se ve en el item. */
    record Variant(String id, int width, int height, String title) {
    }

    static final List<Variant> ALL = List.of(
        new Variant("retrato", 3, 4, "El Retrato"),
        new Variant("laptop", 2, 3, "La Laptop"),
        new Variant("pintor", 3, 4, "El Pintor"),
        new Variant("esfera", 4, 5, "La Esfera"),
        new Variant("gotico", 4, 5, "Gótico"),
        new Variant("grito", 4, 5, "El Grito"),
        new Variant("cena", 6, 2, "La Cena"));

    /** Para avisar una sola vez que faltan las variantes. */
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    /** Busca una variante de cuadro por su clave (el registro del servidor, o un doble en las pruebas). */
    interface Variants {
        Art find(NamespacedKey key);
    }

    static NamespacedKey key(String id) {
        return new NamespacedKey(NAMESPACE, id);
    }

    static List<String> ids() {
        return ALL.stream().map(Variant::id).toList();
    }

    static Variant byId(String id) {
        for (Variant variant : ALL) {
            if (variant.id().equals(id)) {
                return variant;
            }
        }
        return null;
    }

    /** El id si la clave es de uno de nuestros cuadros (caichet:retrato...), y si no null. */
    static String idOf(Key key) {
        if (key == null || !NAMESPACE.equals(key.namespace()) || byId(key.value()) == null) {
            return null;
        }
        return key.value();
    }

    static String randomId(Random random) {
        return ALL.get(random.nextInt(ALL.size())).id();
    }

    /** Las variantes que carga el servidor (las del datapack llegan despues de reiniciar). */
    static Variants registry() {
        return key -> RegistryAccess.registryAccess().getRegistry(RegistryKey.PAINTING_VARIANT).get(key);
    }

    /** El cuadro como item, o null si el servidor todavia no cargo la variante (se avisa una sola vez). */
    static ItemStack create(String id) {
        return create(id, registry(), message -> {
            if (WARNED.compareAndSet(false, true) && CaiChetRuntime.plugin() != null) {
                CaiChetRuntime.logger().warning(message);
            }
        });
    }

    /**
     * El item de un cuadro (un {@code PAINTING} con la variante guardada), o null si el id no existe o la variante no
     * esta en el registro.
     */
    static ItemStack create(String id, Variants variants, Consumer<String> warn) {
        Variant variant = byId(id);
        if (variant == null) {
            return null;
        }
        Art art = variants.find(key(id));
        if (art == null) {
            warn.accept("[CaiChet] Falta la variante de cuadro " + key(id) + ": reinicia el servidor para que Minecraft"
                + " lea el datapack.");
            return null;
        }
        ItemStack item = new ItemStack(Material.PAINTING);
        item.setData(DataComponentTypes.PAINTING_VARIANT, art);
        item.setData(DataComponentTypes.ITEM_NAME,
            Component.text(variant.title(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        return item;
    }

    /** Un cuadro sin ninguno de estos dos frenos suelta su item: la regla del mundo y el modo creativo de quien lo rompe. */
    static boolean shouldDrop(boolean entityDrops, boolean creativeRemover) {
        return entityDrops && !creativeRemover;
    }

    /**
     * Cambia el drop de un cuadro nuestro. Los tres caminos del servidor que rompen un cuadro (golpe, fisica y
     * explosion) miran {@code isRemoved()} despues de este evento y, si ya no existe, no sueltan nada: asi no se
     * duplica el cuadro comun.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof Painting painting)) {
            return;
        }
        String id = idOf(painting.getArt().key());
        if (id == null) {
            return;
        }
        boolean creative = event instanceof HangingBreakByEntityEvent byEntity
            && byEntity.getRemover() instanceof Player player && player.getGameMode() == GameMode.CREATIVE;
        World world = painting.getWorld();
        Boolean rule = world.getGameRuleValue(GameRules.ENTITY_DROPS);
        boolean entityDrops = rule == null || rule;
        ItemStack drop = shouldDrop(entityDrops, creative) ? create(id) : null;
        if (shouldDrop(entityDrops, creative) && drop == null) {
            return; // sin la variante no hay item propio: que suelte el comun
        }
        Location where = painting.getLocation();
        painting.remove();
        if (entityDrops) {
            world.playSound(where, Sound.ENTITY_PAINTING_BREAK, 1f, 1f);
        }
        if (drop != null) {
            world.dropItemNaturally(where, drop);
        }
    }
}
