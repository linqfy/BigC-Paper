package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

/**
 * Resolves live-text tokens on the player's owning thread. Built-ins need nothing else; {balance}
 * uses Vault when installed (else the value set through TideframeApi.setBalance), and %tokens% go
 * through PlaceholderAPI when installed. Both integrations are reflective: no hard dependency.
 */
final class LivePlaceholders {
    private static final Pattern BUILT_IN = Pattern.compile("\\{([a-z_]{1,24})}");
    private static final Pattern PAPI_TOKEN = Pattern.compile("%[A-Za-z0-9_:.-]{1,64}%");
    private volatile Method papi;
    private volatile boolean papiChecked;
    private volatile Object economy;
    private volatile Method economyBalance;
    private volatile boolean economyChecked;

    /**
     * Screen logic behind live text: variables and list-row tokens are inserted after placeholders (so typed text is
     * never resolved again), hidden elements need no value, list templates draw one value per visible row.
     */
    interface Values {
        String insert(String text, Map<String, String> scope);
        String variable(String name);
        List<Map<String, String>> rows(Screen.Element template);
        boolean shown(String id);
        Values NONE = new Values() {
            public String insert(String text, Map<String, String> scope) { return text; }
            public String variable(String name) { return null; }
            public List<Map<String, String>> rows(Screen.Element template) { return List.of(); }
            public boolean shown(String id) { return true; }
        };
    }
    /** Element id -> resolved text, for every live element of the screen. */
    Map<String, String> resolve(Player player, Screen screen, Function<Player, BigDecimal> apiBalance) {
        return resolve(player, screen, apiBalance, Map.of());
    }
    /** {@code extra}: game values ("key" -> "W", "bar:boss" -> "0.5000") that override unknown tokens and bar sources. */
    Map<String, String> resolve(Player player, Screen screen, Function<Player, BigDecimal> apiBalance, Map<String, String> extra) {
        return resolve(player, screen, apiBalance, extra, Values.NONE);
    }
    Map<String, String> resolve(Player player, Screen screen, Function<Player, BigDecimal> apiBalance, Map<String, String> extra, Values logic) {
        var values = new LinkedHashMap<String, String>();
        for (var element : screen.elements()) {
            if (!logic.shown(element.id())) continue;
            if (element.logic().list() != null) {
                var rows = logic.rows(element);
                boolean live = LiveText.isLive(element), bar = LiveText.isBar(element);
                if (!live && !bar) continue;
                for (int i = 0; i < rows.size(); i++) {
                    String id = dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows.rowId(element.id(), i);
                    if (bar) values.put(id, barValue(player, element, apiBalance, extra, logic, rows.get(i)));
                    else values.put(id, renderLive(player, screen, element.text(), apiBalance, extra, logic, rows.get(i)));
                }
                continue;
            }
            if (LiveText.isBar(element)) values.put(element.id(), barValue(player, element, apiBalance, extra, logic, Map.of()));
            else if (LiveText.isLive(element)) values.put(element.id(), renderLive(player, screen, element.text(), apiBalance, extra, logic, Map.of()));
        }
        return values;
    }
    /**
     * Live text with words in their own colour keeps them: each run is filled in on its own and travels in
     * RichText.compose's frame. A schema 1-8 page's text has neither colours nor calculated tokens: placeholders and
     * variables only, as it had (Screen.legacyText).
     */
    private String renderLive(Player player, Screen screen, String template, Function<Player, BigDecimal> apiBalance, Map<String, String> extra, Values logic, Map<String, String> scope) {
        if (screen.legacyText()) return logic.insert(apply(player, template, apiBalance, extra), scope);
        if (!dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.hasTags(template)) return render(player, template, apiBalance, extra, logic, scope);
        var runs = new java.util.ArrayList<dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.Run>();
        for (var run : dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.runs(template)) runs.add(new dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.Run(render(player, run.text(), apiBalance, extra, logic, scope), run.color()));
        return dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.compose(runs);
    }
    /**
     * Live text: placeholders, then variables and row values; calculated tokens ({coins * 2}) take their operands from
     * the same places (a %placeholder%, a built-in, the extra game values, the row, a variable).
     */
    private String render(Player player, String template, Function<Player, BigDecimal> apiBalance, Map<String, String> extra, Values logic, Map<String, String> scope) {
        return dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Expressions.render(template, part -> logic.insert(apply(player, part, apiBalance, extra), scope), name -> {
            if (name.startsWith("%")) { String placed = apply(player, name, apiBalance, extra); return placed.equals(name) ? null : placed; }
            if (name.startsWith("$")) return name.indexOf('.') < 0 ? logic.variable(name) : null;
            if (extra.containsKey(name)) return extra.get(name);
            if (name.matches("[a-z_]{1,24}")) {
                String token = "{" + name + "}", builtIn = builtIn(player, name, apiBalance, token);
                if (!builtIn.equals(token)) return builtIn;
            }
            if (scope.containsKey(name)) return scope.get(name);
            return name.indexOf('.') < 0 ? logic.variable(name) : null;
        });
    }
    private String barValue(Player player, Screen.Element element, Function<Player, BigDecimal> apiBalance, Map<String, String> extra, Values logic, Map<String, String> scope) {
        String source = LiveText.barSource(element);
        if (LiveText.builtInBar(element)) return extra.containsKey("bar:" + source) ? extra.get("bar:" + source) : fraction(player, source);
        String max = LiveText.barMax(element);
        Double value = number(player, source, apiBalance, extra, logic, scope), limit = max == null ? Double.valueOf(100) : number(player, max, apiBalance, extra, logic, scope);
        if (value == null || limit == null || limit <= 0) return "0.0000";
        return String.format(Locale.ROOT, "%.4f", Math.round(Math.max(0, Math.min(1, value / limit)) * 256) / 256.0);
    }
    /** A bar operand: a number, %placeholder% (with optional clamp) or a variable (scope tokens first). */
    private Double number(Player player, String operand, Function<Player, BigDecimal> apiBalance, Map<String, String> extra, Values logic, Map<String, String> scope) {
        String text;
        if (operand.matches("[0-9.]+")) text = operand;
        else if (operand.startsWith("%")) text = apply(player, operand, apiBalance, extra);
        else text = scope.containsKey(operand) ? scope.get(operand) : logic.variable(operand);
        if (text == null) return null;
        try { double parsed = Double.parseDouble(text.trim()); return Double.isFinite(parsed) ? parsed : null; }
        catch (NumberFormatException notNumber) { return null; }
    }

    String apply(Player player, String template, Function<Player, BigDecimal> apiBalance) { return apply(player, template, apiBalance, Map.of()); }
    String apply(Player player, String template, Function<Player, BigDecimal> apiBalance, Map<String, String> extra) {
        // Ultimate UI pages write %player% for the player's name.
        if (template.contains("%player%")) template = template.replace("%player%", player.getName());
        if (template.indexOf('%') >= 0 && (template.contains(",MIN=") || template.contains(",MAX=") || template.contains(",DEFAULT=")))
            template = dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Conditions.clamp(template, token -> papi(player, token));
        Matcher matcher = BUILT_IN.matcher(template);
        var out = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(out, Matcher.quoteReplacement(
                extra.containsKey(matcher.group(1)) ? extra.get(matcher.group(1)) : builtIn(player, matcher.group(1), apiBalance, matcher.group())));
        matcher.appendTail(out);
        String text = out.toString();
        if (PAPI_TOKEN.matcher(text).find()) text = papi(player, text);
        return text;
    }

    private String builtIn(Player player, String name, Function<Player, BigDecimal> apiBalance, String original) {
        var location = player.getLocation();
        return switch (name) {
            case "player" -> player.getName();
            case "health" -> String.valueOf((int) Math.ceil(player.getHealth()));
            case "max_health" -> String.valueOf((int) Math.round(maxHealth(player)));
            case "food" -> String.valueOf(player.getFoodLevel());
            case "level" -> String.valueOf(player.getLevel());
            case "xp" -> String.valueOf(Math.round(player.getExp() * 100)) + "%";
            case "ping" -> String.valueOf(player.getPing());
            case "online" -> String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max_players" -> String.valueOf(Bukkit.getMaxPlayers());
            case "world" -> player.getWorld().getName();
            case "x" -> String.valueOf(location.getBlockX());
            case "y" -> String.valueOf(location.getBlockY());
            case "z" -> String.valueOf(location.getBlockZ());
            case "time" -> { long t = (player.getWorld().getTime() + 6000) % 24000; yield String.format(Locale.ROOT, "%02d:%02d", t / 1000, t % 1000 * 60 / 1000); }
            case "balance" -> balance(player, apiBalance);
            case "balance_short" -> compact(balance(player, apiBalance));
            default -> original; // Unknown tokens stay visible so authors notice typos.
        };
    }

    /** Fill fraction of a progress bar, rounded to 1/256 so the live key only changes when pixels can. */
    private static String fraction(Player player, String source) {
        double value = switch (source) {
            case "health" -> player.getHealth() / Math.max(1, maxHealth(player));
            case "food" -> player.getFoodLevel() / 20.0;
            case "xp" -> player.getExp();
            case "air" -> player.getMaximumAir() <= 0 ? 1 : Math.max(0, player.getRemainingAir()) / (double) player.getMaximumAir();
            case "armor" -> attribute(player, "armor", 0) / 20.0;
            default -> 0; // boss, hp, timer: supplied by a running game

        };
        return String.format(Locale.ROOT, "%.4f", Math.round(Math.max(0, Math.min(1, value)) * 256) / 256.0);
    }
    private static double attribute(Player player, String name, double fallback) {
        for (String key : new String[]{name, "generic." + name}) {
            try {
                Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (attribute != null) { var instance = player.getAttribute(attribute); if (instance != null) return instance.getValue(); }
            } catch (RuntimeException | LinkageError ignored) { }
        }
        return fallback;
    }

    /** "max_health" on 26.x, "generic.max_health" on older servers; looked up by key so renamed constants cannot break it. */
    private static double maxHealth(Player player) {
        for (String key : new String[]{"max_health", "generic.max_health"}) {
            try {
                Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (attribute != null) { var instance = player.getAttribute(attribute); if (instance != null) return instance.getValue(); }
            } catch (RuntimeException | LinkageError ignored) { }
        }
        return 20;
    }

    private String balance(Player player, Function<Player, BigDecimal> apiBalance) {
        Object vault = economy();
        if (vault != null) {
            try { return format(BigDecimal.valueOf(((Number) economyBalance.invoke(vault, (OfflinePlayer) player)).doubleValue())); }
            catch (ReflectiveOperationException | RuntimeException failure) { economyChecked = false; }
        }
        var supplied = apiBalance.apply(player);
        return supplied == null ? "0" : format(supplied);
    }
    /** 1250 -> 1250, 25300 -> 25.3K, 1250000 -> 1.25M: at most 6 characters, so a HUD never cuts digits off. */
    static String compact(String plain) {
        BigDecimal value;
        try { value = new BigDecimal(plain); } catch (NumberFormatException notNumber) { return plain; }
        boolean negative = value.signum() < 0; value = value.abs();
        String[] units = {"", "K", "M", "B", "T", "Q"};
        int unit = 0;
        while (value.compareTo(BigDecimal.valueOf(10_000)) >= 0 && unit < units.length - 1 || unit > 0 && value.compareTo(BigDecimal.valueOf(1000)) >= 0 && unit < units.length - 1) {
            value = value.divide(BigDecimal.valueOf(1000)); unit++;
        }
        String digits;
        if (unit == 0) digits = value.setScale(0, RoundingMode.DOWN).toPlainString();
        else {
            int decimals = value.compareTo(BigDecimal.valueOf(100)) >= 0 ? 0 : value.compareTo(BigDecimal.TEN) >= 0 ? 1 : 2;
            digits = value.setScale(decimals, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
        }
        return (negative ? "-" : "") + digits + units[unit];
    }
    private static String format(BigDecimal value) {
        var scaled = value.setScale(2, RoundingMode.HALF_UP);
        return scaled.stripTrailingZeros().scale() <= 0 ? scaled.setScale(0, RoundingMode.UNNECESSARY).toPlainString() : scaled.toPlainString();
    }

    private Object economy() {
        if (!economyChecked) {
            economyChecked = true; economy = null;
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
                    Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy", true, Bukkit.getPluginManager().getPlugin("Vault").getClass().getClassLoader());
                    var registration = Bukkit.getServicesManager().getRegistration(type);
                    if (registration != null) { economy = registration.getProvider(); economyBalance = type.getMethod("getBalance", OfflinePlayer.class); }
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { economy = null; }
        }
        return economy;
    }

    private String papi(Player player, String text) {
        if (!papiChecked) {
            papiChecked = true; papi = null;
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                    Class<?> type = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, Bukkit.getPluginManager().getPlugin("PlaceholderAPI").getClass().getClassLoader());
                    papi = type.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { papi = null; }
        }
        if (papi == null) return text;
        try { return String.valueOf(papi.invoke(null, (OfflinePlayer) player, text)); }
        catch (ReflectiveOperationException | RuntimeException failure) { return text; }
    }
    /** Forget cached integrations (after /tideframe reload or plugin changes). */
    void reset() { papiChecked = false; economyChecked = false; }
}
