package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * Si un mob mitico suelta su Fragmento de Odio (sin servidor). Solo los que nacieron en la mazmorra (marca
 * {@code mythic_dungeon_spawn}) y los mato un jugador; siempre el fragmento de la cultura del propio mob.
 * Cada nivel de Botin suma {@code lootingBonusPerLevel} a la chance (sin pasar de 1). Las culturas que todavia no
 * tienen jefe no tienen fragmento: para sus mobs la chance es 0 aunque el catalogo o el yml digan otra cosa (sus
 * drops vanilla y su experiencia no pasan por aca y siguen igual).
 */
public final class FragmentDropRoll {

    private final double lootingBonusPerLevel;
    private final DoubleSupplier random;

    public FragmentDropRoll(double lootingBonusPerLevel, DoubleSupplier random) {
        if (!Double.isFinite(lootingBonusPerLevel) || lootingBonusPerLevel < 0.0) {
            throw new IllegalArgumentException("looting bonus must be finite and non-negative");
        }
        this.lootingBonusPerLevel = lootingBonusPerLevel;
        this.random = random;
    }

    public double chance(MythicMobType type, int lootingLevel) {
        if (type.fragmentChance() <= 0.0 || type.culture().fragmentItemId().isEmpty()) {
            return 0.0;
        }
        return Math.min(1.0, type.fragmentChance() + Math.max(0, lootingLevel) * lootingBonusPerLevel);
    }

    /** El id del fragmento que cae, si cae. */
    public Optional<String> roll(MythicMobType type, boolean dungeonSpawn, boolean killedByPlayer, int lootingLevel) {
        if (!dungeonSpawn || !killedByPlayer) {
            return Optional.empty();
        }
        double chance = chance(type, lootingLevel);
        if (chance <= 0.0) {
            return Optional.empty();
        }
        return random.getAsDouble() < chance ? type.culture().fragmentItemId() : Optional.empty();
    }
}
