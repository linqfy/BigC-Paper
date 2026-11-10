package dev.linqfy.bigCasares.modules.forex;

import dev.linqfy.bigCasares.modules.teams.Team;
import dev.linqfy.bigCasares.modules.teams.TeamId;
import dev.linqfy.bigCasares.modules.teams.TeamService;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Reads clans from the team system, looked up on every call so it follows reloads. */
public final class TeamServiceForexTeams implements ForexTeams {

    private final Supplier<Optional<TeamService>> service;

    public TeamServiceForexTeams(Supplier<Optional<TeamService>> service) {
        this.service = service;
    }

    @Override
    public Optional<String> ownedTeam(UUID player) {
        return service.get().flatMap(teams -> teams.findByMember(player))
            .filter(team -> team.ownerId().equals(player))
            .map(team -> team.id().toString());
    }

    @Override
    public Optional<String> label(String teamId) {
        TeamId id;
        try {
            id = TeamId.parse(teamId);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
        return service.get().flatMap(teams -> teams.findById(id)).map(TeamServiceForexTeams::label);
    }

    private static String label(Team team) {
        return team.color().legacyCode() + team.name() + " [" + team.tag() + "]";
    }
}
