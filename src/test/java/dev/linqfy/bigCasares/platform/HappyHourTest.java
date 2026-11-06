package dev.linqfy.bigCasares.platform;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HappyHourTest {
    private static HappyHour load(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return HappyHour.load(config);
    }

    private static ZonedDateTime at(String time) {
        return ZonedDateTime.parse(time);
    }

    @Test
    void offUnlessEnabledAndTheBundledConfigLeavesItOff() throws Exception {
        assertEquals(HappyHour.OFF, load(""));
        assertEquals(HappyHour.OFF, load("eventos:\n  hora-feliz:\n    enabled: false\n    dias: [SATURDAY]\n"));
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        assertFalse(HappyHour.load(bundled).enabled());
    }

    @Test
    void activeOnItsDaysBetweenItsTimes() throws Exception {
        HappyHour saturday = load("eventos:\n  hora-feliz:\n    enabled: true\n    dias: [saturday]\n    desde: '20:00'\n    hasta: '22:00'\n");
        assertTrue(saturday.active(at("2026-11-07T20:00:00Z")), "a Saturday at 20:00");
        assertTrue(saturday.active(at("2026-11-07T21:59:00Z")));
        assertFalse(saturday.active(at("2026-11-07T22:00:00Z")), "ends at 22:00");
        assertFalse(saturday.active(at("2026-11-06T21:00:00Z")), "not on Friday");
    }

    @Test
    void aWindowPastMidnightBelongsToTheDayItStarts() throws Exception {
        HappyHour late = load("eventos:\n  hora-feliz:\n    enabled: true\n    dias: [SATURDAY]\n    desde: '23:00'\n    hasta: '01:00'\n");
        assertTrue(late.active(at("2026-11-07T23:30:00Z")));
        assertTrue(late.active(at("2026-11-08T00:30:00Z")), "Sunday 00:30 is still Saturday's");
        assertFalse(late.active(at("2026-11-09T00:30:00Z")), "but not Monday 00:30");
    }

    @Test
    void valuesAreClampedAndAnUnreadableTimeTurnsItOff() throws Exception {
        HappyHour greedy = load("eventos:\n  hora-feliz:\n    enabled: true\n    dias: [MONDAY]\n    premio-misiones: 50\n    descuento-tienda: 0.9\n");
        assertEquals(5, greedy.missionMultiplier());
        assertEquals(0.5, greedy.shopDiscount());
        assertEquals(HappyHour.OFF, load("eventos:\n  hora-feliz:\n    enabled: true\n    dias: [MONDAY]\n    desde: 'ocho'\n"));
        assertEquals(HappyHour.OFF, load("eventos:\n  hora-feliz:\n    enabled: true\n    dias: [LUNES]\n"));
    }
}
