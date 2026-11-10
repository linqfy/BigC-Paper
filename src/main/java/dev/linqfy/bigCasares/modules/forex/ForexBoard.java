package dev.linqfy.bigCasares.modules.forex;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The trading terminal: a market board, an emerald desk and one desk per coin. */
final class ForexBoard {

    private static final long[] BUY_AMOUNTS = {100, 1_000, 10_000};
    private static final double[] SELL_FRACTIONS = {0.25, 0.5, 1.0};
    private static final int[] COIN_SLOTS = {
        19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final ForexRuntime runtime;

    ForexBoard(ForexRuntime runtime) {
        this.runtime = runtime;
    }

    void openMarket(Player player) {
        ForexMarket market = runtime.market();
        ForexMenu menu = new ForexMenu(6, "§2§lFOREX §8· §7Mercado Casares");
        menu.set(4, emeraldIcon(player.getUniqueId()), this::openEmerald);
        menu.set(10, ForexMenu.button(Material.CLOCK, "§7Próximo ciclo de mercado",
            "§f" + ForexFormat.duration(market.millisToNextCycle()),
            "§8Cada ciclo: tendencia, volatilidad y",
            "§81 en 10 de pump §6\"To the moon!\""), null);
        menu.set(16, ForexMenu.button(Material.WRITABLE_BOOK, "§eLanzá tu shitcoin",
            "§7/forex crear <ticker> <nombre>",
            "§7Costo: §f" + ForexFormat.money(market.settings().coins().creationCost()),
            "§7Cobrás §f" + ForexFormat.percent(market.settings().coins().creatorFeeMin()) + "§7-§f"
                + ForexFormat.percent(market.settings().coins().creatorFeeMax()) + " §7de cada operación.",
            "§8...o hacés rug pull y te vas."), null);
        List<Shitcoin> coins = new ArrayList<>(market.coins());
        coins.sort(Comparator.comparingDouble(market::marketCap).reversed());
        for (int index = 0; index < Math.min(coins.size(), COIN_SLOTS.length); index++) {
            Shitcoin coin = coins.get(index);
            menu.set(COIN_SLOTS[index], coinIcon(coin, player.getUniqueId(), index), clicker -> openCoin(clicker, coin.ticker()));
        }
        if (coins.isEmpty()) {
            menu.set(31, ForexMenu.button(Material.BARRIER, "§7No cotiza ninguna shitcoin",
                "§8Sé el primero: /forex crear"), null);
        }
        menu.set(45, graveyardIcon(), null);
        menu.set(49, portfolioIcon(player.getUniqueId()), null);
        menu.set(53, ForexMenu.button(Material.OAK_DOOR, "§7Cerrar"), Player::closeInventory);
        menu.fill();
        menu.open(player);
    }

    void openEmerald(Player player) {
        ForexMarket market = runtime.market();
        UUID id = player.getUniqueId();
        ForexMenu menu = new ForexMenu(4, "§2Esmeraldas Virtuales §8($vEMR)");
        menu.set(4, emeraldIcon(id), null);
        int slot = 10;
        for (long amount : BUY_AMOUNTS) {
            menu.set(slot++, ForexMenu.button(Material.LIME_DYE, "§aComprar " + ForexFormat.money(amount),
                "§7≈ " + ForexFormat.tokens(amount / market.emerald().price()) + " vEMR"),
                clicker -> {
                    runtime.buy(clicker, ForexMarket.EMERALD_TICKER, amount);
                    openEmerald(clicker);
                });
        }
        slot = 14;
        for (double fraction : SELL_FRACTIONS) {
            menu.set(slot++, ForexMenu.button(Material.RED_DYE, "§cVender " + ForexFormat.percent(fraction),
                "§7≈ " + ForexFormat.money((long) (market.emeralds(id) * fraction * market.emerald().price()))),
                clicker -> {
                    runtime.sell(clicker, ForexMarket.EMERALD_TICKER, held -> held * fraction);
                    openEmerald(clicker);
                });
        }
        menu.set(27, ForexMenu.button(Material.ARROW, "§7Volver"), this::openMarket);
        menu.fill();
        menu.open(player);
    }

    void openCoin(Player player, String ticker) {
        ForexMarket market = runtime.market();
        Shitcoin coin = market.coin(ticker).orElse(null);
        if (coin == null) {
            player.sendMessage(ForexAnnouncer.PREFIX + "§c$" + ForexMarket.normalizeTicker(ticker)
                + " ya no existe. §8/forex cementerio");
            openMarket(player);
            return;
        }
        UUID id = player.getUniqueId();
        ForexMenu menu = new ForexMenu(5, "§e$" + coin.ticker() + " §8· §7" + coin.name());
        menu.set(4, coinIcon(coin, id, -1), null);
        int slot = 10;
        for (long amount : BUY_AMOUNTS) {
            menu.set(slot++, ForexMenu.button(Material.LIME_DYE, "§aComprar " + ForexFormat.money(amount),
                "§8Comisión del creador " + ForexFormat.percent(coin.creatorFee())), clicker -> {
                    runtime.buy(clicker, coin.ticker(), amount);
                    openCoin(clicker, coin.ticker());
                });
        }
        slot = 14;
        for (double fraction : SELL_FRACTIONS) {
            long quote = market.quoteSale(coin, coin.free(id) * fraction);
            menu.set(slot++, ForexMenu.button(Material.RED_DYE, "§cVender " + ForexFormat.percent(fraction),
                "§7≈ " + ForexFormat.money(quote) + " §8antes de comisiones",
                "§8El pozo paga mientras le quede plata."), clicker -> {
                    runtime.sell(clicker, coin.ticker(), free -> free * fraction);
                    openCoin(clicker, coin.ticker());
                });
        }
        menu.set(21, ForexMenu.button(Material.IRON_CHAIN, "§bBloquear todo (staking)",
            "§7Libres: §f" + ForexFormat.tokens(coin.free(id)),
            "§7Cobrás §f" + ForexFormat.percent(coin.stakingShare()) + " §7de la comisión del creador",
            "§7repartida cada " + market.settings().coins().dividendMinutes() + " minutos."), clicker -> {
                runtime.stake(clicker, coin.ticker(), free -> free);
                openCoin(clicker, coin.ticker());
            });
        long lockLeft = coin.lockedUntil(id) - market.now();
        menu.set(23, ForexMenu.button(Material.TRIPWIRE_HOOK, "§bDesbloquear todo",
            "§7En staking: §f" + ForexFormat.tokens(coin.staked(id)),
            lockLeft > 0 ? "§cBloqueado " + ForexFormat.duration(lockLeft) : "§aDisponible"), clicker -> {
                runtime.unstake(clicker, coin.ticker(), staked -> staked);
                openCoin(clicker, coin.ticker());
            });
        if (coin.creator().equals(id)) {
            long pool = coin.pool();
            menu.set(31, ForexMenu.button(Material.TNT, "§4§lRUG PULL",
                "§7Te llevás entre §c" + ForexFormat.percent(market.settings().coins().rugPullMin()) + " §7y §c"
                    + ForexFormat.percent(market.settings().coins().rugPullMax()) + " §7del pozo:",
                "§f≈ " + ForexFormat.money((long) (pool * market.settings().coins().rugPullMin())) + " §7- §f"
                    + ForexFormat.money((long) (pool * market.settings().coins().rugPullMax())),
                "§8Todo el server se va a enterar.",
                "§8Don Casares no olvida."), clicker -> confirmRug(clicker, coin.ticker()));
        }
        menu.set(36, ForexMenu.button(Material.ARROW, "§7Volver"), this::openMarket);
        menu.fill();
        menu.open(player);
    }

    private void confirmRug(Player player, String ticker) {
        ForexMenu menu = new ForexMenu(3, "§4¿Rug pull de $" + ForexMarket.normalizeTicker(ticker) + "?");
        menu.set(11, ForexMenu.button(Material.LIME_CONCRETE, "§aNo, sigo cobrando el goteo",
            "§7Las comisiones caen solas todos los días."), clicker -> openCoin(clicker, ticker));
        menu.set(15, ForexMenu.button(Material.RED_CONCRETE, "§4§lSÍ. ME LLEVO TODO.",
            "§8No hay vuelta atrás."), clicker -> {
                clicker.closeInventory();
                runtime.rugPull(clicker, ticker);
            });
        menu.fill();
        menu.open(player);
    }

    // ---------------------------------------------------------------- icons

    private ItemStack emeraldIcon(UUID viewer) {
        ForexMarket market = runtime.market();
        EmeraldMarket emerald = market.emerald();
        double held = market.emeralds(viewer);
        return ForexMenu.button(Material.EMERALD, "§a§l$vEMR §7Esmeraldas Virtuales",
            "§7Precio: §f" + ForexFormat.price(emerald.price()) + " " + ForexFormat.change(ForexFormat.trend(emerald.history())),
            ForexFormat.sparkline(emerald.history()),
            "§7Piso de reserva: §f" + ForexFormat.price(emerald.floor()),
            "§8Activo refugio: 0% rug pull, crecimiento lento.",
            "",
            "§7Tenés: §f" + ForexFormat.tokens(held) + " vEMR §8(" + ForexFormat.money((long) (held * emerald.price())) + ")",
            "§eClick para operar");
    }

    private ItemStack coinIcon(Shitcoin coin, UUID viewer, int rank) {
        ForexMarket market = runtime.market();
        double trend = ForexFormat.trend(coin.history());
        Material material = coin.isBubble() ? Material.SLIME_BALL
            : trend >= 0 ? Material.GOLD_NUGGET : Material.REDSTONE;
        List<String> lore = new ArrayList<>();
        lore.add("§8" + coin.name() + " · de " + runtime.announcer().name(coin.creator()));
        lore.add("§7Precio: §f" + ForexFormat.price(market.price(coin)) + " " + ForexFormat.change(trend));
        lore.add(ForexFormat.sparkline(coin.history()));
        lore.add("§7Capitalización: §f" + ForexFormat.money((long) market.marketCap(coin)));
        lore.add("§7Pozo real: §f" + ForexFormat.money(coin.pool()) + collateral(coin));
        lore.add("§7Holders: §f" + coin.holderCount() + " §8· §7Comisión: §f" + ForexFormat.percent(coin.creatorFee()));
        lore.add("§7Staking: §f" + ForexFormat.percent(coin.stakingShare()) + " §7de la comisión §8(pozo "
            + ForexFormat.money(coin.dividendPot()) + ")");
        coin.teamId().ifPresent(team -> lore.add("§7Moneda de clan: §f"
            + runtime.teams().label(team).orElse(team)));
        if (coin.isBubble()) {
            lore.add("§d§lBURBUJA §7· 50/50 en §f" + ForexFormat.duration(coin.nextFlipAt() - market.now()));
            lore.add("§7Sobrevivió §f" + coin.flipsSurvived() + " §7lanzamientos");
        }
        double balance = coin.balance(viewer);
        if (balance > Shitcoin.DUST) {
            lore.add("");
            lore.add("§7Tenés: §f" + ForexFormat.tokens(balance) + " §8(" + ForexFormat.tokens(coin.staked(viewer))
                + " en staking)");
        }
        if (rank >= 0) {
            lore.add("§eClick para operar");
        }
        String prefix = rank >= 0 && rank < market.settings().whales().topCoins() ? "§6#" + (rank + 1) + " " : "";
        return ForexMenu.button(material, prefix + "§e§l$" + coin.ticker(), lore);
    }

    private String collateral(Shitcoin coin) {
        double cap = runtime.market().marketCap(coin);
        if (cap < 1) {
            return "";
        }
        double ratio = coin.pool() / cap;
        String color = ratio >= 0.9 ? "§a" : ratio >= 0.5 ? "§e" : "§c";
        return " §8(" + color + ForexFormat.percent(Math.min(ratio, 9.99)) + " §8respaldado)";
    }

    private ItemStack graveyardIcon() {
        List<String> lore = new ArrayList<>();
        List<DeadCoin> graves = runtime.market().graveyard();
        if (graves.isEmpty()) {
            lore.add("§8Todavía no murió nadie.");
        }
        for (DeadCoin dead : graves.subList(0, Math.min(8, graves.size()))) {
            lore.add((dead.fate() == DeadCoin.Fate.RUG_PULL ? "§c🧹 " : "§4☠ ") + "§7$" + dead.ticker() + " §8- pico "
                + ForexFormat.money((long) dead.peakCap()));
        }
        return ForexMenu.button(Material.SKELETON_SKULL, "§7Cementerio", lore);
    }

    private ItemStack portfolioIcon(UUID viewer) {
        ForexMarket market = runtime.market();
        ForexMarket.Portfolio portfolio = market.portfolio(viewer);
        List<String> lore = new ArrayList<>();
        lore.add("§a$vEMR: §f" + ForexFormat.tokens(portfolio.emeralds()) + " §8(" + ForexFormat.money(portfolio.emeraldValue()) + ")");
        for (ForexMarket.Position position : portfolio.coins()) {
            lore.add("§e$" + position.ticker() + ": §f" + ForexFormat.tokens(position.balance()) + " §8("
                + ForexFormat.money(position.value()) + ")");
        }
        lore.add("§7Total: §f" + ForexFormat.money(portfolio.totalValue()));
        if (market.whales().contains(viewer)) {
            lore.add("§b🐋 Ballena §8· comisiones de plataforma con descuento");
        }
        double credit = market.creditMultiplier(viewer);
        if (Math.abs(credit - 1.0) > 1e-9) {
            lore.add("§7Tasa con Don Casares: " + (credit < 1 ? "§a" : "§c") + "x" + String.format(Locale.ROOT, "%.2f", credit));
        }
        return ForexMenu.button(Material.BOOK, "§fTu cartera", lore);
    }
}
