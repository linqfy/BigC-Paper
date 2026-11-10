package dev.linqfy.bigCasares.modules.forex;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Chat, titles, the global siren and the funeral march. Timed sounds run from {@link #tick()}. */
final class ForexAnnouncer {

    static final String PREFIX = "§2[FOREX] §r";

    /** A note of the funeral march: semitones above F#3 (note block scale) and the ticks until the next one. */
    record Note(int semitone, int ticks) {

        float pitch() {
            return (float) Math.pow(2.0, (semitone - 12) / 12.0);
        }
    }

    /** Opening bars of Chopin's funeral march, in B♭ minor on the note block's two octaves. */
    static final List<Note> FUNERAL_MARCH = List.of(
        new Note(4, 16), new Note(4, 12), new Note(4, 4), new Note(4, 16),
        new Note(7, 12), new Note(6, 4), new Note(6, 12), new Note(4, 4), new Note(4, 12), new Note(3, 4),
        new Note(4, 32));

    private record Cue(long at, Runnable action) {
    }

    private final Server server;
    private final LongSupplier clock;
    private final List<Cue> cues = new ArrayList<>();
    private long tick;

    ForexAnnouncer(Server server, LongSupplier clock) {
        this.server = server;
        this.clock = clock;
    }

    void tick() {
        tick++;
        if (cues.isEmpty()) {
            return;
        }
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Cue> iterator = cues.iterator(); iterator.hasNext(); ) {
            Cue cue = iterator.next();
            if (cue.at() <= tick) {
                due.add(cue.action());
                iterator.remove();
            }
        }
        due.forEach(Runnable::run);
    }

    void clear() {
        cues.clear();
    }

    void broadcast(String message) {
        for (Player player : server.getOnlinePlayers()) {
            player.sendMessage(PREFIX + message);
        }
        server.getConsoleSender().sendMessage(PREFIX + message);
    }

    void tell(UUID playerId, String message) {
        Player player = server.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(PREFIX + message);
        }
    }

    String name(UUID playerId) {
        OfflinePlayer player = server.getOfflinePlayer(playerId);
        return player.getName() == null ? "un anónimo" : player.getName();
    }

    void launched(Shitcoin coin) {
        broadcast("§f" + name(coin.creator()) + " §7lanzó §e$" + coin.ticker() + " §8(" + coin.name()
            + ")§7. ¿Gema o estafa? §8/forex ver " + coin.ticker());
        everyone(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f);
    }

    void announce(MarketEvent event) {
        switch (event) {
            case MarketEvent.EmeraldMoved moved -> {
                if (moved.step().correction()) {
                    broadcast("§7Corrección controlada en §a$vEMR§7: " + ForexFormat.change(moved.step().change())
                        + "§7. Precio " + ForexFormat.price(moved.step().after()) + ". §8El oro siempre vuelve.");
                }
            }
            case MarketEvent.Pump pump -> {
                broadcast("§6§l🚀 TO THE MOON! §e$" + pump.ticker() + " §7pegó un salto de "
                    + ForexFormat.change(pump.gain()) + "§7. Euforia en el mercado.");
                everyone(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.0f);
                later(10, () -> everyone(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.7f, 1.2f));
            }
            case MarketEvent.BubbleFormed bubble -> {
                broadcast("§d§lBURBUJA DETECTADA: §e$" + bubble.ticker() + " §7ya es masa crítica. En "
                    + ForexFormat.duration(bubble.flipAt() - clock.getAsLong())
                    + " se tira una moneda al aire: §acara §7sube, §ccruz §7se pudre todo. §850/50.");
                everyone(Sound.BLOCK_BELL_USE, 0.8f, 0.8f);
            }
            case MarketEvent.BubbleDeflated deflated ->
                broadcast("§7La burbuja de §e$" + deflated.ticker() + " §7se desinfló. Se suspende el 50/50.");
            case MarketEvent.FlipWarning warning -> {
                broadcast("§c⚠ §e$" + warning.ticker() + "§7: la moneda se tira al aire en §f"
                    + ForexFormat.duration(warning.millisLeft()) + "§7. ¿Vendés o aguantás?");
                for (UUID holder : warning.holders()) {
                    Player player = server.getPlayer(holder);
                    if (player != null) {
                        player.showTitle(title("§c⚠ $" + warning.ticker(), "§750/50 en §f"
                            + ForexFormat.duration(warning.millisLeft()), 3));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, SoundCategory.MASTER, 1f, 0.6f);
                    }
                }
            }
            case MarketEvent.FlipSurvived survived -> {
                broadcast("§a§lCARA! §e$" + survived.ticker() + " §7sobrevive §f(" + survived.survived()
                    + "x) §7y sube otro escalón: " + ForexFormat.change(survived.gain())
                    + "§7. El frenesí continúa.");
                everyone(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
            case MarketEvent.Collapse collapse -> {
                broadcast("§4§l☠ CRUZ. COLAPSO TOTAL DE $" + collapse.ticker() + " §c(-99,999%)§4§l ☠");
                broadcast("§7La burbuja de §f" + collapse.name() + " §7explotó. Se quemaron §c"
                    + ForexFormat.money(collapse.burned()) + " §7del pozo. Pico de capitalización: §f"
                    + ForexFormat.money((long) collapse.peakCap()) + "§7. Descansen en paz, inversores.");
                siren();
                mourn(collapse.victims(), "§4§lCOLAPSO TOTAL", "§7$" + collapse.ticker() + " §c-99,999%");
            }
            case MarketEvent.Dividends dividends -> {
                for (Map.Entry<UUID, Long> payout : dividends.payouts().entrySet()) {
                    tell(payout.getKey(), "§a+" + ForexFormat.money(payout.getValue()) + " §7de dividendos de §e$"
                        + dividends.ticker() + " §8(staking)");
                }
            }
        }
    }

    void rugPulled(RugPull rug, Collection<UUID> victims) {
        broadcast("§4§l🧹 RUG PULL! §c" + name(rug.creator()) + " §7se llevó §c" + ForexFormat.money(rug.stolen())
            + " §8(" + ForexFormat.percent(rug.fraction()) + ") §7del pozo de §e$" + rug.ticker()
            + "§7. Los holders se reparten las migajas.");
        siren();
        mourn(victims, "§4§lRUG PULL", "§7" + name(rug.creator()) + " se llevó la plata de $" + rug.ticker());
        rug.refunds().forEach((holder, refund) -> tell(holder, "§7Te tocaron §f" + ForexFormat.money(refund)
            + " §7de migajas de §e$" + rug.ticker() + "§7."));
    }

    /** Wee-woo for everyone online. */
    void siren() {
        for (int i = 0; i < 8; i++) {
            float pitch = i % 2 == 0 ? 2.0f : 1.4f;
            later(i * 6L, () -> everyone(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch));
        }
        later(50, () -> everyone(Sound.ENTITY_WITHER_DEATH, 0.35f, 0.6f));
    }

    /** Title plus the funeral march for every victim online. */
    void mourn(Collection<UUID> victims, String headline, String subtitle) {
        List<Player> online = victims.stream().map(server::getPlayer).filter(Objects::nonNull).toList();
        for (Player player : online) {
            player.showTitle(title(headline, subtitle, 5));
        }
        long at = 60;
        for (Note note : FUNERAL_MARCH) {
            for (Player player : online) {
                later(at, () -> {
                    if (player.isOnline()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER,
                            1.0f, note.pitch());
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, SoundCategory.MASTER,
                            0.5f, note.pitch());
                    }
                });
            }
            at += note.ticks();
        }
    }

    private void everyone(Sound sound, float volume, float pitch) {
        for (Player player : server.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    private void later(long delayTicks, Runnable action) {
        cues.add(new Cue(tick + Math.max(1, delayTicks), action));
    }

    private static Title title(String headline, String subtitle, int seconds) {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        Component main = legacy.deserialize(headline);
        Component sub = legacy.deserialize(subtitle);
        return Title.title(main, sub, Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(seconds),
            Duration.ofSeconds(1)));
    }
}
