package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Owner-thread state supplied by real plugin code; it does not pretend to query an economy. */
public final class PassiveHudState {
    public record Snapshot(String balanceText,String bossTitle,float bossProgress,boolean bossVisible,boolean balanceVisible) {
        public Snapshot(String balanceText,String bossTitle,float bossProgress,boolean bossVisible) {this(balanceText,bossTitle,bossProgress,bossVisible,true);}
    }
    private static final BigDecimal MAX_BALANCE=new BigDecimal("1000000000000");
    // Nothing claims the action bar until a balance is actually supplied.
    private Snapshot current=new Snapshot("","BOSS",0f,false,false);

    public Snapshot snapshot() {return current;}

    public void setBalance(BigDecimal amount,boolean demo) {
        if(amount==null || amount.signum()<0 || amount.compareTo(MAX_BALANCE)>0)
            throw new IllegalArgumentException("Balance must be between 0 and 1000000000000.");
        String text=(demo?"DEMO BALANCE $":"BALANCE $")+amount.setScale(2,RoundingMode.HALF_UP).toPlainString();
        current=new Snapshot(text,current.bossTitle(),current.bossProgress(),current.bossVisible(),true);
    }
    public void setBoss(String title,double hp,double maxHp) {
        if(title==null || !title.matches("[ -~]{1,40}") || !Double.isFinite(hp)
                || !Double.isFinite(maxHp) || maxHp<=0 || hp<0 || hp>maxHp)
            throw new IllegalArgumentException("Boss requires an ASCII title, finite maximum > 0, and 0 <= health <= maximum.");
        current=new Snapshot(current.balanceText(),title,(float)Math.max(0,hp/maxHp),true,current.balanceVisible());
    }
    public void hideBoss() {
        current=new Snapshot(current.balanceText(),current.bossTitle(),current.bossProgress(),false,current.balanceVisible());
    }
}
