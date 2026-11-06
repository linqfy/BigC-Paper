package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Integrations may veto an effect, dash, or altar before any mutation occurs. */
public final class LegendaryProtectionEvent extends Event implements Cancellable {
    public enum Action { DAMAGE, MOVE, ALTAR, BREAK }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Location location;
    private final LivingEntity target;
    private final Action action;
    private boolean cancelled;

    public LegendaryProtectionEvent(Player player, Location location, LivingEntity target, Action action) {
        this.player = player;
        this.location = location.clone();
        this.target = target;
        this.action = action;
    }

    /** Null for automatic altar generation; protection adapters must handle this case. */
    @org.jetbrains.annotations.Nullable public Player getPlayer() { return player; }
    public Location getLocation() { return location.clone(); }
    public LivingEntity getTarget() { return target; }
    public Action getAction() { return action; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
