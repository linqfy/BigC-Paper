package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.blocks.CustomBlockService;
import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.GenericGameEvent;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Healthy Sculk trampling crops ({@code HealthySculkSoilBlock#fallOn} in the mod, the class of Healthy Sculk and Dry
 * Healthy Sculk): a player who lands from a fall of more than half a block on one of them destroys the crop, stem or
 * Sculkflower standing on it, with drops, as {@code destroyBlock(pos.above(), true, player)} does.
 *
 * <p>Custom blocks have no {@code fallOn}, so the landing is taken from Paper's {@link GenericGameEvent} for
 * {@link GameEvent#HIT_GROUND}, which vanilla emits right after calling {@code fallOn} and before it resets the fall
 * distance, so {@link Player#getFallDistance()} still holds the fall. The handler returns on the first comparison for
 * every other game event.</p>
 *
 * <p>Differences: vanilla hands {@code fallOn} the block that supports the entity; the event has only a position, so the
 * block under the player's feet is used (landing across two blocks picks the one at the feet's centre). The crops are
 * the vanilla blocks that extend {@code CropBlock}, {@code StemBlock}, {@code AttachedStemBlock} or
 * {@code PitcherCropBlock}, and the Sculkflower crop and flower the port tracks ({@link SiftSculkflowers}); the
 * destruction is announced with an {@link EntityChangeBlockEvent} by the player, as vanilla's farmland trampling is, so
 * protection plugins can veto it. The mod does not check the game mode, nor does this (spectators raise no event).</p>
 */
final class SiftTrampling implements Listener {

    /** The mod's {@code fallDistance > 0.5}. */
    static final float MIN_FALL = 0.5F;
    /** {@code CropBlock} (wheat, carrots, potatoes, beetroots, torchflower crop), {@code PitcherCropBlock} and the stems. */
    static final Set<Material> CROPS = EnumSet.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
        Material.TORCHFLOWER_CROP, Material.PITCHER_CROP, Material.PUMPKIN_STEM, Material.MELON_STEM,
        Material.ATTACHED_PUMPKIN_STEM, Material.ATTACHED_MELON_STEM);
    /** The two blocks of the mod that use {@code HealthySculkSoilBlock}. */
    static final Set<String> SOILS = Set.of("healthy_sculk", "dry_healthy_sculk");

    private final CustomBlockRegistry blocks;
    private final SiftSculkflowers sculkflowers;

    /** {@code sculkflowers} is null when the Sculkflower is switched off. */
    SiftTrampling(CustomBlockRegistry blocks, SiftSculkflowers sculkflowers) {
        this.blocks = blocks;
        this.sculkflowers = sculkflowers;
    }

    /** The mod's condition: a fall of more than half a block onto a healthy sculk soil, with a crop on it. */
    static boolean tramples(float fallDistance, String soilPath, Material above) {
        return fallDistance > MIN_FALL && SOILS.contains(soilPath) && CROPS.contains(above);
    }

    /** The block a landing at this position stands on: the one under the feet. */
    static int[] floorUnder(double x, double y, double z) {
        return new int[]{(int) Math.floor(x), (int) Math.floor(y - 1.0E-3), (int) Math.floor(z)};
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameEvent(GenericGameEvent event) {
        if (!GameEvent.HIT_GROUND.equals(event.getEvent()) || !(event.getEntity() instanceof Player player)
            || player.getFallDistance() <= MIN_FALL) {
            return;
        }
        Location at = event.getLocation();
        int[] under = floorUnder(at.getX(), at.getY(), at.getZ());
        Block floor = at.getWorld().getBlockAt(under[0], under[1], under[2]);
        Optional<String> soil = CustomBlockService.stateOf(floor.getBlockData()).flatMap(blocks::byState)
            .filter(definition -> definition.id().namespace().equals(SiftBlocks.NS)).map(CustomBlockDefinition::id)
            .map(id -> id.path());
        if (soil.isEmpty() || !SOILS.contains(soil.get())) {
            return;
        }
        Block above = floor.getRelative(BlockFace.UP);
        boolean flower = sculkflowers != null && sculkflowers.isPlant(above);
        if (!flower && !tramples(player.getFallDistance(), soil.get(), above.getType())) {
            return;
        }
        EntityChangeBlockEvent change = new EntityChangeBlockEvent(player, above, Material.AIR.createBlockData());
        player.getServer().getPluginManager().callEvent(change);
        if (change.isCancelled()) {
            return;
        }
        if (flower) {
            sculkflowers.trample(above);
        } else {
            above.breakNaturally(true);
        }
    }
}
