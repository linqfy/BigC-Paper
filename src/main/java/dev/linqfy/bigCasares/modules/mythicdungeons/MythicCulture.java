package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Las nueve culturas de las mazmorras miticas (sin Bukkit). Todas tienen su mazmorra (la estructura
 * {@code bigc_myth:<id>} del datapack) y sus mobs ({@link #hasMobs()}). Solo las que {@link #hasBoss() tienen jefe}
 * (del sistema pve-boss, hoy las nueve) tienen ademas Fragmento de Odio e Invocador.
 * Los ids son estables: los usan los items, los modelos, el datapack y los encuentros de los jefes.
 */
public enum MythicCulture {

    GREEK("greek", "Griega", "la mazmorra griega",
        new MythicBoss("myth-zeus", "zeus", "Zeus"), List.of("minotaur", "cerberus", "erinys")),
    NORSE("norse", "Nórdica", "la mazmorra nórdica",
        new MythicBoss("myth-odin", "odin", "Odín"), List.of("draugr", "dwarf", "nidhogg", "hel")),
    MEDIEVAL("medieval", "Europa medieval", "la mazmorra medieval",
        new MythicBoss("myth-diablo", "diablo", "Diablo"), List.of("treasure_dragon", "kobold", "goblin", "ghost")),
    EGYPTIAN("egyptian", "Egipcia", "la mazmorra egipcia",
        new MythicBoss("myth-horus", "horus", "Horus"), List.of("ammit", "apophis", "mummy")),
    MESOPOTAMIAN("mesopotamian", "Mesopotámica", "la mazmorra mesopotámica",
        new MythicBoss("myth-enlil", "enlil", "Enlil"), List.of("ereshkigal", "galla")),
    JAPANESE("japanese", "Japonesa", "la mazmorra japonesa",
        new MythicBoss("myth-enma", "enma", "Enma"), List.of("oni", "cave_yokai", "shikome")),
    MESOAMERICAN("mesoamerican", "Mesoamericana", "la mazmorra mesoamericana",
        new MythicBoss("myth-came", "came", "Hun Camé y Vucub Camé"), List.of("xibalba_lord", "xolotl", "camazotz_bat")),
    ARABIAN("arabian", "Árabe", "la mazmorra árabe",
        new MythicBoss("myth-iblis", "iblis", "Iblís"), List.of("ghul", "djinn")),
    ANDEAN("andean", "Sudamericana", "la mazmorra sudamericana",
        new MythicBoss("myth-pachamama", "pachamama", "Pachamama"), List.of("supay", "salamanca_viper", "salamanca_goat"));

    /** Espacio de nombres de las estructuras del datapack de las mazmorras. */
    public static final String STRUCTURE_NAMESPACE = "bigc_myth";
    /** Prefijo de todas las claves de modelo de BetterModel del modulo. */
    public static final String MODEL_PREFIX = "bigcasares_myth_";

    /** El jefe de una cultura que lo tiene: su id en pve-boss ({@code bosses/<id>.yml}), id corto y nombre. */
    public record MythicBoss(String id, String shortId, String displayName) {

        public MythicBoss {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(shortId, "shortId");
            Objects.requireNonNull(displayName, "displayName");
        }

        /** Clave de BetterModel: {@code bigcasares_myth_<id corto>}. */
        public String modelKey() {
            return MODEL_PREFIX + shortId;
        }
    }

    private final String id;
    private final String displayName;
    private final String dungeonName;
    private final MythicBoss boss;
    private final List<String> mobIds;

    MythicCulture(String id, String displayName, String dungeonName, MythicBoss boss, List<String> mobIds) {
        this.id = id;
        this.displayName = displayName;
        this.dungeonName = dungeonName;
        this.boss = boss;
        this.mobIds = List.copyOf(mobIds);
    }

    public String id() {
        return id;
    }

    /** Nombre de la cultura para mostrar ("Griega", "Europa medieval"...). */
    public String displayName() {
        return displayName;
    }

    /** Como se nombra su mazmorra en una frase ("la mazmorra griega"). */
    public String dungeonName() {
        return dungeonName;
    }

    /** Si su mazmorra se llena con mobs propios (spawners y spawns naturales); si no, sigue vanilla. */
    public boolean hasMobs() {
        return !mobIds.isEmpty();
    }

    /** Si ya tiene jefe y con el su Fragmento de Odio y su Invocador. */
    public boolean hasBoss() {
        return boss != null;
    }

    /** Lo mismo que {@link #hasBoss()}; se mantiene para quien todavia lo llame asi (los encuentros de {@code boss/}). */
    public boolean developed() {
        return hasBoss();
    }

    public Optional<MythicBoss> boss() {
        return Optional.ofNullable(boss);
    }

    /** El jefe de la cultura; falla con las que todavia no lo tienen. */
    public MythicBoss requireBoss() {
        if (boss == null) {
            throw new IllegalStateException("La cultura " + id + " todavía no tiene jefe");
        }
        return boss;
    }

    /** Los mobs de la cultura, en el orden fijo de la tabla. */
    public List<String> mobIds() {
        return mobIds;
    }

    public Optional<String> fragmentItemId() {
        return hasBoss() ? Optional.of("hate_fragment_" + id) : Optional.empty();
    }

    public Optional<String> summonerItemId() {
        return hasBoss() ? Optional.of("boss_summoner_" + id) : Optional.empty();
    }

    /** Clave de la estructura del datapack, por ejemplo {@code bigc_myth:greek}. */
    public String structureKey() {
        return STRUCTURE_NAMESPACE + ":" + id;
    }

    /** Lo que se le contesta a quien pide el jefe, el fragmento o el invocador de una cultura que no tiene jefe. */
    public String notAvailableMessage() {
        return "§eEl jefe de la cultura " + displayName + " todavía no está disponible: por ahora solo existen su"
            + " mazmorra y sus mobs.";
    }

    public static String mobModelKey(String mobId) {
        return MODEL_PREFIX + mobId;
    }

    /** Las culturas con mobs propios (hoy las nueve). */
    public static List<MythicCulture> mobCultures() {
        return Arrays.stream(values()).filter(MythicCulture::hasMobs).toList();
    }

    /** Las culturas con jefe, Fragmento de Odio e Invocador. */
    public static List<MythicCulture> bossCultures() {
        return Arrays.stream(values()).filter(MythicCulture::hasBoss).toList();
    }

    /** Lo mismo que {@link #bossCultures()}; se mantiene para quien todavia lo llame asi. */
    public static List<MythicCulture> developedCultures() {
        return bossCultures();
    }

    public static Optional<MythicCulture> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (MythicCulture culture : values()) {
            if (culture.id.equals(normalized)) {
                return Optional.of(culture);
            }
        }
        return Optional.empty();
    }

    public static Optional<MythicCulture> byStructureKey(String key) {
        if (key == null || !key.startsWith(STRUCTURE_NAMESPACE + ":")) {
            return Optional.empty();
        }
        return byId(key.substring(STRUCTURE_NAMESPACE.length() + 1));
    }

    public static Optional<MythicCulture> byBossId(String bossId) {
        for (MythicCulture culture : values()) {
            if (culture.boss != null && culture.boss.id().equals(bossId)) {
                return Optional.of(culture);
            }
        }
        return Optional.empty();
    }

    /** La cultura cuyo Fragmento de Odio tiene este id de item, si es uno. */
    public static Optional<MythicCulture> byFragmentItemId(String itemId) {
        for (MythicCulture culture : values()) {
            if (itemId != null && culture.fragmentItemId().filter(itemId::equals).isPresent()) {
                return Optional.of(culture);
            }
        }
        return Optional.empty();
    }

    /** La cultura cuyo Invocador tiene este id de item, si es uno. */
    public static Optional<MythicCulture> bySummonerItemId(String itemId) {
        for (MythicCulture culture : values()) {
            if (itemId != null && culture.summonerItemId().filter(itemId::equals).isPresent()) {
                return Optional.of(culture);
            }
        }
        return Optional.empty();
    }
}
