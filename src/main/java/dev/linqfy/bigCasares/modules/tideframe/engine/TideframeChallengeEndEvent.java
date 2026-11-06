package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Fired on the player's thread when a boss key challenge ends (won, lost, or stopped). Use it to give rewards. */
public final class TideframeChallengeEndEvent extends PlayerEvent {
    public enum Result { WON, LOST, STOPPED }
    private static final HandlerList HANDLERS = new HandlerList();
    private final LivingEntity boss;
    private final Result result;
    private final int score, hits, misses;

    public TideframeChallengeEndEvent(Player player, LivingEntity boss, Result result, int score, int hits, int misses) {
        super(player);
        this.boss = boss; this.result = result; this.score = score; this.hits = hits; this.misses = misses;
    }
    /** The real boss entity, or null for the practice boss. */
    public LivingEntity getBoss() { return boss; }
    public Result getResult() { return result; }
    public int getScore() { return score; }
    public int getHits() { return hits; }
    public int getMisses() { return misses; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
