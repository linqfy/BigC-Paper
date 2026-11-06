package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.PlaneProjection;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.util.Objects;
import java.util.function.Supplier;

/** Selects immutable screen glyphs. No entities, projection, image decode or I/O. */
public final class HudRenderer implements AutoCloseable {
    public interface Channel {
        void show(String glyphs);
        /** Canvas and cursor apart, so runtime layers and live text can go between them (the cursor stays on top). */
        default void show(String canvas, String cursor) { show(canvas + cursor); }
        void clear();
    }
    private final HudAssets assets;
    private Screen screen;
    private final Channel channel;
    private final Supplier<String> underlay;
    private String previous;
    private int ticksSinceSend;
    private boolean closed;
    /** Extra republish key: live text values drawn by the channel, outside the glyph string. */
    private volatile Supplier<String> liveKey = () -> "";
    public void setLiveKey(Supplier<String> key) { liveKey = Objects.requireNonNull(key); }
    /** Display size (quarters) whose native canvas glyphs this renderer emits; 4 = the 1x atlas. */
    private volatile int quarters = 4;
    /** True while the passive HUD shows only pages over the main one (toasts): the main canvas is left out. */
    private volatile boolean mainHidden;
    public void hideMain(boolean value) { mainHidden = value; previous = null; }
    public void setQuarters(int value) { quarters = value; previous = null; }

    public HudRenderer(HudAssets assets, Screen screen, Channel channel) {
        this(assets,screen,channel,()->"");
    }
    /** The supplied glyph underlay must use the same zero-advance HUD font as this renderer. */
    public HudRenderer(HudAssets assets, Screen screen, Channel channel,Supplier<String> underlay) {
        this.assets = Objects.requireNonNull(assets);
        assets.requireKnown(screen);
        this.screen = screen;
        this.channel = Objects.requireNonNull(channel);
        this.underlay = Objects.requireNonNull(underlay);
    }
    /** Validate before changing state; the next frame replaces the old frame without clearing. */
    public void replaceScreen(Screen screen) {
        assets.requireKnown(screen);
        if(closed)return;
        this.screen=screen;previous=null;ticksSinceSend=0;
    }
    public void frame(PlaneProjection.Point cursor, String hover, double progress) {
        if (closed) return;
        if (!Double.isFinite(progress)) throw new IllegalArgumentException("Invalid HUD progress");
        int step = Math.max(0, Math.min(8, (int)Math.floor(progress * 8)));
        String canvas = Objects.requireNonNull(underlay.get(),"HUD underlay returned null") + assets.frame(screen, hover, step, quarters);
        publish(canvas, assets.cursor(screen,cursor.x(), cursor.y()), false);
    }
    /** A persistent overlay has no cursor or entrance animation and does not require a camera. */
    public void passiveFrame() {passiveFrame(false);}
    public void passiveFrame(boolean force) {
        if(closed)return;
        publish(Objects.requireNonNull(underlay.get(),"HUD underlay returned null")+(mainHidden?"":assets.frame(screen,null,8,quarters)),"",force);
    }
    private void publish(String canvas,String cursor,boolean force) {
        // Titles expire after 15 client ticks; refresh even a stationary menu every 10 ticks.
        String key = canvas + '\u0002' + cursor + '\u0001' + liveKey.get();
        if (force || !key.equals(previous) || ++ticksSinceSend >= 10) {
            if (cursor.isEmpty()) channel.show(canvas); else channel.show(canvas, cursor);
            previous = key; ticksSinceSend = 0;
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true; channel.clear();
    }
    /** Folia shutdown may run on a different owner. Let the short native title TTL expire. */
    public void abandon() { closed = true; }
}
