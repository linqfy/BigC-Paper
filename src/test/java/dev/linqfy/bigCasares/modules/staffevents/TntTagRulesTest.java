package dev.linqfy.bigCasares.modules.staffevents;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TntTagRulesTest {

    @Test
    void oneTaggerPerGroupButNeverEveryone() {
        assertEquals(0, TntTagRules.taggerCount(1, 4));
        assertEquals(1, TntTagRules.taggerCount(2, 4));
        assertEquals(1, TntTagRules.taggerCount(4, 4));
        assertEquals(2, TntTagRules.taggerCount(5, 4));
        assertEquals(1, TntTagRules.taggerCount(2, 1));
    }

    @Test
    void taggersAreChosenAmongTheAlive() {
        List<UUID> alive = Stream.generate(UUID::randomUUID).limit(9).toList();
        Set<UUID> taggers = TntTagRules.chooseTaggers(alive, 4, new Random(1));
        assertEquals(3, taggers.size());
        assertTrue(alive.containsAll(taggers));
    }
}
