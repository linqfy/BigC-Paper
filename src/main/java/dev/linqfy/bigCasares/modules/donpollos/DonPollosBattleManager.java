package dev.linqfy.bigCasares.modules.donpollos;

import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Batallas de baile contra el Don Pollo Salsero (boton PVP). */
final class DonPollosBattleManager implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final long INTRO_TICKS = 60;

    private final JavaPlugin plugin;
    private final DonPollosModelBinder binder;
    private final DonPollosBattleSettings settings;
    private final List<String> dances;
    private final Consumer<Villager> restoreBehaviour;
    private final Consumer<Villager> onWin;
    private final Map<UUID, Session> byPlayer = new HashMap<>();
    private final Map<UUID, UUID> bySalsero = new HashMap<>();
    private BukkitTask task;

    DonPollosBattleManager(JavaPlugin plugin, DonPollosModelBinder binder, DonPollosBattleSettings settings,
                           List<String> dances, Consumer<Villager> restoreBehaviour, Consumer<Villager> onWin) {
        this.plugin = plugin;
        this.binder = binder;
        this.settings = settings;
        this.dances = dances;
        this.restoreBehaviour = restoreBehaviour;
        this.onWin = onWin;
    }

    void startTicking() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    boolean isBattling(UUID playerId) {
        return byPlayer.containsKey(playerId);
    }

    boolean isBusy(UUID salseroId) {
        return bySalsero.containsKey(salseroId);
    }

    // ------------------------------------------------------------ inicio

    void start(Player player, Villager salsero) {
        if (isBattling(player.getUniqueId())) {
            player.sendMessage("§cYa estas en una batalla de baile.");
            return;
        }
        if (isBusy(salsero.getUniqueId())) {
            player.sendMessage("§cEste Salsero ya esta bailando contra otro.");
            return;
        }
        long now = Bukkit.getCurrentTick();
        DonPollosDanceBattle battle = new DonPollosDanceBattle(settings,
            bound -> ThreadLocalRandom.current().nextInt(bound), now, INTRO_TICKS);
        BossBar bar = BossBar.bossBar(text("§d§lDon Pollo Salsero §7- ¡Batalla de baile!"), 1f,
            BossBar.Color.PINK, BossBar.Overlay.NOTCHED_12);
        Session session = new Session(player.getUniqueId(), salsero, battle, bar);
        session.lastInput = player.getCurrentInput();
        byPlayer.put(player.getUniqueId(), session);
        bySalsero.put(salsero.getUniqueId(), player.getUniqueId());

        salsero.getPathfinder().stopPathfinding();
        salsero.setAI(false);
        face(salsero, player);
        player.showBossBar(bar);
        player.showTitle(Title.title(text("§d§l¡A BAILAR!"), text("§7Copia los pasos que te pide el Salsero"),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(400))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        player.sendMessage("§d¡Batalla de baile! §7Hace el paso que aparece en pantalla (W/A/S/D, saltar o agacharte). "
            + "Tenes §c" + settings.lives() + " vidas§7.");
        playNextDance(session);
    }

    // ------------------------------------------------------------ bailes encadenados

    private void playNextDance(Session session) {
        if (!session.active() || dances.isEmpty()) {
            return;
        }
        String next = dances.get(ThreadLocalRandom.current().nextInt(dances.size()));
        if (dances.size() > 1) {
            while (next.equals(session.dance)) {
                next = dances.get(ThreadLocalRandom.current().nextInt(dances.size()));
            }
        }
        String chosen = next;
        session.dance = chosen;
        UUID salseroId = session.salsero.getUniqueId();
        binder.playOnce(salseroId, chosen, () -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (session.active() && chosen.equals(session.dance)) {
                playNextDance(session);
            }
        }));
    }

    private void stopDance(Session session) {
        if (session.dance != null) {
            binder.stopAnimation(session.salsero.getUniqueId(), session.dance);
            session.dance = null;
        }
    }

    // ------------------------------------------------------------ juego

    private void tick() {
        long now = Bukkit.getCurrentTick();
        for (Session session : new ArrayList<>(byPlayer.values())) {
            if (!session.active()) {
                continue;
            }
            Player player = Bukkit.getPlayer(session.playerId);
            if (player == null || !session.salsero.isValid()) {
                end(session, null);
                continue;
            }
            if (player.getWorld() != session.salsero.getWorld()
                || player.getLocation().distanceSquared(session.salsero.getLocation())
                    > settings.maxDistance() * settings.maxDistance()) {
                player.sendMessage("§eTe fuiste de la pista: la batalla de baile se corto.");
                end(session, player);
                continue;
            }
            if (now % 10 == 0) {
                face(session.salsero, player);
            }
            handle(session, player, session.battle.tick(now), now);
            actionBar(session, player, now);
        }
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Session session = byPlayer.get(event.getPlayer().getUniqueId());
        if (session == null || !session.active()) {
            return;
        }
        Input now = event.getInput();
        Input before = session.lastInput;
        session.lastInput = now;
        Set<DonPollosDanceMove> pressed = EnumSet.noneOf(DonPollosDanceMove.class);
        if (now.isJump() && (before == null || !before.isJump())) pressed.add(DonPollosDanceMove.JUMP);
        if (now.isSneak() && (before == null || !before.isSneak())) pressed.add(DonPollosDanceMove.SNEAK);
        if (now.isLeft() && (before == null || !before.isLeft())) pressed.add(DonPollosDanceMove.LEFT);
        if (now.isRight() && (before == null || !before.isRight())) pressed.add(DonPollosDanceMove.RIGHT);
        if (now.isForward() && (before == null || !before.isForward())) pressed.add(DonPollosDanceMove.FORWARD);
        if (now.isBackward() && (before == null || !before.isBackward())) pressed.add(DonPollosDanceMove.BACKWARD);
        long tick = Bukkit.getCurrentTick();
        handle(session, event.getPlayer(), session.battle.input(tick, pressed), tick);
    }

    private void handle(Session session, Player player, DonPollosDanceBattle.Outcome outcome, long now) {
        DonPollosDanceBattle battle = session.battle;
        switch (outcome) {
            case PROMPT -> {
                DonPollosDanceMove move = battle.current();
                long stay = Math.max(1, battle.windowTicks() - 4) * 50L;
                player.showTitle(Title.title(text(move.title()), text(move.arrows()),
                    Title.Times.times(Duration.ofMillis(60), Duration.ofMillis(stay), Duration.ofMillis(150))));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
            }
            case HIT -> {
                session.bar.progress(battle.salseroHealth());
                player.showTitle(Title.title(text("§a§l✔ ¡BIEN!"), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(350), Duration.ofMillis(150))));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f,
                    0.8f + 1.0f * (1 - battle.salseroHealth()));
                session.salsero.getWorld().spawnParticle(Particle.NOTE,
                    session.salsero.getLocation().add(0, 2.4, 0), 4, 0.5, 0.3, 0.5, 1.0);
            }
            case MISS, TIMEOUT -> {
                player.showTitle(Title.title(text(outcome == DonPollosDanceBattle.Outcome.MISS
                        ? "§c§l✘ ¡ESE NO!" : "§c§l✘ ¡TARDE!"), text("§7Te quedan §c" + battle.lives() + " §7vidas"),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(200))));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.9f);
            }
            case WON -> win(session, player);
            case LOST -> lose(session, player);
            default -> {
            }
        }
    }

    private void actionBar(Session session, Player player, long now) {
        DonPollosDanceBattle battle = session.battle;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < settings.lives(); i++) {
            bar.append(i < battle.lives() ? "§c❤" : "§8❤");
        }
        bar.append("  §7Salsero: §d").append(battle.hitsLeft()).append(" pasos");
        if (battle.current() != null) {
            int total = battle.windowTicks();
            int filled = (int) Math.ceil(10.0 * battle.ticksLeft(now) / Math.max(1, total));
            bar.append("  §e");
            for (int i = 0; i < 10; i++) {
                bar.append(i < filled ? "▮" : "§8▮");
            }
        }
        player.sendActionBar(text(bar.toString()));
    }

    // ------------------------------------------------------------ final

    private void win(Session session, Player player) {
        session.over = true;
        session.bar.progress(0f);
        stopDance(session);
        player.hideBossBar(session.bar);
        player.showTitle(Title.title(text("§a§l¡GANASTE!"), text("§7El Salsero se rinde..."),
            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2500), Duration.ofMillis(500))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Villager salsero = session.salsero;
        onWin.accept(salsero);
        restoreBehaviour.accept(salsero);
        salsero.setAI(true);
        salsero.getPathfinder().moveTo(player, 1.0);
        int[] waited = {0};
        BukkitTask[] walk = new BukkitTask[1];
        walk[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            waited[0] += 5;
            Player target = Bukkit.getPlayer(session.playerId);
            boolean close = target != null && salsero.isValid() && target.getWorld() == salsero.getWorld()
                && target.getLocation().distanceSquared(salsero.getLocation()) <= 2.5 * 2.5;
            if (target != null && salsero.isValid() && !close && waited[0] < 100) {
                salsero.getPathfinder().moveTo(target, 1.0);
                return;
            }
            walk[0].cancel();
            if (target != null && salsero.isValid()) {
                salsero.getPathfinder().stopPathfinding();
                face(salsero, target);
                throwReward(salsero, target);
            } else if (target != null) {
                target.getInventory().addItem(new ItemStack(Material.valueOf(settings.reward().material()),
                    settings.reward().amount()));
            }
            release(session);
        }, 5L, 5L);
    }

    private void throwReward(Villager salsero, Player player) {
        Material material = Material.valueOf(settings.reward().material());
        int amount = settings.reward().amount();
        player.sendMessage("§d¡El Salsero te tira " + amount + " " + material.name().toLowerCase().replace('_', ' ')
            + "! §7Bien bailado.");
        for (int i = 0; i < amount; i++) {
            int delay = i * 4;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!salsero.isValid() || !player.isOnline()) {
                    return;
                }
                Location from = salsero.getEyeLocation();
                Vector toward = player.getLocation().toVector().subtract(from.toVector()).normalize().multiply(0.3);
                Item item = salsero.getWorld().dropItem(from, new ItemStack(material));
                item.setVelocity(toward.setY(0.25));
                item.setPickupDelay(10);
                salsero.getWorld().playSound(from, Sound.ENTITY_ITEM_PICKUP, 1f, 1.3f);
            }, delay);
        }
    }

    private void lose(Session session, Player player) {
        session.over = true;
        player.hideBossBar(session.bar);
        player.showTitle(Title.title(text("§c§lPERDISTE"), text("§7El Salsero te bailo"),
            Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(500))));
        player.sendMessage("§cEl Don Pollo Salsero te gano la batalla de baile.");
        release(session);
        player.setHealth(0);
    }

    private void end(Session session, Player player) {
        session.over = true;
        if (player != null) {
            player.hideBossBar(session.bar);
        }
        release(session);
    }

    private void release(Session session) {
        stopDance(session);
        byPlayer.remove(session.playerId);
        bySalsero.remove(session.salsero.getUniqueId());
        if (session.salsero.isValid()) {
            restoreBehaviour.accept(session.salsero);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = byPlayer.get(event.getPlayer().getUniqueId());
        if (session != null) {
            end(session, event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Session session = byPlayer.get(event.getEntity().getUniqueId());
        if (session != null && session.active()) {
            end(session, event.getEntity());
        }
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Session session : new ArrayList<>(byPlayer.values())) {
            end(session, Bukkit.getPlayer(session.playerId));
        }
    }

    private static void face(Villager salsero, Player player) {
        Location eyes = player.getEyeLocation();
        salsero.lookAt(eyes.getX(), eyes.getY(), eyes.getZ(), LookAnchor.EYES);
    }

    private static Component text(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    private static final class Session {
        private final UUID playerId;
        private final Villager salsero;
        private final DonPollosDanceBattle battle;
        private final BossBar bar;
        private Input lastInput;
        private String dance;
        private boolean over;

        private Session(UUID playerId, Villager salsero, DonPollosDanceBattle battle, BossBar bar) {
            this.playerId = playerId;
            this.salsero = salsero;
            this.battle = battle;
            this.bar = bar;
        }

        boolean active() {
            return !over;
        }
    }
}
