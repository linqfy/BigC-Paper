package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForlornCapsTest {

    @Test
    void bladesAreCappedPerPlayerAndGlobally() {
        assertTrue(DesolateDagger.canSummon(0, 0));
        assertTrue(DesolateDagger.canSummon(DesolateDagger.MAX_BLADES_PER_PLAYER - 1, 10));
        assertFalse(DesolateDagger.canSummon(DesolateDagger.MAX_BLADES_PER_PLAYER, 10));
        assertFalse(DesolateDagger.canSummon(1, DesolateDagger.MAX_BLADES_GLOBAL));
    }

    @Test
    void harmVerdictIsCachedUntilItExpires() {
        HarmVerdictCache cache = new HarmVerdictCache();
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        int[] asked = {0};
        assertTrue(cache.allowed(owner, target, 100, () -> {
            asked[0]++;
            return true;
        }));
        assertTrue(cache.allowed(owner, target, 100 + HarmVerdictCache.TTL_TICKS - 1, () -> {
            asked[0]++;
            return false;
        }));
        assertEquals(1, asked[0]);
        assertFalse(cache.allowed(owner, target, 100 + HarmVerdictCache.TTL_TICKS, () -> {
            asked[0]++;
            return false;
        }));
        assertEquals(2, asked[0]);
    }

    @Test
    void refreshAsksAgainImmediately() {
        HarmVerdictCache cache = new HarmVerdictCache();
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        assertTrue(cache.allowed(owner, target, 0, () -> true));
        assertFalse(cache.refresh(owner, target, 1, () -> false));
        assertFalse(cache.allowed(owner, target, 2, () -> true));
    }
}
