package dev.linqfy.bigCasares.modules.pveboss;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardedBossEncounterTest {

    private final List<String> failures = new ArrayList<>();

    @Test
    void startsOnceAndStopsExactlyOnce() {
        RecordingController controller = new RecordingController();
        GuardedBossEncounter encounter = guard(controller);

        encounter.start();
        encounter.start();
        encounter.stop();
        encounter.stop();
        encounter.start();

        assertEquals(1, controller.starts);
        assertEquals(1, controller.stops);
        assertTrue(encounter.isStopped());
    }

    @Test
    void stopsEvenWhenItNeverStarted() {
        RecordingController controller = new RecordingController();
        GuardedBossEncounter encounter = guard(controller);

        encounter.stop();

        assertEquals(0, controller.starts);
        assertEquals(1, controller.stops);
    }

    @Test
    void forwardsDecisionsWhileRunning() {
        RecordingController controller = new RecordingController();
        controller.phase = OptionalInt.of(2);
        controller.suspends = true;
        controller.blocks = true;
        controller.damageFactor = 0.5;
        controller.handles = true;
        GuardedBossEncounter encounter = guard(controller);

        encounter.tick(Instant.EPOCH);

        assertEquals(1, controller.ticks);
        assertEquals(2, encounter.phase(3));
        assertTrue(encounter.suspendsAbilities());
        assertTrue(encounter.blocksProjectile(null));
        assertEquals(5.0, encounter.filterDamage(null, null, 10.0));
        assertTrue(encounter.execute(BossAbilityEffectDefinition.of(BossAbilityEffectType.DAMAGE), List.of()));
        assertEquals(List.of(), failures);
    }

    @Test
    void anInvulnerableControllerNegatesTheHit() {
        RecordingController controller = new RecordingController();
        controller.damageFactor = 0.0;

        assertEquals(0.0, guard(controller).filterDamage(null, null, 10.0));
        controller.damageFactor = Double.NaN;
        assertEquals(0.0, guard(controller).filterDamage(null, null, 10.0));
    }

    @Test
    void aStoppedControllerIsNoLongerConsulted() {
        RecordingController controller = new RecordingController();
        controller.phase = OptionalInt.of(1);
        controller.suspends = true;
        controller.blocks = true;
        controller.damageFactor = 0.0;
        controller.handles = true;
        GuardedBossEncounter encounter = guard(controller);
        encounter.stop();

        encounter.tick(Instant.EPOCH);

        assertEquals(0, controller.ticks);
        assertEquals(3, encounter.phase(3));
        assertFalse(encounter.suspendsAbilities());
        assertFalse(encounter.blocksProjectile(null));
        assertEquals(10.0, encounter.filterDamage(null, null, 10.0));
        assertFalse(encounter.execute(BossAbilityEffectDefinition.of(BossAbilityEffectType.DAMAGE), List.of()));
    }

    @Test
    void reportsEveryFailureAndFallsBackToNeutralBehaviour() {
        GuardedBossEncounter encounter = guard(new ExplodingController());

        encounter.start();
        encounter.tick(Instant.EPOCH);
        assertEquals(2, encounter.phase(2));
        assertFalse(encounter.suspendsAbilities());
        assertFalse(encounter.blocksProjectile(null));
        assertEquals(10.0, encounter.filterDamage(null, null, 10.0));
        assertTrue(encounter.execute(BossAbilityEffectDefinition.of(BossAbilityEffectType.DAMAGE), List.of()));
        encounter.stop();

        assertEquals(List.of(
            "start", "tick", "phaseOverride", "suspendsAbilities", "blocksProjectile",
            "filterDamage", "execute DAMAGE", "stop"
        ), failures);
        assertTrue(encounter.isStopped());
    }

    @Test
    void aMissingPhaseOverrideMeansTheHealthPhase() {
        RecordingController controller = new RecordingController();
        controller.phase = null;

        assertEquals(2, guard(controller).phase(2));
    }

    private GuardedBossEncounter guard(BossEncounterController controller) {
        return new GuardedBossEncounter(controller, (stage, failure) -> failures.add(stage));
    }

    private static final class RecordingController implements BossEncounterController {
        private int starts;
        private int ticks;
        private int stops;
        private OptionalInt phase = OptionalInt.empty();
        private boolean suspends;
        private boolean blocks;
        private boolean handles;
        private double damageFactor = 1.0;

        @Override
        public void start() {
            starts++;
        }

        @Override
        public void tick(Instant now) {
            ticks++;
        }

        @Override
        public void stop() {
            stops++;
        }

        @Override
        public boolean blocksProjectile(Projectile projectile) {
            return blocks;
        }

        @Override
        public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
            return damage * damageFactor;
        }

        @Override
        public OptionalInt phaseOverride() {
            return phase;
        }

        @Override
        public boolean suspendsAbilities() {
            return suspends;
        }

        @Override
        public boolean execute(BossAbilityEffectDefinition effect, List<UUID> targets) {
            return handles;
        }
    }

    private static final class ExplodingController implements BossEncounterController {
        @Override
        public void start() {
            throw new IllegalStateException("start");
        }

        @Override
        public void tick(Instant now) {
            throw new IllegalStateException("tick");
        }

        @Override
        public void stop() {
            throw new IllegalStateException("stop");
        }

        @Override
        public boolean blocksProjectile(Projectile projectile) {
            throw new IllegalStateException("blocks");
        }

        @Override
        public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
            throw new IllegalStateException("filter");
        }

        @Override
        public OptionalInt phaseOverride() {
            throw new IllegalStateException("phase");
        }

        @Override
        public boolean suspendsAbilities() {
            throw new IllegalStateException("suspends");
        }

        @Override
        public boolean execute(BossAbilityEffectDefinition effect, List<UUID> targets) {
            throw new IllegalStateException("execute");
        }
    }
}
