package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Calculated text: {coins * 2}, {a ? "x" : "y"}... parsed from the page, values only ever operands. */
class ExpressionsTest {
    private static final Map<String, String> VALUES = new HashMap<>(Map.of("coins", "120", "price", "45", "discount", "5", "vip", "true",
            "name", "Alex", "zero", "0", "word", "abc", "item.price", "7.5", "%vault_eco_balance%", "1250.5"));
    private static String eval(String expression) { return Expressions.evaluate(expression, VALUES::get); }
    private static String render(String text) { return Expressions.render(text, UnaryOperator.identity(), VALUES::get); }

    @Test void numbersFollowTheUsualRules() {
        assertEquals("240", eval("coins * 2"));
        assertEquals("20", eval("(price - discount) / 2"));
        assertEquals("7", eval("1 + 2 * 3"));
        assertEquals("9", eval("(1 + 2) * 3"));
        assertEquals("-115", eval("-coins + 5"));
        assertEquals("0", eval("coins % 12"));
        assertEquals("33.333333", eval("100 / 3"), "at most six decimals, like variables");
        assertEquals("15", eval("item.price * 2"));
        assertEquals("2501", eval("%vault_eco_balance% * 2"));
    }
    @Test void functionsRoundAndPick() {
        assertEquals("33", eval("round(100 / 3)"));
        assertEquals("33.3", eval("round(100 / 3, 1)"));
        assertEquals("3", eval("floor(3.9)"));
        assertEquals("4", eval("ceil(3.1)"));
        assertEquals("5", eval("abs(0 - 5)"));
        assertEquals("5", eval("min(coins, price, discount)"));
        assertEquals("120", eval("max(coins, price)"));
        assertNull(eval("round(1, 7)"), "at most six places");
        assertNull(eval("abs(1, 2)"), "abs takes one value");
    }
    @Test void textComparisonsAndDecisions() {
        assertEquals("Enough", eval("coins >= 100 ? \"Enough\" : \"Missing\""));
        assertEquals("Missing", eval("price >= 100 ? 'Enough' : 'Missing'"));
        assertEquals("VIP", eval("vip ? \"VIP\" : \"Normal\""));
        assertEquals("Hi Alex!", eval("\"Hi \" + name + \"!\""));
        assertEquals("Level 13", eval("\"Level \" + (12 + 1)"));
        assertEquals("true", eval("name == \"Alex\" && coins > price"));
        assertEquals("false", eval("!vip"));
        assertEquals("false", eval("word > 3"), "ordering needs numbers on both sides");
        assertEquals("yes", eval("zero ? \"no\" : \"yes\""), "0 is false, like conditions");
        assertEquals("small", eval("zero > 0 ? coins / zero : \"small\""), "only the chosen branch is computed");
    }
    @Test void anythingThatCannotBeComputedStaysAsWritten() {
        assertEquals("Total {coins / zero}", render("Total {coins / zero}"), "division by zero");
        assertEquals("{word * 2}", render("{word * 2}"), "a word where a number is needed");
        assertEquals("{missing + 1}", render("{missing + 1}"), "unknown name");
        assertEquals("{a b}", render("{a b}"), "not an expression");
        assertEquals("{\"key\": 1}", render("{\"key\": 1}"), "JSON-looking text");
        assertEquals("{bar:health}", render("{bar:health}"), "bars keep their own syntax");
        assertEquals("{ SHOP }", render("{ SHOP }"), "names are lower case");
        assertEquals("{coins}", render("{coins}"), "plain tokens go through the normal variable pass");
        assertFalse(Expressions.contains("Buy {coins} now"));
        assertTrue(Expressions.contains("Buy {coins * 2} now"));
        assertNull(eval("1 +"));
        assertNull(eval("((((((((((((((((((((((((((((((((((1))))))))))))))))))))))))))))))))))"), "nesting is bounded");
    }
    @Test void renderingKeepsTheTextAroundAndEachTokenOnItsOwn() {
        assertEquals("Price: 90 (VIP)", render("Price: {price * 2} ({vip ? \"VIP\" : \"Normal\"})"));
        assertEquals("$90", render("${price * 2}"), "a dollar sign before a calculation is just text");
        var seen = new StringBuilder();
        String out = Expressions.render("A {coins} B {coins + 1} C", part -> { seen.append('[').append(part).append(']'); return part.toUpperCase(); }, VALUES::get);
        assertEquals("A {COINS} B 121 C", out);
        assertEquals("[A {coins} B ][ C]", seen.toString(), "only the text around a computed token goes through the chunk pass");
    }
    @Test void typedValuesAreNeverParsed() {
        var typed = new HashMap<>(VALUES);
        typed.put("answer", "1 + 1");
        typed.put("evil", "} {coins * 1000");
        typed.put("quote", "\" + coins + \"");
        assertEquals("1 + 1!", Expressions.evaluate("answer + \"!\"", typed::get), "a typed sum is text, not a calculation");
        assertEquals("} {coins * 1000x", Expressions.evaluate("evil + \"x\"", typed::get));
        assertEquals("\" + coins + \"", Expressions.evaluate("quote + \"\"", typed::get));
        assertEquals("false", Expressions.evaluate("answer == 2", typed::get));
    }
    @Test void conditionsUseCalculatedOperands() {
        var variables = new Variables();
        variables.declare(Map.of("coins", "60", "price", "25"));
        UnaryOperator<String> resolve = text -> Expressions.render(text, part -> ActionRunner.substitute(part, Map.of(), variables), variables::get);
        assertTrue(Conditions.test("{coins - price * 2} >= 10", resolve));
        assertTrue(Conditions.test("{coins >= price * 2 ? \"yes\" : \"no\"} == yes", resolve), "operators inside braces are not split");
        assertFalse(Conditions.test("{coins < price}", resolve));
    }
}
