package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryWindLaunchTest {
    @Test void launchReversesFallingWithoutErasingHorizontalMovementOrMutatingInput() {
        Vector before = new Vector(.25, -.8, -.4);
        Vector launch = LegendaryWindLaunch.velocity(before, 1.3);
        assertEquals(new Vector(.25, 1.3, -.4), launch);
        assertEquals(new Vector(.25, -.8, -.4), before);
    }

    @Test void strongerUpwardMomentumIsPreservedAndBoostDoesNotStack() {
        Vector fast = new Vector(0, 2, 0);
        assertEquals(fast, LegendaryWindLaunch.velocity(fast, 1.3));
        assertEquals(new Vector(0, 1.3, 0), LegendaryWindLaunch.velocity(new Vector(0, 1.3, 0), 1.3));
    }

    @Test void launchBuildsEnoughFallingDistanceForTheNativeMaceSmash() {
        double velocity = LegendaryWindLaunch.velocity(new Vector(), 1.3).getY();
        double height = 0;
        while (velocity > 0) {
            height += velocity;
            velocity = (velocity - .08) * .98;
        }
        assertTrue(height > 8 && height < 11, "Vertical impulse must enable a mace smash without an extreme launch");
    }
}
