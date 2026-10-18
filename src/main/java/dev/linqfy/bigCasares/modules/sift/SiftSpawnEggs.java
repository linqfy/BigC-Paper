package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Spawn eggs of the creatures the port dresses over vanilla mobs (the Blub, the Sifter and the Dark Sniffer).
 * The mod's creatures are entity types a vanilla client and server cannot register, so their eggs are content
 * items on the egg of a similar mob. This listener makes every use of such an egg spawn the real port creature
 * and never the vanilla mob the base egg stands for.
 *
 * <p>What a use does, like a vanilla egg: right-clicking a block spawns the creature on the clicked face
 * (a block that has its own use, such as a chest, keeps it unless the player sneaks); right-clicking water
 * (Ichor, here) spawns it on the surface; survival players spend the egg. Differences:</p>
 * <ul>
 *   <li>Spawners are not changed. A vanilla egg on a spawner makes it spawn that mob type; the mod's creatures
 *       are marked and dressed vanilla mobs, so a spawner set to the base mob would spawn plain wolves,
 *       silverfish or sniffers. Right-clicking a spawner with these eggs does nothing (and the egg is kept).</li>
 *   <li>Only inside the Sift world, where the creature code runs, and only while that creature's switch is on;
 *       elsewhere the egg is refused with a message, never turned into the base mob.</li>
 *   <li>Using the egg on a mob (vanilla spawns a baby of the same type) and dispensers (vanilla spawns the base
 *       mob) are cancelled.</li>
 * </ul>
 */
final class SiftSpawnEggs implements Listener {

    /** How far the player reaches when aiming an egg at water. */
    private static final double REACH = 4.5;

    /** What a use of the egg means. */
    enum Use {
        /** Not for the egg: let the event go on (a chest opens, the player was not aiming at anything). */
        LEAVE,
        /** A spawner: the egg refuses. */
        SPAWNER,
        /** Spawn on the face of the clicked block. */
        SPAWN_AT_FACE,
        /** Spawn on the water the player aims at, if any. */
        SPAWN_AT_FLUID
    }

    /** Spawns the creature at a position; false when it could not (the egg is then kept). */
    @FunctionalInterface
    interface Spawner {
        boolean spawn(Location at);
    }

    /**
     * An egg and what it spawns.
     *
     * @param name    the creature's name for messages
     * @param spawner null when the creature's module is switched off
     */
    record Egg(NamespacedId id, String name, Spawner spawner) {
    }

    private final Predicate<World> inSift;
    private final Map<NamespacedId, Egg> eggs;

    SiftSpawnEggs(Predicate<World> inSift, List<Egg> eggs) {
        this.inSift = inSift;
        this.eggs = eggs.stream().collect(Collectors.toUnmodifiableMap(Egg::id, egg -> egg));
    }

    /** The use of an egg for an interaction, from the facts the server tells about it. */
    static Use decide(Action action, boolean spawnerClicked, boolean blockHasOwnUse, boolean sneaking) {
        return switch (action) {
            case RIGHT_CLICK_BLOCK -> spawnerClicked ? Use.SPAWNER
                : blockHasOwnUse && !sneaking ? Use.LEAVE : Use.SPAWN_AT_FACE;
            case RIGHT_CLICK_AIR -> Use.SPAWN_AT_FLUID;
            default -> Use.LEAVE;
        };
    }

    private Optional<Egg> eggOf(ItemStack stack) {
        return ContentItemFactory.idOf(stack).map(eggs::get);
    }

    // The air click arrives "cancelled" (no block to use), so cancellation is read from the item use instead.
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Optional<Egg> found = eggOf(event.getItem());
        if (found.isEmpty() || event.getHand() == null || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Egg egg = found.get();
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Use use = decide(event.getAction(), clicked != null && clicked.getType() == Material.SPAWNER,
            clicked != null && clicked.getType().isInteractable(), player.isSneaking());
        if (use == Use.LEAVE) {
            return;
        }
        event.setCancelled(true);
        World world = clicked != null ? clicked.getWorld() : player.getWorld();
        if (use == Use.SPAWNER) {
            player.sendActionBar(Component.text("Un spawner no acepta este huevo.", NamedTextColor.GRAY));
            return;
        }
        if (egg.spawner() == null || !inSift.test(world)) {
            player.sendActionBar(Component.text(egg.spawner() == null
                ? "El huevo de " + egg.name() + " está desactivado."
                : "El huevo de " + egg.name() + " solo funciona dentro de The Sift.", NamedTextColor.GRAY));
            return;
        }
        Location at = use == Use.SPAWN_AT_FACE
            ? clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5)
            : surfaceOfAimedWater(player);
        if (at != null && egg.spawner().spawn(at) && player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().getItem(event.getHand()).subtract(1);
        }
    }

    /** The water block the player aims at, as a spawn position; null when the player aims at no water. */
    private static Location surfaceOfAimedWater(Player player) {
        RayTraceResult hit = player.rayTraceBlocks(REACH, FluidCollisionMode.ALWAYS);
        Block block = hit == null ? null : hit.getHitBlock();
        return block == null || !block.isLiquid() ? null : block.getLocation().add(0.5, 0.0, 0.5);
    }

    /** Vanilla spawns a baby of the base mob when its egg is used on a mob of that type. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUseOnEntity(PlayerInteractEntityEvent event) {
        if (eggOf(event.getPlayer().getInventory().getItem(event.getHand())).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** A dispenser would spawn the base mob. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (eggOf(event.getItem()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
