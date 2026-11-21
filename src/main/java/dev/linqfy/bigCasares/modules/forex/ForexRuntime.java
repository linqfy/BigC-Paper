package dev.linqfy.bigCasares.modules.forex;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleUnaryOperator;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Glue between the market and the server: player-facing operations shared by the command and the menus,
 * the market tick, persistence and the whale aura.
 */
final class ForexRuntime {

    private final Plugin plugin;
    private final ForexMarket market;
    private final JournaledForexBank journaledBank;
    private final ForexStore store;
    private final ForexAnnouncer announcer;
    private final ForexTeams teams;
    private final ForexBoard board;
    private final ForexCharts charts;

    ForexRuntime(Plugin plugin, ForexSettings settings, ForexBank bank, ForexTeams teams) {
        this.plugin = plugin;
        LongSupplier clock = System::currentTimeMillis;
        this.journaledBank = new JournaledForexBank(bank,
            new File(new File(plugin.getDataFolder(), "forex"), "transfers.log").toPath(),
            new File(new File(plugin.getDataFolder(), "forex"), "market.yml").toPath());
        this.market = new ForexMarket(settings, journaledBank, ThreadLocalRandom.current(), clock);
        this.store = new ForexStore(new File(new File(plugin.getDataFolder(), "forex"), "market.yml"),
            plugin.getLogger());
        this.announcer = new ForexAnnouncer(plugin.getServer(), clock);
        this.teams = teams;
        this.board = new ForexBoard(this);
        this.charts = new ForexCharts(plugin.getServer(), new ChartFeed(market),
            new File(new File(plugin.getDataFolder(), "forex"), "charts.yml"), plugin.getLogger());
    }

    ForexMarket market() {
        return market;
    }

    ForexTeams teams() {
        return teams;
    }

    ForexBoard board() {
        return board;
    }

    ForexAnnouncer announcer() {
        return announcer;
    }

    boolean start() {
        if (journaledBank.paused()) {
            plugin.getLogger().severe("[Forex] Unresolved transfers.log: market disabled pending manual reconciliation.");
            return false;
        }
        store.load(market);
        charts.start();
        return true;
    }

    void stop() {
        charts.stop();
        if (!journaledBank.paused()) save();
        announcer.clear();
    }

    void save() {
        if (journaledBank.paused()) throw new ForexException("Mercado pausado: avisá a un administrador.");
        try {
            store.save(market);
            journaledBank.committed();
        } catch (RuntimeException failure) {
            journaledBank.pause();
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "[Forex] Market paused; preserve forex/transfers.log and reconcile before restarting.", failure);
            throw new ForexException("Mercado pausado: avisá a un administrador.");
        }
    }

    boolean paused() { return journaledBank.paused(); }

    /** Every second: market cycles, bubbles, flips and dividends. */
    void tickMarket() {
        if (paused()) return;
        try {
            journaledBank.context("market tick");
            List<MarketEvent> events = market.tick();
            if (!events.isEmpty()) {
                save();
                events.forEach(announcer::announce);
            }
        } catch (RuntimeException failure) {
            journaledBank.pause();
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Forex] Market tick paused; reconcile transfers.", failure);
        }
    }

    /** Every tick: the timed sounds of sirens and funerals. */
    void tickEffects() {
        announcer.tick();
    }

    /** Emerald sparkles around the whales who did not hide them. */
    void tickAura() {
        Set<UUID> whales = market.whales();
        for (UUID whale : whales) {
            Player player = plugin.getServer().getPlayer(whale);
            if (player == null || market.auraHidden(whale) || player.isInvisible()) {
                continue;
            }
            Location at = player.getLocation().add(0, 1.0, 0);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 6, 0.4, 0.6, 0.4, 0);
        }
    }

    // ---------------------------------------------------------------- operations

    boolean buy(Player player, String asset, long money) {
        return run(player, "buy " + asset + " " + money, () -> {
            TradeReceipt receipt = ForexMarket.isEmeraldAlias(asset)
                ? market.buyEmeralds(player.getUniqueId(), money)
                : market.buy(player.getUniqueId(), asset, money);
            ding(player, true);
            return "§aCompraste §f" + ForexFormat.tokens(receipt.quantity()) + " " + label(receipt.asset())
                + " §apor §f" + ForexFormat.money(receipt.money()) + " §8(comisiones " + ForexFormat.money(receipt.fees())
                + ", precio " + ForexFormat.price(receipt.priceAfter()) + ")";
        });
    }

    /** {@code quantity} receives the sellable balance and returns how much to sell. */
    boolean sell(Player player, String asset, DoubleUnaryOperator quantity) {
        return run(player, "sell " + asset, () -> {
            UUID id = player.getUniqueId();
            TradeReceipt receipt;
            if (ForexMarket.isEmeraldAlias(asset)) {
                receipt = market.sellEmeralds(id, quantity.applyAsDouble(market.emeralds(id)));
            } else {
                Shitcoin coin = market.coin(asset).orElseThrow(() ->
                    new ForexException("No existe $" + ForexMarket.normalizeTicker(asset) + "."));
                receipt = market.sell(id, asset, quantity.applyAsDouble(coin.free(id)));
            }
            ding(player, false);
            String message = "§aVendiste §f" + ForexFormat.tokens(receipt.quantity()) + " " + label(receipt.asset())
                + " §apor §f" + ForexFormat.money(receipt.money()) + " §8(comisiones " + ForexFormat.money(receipt.fees())
                + ")";
            if (receipt.shortfall() > 0) {
                message += "\n" + ForexAnnouncer.PREFIX + "§c§l¡CORRIDA BANCARIA! §cEl pozo no alcanzó: te quedaron sin cobrar §f"
                    + ForexFormat.money(receipt.shortfall()) + "§c.";
            }
            return message;
        });
    }

    boolean stake(Player player, String ticker, DoubleUnaryOperator quantity) {
        return run(player, "stake " + ticker, () -> {
            Shitcoin coin = require(ticker);
            double staked = market.stake(player.getUniqueId(), ticker, quantity.applyAsDouble(coin.free(player.getUniqueId())));
            return "§aBloqueaste §f" + ForexFormat.tokens(staked) + " $" + coin.ticker() + "§a. Cobrás dividendos cada "
                + market.settings().coins().dividendMinutes() + " minutos. §8(bloqueo "
                + market.settings().coins().stakeLockMinutes() + "m)";
        });
    }

    boolean unstake(Player player, String ticker, DoubleUnaryOperator quantity) {
        return run(player, "unstake " + ticker, () -> {
            Shitcoin coin = require(ticker);
            double freed = market.unstake(player.getUniqueId(), ticker,
                quantity.applyAsDouble(coin.staked(player.getUniqueId())));
            return "§aDesbloqueaste §f" + ForexFormat.tokens(freed) + " $" + coin.ticker() + "§a.";
        });
    }

    boolean create(Player player, String ticker, String name) {
        return run(player, "create " + ticker, () -> {
            Shitcoin coin = market.create(player.getUniqueId(), ticker, name);
            announcer.launched(coin);
            return "§a$" + coin.ticker() + " ya cotiza. Cobrás §f" + ForexFormat.percent(coin.creatorFee())
                + " §ade cada compra y venta. §7Ajustá con §f/forex comision§7, §f/forex dividendos §7y §f/forex clan§7.";
        });
    }

    boolean rugPull(Player player, String ticker) {
        return run(player, "rug pull " + ticker, () -> {
            Shitcoin coin = require(ticker);
            List<UUID> victims = coin.holdings().keySet().stream()
                .filter(holder -> !holder.equals(player.getUniqueId())).toList();
            RugPull rug = market.rugPull(player.getUniqueId(), ticker);
            announcer.rugPulled(rug, victims);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 1f, 0.5f);
            return "§2Te llevaste §f" + ForexFormat.money(rug.stolen()) + "§2. §7Don Casares ya anotó tu nombre.";
        });
    }

    boolean setFee(Player player, String ticker, double fee) {
        return run(player, "set fee " + ticker, () -> {
            market.setCreatorFee(player.getUniqueId(), ticker, fee);
            return "§aComisión de $" + ForexMarket.normalizeTicker(ticker) + ": §f" + ForexFormat.percent(fee);
        });
    }

    boolean setStakingShare(Player player, String ticker, double share) {
        return run(player, "set staking share " + ticker, () -> {
            market.setStakingShare(player.getUniqueId(), ticker, share);
            return "§aDe tu comisión, §f" + ForexFormat.percent(share) + " §ava a los stakers de $"
                + ForexMarket.normalizeTicker(ticker) + ".";
        });
    }

    boolean linkClan(Player player, String ticker, boolean unlink) {
        return run(player, "link clan " + ticker, () -> {
            if (unlink) {
                market.linkTeam(player.getUniqueId(), ticker, null);
                return "§7$" + ForexMarket.normalizeTicker(ticker) + " ya no es la moneda de tu clan.";
            }
            String team = teams.ownedTeam(player.getUniqueId()).orElseThrow(() ->
                new ForexException("Tenés que ser el dueño de un clan."));
            market.linkTeam(player.getUniqueId(), ticker, team);
            String label = teams.label(team).orElse("tu clan");
            announcer.broadcast("§e$" + ForexMarket.normalizeTicker(ticker) + " §7es ahora la moneda oficial de "
                + label + "§7. Con §f" + ForexFormat.tokens(market.settings().clanPassThreshold())
                + " §7tokens tenés pase libre a sus cofres protegidos.");
            return "§aListo.";
        });
    }

    /** Hands out the live chart map of an asset; dead coins keep their last chart. */
    void giveChart(Player player, String rawAsset) {
        String asset = ChartFeed.asset(rawAsset);
        boolean known = ForexMarket.EMERALD_TICKER.equals(asset) || market.coin(asset).isPresent()
            || market.graveyard().stream().anyMatch(dead -> dead.ticker().equals(asset));
        if (!known) {
            player.sendMessage(ForexAnnouncer.PREFIX + "§cNo existe $" + asset + ".");
            return;
        }
        player.getInventory().addItem(charts.item(player, asset)).values()
            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        player.sendMessage(ForexAnnouncer.PREFIX + "§aGráfico de " + label(asset)
            + " §aen vivo. §7Ponelo en marcos para armar tu pared de trading.");
        player.playSound(player.getLocation(), Sound.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.MASTER, 0.8f, 1f);
    }

    void toggleAura(Player player) {
        boolean shown = market.toggleAura(player.getUniqueId());
        save();
        player.sendMessage(ForexAnnouncer.PREFIX + (shown ? "§aTu aura de ballena está visible."
            : "§7Tu aura de ballena está oculta."));
    }

    // ---------------------------------------------------------------- helpers

    String label(String asset) {
        return ForexMarket.EMERALD_TICKER.equals(asset) ? "§a$vEMR" : "§e$" + asset;
    }

    private Shitcoin require(String ticker) {
        return market.coin(ticker).orElseThrow(() ->
            new ForexException("No existe $" + ForexMarket.normalizeTicker(ticker) + "."));
    }

    private boolean run(Player player, String description, Supplier<String> operation) {
        if (paused()) {
            player.sendMessage(ForexAnnouncer.PREFIX + "§cMercado pausado: avisá a un administrador.");
            return false;
        }
        try {
            journaledBank.context(description + " player=" + player.getUniqueId());
            String message = operation.get();
            save();
            player.sendMessage(ForexAnnouncer.PREFIX + message);
            return true;
        } catch (ForexException refused) {
            if (journaledBank.hasUncommittedTransfer()) journaledBank.pause();
            player.sendMessage(ForexAnnouncer.PREFIX + "§c" + refused.getMessage());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.7f, 1f);
            return false;
        } catch (RuntimeException failure) {
            journaledBank.pause();
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Forex] Market paused; reconcile transfers.", failure);
            player.sendMessage(ForexAnnouncer.PREFIX + "§cMercado pausado: avisá a un administrador.");
            return false;
        }
    }

    private static void ding(Player player, boolean buy) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.7f,
            buy ? 1.4f : 0.9f);
    }
}
