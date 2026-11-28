package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyTournamentRewardsTest {

    @Test
    void itemsAreListedBeforeCommandsAndNumberedFromOne() {
        ToffyTournament tournament = new ToffyTournament("tenis", "Tenis");
        tournament.addRewardItem("nuke_shot:2");
        tournament.addRewardCommand("give {player} diamond 1");

        assertEquals(List.of("item nuke_shot:2", "comando give {player} diamond 1"), tournament.rewardLines());
        assertEquals(List.of("nuke_shot:2"), tournament.rewards().items());
        assertEquals(List.of("give {player} diamond 1"), tournament.rewards().commands());
    }

    @Test
    void removingByNumberFollowsTheListing() {
        ToffyTournament tournament = new ToffyTournament("tenis", "Tenis");
        tournament.addRewardItem("nuke_shot:1");
        tournament.addRewardItem("minecraft:diamond:3");
        tournament.addRewardCommand("say hola");

        assertTrue(tournament.removeReward(2));
        assertEquals(List.of("item nuke_shot:1", "comando say hola"), tournament.rewardLines());
        assertFalse(tournament.removeReward(3));
        assertFalse(tournament.removeReward(0));
    }

    @Test
    void clearingEmptiesTheRewardsSoTheFileRewardsApply() {
        ToffyTournament tournament = new ToffyTournament("tenis", "Tenis");
        tournament.addRewardItem("nuke_shot:1");

        tournament.clearRewards();

        assertTrue(tournament.rewards().isEmpty());
    }

    @Test
    void invalidStoredItemsAreDroppedAndReported() {
        ToffyTournament tournament = new ToffyTournament("tenis", "Tenis");
        tournament.addRewardItem("nuke_shot:2");
        tournament.addRewardItem("bad item!");
        tournament.addRewardCommand("say hola");

        assertEquals(List.of("bad item!"), tournament.dropInvalidRewardItems());
        assertEquals(List.of("nuke_shot:2"), tournament.rewards().items());
        assertEquals(List.of("say hola"), tournament.rewards().commands());
    }
}
