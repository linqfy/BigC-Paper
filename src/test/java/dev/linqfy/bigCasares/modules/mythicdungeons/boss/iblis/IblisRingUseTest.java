package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IblisRingUseTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BETO = UUID.randomUUID();

    private static IblisVessels vessels() {
        return new IblisVessels(List.of(new IblisPoint(0, 0), new IblisPoint(10, 0)));
    }

    @Test
    void sneakingWithTheRingNextToAVesselArmsIt() {
        IblisRingUse use = new IblisRingUse();
        IblisVessels vessels = vessels();
        IblisPoint near = new IblisPoint(1.5, 0);
        assertEquals(IblisRingUse.Outcome.NONE, use.attempt(ANA, false, true, near, vessels, 0).outcome());
        assertEquals(IblisRingUse.Outcome.NONE, use.attempt(ANA, true, false, near, vessels, 0).outcome());
        IblisRingUse.Result armed = use.attempt(ANA, true, true, near, vessels, 0);
        assertEquals(IblisRingUse.Outcome.ARMED, armed.outcome());
        assertEquals(0, armed.vessel());
        assertEquals(IblisVessels.State.ARMED, vessels.state(0));
    }

    @Test
    void eachPlayerWaitsBeforeArmingAgainButOthersCanArmMeanwhile() {
        IblisRingUse use = new IblisRingUse();
        IblisVessels vessels = vessels();
        use.attempt(ANA, true, true, new IblisPoint(0, 1), vessels, 0);
        assertEquals(IblisRingUse.Outcome.VESSEL_BUSY,
            use.attempt(BETO, true, true, new IblisPoint(0, 1), vessels, 1).outcome());
        assertEquals(IblisRingUse.Outcome.RECHARGING,
            use.attempt(ANA, true, true, new IblisPoint(9, 0), vessels, 10).outcome());
        assertEquals(IblisRingUse.COOLDOWN_TICKS - 10, use.recharge(ANA, 10));
        assertEquals(IblisRingUse.Outcome.ARMED,
            use.attempt(BETO, true, true, new IblisPoint(9, 0), vessels, 10).outcome());
        assertEquals(IblisRingUse.Outcome.NO_VESSEL,
            use.attempt(ANA, true, true, new IblisPoint(5, 5), vessels, 200).outcome());
    }

    @Test
    void afterTheCooldownTheRingWorksAgain() {
        IblisRingUse use = new IblisRingUse();
        IblisVessels vessels = vessels();
        use.attempt(ANA, true, true, new IblisPoint(0, 1), vessels, 0);
        assertEquals(IblisRingUse.Outcome.ARMED, use.attempt(ANA, true, true, new IblisPoint(9, 1), vessels,
            IblisRingUse.COOLDOWN_TICKS).outcome());
        assertEquals(0, use.recharge(ANA, IblisRingUse.COOLDOWN_TICKS * 3));
    }
}
