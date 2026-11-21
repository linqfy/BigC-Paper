package dev.linqfy.bigCasares.modules.legendary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

class LegendaryAnimationTimelineTest {
    private LegendaryAnimationCatalog catalog() throws Exception {
        return LegendaryAnimationCatalog.load(
                new ByteArrayInputStream(
                        ("fps=20\n"
                                        + "bloodlust.idle_aura.frames=0,1,2\n"
                                        + "bloodlust.idle_aura.loop=true\n"
                                        + "bloodlust.ataque_tajo.frames=3,4\n"
                                        + "bloodlust.ataque_tajo.loop=false\n"
                                        + "bloodlust.estocada.frames=5,6,7\n"
                                        + "bloodlust.estocada.loop=false\n"
                                        + "windweaver.idle_aura.frames=0,8\n"
                                        + "windweaver.idle_aura.loop=true\n")
                                .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void idleLoopsAndAttackReturnsToTheCurrentIdleClock() throws Exception {
        var timeline = new LegendaryAnimationTimeline(catalog());
        var player = UUID.randomUUID();
        assertEquals(0, timeline.frame(player, LegendaryWeapon.BLOODLUST, 2, 100));
        assertEquals(2, timeline.frame(player, LegendaryWeapon.BLOODLUST, 2, 102));
        assertEquals(0, timeline.frame(player, LegendaryWeapon.BLOODLUST, 2, 103));
        timeline.play(player, LegendaryWeapon.BLOODLUST, 2, 104, 1, "ataque_tajo");
        assertEquals(3, timeline.frame(player, LegendaryWeapon.BLOODLUST, 2, 104));
        assertEquals(4, timeline.frame(player, LegendaryWeapon.BLOODLUST, 2, 105));
        assertEquals(0, timeline.frame(player, LegendaryWeapon.BLOODLUST, 2, 106));
    }

    @Test
    void abilitySequenceHasPriorityAndReachesEverySuppliedClip() throws Exception {
        var timeline = new LegendaryAnimationTimeline(catalog());
        var player = UUID.randomUUID();
        timeline.play(player, LegendaryWeapon.BLOODLUST, 0, 0, 2, "ataque_tajo", "estocada");
        timeline.play(player, LegendaryWeapon.BLOODLUST, 0, 1, 1, "ataque_tajo");
        assertEquals(4, timeline.frame(player, LegendaryWeapon.BLOODLUST, 0, 1));
        assertEquals(5, timeline.frame(player, LegendaryWeapon.BLOODLUST, 0, 2));
        assertEquals(7, timeline.frame(player, LegendaryWeapon.BLOODLUST, 0, 4));
        assertEquals(2, timeline.frame(player, LegendaryWeapon.BLOODLUST, 0, 5));
    }

    @Test
    void changingSlotOrWeaponAndForgettingPlayerCancelOldPlayback() throws Exception {
        var timeline = new LegendaryAnimationTimeline(catalog());
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        timeline.play(first, LegendaryWeapon.BLOODLUST, 0, 0, 2, "estocada");
        assertEquals(5, timeline.frame(first, LegendaryWeapon.BLOODLUST, 0, 0));
        assertEquals(0, timeline.frame(second, LegendaryWeapon.BLOODLUST, 0, 0));
        assertEquals(0, timeline.frame(first, LegendaryWeapon.BLOODLUST, 1, 1));
        assertEquals(0, timeline.frame(first, LegendaryWeapon.WINDWEAVER, 1, 2));
        timeline.forget(first);
        assertEquals(0, timeline.frame(first, LegendaryWeapon.BLOODLUST, 1, 4));
        assertEquals(0, timeline.frame(first, LegendaryWeapon.FROST_SCYTHE, 1, 5));
    }

    @Test
    void malformedCatalogCannotProduceFramesOutsideThePublishedManifest() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        LegendaryAnimationCatalog.load(
                                new ByteArrayInputStream(
                                        "fps=20\nbloodlust.bad.frames=-1\nbloodlust.bad.loop=false\n"
                                                .getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void staticIdleStillPlaysAttacksAndReturnsToRest() throws Exception {
        var timeline = new LegendaryAnimationTimeline(catalog());
        var player = UUID.randomUUID();
        assertEquals(0, timeline.frame(player, LegendaryWeapon.BLOODLUST, 0, 100, false));
        assertEquals(0, timeline.frame(player, LegendaryWeapon.BLOODLUST, 0, 102, false));
        timeline.play(player, LegendaryWeapon.BLOODLUST, 0, 103, 1, "ataque_tajo");
        assertEquals(3, timeline.frame(player, LegendaryWeapon.BLOODLUST, 0, 103, false));
        assertEquals(4, timeline.frame(player, LegendaryWeapon.BLOODLUST, 0, 104, false));
        assertEquals(0, timeline.frame(player, LegendaryWeapon.BLOODLUST, 0, 105, false));
    }
}
