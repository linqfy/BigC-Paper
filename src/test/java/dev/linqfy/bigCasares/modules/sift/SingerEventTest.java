package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SingerEvent.Phase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingerEventTest {

    private static final SingerTimings TIMINGS = new SingerTimings(10, 20, 8);

    private static final class FakeGolem implements SoulExchange.Golem {
        final UUID id = UUID.randomUUID();
        boolean soul = true;
        boolean free = true;
        boolean alive = true;
        SiftPoint at = new SiftPoint(5, 0, 0);
        int bows;
        int finished;
        int faced;

        @Override public UUID id() { return id; }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean hasSoulBlock() { return soul; }
        @Override public boolean canInteractWithSinger() { return free; }
        @Override public SiftPoint position() { return at; }
        @Override public void beginBow() { bows++; }
        @Override public void finishBow() { finished++; }
        @Override public void faceToward(SiftPoint point) { faced++; }

        @Override
        public boolean transferSoulToSinger() {
            if (!soul) {
                return false;
            }
            soul = false;
            return true;
        }
    }

    private static final class FakeEnv implements SingerEvent.Env {
        FakeGolem golem;
        SiftPoint position = new SiftPoint(0, 0, 0);
        boolean named;
        boolean payment;
        boolean avoiding;
        boolean navigationDone = true;
        SiftPoint partner;
        final List<String> log = new ArrayList<>();

        @Override public SoulExchange.Golem golem(UUID id) { return golem != null && golem.id.equals(id) && golem.alive && golem.free ? golem : null; }
        @Override public boolean isLoaded(UUID id) { return golem != null && golem.id.equals(id); }
        @Override public SoulExchange.Golem nearestOfferingGolem() { return golem != null && golem.soul && golem.free ? golem : null; }
        @Override public double distanceSqrTo(SoulExchange.Golem g) { return position.distanceSqr(g.position()); }
        @Override public SiftPoint position() { return position; }
        @Override public Optional<SiftPoint> barterPartner(UUID player) { return Optional.ofNullable(partner); }
        @Override public boolean isNamed() { return named; }
        @Override public void playAppearEffects() { log.add("appear"); }
        @Override public void playDisappearEffects() { log.add("disappear"); }
        @Override public void becomeSolid() { log.add("solid"); }
        @Override public void becomeIntangible() { log.add("intangible"); }
        @Override public void stopNavigation() { navigationDone = true; }
        @Override public boolean navigationDone() { return navigationDone; }
        @Override public void moveToGolem(SoulExchange.Golem g, double speed) { navigationDone = false; log.add("walk " + speed); }
        @Override public void faceToward(SiftPoint point) { log.add("face"); }
        @Override public void lookAt(SoulExchange.Golem g) { log.add("look"); }
        @Override public void playHappy(float pitch) { log.add("happy"); }
        @Override public void holdSoulBlock(boolean holding) { log.add(holding ? "hold" : "release"); }
        @Override public boolean seekPayment() { return payment; }
        @Override public boolean avoidSoulCanyon() { return avoiding; }
        @Override public void roam(double speed) { navigationDone = false; log.add("roam " + speed); }
        @Override public void reward(UUID player, SiftPoint target) { log.add("reward " + (player != null) + " " + (target != null)); }
        @Override public void discard() { log.add("discard"); }
        @Override public void phaseChanged() { log.add("phase"); }
    }

    private static void tick(SingerEvent event, FakeEnv env, int times) {
        for (int index = 0; index < times; index++) {
            event.tick(env);
        }
    }

    private static SingerEvent risen(FakeEnv env, UUID golem) {
        SingerEvent event = new SingerEvent(TIMINGS, new Random(1));
        event.begin(golem, env);
        tick(event, env, TIMINGS.appearTicks());
        assertEquals(Phase.ROAMING, event.phase());
        return event;
    }

    @Test
    void sheRisesForTheAppearAnimationAndThenBecomesSolid() {
        FakeEnv env = new FakeEnv();
        SingerEvent event = new SingerEvent(TIMINGS, new Random(1));
        event.begin(null, env);
        assertTrue(event.isIntangible());
        tick(event, env, 1);
        assertEquals(1, env.log.stream().filter("appear"::equals).count(), "the emerge effects play on the first tick");
        tick(event, env, TIMINGS.appearTicks() - 2);
        assertEquals(Phase.APPEAR, event.phase());
        tick(event, env, 1);
        assertEquals(Phase.ROAMING, event.phase());
        assertTrue(env.log.contains("solid"));
        assertEquals(0, event.phaseTick());
    }

    @Test
    void roamingStartsWalkingAtTheModsSpeedAndSinksAfterThirtySeconds() {
        FakeEnv env = new FakeEnv();
        SingerEvent event = risen(env, null);
        tick(event, env, 1);
        assertTrue(env.log.contains("roam 0.78"));
        tick(event, env, SingerEvent.ROAM_TICKS - 1);
        assertEquals(Phase.DISAPPEAR, event.phase());
        tick(event, env, 1);
        assertEquals(1, env.log.stream().filter("disappear"::equals).count());
        tick(event, env, TIMINGS.disappearTicks() - 1);
        assertEquals(List.of("discard"), env.log.subList(env.log.size() - 1, env.log.size()));
    }

    @Test
    void permanentAndNamedSingersNeverLeave() {
        FakeEnv env = new FakeEnv();
        SingerEvent permanent = new SingerEvent(TIMINGS, new Random(1));
        permanent.beginPermanent(env);
        tick(permanent, env, TIMINGS.appearTicks() + SingerEvent.ROAM_TICKS + 50);
        assertEquals(Phase.ROAMING, permanent.phase());

        FakeEnv namedEnv = new FakeEnv();
        namedEnv.named = true;
        SingerEvent named = risen(namedEnv, null);
        tick(named, namedEnv, SingerEvent.ROAM_TICKS + 50);
        assertEquals(Phase.ROAMING, named.phase());
        assertFalse(namedEnv.log.contains("discard"));
    }

    @Test
    void aNamedSingerInTheMiddleOfHerExitStays() {
        FakeEnv env = new FakeEnv();
        SingerEvent event = risen(env, null);
        tick(event, env, SingerEvent.ROAM_TICKS);
        assertEquals(Phase.DISAPPEAR, event.phase());
        event.stay(env);
        assertEquals(Phase.ROAMING, event.phase());
        assertTrue(event.isPermanent());
    }

    @Test
    void sheWalksToAGolemThatIsCloseAndReceivesItsSoul() {
        FakeEnv env = new FakeEnv();
        FakeGolem golem = new FakeGolem();
        env.golem = golem;
        SingerEvent event = risen(env, golem.id);
        tick(event, env, 1);
        assertEquals(Phase.APPROACH, event.phase(), "the golem is 5 blocks away");

        tick(event, env, 1);
        assertTrue(env.log.contains("walk 0.92"));
        env.position = new SiftPoint(3, 0, 0);
        tick(event, env, 1);
        assertEquals(Phase.EXCHANGE, event.phase());
        assertEquals(1, golem.bows);

        tick(event, env, SingerEvent.SOUL_TRANSFER_TICK - 1);
        assertTrue(golem.soul);
        assertFalse(event.isHoldingSoul());
        tick(event, env, 1);
        assertFalse(golem.soul, "the soul changes hands on tick 22");
        assertTrue(event.isHoldingSoul());
        assertTrue(env.log.contains("happy"));
        tick(event, env, SingerEvent.SOUL_RELEASE_TICK - SingerEvent.SOUL_TRANSFER_TICK);
        assertFalse(event.isHoldingSoul(), "the block leaves her hands on tick 32");
        tick(event, env, SingerEvent.EXCHANGE_TICKS - SingerEvent.SOUL_RELEASE_TICK);
        assertEquals(Phase.ROAMING, event.phase());
        assertEquals(1, golem.finished);
        assertNull(event.golemId());
    }

    @Test
    void aFarGolemIsOnlyApproachedOnTheNextScan() {
        FakeEnv env = new FakeEnv();
        FakeGolem golem = new FakeGolem();
        golem.at = new SiftPoint(20, 0, 0);
        env.golem = golem;
        SingerEvent event = risen(env, null);
        tick(event, env, 1);
        assertEquals(Phase.ROAMING, event.phase(), "20 blocks is beyond 10");
        assertEquals(golem.id, event.golemId(), "but the scan on tick 1 found it");
        env.position = new SiftPoint(12, 0, 0);
        tick(event, env, 1);
        assertEquals(Phase.APPROACH, event.phase());
    }

    @Test
    void theApproachEndsWhenTheGolemLosesItsSoulOrIsTooFar() {
        FakeEnv env = new FakeEnv();
        FakeGolem golem = new FakeGolem();
        env.golem = golem;
        SingerEvent event = risen(env, golem.id);
        tick(event, env, 2);
        assertEquals(Phase.APPROACH, event.phase());
        golem.soul = false;
        tick(event, env, 1);
        assertEquals(Phase.ROAMING, event.phase());
        assertNull(event.golemId());

        golem.soul = true;
        event.offer(golem, env);
        assertEquals(golem.id, event.golemId());
        tick(event, env, 1);
        assertEquals(Phase.APPROACH, event.phase());
        golem.at = new SiftPoint(13, 0, 0);
        tick(event, env, 1);
        assertEquals(Phase.ROAMING, event.phase(), "beyond 12 blocks she gives up");
    }

    @Test
    void theExchangeIsCancelledIfTheGolemDropsItsSoulBeforeTheTransfer() {
        FakeEnv env = new FakeEnv();
        FakeGolem golem = new FakeGolem();
        golem.at = new SiftPoint(2, 0, 0);
        env.golem = golem;
        SingerEvent event = risen(env, golem.id);
        tick(event, env, 2);
        assertEquals(Phase.EXCHANGE, event.phase());
        golem.soul = false;
        tick(event, env, 1);
        assertEquals(Phase.ROAMING, event.phase());
        assertEquals(1, golem.finished);
    }

    @Test
    void aBondedGolemIsNeverAcceptedAndAnOfferNeedsASoul() {
        FakeEnv env = new FakeEnv();
        FakeGolem golem = new FakeGolem();
        SingerEvent event = risen(env, null);
        golem.free = false;
        event.offer(golem, env);
        assertNull(event.golemId());
        golem.free = true;
        golem.soul = false;
        event.offer(golem, env);
        assertNull(event.golemId());
        golem.soul = true;
        event.offer(golem, env);
        assertEquals(golem.id, event.golemId());
        // A second golem replaces the first only when she is roaming or the first is gone.
        env.golem = golem;
        FakeGolem other = new FakeGolem();
        event.offer(other, env);
        assertEquals(other.id, event.golemId(), "roaming Singers take the latest offer");
    }

    @Test
    void aBarterHoldsTheBlockForFortyTicksThenGivesTheGiftTowardThePlayer() {
        FakeEnv env = new FakeEnv();
        SingerEvent event = risen(env, null);
        assertTrue(event.canBarter(true));
        UUID player = UUID.randomUUID();
        SiftPoint where = new SiftPoint(4, 0, 4);
        event.beginBarter(player, where, env);
        assertEquals(Phase.BARTER, event.phase());
        assertTrue(event.isHoldingSoul());
        assertFalse(event.canBarter(true), "she takes one block at a time");
        assertTrue(env.log.contains("happy"));

        env.partner = new SiftPoint(6, 0, 6);
        tick(event, env, SingerEvent.BARTER_TICKS - 1);
        assertEquals(Phase.BARTER, event.phase());
        assertEquals(new SiftPoint(6, 0, 6), event.barterTarget(), "she turns to the player as they move");
        tick(event, env, 1);
        assertEquals(Phase.ROAMING, event.phase());
        assertFalse(event.isHoldingSoul());
        assertTrue(env.log.contains("reward true true"));
        assertNull(event.barterPlayer());
        assertTrue(event.canBarter(true));
        assertFalse(event.canBarter(false));
    }

    @Test
    void aBlockFromTheGroundHasNoPlayerToThank() {
        FakeEnv env = new FakeEnv();
        SingerEvent event = risen(env, null);
        event.beginBarter(null, new SiftPoint(1, 0, 1), env);
        tick(event, env, SingerEvent.BARTER_TICKS);
        assertTrue(env.log.contains("reward false true"));
        assertFalse(env.log.contains("face"), "she only faces a player");
    }

    @Test
    void thePaymentSearchAndTheCanyonAvoidanceTakePrecedenceWhileRoaming() {
        FakeEnv env = new FakeEnv();
        SingerEvent event = risen(env, null);
        env.payment = true;
        tick(event, env, 5);
        assertFalse(env.log.stream().anyMatch(entry -> entry.startsWith("roam")));
        env.payment = false;
        env.avoiding = true;
        tick(event, env, 5);
        assertFalse(env.log.stream().anyMatch(entry -> entry.startsWith("roam")));
        env.avoiding = false;
        tick(event, env, 1);
        assertTrue(env.log.stream().anyMatch(entry -> entry.startsWith("roam")));
    }

    @Test
    void theSavedStateComesBackWithTheHeldBlockWorkedOutFromTheTick() {
        FakeEnv env = new FakeEnv();
        UUID golem = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        SingerEvent event = new SingerEvent(TIMINGS, new Random(1));
        event.restore(Phase.BARTER, 12, golem, true, player, new SiftPoint(1, 2, 3));
        assertTrue(event.isHoldingSoul());
        assertEquals(12, event.phaseTick());
        assertEquals(player, event.barterPlayer());
        assertEquals(new SiftPoint(1, 2, 3), event.barterTarget());
        assertTrue(event.isPermanent());
        event.restore(Phase.BARTER, 40, golem, false, null, null);
        assertFalse(event.isHoldingSoul());
        event.restore(Phase.EXCHANGE, 25, golem, false, null, null);
        assertTrue(event.isHoldingSoul());
        event.restore(Phase.EXCHANGE, 32, golem, false, null, null);
        assertFalse(event.isHoldingSoul());
        event.restore(Phase.DISAPPEAR, 3, null, false, null, null);
        assertTrue(event.isIntangible());
        tick(event, env, 1);
        assertEquals(4, event.phaseTick());
    }
}
