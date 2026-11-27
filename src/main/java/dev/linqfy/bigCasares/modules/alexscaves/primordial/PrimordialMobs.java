package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialPose.Kind;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;

import java.util.List;
import java.util.Map;

/**
 * Mobs of the Primordial Caves with the stats and drops of the mod. Each one
 * rides a vanilla base whose AI is close to the mod's behaviour: neutral
 * herbivores on polar bears and iron golems (they fight back), the raptor on a
 * zombie, the tremorsaurus on a ravager. Riding, taming and breeding come in a
 * later part.
 */
public final class PrimordialMobs {
    private static final CaveBiomeId BIOME = CaveBiomeId.PRIMORDIAL_CAVES;

    /** Flying reptile; neutral (see {@link PrimordialMobListener}). Mod: no entity loot table. */
    public static final ModMobType SUBTERRANODON = new ModMobType("subterranodon", BIOME, "phantom",
        SpawnCategory.CAVE_CREATURE,
        Map.of("max_health", 20.0, "attack_damage", 2.0, "movement_speed", 0.2, "flying_speed", 1.0, "follow_range", 32.0),
        "alexscaves_subterranodon", List.of());
    /** Pack hunter. The mod's 0.2 speed is boosted by its own sprint AI; the zombie base needs a faster base value. */
    public static final ModMobType VALLUMRAPTOR = new ModMobType("vallumraptor", BIOME, "zombie",
        SpawnCategory.CAVE_CREATURE,
        Map.of("max_health", 28.0, "attack_damage", 3.0, "movement_speed", 0.3, "follow_range", 32.0),
        "alexscaves_vallumraptor", List.of());
    public static final ModMobType GROTTOCERATOPS = new ModMobType("grottoceratops", BIOME, "polar_bear",
        SpawnCategory.CAVE_CREATURE,
        Map.of("max_health", 50.0, "attack_damage", 10.0, "armor", 8.0, "movement_speed", 0.2,
            "knockback_resistance", 0.9),
        "alexscaves_grottoceratops", List.of(MobDrop.of("ac_dinosaur_chop", 1, 2), MobDrop.of("ac_tough_hide", 0, 2)));
    public static final ModMobType TRILOCARIS = new ModMobType("trilocaris", BIOME, "cod",
        SpawnCategory.WATER_AMBIENT,
        Map.of("max_health", 10.0, "attack_damage", 1.0, "movement_speed", 0.15),
        "alexscaves_trilocaris", List.of(MobDrop.of("ac_trilocaris_tail", 0, 1)));
    public static final ModMobType TREMORSAURUS = new ModMobType("tremorsaurus", BIOME, "ravager",
        SpawnCategory.CAVE_CREATURE,
        Map.of("max_health", 150.0, "attack_damage", 14.0, "armor", 8.0, "movement_speed", 0.25,
            "knockback_resistance", 0.9, "follow_range", 32.0),
        "alexscaves_tremorsaurus", List.of(MobDrop.of("ac_dinosaur_chop", 0, 2), MobDrop.of("ac_heavy_bone", 2, 3)));
    public static final ModMobType RELICHEIRUS = new ModMobType("relicheirus", BIOME, "iron_golem",
        SpawnCategory.CAVE_CREATURE,
        Map.of("max_health", 120.0, "attack_damage", 12.0, "movement_speed", 0.2),
        "alexscaves_relicheirus", List.of(MobDrop.of("ac_dinosaur_chop", 1, 2), MobDrop.of("ac_heavy_bone", 1, 3),
            MobDrop.of("feather", 1, 4)));

    public static final List<ModMobType> ALL = List.of(SUBTERRANODON, VALLUMRAPTOR, GROTTOCERATOPS, TRILOCARIS,
        TREMORSAURUS, RELICHEIRUS);

    private PrimordialMobs() {
    }

    public static void register(ModMobCatalog catalog) {
        register(catalog, null);
    }

    public static void register(ModMobCatalog catalog, PrimordialBlocks blocks) {
        catalog.register(SUBTERRANODON, (entity, type) -> new Subterranodon(entity));
        catalog.register(VALLUMRAPTOR, (entity, type) -> new PrimordialDinosaur(Kind.VALLUMRAPTOR, blocks));
        catalog.register(GROTTOCERATOPS, (entity, type) -> new PrimordialDinosaur(Kind.GROTTOCERATOPS, blocks));
        catalog.register(TRILOCARIS, (entity, type) -> new PrimordialDinosaur(Kind.TRILOCARIS));
        catalog.register(TREMORSAURUS, (entity, type) -> new PrimordialDinosaur(Kind.TREMORSAURUS));
        catalog.register(RELICHEIRUS, (entity, type) -> new PrimordialDinosaur(Kind.RELICHEIRUS, blocks));
    }

    /** Phantom base sized to the mod's 1.75 wide hitbox. */
    static final class Subterranodon extends PrimordialDinosaur {
        static final int PHANTOM_SIZE = 4;

        Subterranodon(LivingEntity entity) {
            super(Kind.SUBTERRANODON);
            if (entity instanceof Phantom phantom) {
                phantom.setSize(PHANTOM_SIZE);
                phantom.setShouldBurnInDay(false);
                // setSize also sets the phantom's attack damage (6 + size): restore the mod's value.
                AttributeInstance damage = phantom.getAttribute(Attribute.ATTACK_DAMAGE);
                if (damage != null) {
                    damage.setBaseValue(SUBTERRANODON.attribute("attack_damage", 2.0));
                }
            }
        }
    }
}
