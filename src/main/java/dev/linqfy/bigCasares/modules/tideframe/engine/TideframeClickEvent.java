package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the player's owning thread when they click a Tideframe button, before its action runs.
 * Cancel it to suppress the action (for example to handle the click yourself).
 *
 * <pre>{@code
 * @EventHandler public void onClick(TideframeClickEvent e) {
 *     if (e.getScreenId().equals("shop") && e.getElementId().equals("buy_sword")) { ...; e.setCancelled(true); }
 * }
 * }</pre>
 */
public final class TideframeClickEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String screenId, elementId, action, targetId;
    private final ClickType clickType;
    private final boolean shift;
    private boolean cancelled;
    public enum ClickType { LEFT, RIGHT }

    public TideframeClickEvent(Player player, String screenId, String elementId, String action) {
        this(player, screenId, elementId, action, ClickType.LEFT, false);
    }
    /** {@code elementId} may be a list row ("servers#2"): {@link #getElementId()} then gives "servers" and {@link #getRow()} 2. */
    public TideframeClickEvent(Player player, String screenId, String elementId, String action, ClickType clickType, boolean shift) {
        super(player);
        this.screenId = Objects.requireNonNull(screenId); this.targetId = Objects.requireNonNull(elementId);
        this.elementId = dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows.template(elementId);
        this.action = Objects.requireNonNull(action); this.clickType = Objects.requireNonNull(clickType); this.shift = shift;
    }
    public ClickType getClickType() { return clickType; }
    public boolean isLeftClick() { return clickType == ClickType.LEFT; }
    public boolean isRightClick() { return clickType == ClickType.RIGHT; }
    /** True when the player was sneaking (Shift) while clicking. */
    public boolean isShiftClick() { return shift; }
    /** List row that was clicked (0 = first visible row), or -1 for a plain element. */
    public int getRow() { return dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows.index(targetId); }
    /** Exactly what was clicked: an element id, or a list row such as "servers#2". */
    public String getTargetId() { return targetId; }
    /** Screen id, e.g. "visual_menu09". */
    public String getScreenId() { return screenId; }
    /** Clicked button id from the screen JSON. */
    public String getElementId() { return elementId; }
    /** The action that will run unless cancelled (close, open:id, message:text, command:/x, or empty). */
    public String getAction() { return action; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
