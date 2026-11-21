package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicMobBehavioursTest {

    private final MythicMobCatalog catalog = MythicMobCatalog.defaults();

    @Test
    void theBlinkCasterTeleportsAwayOnlyWhenTheTargetIsClose() {
        assertTrue(MythicMobBehaviours.shouldBlink(0.0));
        assertTrue(MythicMobBehaviours.shouldBlink(MythicMobBehaviours.BLINK_TRIGGER - 0.01));
        assertFalse(MythicMobBehaviours.shouldBlink(MythicMobBehaviours.BLINK_TRIGGER));
        assertFalse(MythicMobBehaviours.shouldBlink(12.0));
        // reaparece lo bastante lejos como para no volver a teletransportarse enseguida
        assertTrue(MythicMobBehaviours.BLINK_RANGE > MythicMobBehaviours.BLINK_TRIGGER * 1.5);
    }

    @Test
    void theDjinnIsAnEliteBlinkCasterWithFireballs() {
        MythicMobType djinn = catalog.require("djinn");
        assertEquals(MythicBehaviour.BLINK_CASTER, djinn.behaviour());
        assertEquals(MythicProjectile.FIREBALL, djinn.projectile());
        assertTrue(djinn.elite() && djinn.floating());
        assertEquals(4.0 * 0.8, MythicMobBehaviours.cooldownSeconds(djinn), 1.0E-9);
    }

    @Test
    void everyBehaviourHasAPositiveCooldown() {
        for (MythicMobType mob : catalog.all()) {
            assertTrue(MythicMobBehaviours.cooldownSeconds(mob) > 0.0, mob.id());
        }
    }
}
