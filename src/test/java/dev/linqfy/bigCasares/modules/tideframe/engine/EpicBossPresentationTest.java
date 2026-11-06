package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.PassiveHudState;
import java.math.BigDecimal;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EpicBossPresentationTest {
    private static BossBar bar(){return BossBar.bossBar(Component.text("BEFORE"),.5f,BossBar.Color.GREEN,BossBar.Overlay.PROGRESS);}
    private static String text(Component component){
        StringBuilder result=new StringBuilder();
        if(component instanceof TextComponent value)value.content().chars().filter(c->c<128).forEach(c->result.append((char)c));
        component.children().forEach(child->result.append(text(child)));return result.toString();
    }
    @Test void normalChallengeUsesOriginalArtworkWithPinkFallbackAndActualPercentage(){
        var state=new PassiveHudState();state.setBoss("DEMO BOSS",75,100);var before=state.snapshot();var bar=bar();
        EpicBossPresentation.apply(bar,before);
        assertEquals("DEMO BOSS | 75%",text(bar.name()));
        assertEquals(Key.key("tideframe:boss"),bar.name().font());
        assertEquals(.75f,bar.progress());assertEquals(BossBar.Color.PINK,bar.color());
        assertEquals(BossBar.Overlay.PROGRESS,bar.overlay());assertTrue(bar.flags().isEmpty());
        assertTrue(((TextComponent)bar.name()).content().chars().anyMatch(c->c>=0xf000&&c<0xf054));
        assertEquals(before,state.snapshot());
    }
    @Test void enrageUsesTheActualThirtyPercentBoundaryNotRoundedDisplay(){
        var bar=bar();
        EpicBossPresentation.apply(bar,new PassiveHudState.Snapshot("BALANCE $1.00","AURORA",.30f,true));
        assertEquals(BossBar.Color.PURPLE,bar.color());assertEquals("AURORA | 30% | ENRAGED",text(bar.name()));
        EpicBossPresentation.apply(bar,new PassiveHudState.Snapshot("BALANCE $1.00","AURORA",Math.nextUp(.30f),true));
        assertEquals(BossBar.Color.PINK,bar.color());assertEquals("AURORA | 30%",text(bar.name()));
    }
    @Test void tinyPositiveProgressRemainsOnePercentAndZeroDoesNotInventVictory(){
        var bar=bar();var state=new PassiveHudState();
        EpicBossPresentation.apply(bar,new PassiveHudState.Snapshot("BALANCE $1.00","BOSS",Float.MIN_VALUE,true));
        assertEquals("BOSS | 1% | ENRAGED",text(bar.name()));assertEquals(Float.MIN_VALUE,bar.progress());
        state.setBoss("BOSS",1,Double.MAX_VALUE);assertEquals(0f,state.snapshot().bossProgress());
        EpicBossPresentation.apply(bar,state.snapshot());assertEquals("BOSS | 0% | ENRAGED",text(bar.name()));
        assertEquals(0f,bar.progress());
    }
    @Test void longNameIsShortenedOnlyForDisplayAndTheEntireHeadingStaysBounded(){
        var state=new PassiveHudState();state.setBoss("A".repeat(40),1,100);var bar=bar();
        EpicBossPresentation.apply(bar,state.snapshot());
        assertEquals("A".repeat(40),state.snapshot().bossTitle());
        assertEquals("A".repeat(15)+"... | 1% | ENRAGED",text(bar.name()));
        assertTrue(text(bar.name()).length()<=36);
    }
    @Test void laterBossAndBalanceUpdatesKeepTheirIndependentValues(){
        var state=new PassiveHudState();state.setBoss("AURORA",75,100);var bar=bar();EpicBossPresentation.apply(bar,state.snapshot());
        state.setBalance(new BigDecimal("4321.50"),false);EpicBossPresentation.apply(bar,state.snapshot());
        assertEquals("BALANCE $4321.50",state.snapshot().balanceText());assertEquals(.75f,bar.progress());
        state.setBoss("AURORA",25,100);EpicBossPresentation.apply(bar,state.snapshot());
        assertEquals(.25f,bar.progress());assertEquals(BossBar.Color.PURPLE,bar.color());
        assertEquals("BALANCE $4321.50",state.snapshot().balanceText());
    }
    @Test void fullAndHiddenBossSnapshotsDoNotAlterHealthOrRequestPlayerSideEffects(){
        var state=new PassiveHudState();state.setBoss("CHALLENGER",100,100);state.hideBoss();var before=state.snapshot();var bar=bar();
        EpicBossPresentation.apply(bar,before);
        assertEquals(1f,bar.progress());assertEquals("CHALLENGER | 100%",text(bar.name()));
        assertFalse(state.snapshot().bossVisible());assertEquals(before,state.snapshot());assertTrue(bar.flags().isEmpty());
    }
    @Test void invalidProgressIsRejectedBeforeChangingAnyNativeBarProperty(){
        var bar=bar();var originalName=bar.name();
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.1f,1.1f}){
            assertThrows(IllegalArgumentException.class,()->EpicBossPresentation.apply(bar,new PassiveHudState.Snapshot("BALANCE $1.00","BOSS",value,true)));
            assertSame(originalName,bar.name());assertEquals(.5f,bar.progress());
            assertEquals(BossBar.Color.GREEN,bar.color());assertEquals(BossBar.Overlay.PROGRESS,bar.overlay());
        }
    }
}
