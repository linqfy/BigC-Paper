package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.modules.pveboss.BossEncounterContext;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicBossEndingsTest {

    private record End(UUID boss, MythicCulture culture, boolean defeated) {
    }

    private final List<End> ends = new ArrayList<>();
    private final MythicBossEndings endings = new MythicBossEndings(
        (boss, culture, defeated) -> ends.add(new End(boss, culture, defeated)));

    @Test
    void aStopWithoutTheRewardCallbackIsARemovalAndIsReportedOnce() {
        UUID boss = UUID.randomUUID();
        BossEncounterController controller = create(MythicCulture.GREEK, boss, new Recording());

        controller.stop();
        controller.stop();

        assertEquals(List.of(new End(boss, MythicCulture.GREEK, false)), ends);
    }

    @Test
    void theRewardCallbackMarksOnlyTheNextStopOfThatCulture() {
        UUID defeated = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID norse = UUID.randomUUID();
        BossEncounterController first = create(MythicCulture.GREEK, defeated, new Recording());
        BossEncounterController second = create(MythicCulture.GREEK, other, new Recording());
        BossEncounterController third = create(MythicCulture.NORSE, norse, new Recording());

        // pve-boss: callback de premios y en el mismo tick el stop() de esa instancia
        endings.defeated(MythicCulture.GREEK);
        first.stop();
        third.stop();
        second.stop();

        assertEquals(List.of(
            new End(defeated, MythicCulture.GREEK, true),
            new End(norse, MythicCulture.NORSE, false),
            new End(other, MythicCulture.GREEK, false)), ends);
    }

    @Test
    void forwardsEveryCallToTheEncounterAndReportsEvenWhenItsStopFails() {
        Recording inner = new Recording();
        inner.failOnStop = true;
        BossEncounterController controller = create(MythicCulture.EGYPTIAN, UUID.randomUUID(), inner);

        controller.start();
        controller.tick(Instant.EPOCH);
        assertTrue(controller.suspendsAbilities());
        assertEquals(OptionalInt.of(2), controller.phaseOverride());
        assertEquals(3.0, controller.filterDamage(null, null, 6.0));
        try {
            controller.stop();
        } catch (IllegalStateException expected) {
            // GuardedBossEncounter lo registra; el aviso de fin sale igual
        }

        assertEquals(List.of("start", "tick", "stop"), inner.calls);
        assertEquals(1, ends.size());
    }

    @Test
    void aFactoryThatReturnsNothingStaysThatWay() {
        assertNull(endings.wrap(MythicCulture.GREEK, context -> null).create(context(UUID.randomUUID())));
    }

    private BossEncounterController create(MythicCulture culture, UUID boss, BossEncounterController inner) {
        return endings.wrap(culture, context -> inner).create(context(boss));
    }

    private static BossEncounterContext context(UUID boss) {
        return (BossEncounterContext) Proxy.newProxyInstance(BossEncounterContext.class.getClassLoader(),
            new Class<?>[] {BossEncounterContext.class}, (proxy, method, arguments) ->
                "bossInstanceId".equals(method.getName()) ? boss : null);
    }

    private static final class Recording implements BossEncounterController {

        private final List<String> calls = new ArrayList<>();
        private boolean failOnStop;

        @Override
        public void start() {
            calls.add("start");
        }

        @Override
        public void tick(Instant now) {
            calls.add("tick");
        }

        @Override
        public void stop() {
            calls.add("stop");
            if (failOnStop) {
                throw new IllegalStateException("stop failed");
            }
        }

        @Override
        public boolean suspendsAbilities() {
            return true;
        }

        @Override
        public OptionalInt phaseOverride() {
            return OptionalInt.of(2);
        }

        @Override
        public double filterDamage(org.bukkit.event.entity.EntityDamageEvent event, org.bukkit.entity.Player attacker,
                                   double damage) {
            return damage / 2.0;
        }
    }
}
