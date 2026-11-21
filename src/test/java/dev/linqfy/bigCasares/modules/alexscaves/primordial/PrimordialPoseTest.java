package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialPose.Kind;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialPose.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrimordialPoseTest {
    private static State state(boolean sitting, boolean dancing, boolean burying, boolean running, boolean relaxed,
                               boolean flying, boolean swimming, boolean firing, boolean onGround, boolean moving) {
        return new State(sitting, dancing, burying, running, relaxed, flying, swimming, firing, onGround, moving);
    }

    private static final State IDLE = state(false, false, false, false, false, false, false, false, true, false);
    private static final State WALKING = state(false, false, false, false, false, false, false, false, true, true);

    @Test
    void walksOrIdlesByMovement() {
        for (Kind kind : Kind.values()) {
            if (kind == Kind.TRILOCARIS) {
                continue;
            }
            assertEquals("idle", PrimordialPose.baseLoop(kind, IDLE), kind.name());
            assertEquals("walk", PrimordialPose.baseLoop(kind, WALKING), kind.name());
        }
    }

    @Test
    void sitWinsOnlyWhereTheModelHasIt() {
        State sitting = state(true, false, false, false, false, false, false, false, true, false);
        assertEquals("sit", PrimordialPose.baseLoop(Kind.VALLUMRAPTOR, sitting));
        assertEquals("sit", PrimordialPose.baseLoop(Kind.TREMORZILLA, sitting));
        assertEquals("idle", PrimordialPose.baseLoop(Kind.GROTTOCERATOPS, sitting));
        assertEquals("idle", PrimordialPose.baseLoop(Kind.SAUROPOD, sitting));
    }

    @Test
    void danceBeatsEverythingButSitting() {
        State dancingMoving = state(false, true, true, true, true, true, true, true, true, true);
        for (Kind kind : Kind.values()) {
            assertEquals("dance", PrimordialPose.baseLoop(kind, dancingMoving), kind.name());
        }
        State both = state(true, true, false, false, false, false, false, false, true, false);
        assertEquals("sit", PrimordialPose.baseLoop(Kind.TREMORSAURUS, both));
        assertEquals("dance", PrimordialPose.baseLoop(Kind.RELICHEIRUS, both));
    }

    @Test
    void burialOnlyForTheEggLayers() {
        State burying = state(false, false, true, false, false, false, false, false, true, true);
        assertEquals("bury_eggs", PrimordialPose.baseLoop(Kind.GROTTOCERATOPS, burying));
        assertEquals("bury_eggs", PrimordialPose.baseLoop(Kind.SUBTERRANODON, burying));
        assertEquals("walk", PrimordialPose.baseLoop(Kind.TREMORZILLA, burying));
        assertEquals("walk", PrimordialPose.baseLoop(Kind.SAUROPOD, burying));
    }

    @Test
    void raptorRelaxedThenRunning() {
        State relaxedRunning = state(false, false, false, true, true, false, false, false, true, true);
        assertEquals("relaxed", PrimordialPose.baseLoop(Kind.VALLUMRAPTOR, relaxedRunning));
        State running = state(false, false, false, true, false, false, false, false, true, true);
        assertEquals("run", PrimordialPose.baseLoop(Kind.VALLUMRAPTOR, running));
        assertEquals("walk", PrimordialPose.baseLoop(Kind.RELICHEIRUS, running));
    }

    @Test
    void subterranodonFliesOffTheGround() {
        State flying = state(false, false, false, false, false, true, false, false, false, true);
        assertEquals("fly", PrimordialPose.baseLoop(Kind.SUBTERRANODON, flying));
        assertEquals("walk", PrimordialPose.baseLoop(Kind.VALLUMRAPTOR, flying));
    }

    @Test
    void trilocarisCrawlsOnTheGroundAndSwimsInWater() {
        State ground = state(false, false, false, false, false, false, false, false, true, true);
        assertEquals("ground", PrimordialPose.baseLoop(Kind.TRILOCARIS, ground));
        State water = state(false, false, false, false, false, false, true, false, false, false);
        assertEquals("idle", PrimordialPose.baseLoop(Kind.TRILOCARIS, water));
    }

    @Test
    void tremorzillaBeamThenSwim() {
        State both = state(false, false, false, false, false, false, true, true, false, true);
        assertEquals("beam", PrimordialPose.baseLoop(Kind.TREMORZILLA, both));
        State swimming = state(false, false, false, false, false, false, true, false, false, true);
        assertEquals("swim", PrimordialPose.baseLoop(Kind.TREMORZILLA, swimming));
    }

    @Test
    void blowsFollowTheModGoals() {
        assertEquals("melee_bite", PrimordialPose.raptorBlow(true, true));
        assertEquals("melee_slash_2", PrimordialPose.raptorBlow(false, true));
        assertEquals("melee_slash_1", PrimordialPose.raptorBlow(false, false));
        assertEquals("melee_ram", PrimordialPose.grottoBlow(true, false));
        assertEquals("melee_tail_1", PrimordialPose.grottoBlow(false, true));
        assertEquals("melee_tail_2", PrimordialPose.grottoBlow(false, false));
        assertEquals("eat_trilocaris", PrimordialPose.relicheirusBlow(true, true));
        assertEquals("melee_slash_1", PrimordialPose.relicheirusBlow(false, true));
        assertEquals("melee_slash_2", PrimordialPose.relicheirusBlow(false, false));
    }

    @Test
    void tremorsaurusShakesSmallOrFlyingPreyUnlessBaby() {
        assertEquals("shake_prey", PrimordialPose.tremorsaurusBlow(false, 1.8, false, false));
        assertEquals("bite", PrimordialPose.tremorsaurusBlow(true, 1.8, false, false));
        assertEquals("bite", PrimordialPose.tremorsaurusBlow(false, 2.0, false, false));
        assertEquals("shake_prey", PrimordialPose.tremorsaurusBlow(true, 3.0, true, false));
        assertEquals("bite", PrimordialPose.tremorsaurusBlow(false, 1.0, true, true));
    }

    @Test
    void fidgetsAndSpeech() {
        assertEquals("scratch_1", PrimordialPose.raptorFidget(0.1F));
        assertEquals("scratch_2", PrimordialPose.raptorFidget(0.5F));
        assertEquals("shake", PrimordialPose.raptorFidget(0.95F));
        assertEquals("scratch_1", PrimordialPose.relicheirusFidget(0.1F));
        assertEquals("scratch_2", PrimordialPose.relicheirusFidget(0.2F));
        assertEquals("shake", PrimordialPose.relicheirusFidget(0.5F));
        assertEquals("call_2", PrimordialPose.speak(Kind.VALLUMRAPTOR, true, false, false));
        assertEquals("call_1", PrimordialPose.speak(Kind.VALLUMRAPTOR, true, true, false));
        assertNull(PrimordialPose.speak(Kind.VALLUMRAPTOR, true, false, true));
        assertEquals("speak_2", PrimordialPose.speak(Kind.GROTTOCERATOPS, true, false, false));
        assertEquals("speak", PrimordialPose.speak(Kind.TREMORSAURUS, false, false, false));
        assertNull(PrimordialPose.speak(Kind.TRILOCARIS, true, false, false));
    }

    @Test
    void sauropodAndTremorzillaActionsMapToModelAnimations() {
        assertEquals("left_kick", PrimordialPose.sauropod(SauropodRules.Anim.LEFT_KICK));
        assertEquals("epic_death", PrimordialPose.sauropod(SauropodRules.Anim.EPIC_DEATH));
        assertNull(PrimordialPose.sauropod(SauropodRules.Anim.LEFT_WHIP));
        assertNull(PrimordialPose.sauropod(SauropodRules.Anim.NONE));
        assertEquals("right_stomp", PrimordialPose.tremorzilla(TremorzillaRules.Anim.RIGHT_STOMP));
        assertEquals("prepare_breath", PrimordialPose.tremorzilla(TremorzillaRules.Anim.PREPARE_BREATH));
        assertNull(PrimordialPose.tremorzilla(TremorzillaRules.Anim.LEFT_TAIL));
    }

    @Test
    void actionLengthsMatchTheMod() {
        assertEquals(40, PrimordialPose.length(Kind.VALLUMRAPTOR, "shake"));
        assertEquals(55, PrimordialPose.length(Kind.TREMORSAURUS, "roar"));
        assertEquals(0, PrimordialPose.length(Kind.TRILOCARIS, "nothing"));
    }

    @Test
    void choreAnimationsHaveTheirModelLengths() {
        assertEquals(20, PrimordialPose.length(Kind.VALLUMRAPTOR, "startleap"));
        assertEquals(40, PrimordialPose.length(Kind.VALLUMRAPTOR, "grab"));
        assertEquals(40, PrimordialPose.length(Kind.RELICHEIRUS, "eat_tree"));
        assertEquals(60, PrimordialPose.length(Kind.RELICHEIRUS, "push_tree"));
        assertEquals(60, PrimordialPose.length(Kind.GROTTOCERATOPS, "chew_from_ground"));
    }
}
