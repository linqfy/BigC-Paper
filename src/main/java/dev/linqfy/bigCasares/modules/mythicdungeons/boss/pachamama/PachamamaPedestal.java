package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import dev.linqfy.bigCasares.items.ModelDataUtil;
import dev.linqfy.bigCasares.modules.mythicdungeons.MandingaGuitar;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Mandinga's offer: a pedestal block on the floor with his guitar spinning over it. Clicks are read by polling an
 * owned {@link Interaction} entity ({@code getLastInteraction()}), so the encounter needs no listener. Java players
 * see a spinning {@link ItemDisplay}; Bedrock players (Geyser draws no display entities) see a floating dropped item
 * that nobody can pick up, hidden from Java players. Neither holds the guitar itself: both show a look-alike stack
 * ({@link #lookAlike}) with no catalog tags, so a hopper or hopper minecart that swallows the floating item gets a
 * plain crossbow, not a guitar that skips the heart; the floating item is also taken away while anything that could
 * swallow it stands over the pedestal, and comes back when the space is clear. Whether Geyser forwards clicks on an Interaction entity depends
 * on its version, so the fight also accepts holding sneak next to the pedestal ({@link PachamamaDealHold}).
 */
final class PachamamaPedestal {

    private static final Material BLOCK = Material.CHISELED_POLISHED_BLACKSTONE;
    private static final float SPIN_PER_TICK = 6.0f;
    /** A hopper minecart this close to the floating item takes it away (minecarts move up to ~1 block a tick). */
    private static final double HOPPER_REACH = 4.0;
    /** Floating items lost some other way that are replaced, at most. */
    private static final int MAX_LOST = 3;

    private final MythicBossSupport support;
    private final UUID bossId;
    private final PachamamaFloor floor;
    private Block block;
    private Location top;
    private ItemDisplay display;
    private Item bedrockItem;
    private ItemStack look;
    private int lost;
    private Interaction interaction;
    private long lastSeen = Long.MIN_VALUE;
    private float yaw;

    PachamamaPedestal(MythicBossSupport support, UUID bossId, PachamamaFloor floor) {
        this.support = support;
        this.bossId = bossId;
        this.floor = floor;
    }

    /** Raises the pedestal at the floor block column (x, z), on the walking level {@code floorY}. */
    boolean raise(World world, int x, int floorY, int z, ItemStack guitar) {
        Block target = world.getBlockAt(x, floorY, z);
        if (!target.getType().isAir()) {
            return false;
        }
        if (!floor.change(Map.of(target, BLOCK.createBlockData()))) {
            return false;
        }
        block = target;
        look = lookAlike(guitar);
        top = new Location(world, x + 0.5, floorY + 1.0, z + 0.5);
        Location spin = top.clone().add(0.0, 0.75, 0.0);
        display = support.spawnOwned(bossId, spin, ItemDisplay.class, created -> {
            created.setItemStack(look.clone());
            created.setBillboard(Display.Billboard.FIXED);
            created.setPersistent(false);
            created.setTeleportDuration(1);
            created.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                new Vector3f(1.1f, 1.1f, 1.1f), new AxisAngle4f()));
        });
        spawnDouble(world.getPlayers());
        interaction = support.spawnOwned(bossId, top.clone().subtract(0.0, 1.0, 0.0), Interaction.class, created -> {
            created.setInteractionWidth(1.4f);
            created.setInteractionHeight(2.4f);
            created.setResponsive(true);
            created.setPersistent(false);
        });
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, spin, 40, 0.4, 0.6, 0.4, 0.03);
        world.playSound(top, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.6f);
        return true;
    }

    /**
     * The stack the pedestal shows: same material, item model and custom model data as the guitar, nothing else (no
     * catalog id or other tags, no name), so it is never {@link MandingaGuitar#isGuitar a guitar}.
     */
    static ItemStack lookAlike(ItemStack guitar) {
        ItemStack look = new ItemStack(guitar.getType());
        if (!guitar.hasItemMeta()) {
            return look;
        }
        ItemMeta source = guitar.getItemMeta();
        ItemMeta meta = look.getItemMeta();
        if (source == null || meta == null) {
            return look;
        }
        if (source.hasItemModel()) {
            meta.setItemModel(source.getItemModel());
        }
        OptionalInt modelData = ModelDataUtil.readCustomModelData(source);
        if (modelData.isPresent()) {
            ModelDataUtil.writeCustomModelData(meta, modelData.getAsInt());
        }
        look.setItemMeta(meta);
        return look;
    }

    private void spawnDouble(Collection<? extends Player> viewers) {
        Location spin = top.clone().add(0.0, 0.75, 0.0);
        bedrockItem = top.getWorld().dropItem(spin, look.clone(), item -> {
            item.setCanPlayerPickup(false);
            item.setCanMobPickup(false);
            item.setUnlimitedLifetime(true);
            item.setGravity(false);
            item.setInvulnerable(true);
            item.setPersistent(false);
            item.setVelocity(new Vector());
        });
        support.track(bossId, bedrockItem);
        for (Player online : viewers) {
            if (!support.isBedrock(online.getUniqueId())) {
                online.hideEntity(support.plugin(), bedrockItem);
            }
        }
    }

    /**
     * Whether something could swallow the floating item: the pedestal is gone, a block (a hopper, a rail) stands on
     * it, or a hopper minecart is close.
     */
    private boolean exposed() {
        if (block.getType() != BLOCK || !top.getBlock().getType().isAir()) {
            return true;
        }
        return !top.getWorld().getNearbyEntitiesByType(HopperMinecart.class, top.clone().add(0.0, 0.75, 0.0),
            HOPPER_REACH).isEmpty();
    }

    boolean raised() {
        return top != null;
    }

    Location top() {
        return top == null ? null : top.clone();
    }

    /** Block column of the pedestal. */
    Block block() {
        return block;
    }

    /** The player behind the newest click on the pedestal since the last poll, if online. */
    Optional<Player> poll() {
        if (interaction == null || !interaction.isValid()) {
            return Optional.empty();
        }
        Interaction.PreviousInteraction last = interaction.getLastInteraction();
        if (last == null || last.getTimestamp() == lastSeen) {
            return Optional.empty();
        }
        lastSeen = last.getTimestamp();
        OfflinePlayer who = last.getPlayer();
        return Optional.ofNullable(who.getPlayer());
    }

    /** Spins the guitar, keeps the Bedrock item in place and hidden from Java players; call every tick. */
    void tick(long tick, Collection<? extends Player> viewers) {
        if (top == null) {
            return;
        }
        yaw = (yaw + SPIN_PER_TICK) % 360.0f;
        if (display != null && display.isValid()) {
            display.setRotation(yaw, 0.0f);
        }
        if (bedrockItem != null && !bedrockItem.isValid()) {
            // lost without us (swallowed, killed): bring it back only a few times
            bedrockItem = null;
            lost++;
        }
        boolean exposed = exposed();
        if (exposed && bedrockItem != null) {
            bedrockItem.remove();
            bedrockItem = null;
        } else if (!exposed && bedrockItem == null && lost < MAX_LOST) {
            spawnDouble(viewers);
        }
        if (bedrockItem != null && bedrockItem.isValid()) {
            Location spin = top.clone().add(0.0, 0.75, 0.0);
            if (bedrockItem.getLocation().distanceSquared(spin) > 0.04) {
                bedrockItem.teleport(spin);
            }
            bedrockItem.setVelocity(new Vector());
            if (tick % 20 == 0) {
                for (Player viewer : viewers) {
                    if (!support.isBedrock(viewer.getUniqueId())) {
                        viewer.hideEntity(support.plugin(), bedrockItem);
                    }
                }
            }
        }
        if (tick % 6 == 0) {
            top.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, top.clone().add(0.0, 0.2, 0.0), 2, 0.35, 0.05,
                0.35, 0.0);
            top.getWorld().spawnParticle(Particle.NOTE, top.clone().add(0.0, 1.4, 0.0), 0,
                (tick % 24) / 24.0, 0.0, 0.0, 1.0);
        }
    }

    /** Removes the entities; the block goes back with the floor journal. */
    void clear() {
        if (display != null && display.isValid()) {
            display.remove();
        }
        if (bedrockItem != null && bedrockItem.isValid()) {
            bedrockItem.remove();
        }
        if (interaction != null && interaction.isValid()) {
            interaction.remove();
        }
        display = null;
        bedrockItem = null;
        interaction = null;
    }
}
