package dev.linqfy.bigCasares.modules.forex;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

/** {@code /forex}: Spanish subcommands over the same operations as the trading terminal. */
final class ForexCommand implements CommandExecutor, TabCompleter {

    static final String ADMIN_PERMISSION = "bigcasares.forex.admin";
    private static final List<String> SUBCOMMANDS = List.of("menu", "mercado", "ver", "comprar", "vender", "cartera",
        "grafico", "crear", "comision", "dividendos", "stake", "unstake", "rugpull", "clan", "top", "cementerio", "aura",
        "ayuda");

    private final ForexRuntime runtime;

    ForexCommand(ForexRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "ayuda", "help" -> help(sender);
                case "mercado", "lista" -> market(sender);
                case "ver", "info" -> show(sender, arg(args, 1, "/forex ver <ticker|esmeraldas>"));
                case "top" -> top(sender);
                case "cementerio" -> graveyard(sender);
                case "admin" -> admin(sender, args);
                default -> playerCommand(sender, sub, args);
            }
        } catch (ForexException refused) {
            sender.sendMessage(ForexAnnouncer.PREFIX + "§c" + refused.getMessage());
        }
        return true;
    }

    private void playerCommand(CommandSender sender, String sub, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ForexAnnouncer.PREFIX + "§cSolo jugadores.");
            return;
        }
        switch (sub) {
            case "menu", "terminal" -> runtime.board().openMarket(player);
            case "comprar", "buy" -> runtime.buy(player, arg(args, 1, "/forex comprar <esmeraldas|ticker> <plata>"),
                ForexAmounts.money(arg(args, 2, "/forex comprar <esmeraldas|ticker> <plata>")));
            case "vender", "sell" -> runtime.sell(player, arg(args, 1, "/forex vender <esmeraldas|ticker> <cantidad|50%|todo>"),
                ForexAmounts.quantity(arg(args, 2, "/forex vender <esmeraldas|ticker> <cantidad|50%|todo>")));
            case "cartera", "portfolio" -> portfolio(player);
            case "grafico", "chart" -> runtime.giveChart(player, arg(args, 1, "/forex grafico <esmeraldas|ticker>"));
            case "crear" -> {
                String ticker = arg(args, 1, "/forex crear <ticker> <nombre>");
                String name = String.join(" ", Arrays.copyOfRange(args, Math.min(2, args.length), args.length));
                runtime.create(player, ticker, name.isBlank() ? ticker : name);
            }
            case "comision" -> runtime.setFee(player, arg(args, 1, "/forex comision <ticker> <3-5>"),
                ForexAmounts.percent(arg(args, 2, "/forex comision <ticker> <3-5>")));
            case "dividendos" -> runtime.setStakingShare(player, arg(args, 1, "/forex dividendos <ticker> <0-100>"),
                ForexAmounts.percent(arg(args, 2, "/forex dividendos <ticker> <0-100>")));
            case "stake", "bloquear" -> runtime.stake(player, arg(args, 1, "/forex stake <ticker> <cantidad|todo>"),
                ForexAmounts.quantity(args.length > 2 ? args[2] : "todo"));
            case "unstake", "desbloquear" -> runtime.unstake(player, arg(args, 1, "/forex unstake <ticker> <cantidad|todo>"),
                ForexAmounts.quantity(args.length > 2 ? args[2] : "todo"));
            case "rugpull" -> rugPull(player, args);
            case "clan" -> runtime.linkClan(player, arg(args, 1, "/forex clan <ticker> [quitar]"),
                args.length > 2 && args[2].equalsIgnoreCase("quitar"));
            case "aura" -> runtime.toggleAura(player);
            default -> help(sender);
        }
    }

    private void rugPull(Player player, String[] args) {
        String ticker = arg(args, 1, "/forex rugpull <ticker>");
        Shitcoin coin = runtime.market().coin(ticker).orElseThrow(() ->
            new ForexException("No existe $" + ForexMarket.normalizeTicker(ticker) + "."));
        if (!coin.creator().equals(player.getUniqueId())) {
            throw new ForexException("$" + coin.ticker() + " no es tuya.");
        }
        if (args.length > 2 && args[2].equalsIgnoreCase("confirmar")) {
            runtime.rugPull(player, ticker);
            return;
        }
        ForexSettings.Coins config = runtime.market().settings().coins();
        player.sendMessage(ForexAnnouncer.PREFIX + "§4El golpe maestro: §7te llevás entre §c"
            + ForexFormat.money((long) (coin.pool() * config.rugPullMin())) + " §7y §c"
            + ForexFormat.money((long) (coin.pool() * config.rugPullMax())) + "§7. O seguís cobrando §f"
            + ForexFormat.percent(coin.creatorFee()) + " §7de cada operación...");
        player.sendMessage(ForexAnnouncer.PREFIX + "§7Para confirmar: §c/forex rugpull " + coin.ticker() + " confirmar");
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ForexAnnouncer.PREFIX + "§a§lFOREX & Cryptos §7- todo con tu plata de §f/balance");
        Stream.of(
            "§f/forex §7- terminal de trading",
            "§f/forex comprar esmeraldas <plata> §7- refugio seguro $vEMR",
            "§f/forex vender esmeraldas <cantidad|50%|todo>",
            "§f/forex comprar|vender <ticker> ... §7- especulá con shitcoins",
            "§f/forex ver <ticker> §7· §f/forex mercado §7· §f/forex top §7· §f/forex cartera",
            "§f/forex grafico <esmeraldas|ticker> §7- mapa con velas en vivo",
            "§f/forex stake|unstake <ticker> [cantidad] §7- dividendos del creador",
            "§f/forex crear <ticker> <nombre> §7- lanzá tu moneda",
            "§f/forex comision <ticker> <3-5> §7· §f/forex dividendos <ticker> <0-100>",
            "§f/forex clan <ticker> §7- moneda oficial de tu clan (pase a cofres)",
            "§f/forex rugpull <ticker> §7- ...¿te animás?",
            "§f/forex cementerio §7· §f/forex aura"
        ).forEach(line -> sender.sendMessage("§8 » " + line));
    }

    private void market(CommandSender sender) {
        ForexMarket market = runtime.market();
        EmeraldMarket emerald = market.emerald();
        sender.sendMessage(ForexAnnouncer.PREFIX + "§a$vEMR §f" + ForexFormat.price(emerald.price()) + " "
            + ForexFormat.change(ForexFormat.trend(emerald.history())) + " " + ForexFormat.sparkline(emerald.history()));
        List<Shitcoin> coins = new ArrayList<>(market.coins());
        coins.sort((left, right) -> Double.compare(market.marketCap(right), market.marketCap(left)));
        if (coins.isEmpty()) {
            sender.sendMessage("§8 » §7No cotiza ninguna shitcoin. §f/forex crear");
        }
        for (Shitcoin coin : coins) {
            sender.sendMessage("§8 » §e$" + coin.ticker() + " §f" + ForexFormat.price(market.price(coin)) + " "
                + ForexFormat.change(ForexFormat.trend(coin.history())) + " §7cap §f"
                + ForexFormat.money((long) market.marketCap(coin)) + " §7pozo §f" + ForexFormat.money(coin.pool())
                + " §7holders §f" + coin.holderCount() + (coin.isBubble() ? " §d§lBURBUJA" : ""));
        }
        sender.sendMessage("§8Próximo ciclo en " + ForexFormat.duration(market.millisToNextCycle()));
    }

    private void show(CommandSender sender, String asset) {
        ForexMarket market = runtime.market();
        if (ForexMarket.isEmeraldAlias(asset)) {
            EmeraldMarket emerald = market.emerald();
            sender.sendMessage(ForexAnnouncer.PREFIX + "§a§l$vEMR §7Esmeraldas Virtuales");
            sender.sendMessage("§8 » §7Precio §f" + ForexFormat.price(emerald.price()) + " "
                + ForexFormat.change(ForexFormat.trend(emerald.history())) + " §7máximo §f"
                + ForexFormat.price(emerald.allTimeHigh()) + " §7piso §f" + ForexFormat.price(emerald.floor()));
            sender.sendMessage("§8 » " + ForexFormat.sparkline(emerald.history()));
            sender.sendMessage("§8 » §7Crece §f" + ForexFormat.percent(market.settings().emerald().dailyGrowthMin()) + "§7-§f"
                + ForexFormat.percent(market.settings().emerald().dailyGrowthMax())
                + " §7diario según el movimiento del mercado. Cero rug pulls.");
            return;
        }
        Shitcoin coin = market.coin(asset).orElseThrow(() ->
            new ForexException("No existe $" + ForexMarket.normalizeTicker(asset) + "."));
        sender.sendMessage(ForexAnnouncer.PREFIX + "§e§l$" + coin.ticker() + " §7" + coin.name() + " §8de "
            + runtime.announcer().name(coin.creator()));
        sender.sendMessage("§8 » §7Precio §f" + ForexFormat.price(market.price(coin)) + " "
            + ForexFormat.change(ForexFormat.trend(coin.history())) + " §7cap §f"
            + ForexFormat.money((long) market.marketCap(coin)) + " §7pico §f" + ForexFormat.money((long) coin.peakCap()));
        sender.sendMessage("§8 » " + ForexFormat.sparkline(coin.history()));
        sender.sendMessage("§8 » §7Pozo real §f" + ForexFormat.money(coin.pool()) + " §7· holders §f" + coin.holderCount()
            + " §7· comisión §f" + ForexFormat.percent(coin.creatorFee()) + " §7(" + ForexFormat.percent(coin.stakingShare())
            + " a stakers, pozo §f" + ForexFormat.money(coin.dividendPot()) + "§7)");
        coin.teamId().ifPresent(team -> sender.sendMessage("§8 » §7Moneda oficial de "
            + runtime.teams().label(team).orElse("un clan") + " §7· pase con §f"
            + ForexFormat.tokens(market.settings().clanPassThreshold()) + " §7tokens"));
        if (coin.isBubble()) {
            sender.sendMessage("§8 » §d§lBURBUJA §7· 50/50 en §f" + ForexFormat.duration(coin.nextFlipAt() - market.now())
                + " §7· sobrevivió §f" + coin.flipsSurvived() + "§7 veces");
        } else {
            ForexSettings.Bubble bubble = market.settings().bubble();
            sender.sendMessage("§8 » §7Masa crítica: §f" + coin.holderCount() + "/" + bubble.minHolders()
                + " §7holders y pozo §f" + ForexFormat.money(coin.pool()) + "/" + ForexFormat.money(bubble.minPool()));
        }
        if (sender instanceof Player player && coin.balance(player.getUniqueId()) > Shitcoin.DUST) {
            UUID id = player.getUniqueId();
            sender.sendMessage("§8 » §7Tenés §f" + ForexFormat.tokens(coin.balance(id)) + " §8("
                + ForexFormat.tokens(coin.staked(id)) + " en staking) §7≈ §f"
                + ForexFormat.money(market.quoteSale(coin, coin.balance(id))));
        }
    }

    private void portfolio(Player player) {
        ForexMarket market = runtime.market();
        ForexMarket.Portfolio portfolio = market.portfolio(player.getUniqueId());
        player.sendMessage(ForexAnnouncer.PREFIX + "§fTu cartera: §a" + ForexFormat.money(portfolio.totalValue()));
        player.sendMessage("§8 » §a$vEMR §f" + ForexFormat.tokens(portfolio.emeralds()) + " §8("
            + ForexFormat.money(portfolio.emeraldValue()) + ")");
        for (ForexMarket.Position position : portfolio.coins()) {
            player.sendMessage("§8 » §e$" + position.ticker() + " §f" + ForexFormat.tokens(position.balance())
                + (position.staked() > Shitcoin.DUST ? " §b(" + ForexFormat.tokens(position.staked()) + " en staking)" : "")
                + " §8(" + ForexFormat.money(position.value()) + ")");
        }
        if (market.whales().contains(player.getUniqueId())) {
            player.sendMessage("§8 » §b🐋 Ballena: §7comisiones de plataforma con descuento y aura. §8/forex aura");
        }
        double credit = market.creditMultiplier(player.getUniqueId());
        if (Math.abs(credit - 1.0) > 1e-9) {
            player.sendMessage("§8 » §7Tasa con Don Casares: " + (credit < 1 ? "§ax" : "§cx")
                + String.format(Locale.ROOT, "%.2f", credit)
                + (market.rugPulls(player.getUniqueId()) > 0 ? " §8(tus rug pulls no se olvidan)" : " §8(prestigio bancario)"));
        }
    }

    private void top(CommandSender sender) {
        ForexMarket market = runtime.market();
        List<Shitcoin> top = market.topCoins();
        sender.sendMessage(ForexAnnouncer.PREFIX + "§6Top capitalización §8(sus ballenas tienen beneficios)");
        if (top.isEmpty()) {
            sender.sendMessage("§8 » §7Nada todavía.");
        }
        for (int index = 0; index < top.size(); index++) {
            Shitcoin coin = top.get(index);
            sender.sendMessage("§8 » §6#" + (index + 1) + " §e$" + coin.ticker() + " §f"
                + ForexFormat.money((long) market.marketCap(coin)) + " §8de " + runtime.announcer().name(coin.creator()));
        }
    }

    private void graveyard(CommandSender sender) {
        List<DeadCoin> graves = runtime.market().graveyard();
        sender.sendMessage(ForexAnnouncer.PREFIX + "§7Cementerio de shitcoins");
        if (graves.isEmpty()) {
            sender.sendMessage("§8 » §7Todavía no murió nadie.");
        }
        for (DeadCoin dead : graves) {
            sender.sendMessage("§8 » " + (dead.fate() == DeadCoin.Fate.RUG_PULL ? "§c🧹 rug pull" : "§4☠ colapso")
                + " §7$" + dead.ticker() + " §8(" + dead.name() + ", de " + runtime.announcer().name(dead.creator())
                + ") §7pico §f" + ForexFormat.money((long) dead.peakCap()) + " §7pozo perdido §f"
                + ForexFormat.money(dead.lostPool()));
        }
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ForexAnnouncer.PREFIX + "§cNo tenés permiso.");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "ciclo" -> {
                runtime.market().forceCycle().forEach(runtime.announcer()::announce);
                runtime.save();
                sender.sendMessage(ForexAnnouncer.PREFIX + "§aCiclo de mercado forzado.");
            }
            case "flip" -> {
                runtime.market().forceFlip(arg(args, 2, "/forex admin flip <ticker>"))
                    .forEach(runtime.announcer()::announce);
                runtime.save();
            }
            case "guardar" -> {
                runtime.save();
                sender.sendMessage(ForexAnnouncer.PREFIX + "§aMercado guardado.");
            }
            default -> sender.sendMessage(ForexAnnouncer.PREFIX + "§7/forex admin <ciclo|flip <ticker>|guardar>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(SUBCOMMANDS);
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.add("admin");
            }
            return filter(options, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> tickers = runtime.market().coins().stream().map(Shitcoin::ticker).toList();
        if (args.length == 2) {
            return switch (sub) {
                case "comprar", "vender", "ver", "grafico" -> filter(Stream.concat(Stream.of("esmeraldas"), tickers.stream()).toList(), args[1]);
                case "stake", "unstake", "rugpull", "clan", "comision", "dividendos" -> filter(ownedOrAll(sender, sub, tickers), args[1]);
                case "admin" -> sender.hasPermission(ADMIN_PERMISSION) ? filter(List.of("ciclo", "flip", "guardar"), args[1]) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "comprar" -> filter(List.of("100", "1000", "10k"), args[2]);
                case "vender", "stake", "unstake" -> filter(List.of("todo", "50%", "25%"), args[2]);
                case "comision" -> filter(List.of("3", "4", "5"), args[2]);
                case "dividendos" -> filter(List.of("0", "25", "50", "100"), args[2]);
                case "rugpull" -> filter(List.of("confirmar"), args[2]);
                case "clan" -> filter(List.of("quitar"), args[2]);
                case "admin" -> args[1].equalsIgnoreCase("flip") ? filter(tickers, args[2]) : List.of();
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> ownedOrAll(CommandSender sender, String sub, List<String> tickers) {
        if (!(sender instanceof Player player) || sub.equals("stake") || sub.equals("unstake")) {
            return tickers;
        }
        return runtime.market().coins().stream().filter(coin -> coin.creator().equals(player.getUniqueId()))
            .map(Shitcoin::ticker).toList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private static String arg(String[] args, int index, String usage) {
        if (args.length <= index || args[index].isBlank()) {
            throw new ForexException("Uso: " + usage);
        }
        return args[index];
    }
}
