package dev.linqfy.bigCasares.modules.toffy;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Times of the scheduled tournament teleports: parsing what the admin typed and formatting stored epoch millis.
 * Pure: the current time, the zone and the formats are parameters. The formats are the {@code torneos.formato-*}
 * patterns of {@code toffy_eventos.yml}; the defaults ({@code HH:mm} and {@code dd/MM}) also accept one-digit hours.
 */
final class ToffyTournamentTime {
    static final String DEFAULT_TIME_FORMAT = "HH:mm";
    static final String DEFAULT_DATE_FORMAT = "dd/MM";
    static final String HOUR_USAGE = "La hora tiene que ser HH:mm (por ejemplo 21:30).";
    static final String DAY_USAGE = "La fecha tiene que ser dd/MM (por ejemplo 24/12).";
    private static final Pattern HOUR = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern DAY = Pattern.compile("(\\d{1,2})/(\\d{1,2})");
    /** Leap years come every four years, so a 29/02 is always found within this many years. */
    private static final int YEARS_TO_SEARCH = 8;

    private ToffyTournamentTime() {
    }

    /**
     * Epoch millis of the next moment that matches. Without a day: today at {@code hour}, or tomorrow when that
     * moment already passed. With a day: that day this year, or the next year when it already passed.
     *
     * @param hour HH:mm
     * @param day  dd/MM, or null for "today or tomorrow"
     * @throws IllegalArgumentException when the hour or the day is not valid (31/02 and 25:00 are refused)
     */
    static long resolve(String hour, String day, long now, ZoneId zone) {
        return resolve(hour, day, now, zone, DEFAULT_TIME_FORMAT, DEFAULT_DATE_FORMAT);
    }

    /**
     * Same as {@link #resolve(String, String, long, ZoneId)} with the configured formats. A date format that has a year
     * fixes the year; otherwise the next matching day is used. {@code dd/MM} is always accepted as a fallback.
     */
    static long resolve(String hour, String day, long now, ZoneId zone, String timeFormat, String dateFormat) {
        LocalTime time = time(hour, timeFormat);
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if (day == null) {
            long todayAt = at(today, time, zone);
            return todayAt > now ? todayAt : at(today.plusDays(1), time, zone);
        }
        Day parsed = day(day, dateFormat);
        if (parsed.year() != null) {
            if (!exists(parsed.year(), parsed.month(), parsed.day())) {
                throw new IllegalArgumentException("La fecha " + day + " no existe.");
            }
            long at = at(LocalDate.of(parsed.year(), parsed.month(), parsed.day()), time, zone);
            if (at <= now) {
                throw new IllegalArgumentException("La fecha " + day + " ya pasó.");
            }
            return at;
        }
        if (!exists(2000, parsed.month(), parsed.day())) {
            throw new IllegalArgumentException("La fecha " + day + " no existe.");
        }
        for (int year = today.getYear(); year <= today.getYear() + YEARS_TO_SEARCH; year++) {
            if (!exists(year, parsed.month(), parsed.day())) {
                continue;
            }
            long at = at(LocalDate.of(year, parsed.month(), parsed.day()), time, zone);
            if (at > now) {
                return at;
            }
        }
        throw new IllegalArgumentException("La fecha " + day + " no existe.");
    }

    static String hour(long at, ZoneId zone) {
        return hour(at, zone, DEFAULT_TIME_FORMAT);
    }

    /** The time of {@code at} in the configured format. */
    static String hour(long at, ZoneId zone, String timeFormat) {
        return formatter(timeFormat).format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zone));
    }

    /** dd/MM/yyyy */
    static String date(long at, ZoneId zone) {
        return date(at, zone, "dd/MM/yyyy");
    }

    /** The date of {@code at} in the configured format. */
    static String date(long at, ZoneId zone, String dateFormat) {
        return formatter(dateFormat).format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(at), zone));
    }

    /** dd/MM/yyyy HH:mm */
    static String dateTime(long at, ZoneId zone) {
        return date(at, zone) + " " + hour(at, zone);
    }

    /** The date and the time of {@code at} in the configured formats. */
    static String dateTime(long at, ZoneId zone, String timeFormat, String dateFormat) {
        return date(at, zone, dateFormat) + " " + hour(at, zone, timeFormat);
    }

    /** Usage line for the configured date format, for the admin messages. */
    static String dayUsage(String dateFormat) {
        return "La fecha tiene que ser " + dateFormat + " (por ejemplo " + dateFormat + ").";
    }

    /** Usage line for the configured time format, for the admin messages. */
    static String hourUsage(String timeFormat) {
        return "La hora tiene que ser " + timeFormat + ".";
    }

    /** STRICT resolution: 24:00 and 31/02 are refused instead of being adjusted. */
    private static DateTimeFormatter formatter(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    }

    /** Strict parser for a date: the year is {@code uuuu} so STRICT resolution can use it. */
    private static DateTimeFormatter dateParser(String pattern) {
        return formatter(pattern.replace("yyyy", "uuuu").replace("yy", "uu"));
    }

    private static LocalTime time(String hour, String timeFormat) {
        if (hour == null) {
            throw new IllegalArgumentException(HOUR_USAGE);
        }
        try {
            return LocalTime.parse(hour.trim(), formatter(timeFormat));
        } catch (DateTimeException | IllegalArgumentException notTheFormat) {
            Matcher matcher = HOUR.matcher(hour.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException(timeFormat.equals(DEFAULT_TIME_FORMAT) ? HOUR_USAGE : hourUsage(timeFormat));
            }
            int hours = Integer.parseInt(matcher.group(1));
            int minutes = Integer.parseInt(matcher.group(2));
            if (hours > 23 || minutes > 59) {
                throw new IllegalArgumentException(HOUR_USAGE);
            }
            return LocalTime.of(hours, minutes);
        }
    }

    /** A day as typed: the year is null when the format has none. */
    private record Day(int day, int month, Integer year) {
    }

    private static Day day(String text, String dateFormat) {
        try {
            TemporalAccessor parsed = dateParser(dateFormat).parse(text.trim());
            Integer year = null;
            if (parsed.isSupported(ChronoField.YEAR)) {
                year = parsed.get(ChronoField.YEAR);
            } else if (parsed.isSupported(ChronoField.YEAR_OF_ERA)) {
                year = parsed.get(ChronoField.YEAR_OF_ERA);
            }
            return new Day(parsed.get(ChronoField.DAY_OF_MONTH), parsed.get(ChronoField.MONTH_OF_YEAR), year);
        } catch (DateTimeException | IllegalArgumentException notTheFormat) {
            Matcher matcher = DAY.matcher(text.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException(dateFormat.equals(DEFAULT_DATE_FORMAT) ? DAY_USAGE : dayUsage(dateFormat));
            }
            return new Day(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), null);
        }
    }

    private static boolean exists(int year, int month, int day) {
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (DateTimeException invalid) {
            return false;
        }
    }

    private static long at(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli();
    }
}
