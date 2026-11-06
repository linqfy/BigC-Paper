package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PassiveHudStateTest {
    @Test void defaultsClaimNeitherTheActionBarNorTheBossBar() {
        var snapshot=new PassiveHudState().snapshot();
        assertFalse(snapshot.balanceVisible(),"No balance is shown until a caller supplies one");
        assertEquals("",snapshot.balanceText());
        assertEquals("BOSS",snapshot.bossTitle());assertEquals(0f,snapshot.bossProgress());
        assertFalse(snapshot.bossVisible());
    }
    @Test void balanceUpdatesUseTwoDecimalHalfUpAndExplicitDemoLabelling() {
        var state=new PassiveHudState();state.setBalance(new BigDecimal("123.455"),false);
        assertEquals("BALANCE $123.46",state.snapshot().balanceText());
        state.setBalance(new BigDecimal("0.0049"),true);
        assertEquals("DEMO BALANCE $0.00",state.snapshot().balanceText());
        state.setBalance(new BigDecimal("1000000000000"),false);
        assertEquals("BALANCE $1000000000000.00",state.snapshot().balanceText());
    }
    @Test void rejectedBalancesCannotMutateAnyPreviouslyPublishedState() {
        var state=new PassiveHudState();state.setBoss("AURORA",50,100);
        var previous=state.snapshot();
        for(var amount:new BigDecimal[]{null,new BigDecimal("-0.0001"),new BigDecimal("1000000000000.001")}) {
            assertThrows(IllegalArgumentException.class,()->state.setBalance(amount,false));
            assertEquals(previous,state.snapshot());
        }
    }
    @Test void aBossUpdateIsARealProportionalValueAndHidePreservesItForLaterUse() {
        var state=new PassiveHudState();state.setBoss("AURORA SENTINEL",75,200);
        var showing=state.snapshot();assertTrue(showing.bossVisible());
        assertEquals("AURORA SENTINEL",showing.bossTitle());assertEquals(.375f,showing.bossProgress());
        state.hideBoss();var hidden=state.snapshot();assertFalse(hidden.bossVisible());
        assertEquals(showing.bossTitle(),hidden.bossTitle());assertEquals(showing.bossProgress(),hidden.bossProgress());
        assertFalse(hidden.balanceVisible());
        state.setBoss("DEFEATED",0,1);assertTrue(state.snapshot().bossVisible());assertEquals(0f,state.snapshot().bossProgress());
        state.setBoss("READY",Double.MAX_VALUE,Double.MAX_VALUE);assertEquals(1f,state.snapshot().bossProgress());
    }
    @Test void rejectedBossUpdatesLeaveTheLastSnapshotUntouched() {
        var state=new PassiveHudState();state.setBoss("VALID",1,2);var previous=state.snapshot();
        for(String title:new String[]{null,"","X".repeat(41),"not\nascii","á"}) {
            assertThrows(IllegalArgumentException.class,()->state.setBoss(title,1,2));
            assertEquals(previous,state.snapshot());
        }
        for(double[] hp:new double[][]{{-1,2},{3,2},{1,0},{0,-1},{Double.NaN,2},{1,Double.POSITIVE_INFINITY},{Double.NEGATIVE_INFINITY,2}}) {
            assertThrows(IllegalArgumentException.class,()->state.setBoss("VALID",hp[0],hp[1]));
            assertEquals(previous,state.snapshot());
        }
    }
    @Test void immutableSnapshotsRemainStableAfterLaterStateChanges() {
        var state=new PassiveHudState();var initial=state.snapshot();
        state.setBalance(BigDecimal.TEN,false);state.setBoss("BOSS TWO",1,2);
        assertFalse(initial.balanceVisible());assertTrue(state.snapshot().balanceVisible());assertFalse(initial.bossVisible());
    }
}
