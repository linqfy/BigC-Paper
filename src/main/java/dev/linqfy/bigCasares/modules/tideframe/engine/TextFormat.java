package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Chat text from pages: MiniMessage tags (&lt;green&gt;, &lt;#33ff3d&gt;, &lt;click:...&gt;) and legacy &amp;/§ codes, mixed freely. */
final class TextFormat {
    private TextFormat() { }
    private static final Pattern HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY = Pattern.compile("[&§]([0-9a-fA-Fk-oK-OrR])");
    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"), Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"), Map.entry('n', "underlined"),
            Map.entry('o', "italic"), Map.entry('r', "reset"));

    /** Legacy codes become MiniMessage tags; the result is parsed, or shown as plain text when it is not valid MiniMessage. */
    static String toMiniMessage(String text) {
        String hex = HEX.matcher(text).replaceAll("<#$1>");
        Matcher m = LEGACY.matcher(hex);
        var out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement("<" + TAGS.get(Character.toLowerCase(m.group(1).charAt(0))) + ">"));
        return m.appendTail(out).toString();
    }
    static Component parse(String text) {
        try { return MiniMessage.miniMessage().deserialize(toMiniMessage(text)); }
        catch (RuntimeException invalid) { return Component.text(text); }
    }
}
