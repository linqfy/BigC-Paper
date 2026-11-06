package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactMoneyTest {
    @Test void moneyIsShortenedWithoutEverExceedingSixCharacters() {
        assertEquals("0", LivePlaceholders.compact("0"));
        assertEquals("1250", LivePlaceholders.compact("1250"));
        assertEquals("9999", LivePlaceholders.compact("9999.75"));
        assertEquals("10K", LivePlaceholders.compact("10000"));
        assertEquals("25.3K", LivePlaceholders.compact("25300"));
        assertEquals("999K", LivePlaceholders.compact("999999"));
        assertEquals("1.25M", LivePlaceholders.compact("1250000"));
        assertEquals("12.5B", LivePlaceholders.compact("12500000000"));
        assertEquals("-1500", LivePlaceholders.compact("-1500"));
        assertEquals("-25.3K", LivePlaceholders.compact("-25300"));
        for (String value : new String[]{"1", "12", "123", "1234", "12345", "123456", "1234567", "12345678", "123456789", "1234567890", "999999999999999"})
            assertTrue(LivePlaceholders.compact(value).length() <= 6, value + " -> " + LivePlaceholders.compact(value));
        assertEquals("abc", LivePlaceholders.compact("abc"));
    }
}
