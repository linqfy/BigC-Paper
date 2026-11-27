package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The Nuclear Bomb (mod {@code NuclearBombBlock} and {@code NuclearBombEntity}). Lit by flint and steel, a fire
 * charge, redstone power or a burning projectile, the block turns into a primed bomb that blows up after 280
 * ticks (14 seconds: {@link NukeSchedule}) as a size-9 nuclear explosion (three times the mod's). Shears used on the primed bomb defuse it and give the block back.
 *
 * <p>The mod's primed bomb is an entity that falls like TNT; here it is a block display that stays where the
 * block was, with an interaction box for the shears, and plays the mod's shake-and-swell animation
 * ({@link NuclearBombPose}). When its chunk unloads or the module shuts down, a primed
 * bomb turns back into the (unlit) block instead of being lost.
 */
public final class ToxicNuclearBomb implements Listener, Runnable, AutoCloseable {
    static final String BLOCK = "nuclear_bomb";
    static final int FUSE_TICKS = NukeSchedule.FUSE_TICKS;
    /** Played at the bomb when it is lit (heard from afar). */
    static final String ACTIVATION_SOUND = "alexscaves:nuclear_bomb_activation";
    /** Played to everyone in the world when the alert starts. */
    static final String ALERT_SOUND = "alexscaves:nuclear_alert";

    private final ModMobService mobs;
    private final BlockData bomb;
    private final Map<UUID, Primed> primed = new HashMap<>();

    public ToxicNuclearBomb(ModMobService mobs, BlockData bomb) {
        this.mobs = mobs;
        this.bomb = bomb;
    }

    private static final class Primed {
        final BlockDisplay display;
        final Interaction box;
        /** Its name in the alert: A1, A2... */
        final String label;
        int ticks;

        Primed(BlockDisplay display, Interaction box, String label) {
            this.display = display;
            this.box = box;
            this.label = label;
        }
    }

    /** Not ignoreCancelled: module blocks deny the block use (see NoteBlockListener), which reads as cancelled. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onIgnite(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || event.getHand() == null || !isBomb(block)
            || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (held.getType() != Material.FLINT_AND_STEEL && held.getType() != Material.FIRE_CHARGE) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (held.getType() == Material.FLINT_AND_STEEL) {
                player.getInventory().setItem(event.getHand(), held.damage(1, player));
            } else {
                held.setAmount(held.getAmount() - 1);
            }
        }
        prime(block);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRedstone(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (isBomb(block) && block.isBlockIndirectlyPowered()) {
            prime(block);
        }
    }

    /** A bomb placed against a powered block lights at once (mod {@code onPlace} with a neighbour signal). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        mobs.tasks().run(() -> {
            if (isBomb(block) && block.isBlockIndirectlyPowered()) {
                prime(block);
            }
        });
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        for (UUID box : Map.copyOf(primed).keySet()) {
            Primed bomb = primed.get(box);
            Location at = bomb.display.getLocation();
            if (at.getWorld() == event.getWorld() && at.getBlockX() >> 4 == event.getChunk().getX()
                && at.getBlockZ() >> 4 == event.getChunk().getZ()) {
                restore(box);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurningProjectile(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        Block block = event.getHitBlock();
        if (block != null && projectile.getFireTicks() > 0 && isBomb(block)) {
            prime(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDefuse(PlayerInteractEntityEvent event) {
        Primed bomb = primed.get(event.getRightClicked().getUniqueId());
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (bomb == null || held.getType() != Material.SHEARS) {
            return;
        }
        event.setCancelled(true);
        Location at = bomb.display.getLocation();
        at.getWorld().playSound(at, "alexscaves:nuclear_bomb_defuse", SoundCategory.BLOCKS, 1.0f, 1.0f);
        if (NukeSchedule.FUSE_TICKS - bomb.ticks <= NukeSchedule.ALERT_TICKS_LEFT) {
            mobs.plugin().getServer().broadcastMessage("§aLa bomba nuclear " + bomb.label + " fue desactivada.");
        }
        mobs.createItem("ac_" + BLOCK, 1).ifPresent(item -> at.getWorld().dropItemNaturally(at.clone().add(0.5, 0.5, 0.5), item));
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItem(event.getHand(), held.damage(1, player));
        }
        remove(event.getRightClicked().getUniqueId());
    }

    @Override
    public void run() {
        Iterator<Map.Entry<UUID, Primed>> iterator = primed.entrySet().iterator();
        while (iterator.hasNext()) {
            Primed bomb = iterator.next().getValue();
            if (!bomb.display.isValid()) {
                bomb.box.remove();
                iterator.remove();
                continue;
            }
            bomb.ticks++;
            // Mod NuclearBombRenderer: the bomb shakes and swells as the fuse burns (interpolated by the client).
            bomb.display.setInterpolationDelay(0);
            bomb.display.setInterpolationDuration(1);
            bomb.display.setTransformationMatrix(NuclearBombPose.matrix(bomb.ticks));
            Location at = bomb.display.getLocation();
            if (bomb.ticks % 10 == 0) {
                at.getWorld().spawnParticle(Particle.DUST, at.clone().add(0.5, 1.1, 0.5), 4, 0.3, 0.2, 0.3, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(0x7F, 0xD1, 0x1B), 1.2f));
            }
            int left = FUSE_TICKS - bomb.ticks;
            for (String line : NukeSchedule.linesAt(left, bomb.label)) {
                mobs.plugin().getServer().broadcastMessage(line);
            }
            if (NukeSchedule.isAlert(left)) {
                for (Player player : at.getWorld().getPlayers()) {
                    player.playSound(player, ALERT_SOUND, SoundCategory.MASTER, 1.0f, 1.0f);
                }
            }
            if (left <= 0) {
                bomb.display.remove();
                bomb.box.remove();
                iterator.remove();
                // The alert is cut by the blast, so only the detonation is heard.
                stopSound(at, ALERT_SOUND);
                NuclearExplosion.detonateWithWarning(mobs, at.clone().add(0.5, 0.0, 0.5), NukeMath.BOMB_SIZE);
            }
        }
    }

    /** Turns the block into a primed bomb (mod {@code onCaughtFire}). */
    private void prime(Block block) {
        Location at = block.getLocation();
        block.setType(Material.AIR, false);
        BlockDisplay display = at.getWorld().spawn(at, BlockDisplay.class, entity -> {
            entity.setBlock(bomb);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
            // Mod: drawn at full light whatever the light around it.
            entity.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        });
        Interaction box = at.getWorld().spawn(at.clone().add(0.5, 0, 0.5), Interaction.class, entity -> {
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(1.0f);
            entity.setPersistent(false);
        });
        primed.put(box.getUniqueId(), new Primed(display, box, NukeSerial.next(mobs.plugin(), at.getWorld())));
        at.getWorld().playSound(at, Sound.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1.0f, 1.0f);
        at.getWorld().playSound(at, ACTIVATION_SOUND, SoundCategory.MASTER, 16.0f, 1.0f);
    }

    private void remove(UUID box) {
        Primed bomb = primed.remove(box);
        if (bomb != null) {
            // A bomb that will not blow up (defused or turned back into its block) falls silent.
            Location at = bomb.display.getLocation();
            stopSound(at, ACTIVATION_SOUND);
            stopSound(at, ALERT_SOUND);
            bomb.display.remove();
            bomb.box.remove();
        }
    }

    private static void stopSound(Location at, String sound) {
        for (Player player : at.getWorld().getPlayers()) {
            player.stopSound(sound, SoundCategory.MASTER);
        }
    }

    private boolean isBomb(Block block) {
        return block.getType() == Material.NOTE_BLOCK && bomb.equals(block.getBlockData());
    }

    /** Turns the primed bombs back into blocks without exploding (module shutdown). */
    @Override
    public void close() {
        for (UUID box : Map.copyOf(primed).keySet()) {
            restore(box);
        }
    }

    /** Puts the unlit block back where a primed bomb stands. */
    private void restore(UUID box) {
        Primed bomb = primed.get(box);
        if (bomb == null) {
            return;
        }
        Block block = bomb.display.getLocation().getBlock();
        if (block.getType().isAir()) {
            block.setBlockData(this.bomb, false);
        } else {
            mobs.createItem("ac_" + BLOCK, 1).ifPresent(item -> block.getWorld().dropItemNaturally(block.getLocation(), item));
        }
        remove(box);
    }
}
