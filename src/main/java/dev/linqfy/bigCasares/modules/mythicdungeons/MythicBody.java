package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.entity.EntityType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * El cuerpo vanilla de las bases de los mobs miticos (sin servidor): su hitbox a escala 1, cuales no chocan con los
 * bloques y cuales vuelan con la direccion que les da el modulo.
 */
public final class MythicBody {

    /** Lo mas ancho que puede quedar un mob ya escalado: entra por las puertas de 3 de la mazmorra. */
    public static final double MAX_WIDTH = 2.4;
    /** Lo mas alto que puede quedar un mob ya escalado: entra por las puertas de 4 de alto. */
    public static final double MAX_HEIGHT = 3.4;

    /** Ancho y alto de la hitbox en bloques. */
    public record Size(double width, double height) {
    }

    // Hitboxes vanilla (EntityType#getEntityClass no las expone sin servidor).
    private static final Map<EntityType, Size> VANILLA = Map.ofEntries(
        Map.entry(EntityType.HUSK, new Size(0.6, 1.95)),
        Map.entry(EntityType.ZOMBIE, new Size(0.6, 1.95)),
        Map.entry(EntityType.ZOMBIE_VILLAGER, new Size(0.6, 1.95)),
        Map.entry(EntityType.SKELETON, new Size(0.6, 1.99)),
        Map.entry(EntityType.WITHER_SKELETON, new Size(0.7, 2.4)),
        Map.entry(EntityType.VINDICATOR, new Size(0.6, 1.95)),
        Map.entry(EntityType.EVOKER, new Size(0.6, 1.95)),
        Map.entry(EntityType.WITCH, new Size(0.6, 1.95)),
        Map.entry(EntityType.PIGLIN_BRUTE, new Size(0.6, 1.95)),
        Map.entry(EntityType.BLAZE, new Size(0.6, 1.8)),
        Map.entry(EntityType.WOLF, new Size(0.6, 0.85)),
        Map.entry(EntityType.SPIDER, new Size(1.4, 0.9)),
        Map.entry(EntityType.CAVE_SPIDER, new Size(0.7, 0.5)),
        Map.entry(EntityType.RAVAGER, new Size(1.95, 2.2)),
        Map.entry(EntityType.HOGLIN, new Size(1.3965, 1.4)),
        Map.entry(EntityType.VEX, new Size(0.4, 0.8)),
        Map.entry(EntityType.PHANTOM, new Size(0.9, 0.5)),
        Map.entry(EntityType.BAT, new Size(0.5, 0.9)));

    /** Bases sin choque con bloques (el Vex atraviesa paredes por diseno). */
    private static final Set<EntityType> NO_BLOCK_COLLISION = Set.of(EntityType.VEX);

    /**
     * Voladores que chocan con los bloques y cuya IA vanilla no sirve en una mazmorra (el phantom da vueltas en el
     * cielo, el murcielago revolotea al azar): se les apaga la IA y el modulo los dirige con velocidad.
     */
    private static final Set<EntityType> STEERED_FLYERS = Set.of(EntityType.PHANTOM, EntityType.BAT);

    private MythicBody() {
    }

    public static Optional<Size> vanillaSize(EntityType base) {
        return Optional.ofNullable(VANILLA.get(base));
    }

    /** La hitbox vanilla de la base por la escala; falla si la base no esta en la tabla. */
    public static Size scaled(EntityType base, double scale) {
        Size size = vanillaSize(base)
            .orElseThrow(() -> new IllegalArgumentException("no vanilla hitbox for mythic base " + base));
        return new Size(size.width() * scale, size.height() * scale);
    }

    public static boolean ignoresBlocks(EntityType base) {
        return NO_BLOCK_COLLISION.contains(base);
    }

    public static boolean steeredFlyer(EntityType base) {
        return STEERED_FLYERS.contains(base);
    }
}
