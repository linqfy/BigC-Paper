package dev.linqfy.bigCasares.platform;

import org.bukkit.configuration.ConfigurationSection;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * "Hora feliz" ({@code eventos.hora-feliz} in config.yml, off by default): on the chosen days, between two times, missions
 * pay more and the shop sells cheaper. Values outside safe bounds are clamped; an unreadable time turns it off.
 *
 * @param missionMultiplier what mission rewards are multiplied by while it lasts (1 to 5)
 * @param shopDiscount      what is taken off shop purchase prices while it lasts (0 to 0.5); sale prices never change
 */
public record HappyHour(boolean enabled, Set<DayOfWeek> days, LocalTime from, LocalTime to, double missionMultiplier,
                        double shopDiscount) {
    public static final HappyHour OFF = new HappyHour(false, Set.of(), LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, 1, 0);

    public HappyHour {
        days = days.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(days));
        missionMultiplier = Math.max(1, Math.min(5, missionMultiplier));
        shopDiscount = Math.max(0, Math.min(0.5, shopDiscount));
    }

    /** From the root config (its {@code eventos.hora-feliz} section). */
    public static HappyHour load(ConfigurationSection root) {
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("eventos.hora-feliz");
        if (section == null || !section.getBoolean("enabled", false)) {
            return OFF;
        }
        try {
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            for (String day : section.getStringList("dias")) {
                days.add(DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT)));
            }
            return new HappyHour(true, days, LocalTime.parse(section.getString("desde", "20:00")),
                LocalTime.parse(section.getString("hasta", "22:00")), section.getDouble("premio-misiones", 2.0),
                section.getDouble("descuento-tienda", 0.1));
        } catch (IllegalArgumentException | DateTimeParseException unreadable) {
            return OFF;
        }
    }

    /** Whether it is on at {@code now}. A window past midnight belongs to the day it starts on. */
    public boolean active(ZonedDateTime now) {
        if (!enabled || from.equals(to)) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        if (from.isBefore(to)) {
            return days.contains(now.getDayOfWeek()) && !time.isBefore(from) && time.isBefore(to);
        }
        return days.contains(now.getDayOfWeek()) && !time.isBefore(from)
            || days.contains(now.getDayOfWeek().minus(1)) && time.isBefore(to);
    }
}
