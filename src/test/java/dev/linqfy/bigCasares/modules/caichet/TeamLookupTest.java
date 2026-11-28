package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** El orden del roster es fijo: cada integrante ocupa siempre el mismo lugar del tren. */
class TeamLookupTest {

    private static TeamLookup.Member member(String id) {
        return new TeamLookup.Member(UUID.fromString(id), "n" + id.charAt(0));
    }

    private static List<TeamLookup.Member> three() {
        return List.of(
            member("30000000-0000-0000-0000-000000000000"),
            member("10000000-0000-0000-0000-000000000000"),
            member("20000000-0000-0000-0000-000000000000"));
    }

    @Test
    void membersAreSortedByUuidWhateverTheInputOrder() {
        List<TeamLookup.Member> input = new ArrayList<>(three());
        for (int i = 0; i < 6; i++) {
            Collections.rotate(input, 1);
            if (i % 2 == 0) {
                Collections.reverse(input);
            }
            List<String> ids = TeamLookup.roster(input).members().stream().map(m -> m.id().toString()).toList();
            assertEquals(List.of("10000000-0000-0000-0000-000000000000", "20000000-0000-0000-0000-000000000000",
                "30000000-0000-0000-0000-000000000000"), ids);
        }
    }

    @Test
    void theKeyIsTheSameForTheSameSetInAnyOrder() {
        List<TeamLookup.Member> reversed = new ArrayList<>(three());
        Collections.reverse(reversed);
        assertEquals(TeamLookup.roster(three()).key(), TeamLookup.roster(reversed).key());
    }

    @Test
    void theKeyChangesWhenAMemberChanges() {
        List<TeamLookup.Member> other = new ArrayList<>(three());
        other.set(0, member("40000000-0000-0000-0000-000000000000"));
        assertNotEquals(TeamLookup.roster(three()).key(), TeamLookup.roster(other).key());
        assertNotEquals(TeamLookup.roster(three()).key(), TeamLookup.roster(three().subList(0, 2)).key());
    }

    @Test
    void theReturnedListIsImmutable() {
        TeamLookup.Roster roster = TeamLookup.roster(three());
        assertThrows(UnsupportedOperationException.class, () -> roster.members().clear());
    }
}
