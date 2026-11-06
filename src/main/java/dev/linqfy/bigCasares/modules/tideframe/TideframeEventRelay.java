package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeClickEvent;
import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeCloseEvent;
import dev.linqfy.bigCasares.modules.tideframe.engine.TideframeOpenEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Objects;

/**
 * Turns Tideframe's open, click and close events into {@link TideframeEventSink} calls. Names Tideframe types, so it is
 * registered by the embedded module. A click BigCasares handles is cancelled, so a
 * button never runs a Tideframe action (give, charge...) next to the BigCasares service doing the same thing.
 */
final class TideframeEventRelay implements Listener {
    private final TideframeEventSink sink;

    private TideframeEventRelay(TideframeEventSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Throws when Tideframe's events lack a method used here. */
    static Listener create(TideframeEventSink sink) throws NoSuchMethodException {
        TideframeApiShape.require(TideframeClickEvent.class, String.class, "getScreenId");
        TideframeApiShape.require(TideframeClickEvent.class, String.class, "getElementId");
        TideframeApiShape.require(TideframeClickEvent.class, int.class, "getRow");
        TideframeApiShape.require(TideframeClickEvent.class, boolean.class, "isRightClick");
        TideframeApiShape.require(TideframeClickEvent.class, boolean.class, "isShiftClick");
        TideframeApiShape.require(TideframeClickEvent.class, void.class, "setCancelled", boolean.class);
        TideframeApiShape.require(TideframeCloseEvent.class, String.class, "getScreenId");
        TideframeApiShape.require(TideframeCloseEvent.class, boolean.class, "isWholeSession");
        TideframeApiShape.require(TideframeOpenEvent.class, String.class, "getScreenId");
        TideframeApiShape.require(TideframeOpenEvent.class, void.class, "setCancelled", boolean.class);
        return new TideframeEventRelay(sink);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(TideframeOpenEvent event) {
        if (!sink.allowOpen(event.getPlayer(), event.getScreenId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(TideframeClickEvent event) {
        TideframeClick click = new TideframeClick(event.getPlayer().getUniqueId(), event.getScreenId(), event.getElementId(),
            event.getRow(), event.isRightClick(), event.isShiftClick());
        if (sink.click(event.getPlayer(), click)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(TideframeCloseEvent event) {
        sink.closed(event.getPlayer(), event.getScreenId(), event.isWholeSession());
    }
}
