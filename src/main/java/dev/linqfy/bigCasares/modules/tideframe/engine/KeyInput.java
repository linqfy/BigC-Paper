package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.KeyChallenge;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Movement keys while a fixed-camera menu is open. Paper 1.21.3+ (including 26.x) sends every key change as
 * PlayerInputEvent: we read it reflectively (the plugin compiles against an older API) and report key-down edges.
 * Older servers fall back to the direction of the (cancelled) movement attempts.
 */
final class KeyInput implements Listener {
    private static final int FORWARD = 1, BACKWARD = 2, LEFT = 4, RIGHT = 8, JUMP = 16;
    private final BiConsumer<Player, KeyChallenge.Key> sink;
    private final Predicate<Player> listening;
    private final Map<UUID, Integer> held = new ConcurrentHashMap<>();
    private final Map<UUID, long[]> fallbackLast = new ConcurrentHashMap<>();
    private Method getInput, forward, backward, left, right, jump;
    private boolean nativeInput;

    KeyInput(BiConsumer<Player, KeyChallenge.Key> sink, Predicate<Player> listening) { this.sink = sink; this.listening = listening; }

    /** Returns true when the precise input event is available. */
    boolean register(Plugin plugin) {
        var manager = plugin.getServer().getPluginManager();
        try {
            Class<? extends Event> type = Class.forName("org.bukkit.event.player.PlayerInputEvent").asSubclass(Event.class);
            getInput = type.getMethod("getInput");
            Class<?> input = getInput.getReturnType();
            forward = input.getMethod("isForward"); backward = input.getMethod("isBackward");
            left = input.getMethod("isLeft"); right = input.getMethod("isRight"); jump = input.getMethod("isJump");
            manager.registerEvent(type, this, EventPriority.MONITOR, (listener, event) -> onInput(event), plugin, false);
            nativeInput = true;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException unavailable) {
            manager.registerEvent(PlayerMoveEvent.class, this, EventPriority.LOWEST, (listener, event) -> {
                if (event instanceof PlayerMoveEvent move) onMove(move);
            }, plugin, false);
        }
        manager.registerEvent(PlayerQuitEvent.class, this, EventPriority.MONITOR, (listener, event) -> {
            if (event instanceof PlayerQuitEvent quit) { held.remove(quit.getPlayer().getUniqueId()); fallbackLast.remove(quit.getPlayer().getUniqueId()); }
        }, plugin, false);
        return nativeInput;
    }
    void unregister() { HandlerList.unregisterAll(this); held.clear(); fallbackLast.clear(); }

    private void onInput(Event event) {
        if (!(event instanceof PlayerEvent playerEvent)) return;
        Player player = playerEvent.getPlayer();
        try {
            Object input = getInput.invoke(event);
            int now = (on(forward, input) ? FORWARD : 0) | (on(backward, input) ? BACKWARD : 0) | (on(left, input) ? LEFT : 0)
                    | (on(right, input) ? RIGHT : 0) | (on(jump, input) ? JUMP : 0);
            int before = held.getOrDefault(player.getUniqueId(), 0);
            held.put(player.getUniqueId(), now);
            if (!listening.test(player)) return;
            int pressed = now & ~before;
            if ((pressed & FORWARD) != 0) sink.accept(player, KeyChallenge.Key.W);
            if ((pressed & LEFT) != 0) sink.accept(player, KeyChallenge.Key.A);
            if ((pressed & BACKWARD) != 0) sink.accept(player, KeyChallenge.Key.S);
            if ((pressed & RIGHT) != 0) sink.accept(player, KeyChallenge.Key.D);
            if ((pressed & JUMP) != 0) sink.accept(player, KeyChallenge.Key.SPACE);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
    private static boolean on(Method method, Object input) throws ReflectiveOperationException { return (Boolean) method.invoke(input); }

    /** Fallback: classify each movement attempt relative to where the player faces; a held key repeats at most every 0.4 s. */
    private void onMove(PlayerMoveEvent move) {
        Player player = move.getPlayer();
        if (move.getTo() == null || !listening.test(player)) return;
        double dx = move.getTo().getX() - move.getFrom().getX(), dz = move.getTo().getZ() - move.getFrom().getZ(), dy = move.getTo().getY() - move.getFrom().getY();
        KeyChallenge.Key key = null;
        if (dy > .2) key = KeyChallenge.Key.SPACE;
        else if (dx * dx + dz * dz > 1e-4) {
            double yaw = Math.toRadians(move.getFrom().getYaw());
            double forwardAmount = -Math.sin(yaw) * dx + Math.cos(yaw) * dz, sideAmount = Math.cos(yaw) * dx + Math.sin(yaw) * dz;
            key = Math.abs(forwardAmount) >= Math.abs(sideAmount) ? (forwardAmount > 0 ? KeyChallenge.Key.W : KeyChallenge.Key.S)
                    : (sideAmount > 0 ? KeyChallenge.Key.A : KeyChallenge.Key.D);
        }
        if (key == null) return;
        long now = System.nanoTime();
        long[] last = fallbackLast.computeIfAbsent(player.getUniqueId(), id -> new long[KeyChallenge.Key.values().length]);
        if (now - last[key.ordinal()] < 400_000_000L) return;
        last[key.ordinal()] = now;
        sink.accept(player, key);
    }
}
