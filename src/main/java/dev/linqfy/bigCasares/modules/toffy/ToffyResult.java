package dev.linqfy.bigCasares.modules.toffy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Breakdown of one evaluation.
 *
 * @param value          level rounded to the configured decimals, between 0 and {@code maximum}
 * @param exactValue     level without rounding
 * @param maximum        configured maximum level (200 by default)
 * @param progress       combined progress {@code t} before the curve, between 0 and 1
 * @param curvedProgress {@code f(t)}, the fraction of {@code maximum} that was given
 * @param curveType      curve used
 * @param armorScore     armor score {@code A}, between 0 and 1
 * @param armorPower     raw armor power (sum of the four slots)
 * @param armorPieces    the piece that counted in each slot (the best one among the candidates)
 * @param weapon         best weapon (score {@code W})
 * @param tool           best tool (score {@code T})
 */
public record ToffyResult(
    double value,
    double exactValue,
    double maximum,
    double progress,
    double curvedProgress,
    ToffyCurveType curveType,
    double armorScore,
    double armorPower,
    Map<ToffyArmorSlot, ToffyItem> armorPieces,
    Held weapon,
    Held tool
) {
    public ToffyResult {
        EnumMap<ToffyArmorSlot, ToffyItem> pieces = new EnumMap<>(ToffyArmorSlot.class);
        pieces.putAll(Objects.requireNonNull(armorPieces, "armorPieces"));
        armorPieces = Collections.unmodifiableMap(pieces);
    }

    public double weaponScore() {
        return weapon.score();
    }

    public double toolScore() {
        return tool.score();
    }

    public boolean hasWeapon() {
        return weapon.present();
    }

    public boolean hasTool() {
        return tool.present();
    }

    /**
     * The best held item of one category.
     *
     * @param family family it belongs to, or {@code null} when none was found
     * @param item   the item, or {@code null} when none was found
     * @param power  raw power, 0 when none was found
     * @param score  score between 0 and 1 against its family's references
     */
    public record Held(String family, ToffyItem item, double power, double score) {
        public static final Held NONE = new Held(null, null, 0.0, 0.0);

        public boolean present() {
            return item != null;
        }
    }
}
