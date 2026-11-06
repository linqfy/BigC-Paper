package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.KeyChallenge;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Boss-fight key prompts, shown as a transparent overlay while the player keeps fighting (no menu, free camera).
 * With a real boss entity, every hit deals real damage to it, every miss hurts the player, and the boss bar
 * follows the entity's health (sword hits and other players count too). Player-owned: call from the player's thread.
 */
final class BossChallenges {
    /** Plugin services the challenge needs, so this class stays free of plugin internals. */
    record Hooks(Function<String, Screen> screen, Function<Player, PassiveHudSession> overlay, Consumer<Player> closeOverlay,
                 Consumer<Player> closeMenu, BiConsumer<Player, String[]> sound, Function<Player, SchedulerBridge.Ticket> repeat,
                 Consumer<org.bukkit.event.Event> fire, java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightSettings> settings) { }
    static final int RESULT_TICKS = 80, MAX_DISTANCE = 64;
    private static final class Run {
        final KeyChallenge game; final LivingEntity boss; final String bossName; final double bossMax;
        SchedulerBridge.Ticket ticket; String frame; int endTicks;
        Run(KeyChallenge game, LivingEntity boss, String bossName, double bossMax) { this.game = game; this.boss = boss; this.bossName = bossName; this.bossMax = bossMax; }
    }
    private final Hooks hooks;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();

    BossChallenges(Hooks hooks) { this.hooks = hooks; }

    boolean active(Player player) { return runs.containsKey(player.getUniqueId()); }

    /** {@code boss} may be null: a practice boss with its own 100 health. */
    void start(Player player, LivingEntity boss, String name) {
        stop(player, TideframeChallengeEndEvent.Result.STOPPED);
        hooks.closeMenu().accept(player);
        var settings = hooks.settings().get();
        String bossName = sanitize(name != null ? name : boss != null ? displayName(boss) : settings.text("boss-name"));
        var run = new Run(new KeyChallenge(System.nanoTime() ^ player.getUniqueId().getLeastSignificantBits(), settings), boss, bossName,
                boss == null ? KeyChallenge.BOSS_MAX : maxHealth(boss));
        if (boss != null) run.game.syncBoss(boss.getHealth() / run.bossMax);
        runs.put(player.getUniqueId(), run);
        show(player, run);
        run.ticket = hooks.repeat().apply(player);
    }

    void stop(Player player) { stop(player, TideframeChallengeEndEvent.Result.STOPPED); }
    private void stop(Player player, TideframeChallengeEndEvent.Result result) {
        var run = runs.remove(player.getUniqueId());
        if (run == null) return;
        if (run.ticket != null) run.ticket.cancel();
        hooks.closeOverlay().accept(player);
        hooks.fire().accept(new TideframeChallengeEndEvent(player, run.boss, result, run.game.score(), run.game.hits(), run.game.misses()));
    }
    void stopAll() { for (var run : runs.values()) if (run.ticket != null) run.ticket.cancel(); runs.clear(); }

    /** Owner thread, every tick (from the repeat ticket). */
    void tick(Player player) {
        var run = runs.get(player.getUniqueId());
        if (run == null) return;
        if (!player.isOnline() || player.isDead()) { stop(player, TideframeChallengeEndEvent.Result.STOPPED); return; }
        var game = run.game;
        var before = game.phase();
        if (run.boss != null && !game.finished()) {
            if (run.boss.isDead()) game.syncBoss(0);
            else if (!run.boss.isValid() || run.boss.getWorld() != player.getWorld()
                    || run.boss.getLocation().distanceSquared(player.getLocation()) > MAX_DISTANCE * MAX_DISTANCE) {
                player.sendMessage("The boss got away.");
                stop(player, TideframeChallengeEndEvent.Result.STOPPED); return;
            } else game.syncBoss(run.boss.getHealth() / run.bossMax);
        }
        game.tick();
        var settings = game.settings();
        int step = game.countdownTick();
        if (step == 1 || step == 21 || step == 41) sound(player, settings.sound("countdown"), 1.4f);
        else if (step == 61) sound(player, settings.sound("go"), 1.2f);
        if (before != game.phase()) {
            if (game.phase() == KeyChallenge.Phase.WON) sound(player, settings.sound("win"), 1f);
            if (game.phase() == KeyChallenge.Phase.LOST) sound(player, settings.sound("lose"), .8f);
            if (game.phase() == KeyChallenge.Phase.PROMPT) sound(player, settings.sound("prompt"), 1.6f);
        }
        show(player, run);
        if (game.finished() && ++run.endTicks >= RESULT_TICKS)
            stop(player, game.phase() == KeyChallenge.Phase.WON ? TideframeChallengeEndEvent.Result.WON : TideframeChallengeEndEvent.Result.LOST);
    }

    /** Owner thread: a key press (or a swing for CLICK). Returns true when the challenge used it. */
    boolean key(Player player, KeyChallenge.Key key) {
        var run = runs.get(player.getUniqueId());
        if (run == null) return false;
        var outcome = run.game.press(key);
        if (outcome == KeyChallenge.Outcome.IGNORED) return false;
        if (outcome == KeyChallenge.Outcome.HIT) {
            if (run.boss != null && run.boss.isValid() && !run.boss.isDead())
                run.boss.damage(run.game.lastDamage() / (double) KeyChallenge.BOSS_MAX * run.bossMax, player);
            sound(player, run.game.settings().sound(run.game.bossHp() == 0 ? "final-hit" : "hit"), 1f + Math.min(run.game.combo(), 6) * .08f);
        } else {
            // A miss is the boss landing a blow (miss-damage, 2 = one heart), credited to the boss when there is one.
            double damage = run.game.settings().missDamage();
            if (run.boss != null && run.boss.isValid() && damage > 0) player.damage(damage, run.boss);
            sound(player, run.game.settings().sound("miss"), 1f);
        }
        show(player, run);
        return true;
    }

    /** Live values for the challenge overlay (the boss name and health follow the real entity). */
    Map<String, String> values(Player player) {
        var run = runs.get(player.getUniqueId());
        if (run == null) return Map.of();
        var out = new LinkedHashMap<>(run.game.values());
        out.put("boss_name", run.bossName);
        if (run.boss != null) {
            out.put("boss_hp", String.valueOf((int) Math.ceil(Math.max(0, run.boss.isDead() ? 0 : run.boss.getHealth()))));
            out.put("boss_max", String.valueOf((int) Math.round(run.bossMax)));
        }
        return out;
    }

    private void show(Player player, Run run) {
        String frame = run.game.frameId();
        if (frame.equals(run.frame)) return;
        var screen = hooks.screen().apply(frame);
        if (screen == null) return;
        try {
            var view = hooks.overlay().apply(player);
            view.showScreen(screen); view.refresh();
            run.frame = frame;
        } catch (IllegalArgumentException unavailable) {
            player.sendMessage(unavailable.getMessage());
            runs.remove(player.getUniqueId());
            if (run.ticket != null) run.ticket.cancel();
        }
    }
    private void sound(Player player, String id, float pitch) { if (id != null) hooks.sound().accept(player, new String[]{id, String.valueOf(pitch)}); }

    static String displayName(LivingEntity entity) {
        String custom = entity.getCustomName();
        if (custom != null && !custom.isBlank()) return custom.replaceAll("§.", "");
        return entity.getType().name().replace('_', ' ');
    }
    static String sanitize(String name) {
        var out = new StringBuilder();
        for (char c : name.toUpperCase(Locale.ROOT).toCharArray()) {
            if (out.length() >= 24) break;
            out.append(c == ' ' || dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.CHARSET.indexOf(c) >= 0 ? c : '?');
        }
        return out.toString().trim().isEmpty() ? KeyChallenge.BOSS_NAME : out.toString().trim();
    }
    static double maxHealth(LivingEntity entity) {
        for (String key : List.of("max_health", "generic.max_health")) {
            try {
                Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (attribute != null) { var instance = entity.getAttribute(attribute); if (instance != null) return Math.max(1, instance.getValue()); }
            } catch (RuntimeException | LinkageError ignored) { }
        }
        return Math.max(1, entity.getHealth());
    }
}
