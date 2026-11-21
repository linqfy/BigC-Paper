package dev.linqfy.bigCasares.modules.mythicdungeons.boss;

import dev.linqfy.bigCasares.modules.mythicdungeons.MythicDungeonSettings;
import dev.linqfy.bigCasares.modules.pveboss.BossDamageStanding;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A quien se premia cuando cae un jefe mitico y con que (sin servidor). */
public final class MythicBossRewards {

    /** El premio de un jugador. */
    public record Grant(UUID playerId, int placement, int xp, List<MythicDungeonSettings.RewardItem> items) {
    }

    private MythicBossRewards() {
    }

    /** Los mejores {@code topPlayers} por dano (con dano positivo), del primer puesto para abajo. */
    public static List<Grant> grants(List<BossDamageStanding> standings, MythicDungeonSettings.BossReward reward,
                                     int topPlayers) {
        return standings.stream()
            .filter(standing -> standing.damage() > 0.0 && standing.placement() >= 1)
            .sorted(Comparator.comparingInt(BossDamageStanding::placement))
            .limit(Math.max(0, topPlayers))
            .map(standing -> new Grant(standing.playerId(), standing.placement(),
                reward.xpFor(standing.placement()), reward.items()))
            .toList();
    }
}
