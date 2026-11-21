package dev.linqfy.bigCasares.modules.forex;

import java.util.Optional;
import java.util.UUID;

/** What the forex needs from the team system, by plain team id strings. */
public interface ForexTeams {

    ForexTeams NONE = new ForexTeams() {
        @Override
        public Optional<String> ownedTeam(UUID player) {
            return Optional.empty();
        }

        @Override
        public Optional<String> label(String teamId) {
            return Optional.empty();
        }
    };

    /** The team this player owns, if any. */
    Optional<String> ownedTeam(UUID player);

    /** Display name of a team. */
    Optional<String> label(String teamId);
}
