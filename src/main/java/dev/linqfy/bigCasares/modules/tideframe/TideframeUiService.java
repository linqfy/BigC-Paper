package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * {@link TideframeUi} over a connected {@link TideframeBridge}, and the {@link TideframeEventSink} that routes clicks
 * to the feature owning each {@code bigc_} screen. Screens of other plugins are never handled, cancelled or closed, and
 * BigCasares screens open only through BigCasares.
 */
final class TideframeUiService implements TideframeUi, TideframeEventSink {
    /**
     * Longer than Tideframe's own two minutes of waiting for its pack. An opening Tideframe dropped without answering
     * (the player died or left before it ran) stops being allowed after this.
     */
    static final long OPENING_NANOS = 125_000_000_000L;

    /** A screen BigCasares is opening for a player, until Tideframe answers or {@code deadline} passes. */
    private record Opening(UUID player, String screen, long deadline, Runnable prepare, AtomicBoolean prepared) {
    }

    private record DeferredFallback(long ticket, Consumer<Boolean> opened) {
    }

    private final TideframeBridge bridge;
    private final ClientPlatformGateway platforms;
    private final BooleanSupplier ownPack;
    private final Consumer<Runnable> mainThread;
    private final Consumer<LinkageError> incompatible;
    private final LongSupplier clock;
    private final Map<String, TideframeScreenHandler> handlers = new ConcurrentHashMap<>();
    private final Map<Long, Opening> opening = new ConcurrentHashMap<>();
    /** Each player's latest opening: an older one answering later must not fall back over the newer screen. */
    private final Map<UUID, Long> latest = new ConcurrentHashMap<>();
    private final Map<UUID, DeferredFallback> deferredFallbacks = new ConcurrentHashMap<>();
    private final AtomicLong tickets = new AtomicLong();
    private final AtomicBoolean broken = new AtomicBoolean();
    private final Map<TideframeStatus.Fallback, java.util.concurrent.atomic.LongAdder> fallbacks = new ConcurrentHashMap<>();

    TideframeUiService(TideframeBridge bridge, ClientPlatformGateway platforms, BooleanSupplier ownPack,
                       Consumer<Runnable> mainThread, Consumer<LinkageError> incompatible) {
        this(bridge, platforms, ownPack, mainThread, incompatible, System::nanoTime);
    }

    /**
     * {@code ownPack}: Tideframe sends its own pack, so "pack loaded" means that pack. {@code mainThread} runs a task on
     * the server thread later and drops it once this BigCasares runtime is gone. {@code incompatible} hears, once, the
     * first call that found Tideframe's API different from the one BigCasares was built against; from then on nothing
     * is called and every feature keeps its own menus.
     */
    TideframeUiService(TideframeBridge bridge, ClientPlatformGateway platforms, BooleanSupplier ownPack,
                       Consumer<Runnable> mainThread, Consumer<LinkageError> incompatible, LongSupplier clock) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.platforms = Objects.requireNonNull(platforms, "platforms");
        this.ownPack = Objects.requireNonNull(ownPack, "ownPack");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.incompatible = Objects.requireNonNull(incompatible, "incompatible");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The engine's state for an operator ({@link TideframeStatus}); {@code screens}: the BigCasares screens expected. */
    TideframeStatus status(Collection<? extends Player> online, Collection<String> screens, boolean missions, boolean shop) {
        int javaPlayers = 0, withPack = 0, bedrock = 0;
        List<String> missing = new ArrayList<>();
        try {
            var loaded = bridge.screenIds();
            for (String screen : screens) {
                if (!loaded.contains(screen)) missing.add(screen);
            }
            for (Player player : online) {
                if (platforms.resolvePlatform(player.getUniqueId()) != ClientPlatform.JAVA) {
                    bedrock++;
                } else {
                    javaPlayers++;
                    if (bridge.isPackLoaded(player)) withPack++;
                }
            }
        } catch (LinkageError changed) {
            incompatible(changed);
        }
        Map<TideframeStatus.Fallback, Long> counted = new java.util.EnumMap<>(TideframeStatus.Fallback.class);
        fallbacks.forEach((reason, count) -> counted.put(reason, count.sum()));
        return new TideframeStatus(true, broken.get(), ownPack.getAsBoolean(), missions, shop, missing, javaPlayers, withPack,
            bedrock, counted);
    }

    @Override
    public boolean canShow(Player player, String screenId) {
        if (player == null || !player.isOnline() || screenId == null || !screenId.startsWith(PREFIX)) {
            return false;
        }
        if (broken.get()) {
            return fellBack(TideframeStatus.Fallback.API_CHANGED);
        }
        // Bedrock keeps BigCasares' forms: Tideframe's Java screens and fonts do not exist there.
        if (platforms.resolvePlatform(player.getUniqueId()) != ClientPlatform.JAVA) {
            return fellBack(TideframeStatus.Fallback.BEDROCK);
        }
        // A Tideframe menu holds the camera still: not while gliding, riding, falling or dead.
        if (player.isDead() || player.isInsideVehicle() || player.isGliding() || !(player.isOnGround() || player.isFlying())) {
            return fellBack(TideframeStatus.Fallback.MOVING);
        }
        try {
            if (!ownPack.getAsBoolean()) return fellBack(TideframeStatus.Fallback.NO_OWN_PACK);
            if (!bridge.screenIds().contains(screenId)) return fellBack(TideframeStatus.Fallback.SCREEN_MISSING);
            if (!bridge.isPackLoaded(player)) return fellBack(TideframeStatus.Fallback.PACK_NOT_LOADED);
            return true;
        } catch (LinkageError changed) {
            incompatible(changed);
            return fellBack(TideframeStatus.Fallback.API_CHANGED);
        }
    }

    /** Counts why a BigCasares menu takes its classic form (for the status command); always false. */
    private boolean fellBack(TideframeStatus.Fallback reason) {
        fallbacks.computeIfAbsent(reason, ignored -> new java.util.concurrent.atomic.LongAdder()).increment();
        return false;
    }

    @Override
    public void open(Player player, String screenId, Runnable prepare, Consumer<Boolean> opened) {
        if (broken.get()) {
            opened.accept(false);
            return;
        }
        UUID playerId = player.getUniqueId();
        deferredFallbacks.remove(playerId);
        long ticket = tickets.incrementAndGet();
        latest.put(playerId, ticket);
        Opening request = new Opening(playerId, screenId, clock.getAsLong() + OPENING_NANOS, prepare, new AtomicBoolean());
        opening.put(ticket, request);
        CompletableFuture<Boolean> future;
        try {
            future = bridge.openWhenReady(player, screenId);
        } catch (RuntimeException | LinkageError failure) {
            opening.remove(ticket);
            if (failure instanceof LinkageError changed) {
                incompatible(changed);
            }
            fellBack(TideframeStatus.Fallback.OPEN_FAILED);
            opened.accept(false);
            return;
        }
        future.whenComplete((result, error) -> {
            opening.remove(ticket);
            mainThread.accept(() -> {
                // A newer opening for this player decides what they see; this one has nothing to fall back from.
                if (!Objects.equals(latest.get(playerId), ticket)) {
                    opened.accept(true);
                    return;
                }
                // Tideframe also answers true when the menu did not open but the player has a HUD page up (its answer
                // counts overlays), so the screen itself is checked: otherwise the player would get neither menu.
                boolean success = request.prepared().get() && error == null && Boolean.TRUE.equals(result)
                    && player.isOnline() && showing(player, screenId);
                if (!success && player.isOnline()) {
                    fellBack(TideframeStatus.Fallback.OPEN_FAILED);
                }
                if (!success && player.isOnline() && menuOpen(player)) {
                    deferredFallbacks.put(playerId, new DeferredFallback(ticket, opened));
                    player.sendMessage(ChatColor.YELLOW + "Cerrá la pantalla actual para abrir el menú clásico.");
                } else {
                    opened.accept(success);
                }
            });
        });
    }

    @Override
    public long navigationVersion(Player player) {
        return latest.getOrDefault(player.getUniqueId(), 0L);
    }

    @Override
    public void openClassic(Player player, Runnable open) {
        UUID playerId = player.getUniqueId();
        long ticket = tickets.incrementAndGet();
        latest.put(playerId, ticket);
        deferredFallbacks.remove(playerId);
        opening.values().removeIf(request -> request.player().equals(playerId));
        if (!player.isOnline()) {
            return;
        }
        if (menuOpen(player)) {
            deferredFallbacks.put(playerId, new DeferredFallback(ticket, ignored -> open.run()));
            player.sendMessage(ChatColor.YELLOW + "Cerrá la pantalla actual para abrir el menú clásico.");
        } else {
            open.run();
        }
    }

    @Override
    public void setVariable(Player player, String name, String value) {
        // "*": the player's global variable, readable from the screen before and after it opens.
        String global = "*" + owned(name);
        call(() -> bridge.setVariable(player, global, value == null ? "" : value));
    }

    @Override
    public void setList(Player player, String source, List<Map<String, String>> rows) {
        String list = owned(source);
        List<Map<String, String>> copy = List.copyOf(rows);
        call(() -> bridge.setList(player, list, copy));
    }

    @Override
    public boolean showing(Player player, String screenId) {
        if (broken.get()) {
            return false;
        }
        try {
            return bridge.currentScreen(player).filter(screenId::equals).isPresent();
        } catch (LinkageError changed) {
            incompatible(changed);
            return false;
        }
    }

    @Override
    public boolean menuOpen(Player player) {
        if (broken.get()) {
            return false;
        }
        try {
            return bridge.currentScreen(player).isPresent();
        } catch (LinkageError changed) {
            incompatible(changed);
            return false;
        }
    }

    @Override
    public void close(Player player, String screenId) {
        if (showing(player, screenId)) {
            call(() -> bridge.closeScreen(player));
        }
    }

    @Override
    public AutoCloseable handle(String screenId, TideframeScreenHandler handler) {
        owned(screenId);
        Objects.requireNonNull(handler, "handler");
        handlers.put(screenId, handler);
        return () -> handlers.remove(screenId, handler);
    }

    @Override
    public boolean allowOpen(Player player, String screenId) {
        if (screenId == null || !screenId.startsWith(PREFIX)) {
            return true;
        }
        long now = clock.getAsLong();
        opening.values().removeIf(open -> now - open.deadline() > 0);
        UUID playerId = player.getUniqueId();
        Long ticket = latest.get(playerId);
        Opening request = ticket == null ? null : opening.get(ticket);
        if (request != null && request.screen().equals(screenId) && canShow(player, screenId)) {
            try {
                request.prepare().run();
                request.prepared().set(true);
                return true;
            } catch (RuntimeException | LinkageError failure) {
                if (failure instanceof LinkageError changed) {
                    incompatible(changed);
                }
                return false;
            }
        }
        // Tideframe opening a BigCasares screen by itself (after a reload, /tideframe open, the editor): it would show no
        // data and skip BigCasares' checks.
        player.sendMessage(ChatColor.YELLOW + "Ese menú de BigCasares se abre con su comando.");
        return false;
    }

    @Override
    public boolean click(Player player, TideframeClick click) {
        if (!click.screenId().startsWith(PREFIX)) {
            return false;
        }
        TideframeScreenHandler handler = handlers.get(click.screenId());
        return handler != null && handler.click(player, click);
    }

    @Override
    public void closed(Player player, String screenId, boolean wholeSession) {
        // Replaced by another screen, often the same one opened again by BigCasares: Tideframe keeps the player's
        // variables and lists, so what each feature remembers still matches what the screens show.
        if (!wholeSession) {
            return;
        }
        // The whole UI closed, whatever screen was last: Tideframe has forgotten the player's variables and lists.
        forgetViews(player);
        DeferredFallback fallback = deferredFallbacks.remove(player.getUniqueId());
        if (fallback != null) {
            mainThread.accept(() -> {
                if (player.isOnline() && Objects.equals(latest.get(player.getUniqueId()), fallback.ticket()) && !menuOpen(player)) {
                    fallback.opened().accept(false);
                }
            });
        }
    }

    /** The player left: nothing BigCasares was opening for them stays allowed, and every feature forgets them. */
    void quit(Player player) {
        UUID playerId = player.getUniqueId();
        opening.values().removeIf(open -> open.player().equals(playerId));
        latest.remove(playerId);
        deferredFallbacks.remove(playerId);
        forgetViews(player);
    }

    /** Closes the BigCasares screens still open (this runtime is going away); other plugins' screens stay. */
    void closeOwnScreens(Collection<? extends Player> players) {
        for (Player player : players) {
            call(() -> bridge.currentScreen(player).filter(id -> id.startsWith(PREFIX)).ifPresent(id -> bridge.closeScreen(player)));
        }
    }

    private void forgetViews(Player player) {
        for (TideframeScreenHandler handler : handlers.values()) {
            handler.closed(player);
        }
    }

    private void call(Runnable action) {
        if (broken.get()) {
            return;
        }
        try {
            action.run();
        } catch (LinkageError changed) {
            incompatible(changed);
        }
    }

    private void incompatible(LinkageError changed) {
        if (broken.compareAndSet(false, true)) {
            incompatible.accept(changed);
        }
    }

    private static String owned(String name) {
        if (name == null || !name.startsWith(PREFIX)) {
            throw new IllegalArgumentException("BigCasares Tideframe names start with " + PREFIX + ": " + name);
        }
        return name;
    }
}
