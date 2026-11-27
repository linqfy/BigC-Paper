package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MagneticAnimationTest {
    @Test
    void magnetronFormsOrRollsLeaningWhileMoving() {
        assertEquals("form", MagneticMobs.Magnetron.state(true, true));
        assertEquals("form", MagneticMobs.Magnetron.state(true, false));
        assertEquals("roll_lean", MagneticMobs.Magnetron.state(false, true));
        assertNull(MagneticMobs.Magnetron.state(false, false));
    }

    @Test
    void boundroidOnlyRestsOnTheGroundWhenStill() {
        assertEquals("ground", MagneticMobs.Boundroid.state(true, false));
        assertNull(MagneticMobs.Boundroid.state(true, true));
        assertNull(MagneticMobs.Boundroid.state(false, false));
    }
}
