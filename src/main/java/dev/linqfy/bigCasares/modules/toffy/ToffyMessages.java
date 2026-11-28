package dev.linqfy.bigCasares.modules.toffy;

import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Chat messages the library sends for each case. Pure: no Bukkit. A template comes from the tournament override,
 * then from {@code toffy_mensajes.yml}, then from the built-in default. An empty template means "do not send".
 *
 * <p>A case in the file is either a text or a section {@code {texto, tipo, subtitulo, sonido, volumen, tono}}. The
 * {@code tipo} is {@code chat} (default), {@code titulo}, {@code actionbar} or {@code ninguno}; only the sound plays
 * with {@code ninguno}.</p>
 */
final class ToffyMessages {
    static final String ANOTADO = "anotado";
    static final String YA_ANOTADO = "ya-anotado";
    static final String INSCRIPCION_CERRADA = "inscripcion-cerrada";
    static final String TORNEO_NO_EXISTE = "torneo-no-existe";
    static final String ANUNCIO_ANOTADO = "anuncio-anotado";
    static final String CIERRE = "cierre";
    static final String APERTURA = "apertura";
    static final String LLEVADO = "llevado";
    static final String DEVUELTO = "devuelto";
    static final String SIN_LUGAR_GUARDADO = "sin-lugar-guardado";
    static final String TP_PROGRAMADO = "tp-programado";
    static final String TP_AVISO = "tp-aviso";
    static final String TP_CANCELADO = "tp-cancelado";
    static final String NIVEL_FUERA_DE_RANGO = "nivel-fuera-de-rango";
    static final String MINIMO_NO_ALCANZADO = "minimo-no-alcanzado";
    static final String GRUPO_ANOTADO = "grupo-anotado";
    static final String GRUPO_CAMBIADO = "grupo-cambiado";
    static final String GRUPO_CERRADO = "grupo-cerrado";
    static final String GRUPO_LLENO = "grupo-lleno";
    static final String GRUPO_ELEGIR = "grupo-elegir";
    static final String GRUPO_ASIGNADO = "grupo-asignado";
    static final String EVENTO_AVISO = "evento-aviso";
    // Staff events announcements (see ToffyEvents).
    static final String EVENTO_LOBBY = ToffyEvents.CASE_LOBBY;
    static final String EVENTO_UNIDO = ToffyEvents.CASE_JOINED;
    static final String EVENTO_SALIO = ToffyEvents.CASE_LEFT;
    static final String EVENTO_EMPIEZA = ToffyEvents.CASE_STARTED;
    static final String EVENTO_GANADOR = ToffyEvents.CASE_WINNER;
    static final String EVENTO_ELIMINADO = ToffyEvents.CASE_ELIMINATED;
    static final String EVENTO_CANCELADO = ToffyEvents.CASE_CANCELLED;
    static final String EVENTO_FIN = ToffyEvents.CASE_FINISHED;

    /** Prefix of the Toffy messages, used by {prefijo}. */
    static final String DEFAULT_PREFIX = "&6Toffy &8» &7";
    /** Prefix of the event messages, sent before every chat event text that does not place {prefijo} itself. */
    static final String DEFAULT_EVENT_PREFIX = "&6[Eventos] &r";
    static final String PREFIX_KEY = "prefijo";
    static final String EVENT_PREFIX_KEY = "prefijo-eventos";
    static final String PLACEHOLDER_PREFIX = "prefijo";

    private static final Map<String, Entry> DEFAULTS = defaults();
    static final List<String> CASES = List.copyOf(DEFAULTS.keySet());
    /**
     * Event cases that are pieces of other messages (hill state, causes, reasons, button, item names). They are never
     * sent on their own, so the event prefix is not added to them.
     */
    static final Set<String> FRAGMENTS = Set.of(ToffyEvents.CASE_BOTON, ToffyEvents.CASE_BOTON_AYUDA,
        ToffyEvents.CASE_KIT_PALO, ToffyEvents.CASE_KIT_PALA, ToffyEvents.CASE_KIT_LORE,
        ToffyEvents.CASE_CAUSA_MURIO, ToffyEvents.CASE_CAUSA_CAYO, ToffyEvents.CASE_CAUSA_EXPLOTO,
        ToffyEvents.CASE_CAUSA_DESCONECTO, ToffyEvents.CASE_CAUSA_SALIO,
        ToffyEvents.CASE_KOTH_LIBRE, ToffyEvents.CASE_KOTH_DISPUTADA, ToffyEvents.CASE_KOTH_REY,
        ToffyEvents.CASE_MOTIVO_MINIMO, ToffyEvents.CASE_MOTIVO_DETENER, ToffyEvents.CASE_MOTIVO_TIEMPO_LIDER,
        ToffyEvents.CASE_MOTIVO_SIN_GANADOR, ToffyEvents.CASE_MOTIVO_NADIE);

    record Loaded(ToffyMessages messages, List<String> warnings) {
    }

    /** One case: its text and how it is shown. Built-in texts are chat unless a case says otherwise. */
    record Entry(ToffyEvents.Text.Kind kind, String text, String subtitle, String sound, float volume, float pitch) {
        Entry {
            kind = kind == null ? ToffyEvents.Text.Kind.CHAT : kind;
            text = text == null ? "" : text;
            subtitle = subtitle == null ? "" : subtitle;
            sound = sound == null ? "" : sound;
        }

        static Entry chat(String text) {
            return new Entry(ToffyEvents.Text.Kind.CHAT, text, "", "", 1f, 1f);
        }
    }

    private final Map<String, Entry> fileValues;
    private final String prefix;
    private final String eventPrefix;

    private ToffyMessages(Map<String, Entry> fileValues, String prefix, String eventPrefix) {
        this.fileValues = Map.copyOf(fileValues);
        this.prefix = prefix;
        this.eventPrefix = eventPrefix;
    }

    private static Map<String, Entry> defaults() {
        Map<String, Entry> map = new LinkedHashMap<>();
        put(map, ANOTADO, "&aTe anotaste al torneo &e{torneo}&a. Sos el participante &e#{cantidad}&a.");
        put(map, YA_ANOTADO, "&eYa estabas anotado en el torneo &6{torneo}&e.");
        put(map, INSCRIPCION_CERRADA, "&cLa inscripcion al torneo &e{torneo}&c esta cerrada.");
        put(map, TORNEO_NO_EXISTE, "&cNo existe el torneo &e{torneo}&c.");
        put(map, ANUNCIO_ANOTADO, "");
        put(map, CIERRE, "&6Se cerraron las inscripciones al torneo &e{torneo}&6. Participantes: &e{cantidad}&6.");
        put(map, APERTURA, "");
        put(map, LLEVADO, "&aFuiste llevado al torneo &e{torneo}&a.");
        put(map, DEVUELTO, "&aVolviste a donde estabas antes del torneo &e{torneo}&a.");
        put(map, SIN_LUGAR_GUARDADO, "&cNo hay un lugar guardado para devolverte del torneo &e{torneo}&c.");
        put(map, TP_PROGRAMADO, "&6Teletransporte del torneo &e{torneo}&6 programado para el &e{fecha}&6 a las &e{hora}&6.");
        put(map, TP_AVISO, "&6El torneo &e{torneo}&6 se teletransporta en &e{minutos} min&6.");
        put(map, TP_CANCELADO, "&7Se cancelo el teletransporte programado del torneo &e{torneo}&7.");
        put(map, NIVEL_FUERA_DE_RANGO, "&cNo podes entrar al torneo &e{torneo}&c: tu nivel es &e{nivel}&c y el torneo pide entre &e{min}&c y &e{max}&c.");
        put(map, MINIMO_NO_ALCANZADO, "&cSe cancelo el programado del torneo &e{torneo}&c: hay &e{cantidad}&c anotados conectados y el minimo es &e{minimo}&c.");
        put(map, GRUPO_ANOTADO, "&aEntraste al grupo &e{grupo}&a del torneo &e{torneo}&a.");
        put(map, GRUPO_CAMBIADO, "&aTe pasamos al grupo &e{grupo}&a del torneo &e{torneo}&a.");
        put(map, GRUPO_CERRADO, "&cEl grupo &e{grupo}&c del torneo &e{torneo}&c no se elige solo: pedile a un admin que te anote.");
        put(map, GRUPO_LLENO, "&cEl grupo &e{grupo}&c del torneo &e{torneo}&c esta lleno.");
        put(map, GRUPO_ELEGIR, "&6El torneo &e{torneo}&6 se anota por grupo. Grupos abiertos: &e{grupos}&6. Usa &e/anotar {torneo} <grupo>&6.");
        put(map, GRUPO_ASIGNADO, "&aUn admin te puso en el grupo &e{grupo}&a del torneo &e{torneo}&a.");
        put(map, EVENTO_AVISO, "&6El evento &e{evento}&6 del torneo &e{torneo}&6 empieza en &e{minutos} min&6.");
        put(map, EVENTO_LOBBY, "&6Va a empezar el evento &e{evento}&6 en &e{segundos} s&6. Anotate con &e/anotar {torneo}&6 para entrar.");
        put(map, EVENTO_UNIDO, "&aEntraste al evento &e{evento}&a. Tu inventario queda guardado y lo recuperas al salir.");
        put(map, EVENTO_SALIO, "&7{jugador} salio del evento &e{evento}&7. Quedan &e{cantidad}&7.");
        put(map, EVENTO_EMPIEZA, "&e{evento} empieza! &7Participantes: &e{cantidad}&7.");
        put(map, EVENTO_GANADOR, "&6&l{jugador} &7gano el evento &e{evento}&7.");
        put(map, EVENTO_ELIMINADO, "&c{jugador} quedo eliminado ({motivo}). &7Quedan &e{cantidad}&7.");
        put(map, EVENTO_CANCELADO, "&c{evento} se cancelo. &7{motivo}");
        put(map, EVENTO_FIN, "&e{evento} &7termino. {detalle}");
        eventDefaults(map);
        return map;
    }

    /** Built-in texts of the staff events. They reproduce the texts the events always sent. */
    private static void eventDefaults(Map<String, Entry> map) {
        put(map, ToffyEvents.CASE_NO_DISPONIBLE, "&cLos eventos no estan disponibles.");
        put(map, ToffyEvents.CASE_BOTON, "&a&l[UNIRSE]");
        put(map, ToffyEvents.CASE_BOTON_AYUDA, "&7Tu inventario se guarda y se devuelve al salir");
        put(map, ToffyEvents.CASE_CUENTA, "&eEmpieza en &f{segundos}&e...");
        put(map, ToffyEvents.CASE_UNIDO_OTROS, "&f{jugador} &7se unió ({cantidad}).");
        put(map, ToffyEvents.CASE_PREPARANDO, "&7La arena todavía se está preparando.");
        put(map, ToffyEvents.CASE_REAPARECE, "&cReaparecé antes de unirte.");
        put(map, ToffyEvents.CASE_YA_EMPEZO, "&cEl evento ya empezo.");
        put(map, ToffyEvents.CASE_INVENTARIO_OCUPADO, "&cTu inventario está ocupado por otra actividad o recuperación.");
        put(map, ToffyEvents.CASE_INVENTARIO_NO_GUARDADO, "&cNo se pudo guardar tu inventario; no entraste al evento.");
        put(map, ToffyEvents.CASE_INVENTARIO_PENDIENTE, "&cTenés un inventario de evento pendiente; avisá al staff.");
        put(map, ToffyEvents.CASE_COMANDO_BLOQUEADO, "&cDurante el evento solo podés usar /anotar y /toffy. Para irte: /anotar salir {clave}");
        put(map, ToffyEvents.CASE_TP_BLOQUEADO, "&cPara salir del evento usá /anotar salir {clave}.");
        put(map, ToffyEvents.CASE_SALIDA_LIFESTEAL, "&7Saliste del Lifesteal. No ganas el corazon permanente.");
        put(map, ToffyEvents.CASE_RECUPERADO, "&7Recuperaste tu inventario.");
        put(map, ToffyEvents.CASE_RECUPERADO_ANTES, "&7Recuperaste el inventario que tenías antes del evento.");
        put(map, ToffyEvents.CASE_RECUPERACION_PENDIENTE, "&cTu inventario sigue protegido; se reintentará al poder guardarlo.");
        put(map, ToffyEvents.CASE_PREMIO_PENDIENTE, "&eTu premio se te entrega cuando vuelvas a entrar al servidor.");
        put(map, ToffyEvents.CASE_PREMIO, "&6Premio: &f{premio}");
        put(map, ToffyEvents.CASE_PREMIO_AL_ENTRAR, "&6Recibiste el premio del evento &e{evento}&6, que terminó mientras estabas desconectado: &f{premio}");
        put(map, ToffyEvents.CASE_CORAZON_AL_ENTRAR, "&6Recibiste {corazones} corazón permanente del Lifesteal, que terminó mientras estabas desconectado.");
        put(map, ToffyEvents.CASE_ERROR_ARENA, "&cNo se pudo preparar la arena; revisá la consola.");
        put(map, ToffyEvents.CASE_MOTIVO_MINIMO, "Hacían falta {minimo} jugadores y hubo {cantidad}.");
        put(map, ToffyEvents.CASE_MOTIVO_DETENER, "Lo canceló {staff}.");
        put(map, ToffyEvents.CASE_MOTIVO_TIEMPO_LIDER, "&7Se acabó el tiempo. &f{jugador} &7tenía más tiempo en la colina.");
        put(map, ToffyEvents.CASE_MOTIVO_SIN_GANADOR, "&7Se acabó el tiempo sin ganador.");
        put(map, ToffyEvents.CASE_MOTIVO_NADIE, "&7No quedó nadie en pie.");
        put(map, ToffyEvents.CASE_CAUSA_MURIO, "murió");
        put(map, ToffyEvents.CASE_CAUSA_CAYO, "se cayó");
        put(map, ToffyEvents.CASE_CAUSA_EXPLOTO, "explotó");
        put(map, ToffyEvents.CASE_CAUSA_DESCONECTO, "se desconectó");
        put(map, ToffyEvents.CASE_CAUSA_SALIO, "salió");
        put(map, ToffyEvents.CASE_TNT_EXPLOTA, "");
        put(map, ToffyEvents.CASE_LIFESTEAL_INICIADO, "&c&lLIFESTEAL &7iniciado por &f{staff} &7por &f{minutos} min&7. Concursantes: &f{cantidad}&7.");
        put(map, ToffyEvents.CASE_LIFESTEAL_REGLAS, "&7PvP global con &aKeepInventory&7. Cada kill roba &c{robo} ❤&7. Nadie baja de &e{piso} ❤&7 (matar a alguien en el piso no da nada).");
        put(map, ToffyEvents.CASE_LIFESTEAL_META, "&7Terminá con &c{umbral} ❤ o más &7y ganás &6{permanente} corazón permanente&7.");
        put(map, ToffyEvents.CASE_LIFESTEAL_TIEMPO, "&eLifesteal termina en &f{tiempo}&e.");
        put(map, ToffyEvents.CASE_LIFESTEAL_FIN, "&c&lLIFESTEAL &7terminó. Los corazones del evento volvieron a la normalidad.");
        put(map, ToffyEvents.CASE_LIFESTEAL_FIN_CORAZON, "&6¡Terminaste con {total} ❤ y ganaste {permanente} corazón permanente!");
        put(map, ToffyEvents.CASE_LIFESTEAL_FIN_MAXIMO, "&7Terminaste con {total} ❤, pero ya tenés el máximo de corazones permanentes.");
        put(map, ToffyEvents.CASE_LIFESTEAL_FIN_NADIE, "&7Nadie llegó a {umbral} ❤.");
        put(map, ToffyEvents.CASE_LIFESTEAL_FIN_GANADORES, "&6Corazón permanente para: &f{jugadores}");
        put(map, ToffyEvents.CASE_LIFESTEAL_ROBO_VICTIMA, "&c{atacante} te robó {robado} ❤.");
        put(map, ToffyEvents.CASE_LIFESTEAL_ROBO_ATACANTE, "&aLe robaste {robado} ❤ a {victima} &7({total} ❤).");
        put(map, ToffyEvents.CASE_LIFESTEAL_PISO, "&7{victima} está en el piso de {piso} ❤: no le robaste nada.");
        put(map, ToffyEvents.CASE_LIFESTEAL_MAXIMO, "&7Ya estás en el máximo de {maximo} ❤.");
        put(map, ToffyEvents.CASE_LIFESTEAL_CANCELADO, "&c{evento} fue cancelado por {staff}.");
        put(map, ToffyEvents.CASE_KOTH_AJUSTE, "&e¡A la colina! &7Quedate solo sobre el oro {segundos} s para ganar.");
        put(map, ToffyEvents.CASE_KOTH_TIRADO, "&7Te tiraron de la colina.");
        put(map, ToffyEvents.CASE_KOTH_LIBRE, "&7Colina libre");
        put(map, ToffyEvents.CASE_KOTH_DISPUTADA, "&cColina disputada");
        put(map, ToffyEvents.CASE_KOTH_REY, "&6Rey: &f{rey}");
        putAction(map, ToffyEvents.CASE_KOTH_BARRA, "{colina} &8| &eTus puntos: &f{puntos}/{captura}");
        putAction(map, ToffyEvents.CASE_TNT_TENES, "&c&l¡TENÉS LA TNT! &7Pegale a alguien &8| &f{segundos} s");
        putAction(map, ToffyEvents.CASE_TNT_CORRE, "&aCorré &8| &7explota en &f{segundos} s");
        put(map, ToffyEvents.CASE_TNT_RONDA, "&cNueva ronda: &f{portadores} &c{tiene} la TNT. &7Explota en {segundos} s.");
        put(map, ToffyEvents.CASE_TNT_PASA_VICTIMA, "&c¡{atacante} te pasó la TNT!");
        put(map, ToffyEvents.CASE_TNT_PASA_ATACANTE, "&aLe pasaste la TNT a {victima}.");
        put(map, ToffyEvents.CASE_SPLEEF_GRACIA, "&eLa nieve se puede romper en &f{segundos} s&e.");
        put(map, ToffyEvents.CASE_SPLEEF_ROMPER, "&e&l¡A romper!");
        put(map, ToffyEvents.CASE_KIT_PALO, "&6&lPalo del Rey");
        put(map, ToffyEvents.CASE_KIT_PALA, "&b&lPala de Spleef");
        put(map, ToffyEvents.CASE_KIT_LORE, "&7Objeto de evento: desaparece al salir.");
        put(map, ToffyEvents.CASE_DESCRIPCION_KOTH, "Zona de captura con palos irrompibles Knockback {knockback}. Quedate solo en el oro para sumar.");
        put(map, ToffyEvents.CASE_DESCRIPCION_TNT, "Persecución explosiva: pasá la TNT antes de que explote.{bombardeo}");
        put(map, ToffyEvents.CASE_DESCRIPCION_SPLEEF, "Pista de nieve con pala de madera Efficiency {eficiencia}. Caer por debajo de la última capa te deja afuera.");
    }

    private static void put(Map<String, Entry> map, String key, String text) {
        map.put(key, Entry.chat(text));
    }

    private static void putAction(Map<String, Entry> map, String key, String text) {
        map.put(key, new Entry(ToffyEvents.Text.Kind.ACTIONBAR, text, "", "", 1f, 1f));
    }

    static ToffyMessages builtIn() {
        return new ToffyMessages(Map.of(), DEFAULT_PREFIX, DEFAULT_EVENT_PREFIX);
    }

    static boolean isCase(String key) {
        return DEFAULTS.containsKey(key);
    }

    static String defaultTemplate(String caseKey) {
        Entry entry = DEFAULTS.get(caseKey);
        return entry == null ? "" : entry.text();
    }

    /** Prefix of the Toffy messages ({prefijo}). */
    String prefix() {
        return prefix;
    }

    /** Prefix of the event messages ({prefijo-eventos}, added to chat event texts). */
    String eventPrefix() {
        return eventPrefix;
    }

    /**
     * Reads the file values. Unknown keys and values that are not text or a section only produce warnings; those cases
     * keep their built-in default. Accepts {@code prefijo} and {@code prefijo-eventos} at the top.
     */
    static Loaded fromValues(Map<String, ?> raw) {
        List<String> warnings = new ArrayList<>();
        Map<String, Entry> values = new LinkedHashMap<>();
        String prefix = DEFAULT_PREFIX;
        String eventPrefix = DEFAULT_EVENT_PREFIX;
        if (raw != null) {
            for (Map.Entry<String, ?> item : raw.entrySet()) {
                String key = item.getKey();
                Object value = item.getValue();
                if (PREFIX_KEY.equals(key)) {
                    prefix = text(value, DEFAULT_PREFIX, warnings, key);
                } else if (EVENT_PREFIX_KEY.equals(key)) {
                    eventPrefix = text(value, DEFAULT_EVENT_PREFIX, warnings, key);
                } else if (!isCase(key)) {
                    warnings.add("toffy_mensajes.yml: caso desconocido '" + key + "' (se ignora). Casos validos: "
                        + String.join(", ", CASES));
                } else if (value == null) {
                    values.put(key, new Entry(null, "", "", "", 1f, 1f));
                } else if (value instanceof Map<?, ?> section) {
                    values.put(key, section(key, section, warnings));
                } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    values.put(key, Entry.chat(String.valueOf(value)));
                } else {
                    warnings.add("toffy_mensajes.yml: '" + key + "' debe ser un texto o una seccion; se usa el valor por defecto.");
                }
            }
        }
        return new Loaded(new ToffyMessages(values, prefix, eventPrefix), List.copyOf(warnings));
    }

    private static String text(Object value, String fallback, List<String> warnings, String key) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        warnings.add("toffy_mensajes.yml: '" + key + "' debe ser un texto; se usa el valor por defecto.");
        return fallback;
    }

    private static Entry section(String key, Map<?, ?> section, List<String> warnings) {
        Object text = section.get("texto");
        String kindName = section.get("tipo") == null ? "chat" : String.valueOf(section.get("tipo"));
        ToffyEvents.Text.Kind kind = kind(kindName);
        if (kind == null) {
            warnings.add("toffy_mensajes.yml: '" + key + ".tipo' debe ser chat, titulo, actionbar o ninguno; se usa chat.");
            kind = ToffyEvents.Text.Kind.CHAT;
        }
        float volume = number(section.get("volumen"), 1f, key + ".volumen", 0f, 10f, warnings);
        float pitch = number(section.get("tono"), 1f, key + ".tono", 0.5f, 2f, warnings);
        String sound = section.get("sonido") == null ? "" : String.valueOf(section.get("sonido"));
        String soundKey = ToffyEvents.Text.soundKey(sound);
        if (soundKey == null) {
            warnings.add("toffy_mensajes.yml: '" + key + ".sonido' no es una clave valida (por ejemplo minecraft:block.note_block.hat); queda mudo.");
            soundKey = "";
        }
        return new Entry(kind, text == null ? "" : String.valueOf(text),
            section.get("subtitulo") == null ? "" : String.valueOf(section.get("subtitulo")),
            soundKey, volume, pitch);
    }

    /** {@code chat}, {@code titulo}, {@code actionbar} or {@code ninguno}; null when unknown. */
    static ToffyEvents.Text.Kind kind(String name) {
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "chat" -> ToffyEvents.Text.Kind.CHAT;
            case "titulo" -> ToffyEvents.Text.Kind.TITLE;
            case "actionbar" -> ToffyEvents.Text.Kind.ACTIONBAR;
            case "ninguno" -> ToffyEvents.Text.Kind.NONE;
            default -> null;
        };
    }

    /** A number clamped to the range (volume 0-10, pitch 0.5-2); a value that is not a number uses the fallback. */
    private static float number(Object value, float fallback, String path, float min, float max, List<String> warnings) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            warnings.add("toffy_mensajes.yml: '" + path + "' debe ser un numero; se usa " + fallback + ".");
            return fallback;
        }
        float clamped = Math.max(min, Math.min(max, number.floatValue()));
        if (clamped != number.floatValue()) {
            warnings.add("toffy_mensajes.yml: '" + path + "' debe estar entre " + min + " y " + max + "; se ajusta a " + clamped + ".");
        }
        return clamped;
    }

    /**
     * The template for a case before substituting placeholders: override, file or default.
     */
    String template(String caseKey, String override) {
        if (override != null) {
            return override;
        }
        Entry fromFile = fileValues.get(caseKey);
        return fromFile != null ? fromFile.text() : defaultTemplate(caseKey);
    }

    private static ToffyEvents.Text.Kind defaultKind(String caseKey) {
        Entry entry = DEFAULTS.get(caseKey);
        return entry == null ? ToffyEvents.Text.Kind.CHAT : entry.kind();
    }

    /**
     * Final text with colors and placeholders applied, or {@code null} when the template is empty.
     */
    String render(String caseKey, String override, Map<String, String> placeholders) {
        return format(template(caseKey, override), placeholders);
    }

    /**
     * What to show for a case: the text, title and subtitle with placeholders applied, the kind and the sound. Chat
     * event texts start with the event prefix unless they place {prefijo} themselves. {@code null} when the text is
     * empty, which means "send nothing".
     *
     * @param event true for the staff events (their chat texts start with {@code prefijo-eventos})
     */
    ToffyEvents.Text display(String caseKey, String override, Map<String, String> placeholders, boolean event) {
        Entry entry = fileValues.get(caseKey);
        String raw = template(caseKey, override);
        if (raw == null || raw.isBlank()) {
            // No text: a sound may still play (sound only), but an empty chat line is never sent.
            if (entry == null || entry.sound().isBlank()) {
                return null;
            }
            return new ToffyEvents.Text(ToffyEvents.Text.Kind.NONE, "", "", entry.sound(), entry.volume(), entry.pitch());
        }
        // A case the file defines keeps the file's kind; otherwise the built-in kind (action bar for live status lines).
        ToffyEvents.Text.Kind kind = entry != null ? entry.kind() : defaultKind(caseKey);
        Map<String, String> values = new LinkedHashMap<>();
        if (placeholders != null) {
            values.putAll(placeholders);
        }
        String ownPrefix = event ? eventPrefix : prefix;
        values.put(PLACEHOLDER_PREFIX, colors(ownPrefix));
        String text = format(raw, values);
        if (event && kind == ToffyEvents.Text.Kind.CHAT && !FRAGMENTS.contains(caseKey)
            && !raw.contains("{" + PLACEHOLDER_PREFIX + "}")) {
            text = colors(eventPrefix) + text;
        }
        String subtitle = entry == null || entry.subtitle().isBlank() ? "" : format(entry.subtitle(), values);
        String sound = entry == null ? "" : entry.sound();
        float volume = entry == null ? 1f : entry.volume();
        float pitch = entry == null ? 1f : entry.pitch();
        return new ToffyEvents.Text(kind, text, subtitle, sound, volume, pitch);
    }

    /**
     * Applies {@code &} color codes and replaces {@code {name}} placeholders. Returns {@code null} for an empty
     * template. Colors are translated first so a value containing {@code &} is shown as typed.
     */
    static String format(String template, Map<String, String> placeholders) {
        if (template == null || template.isBlank()) {
            return null;
        }
        String text = colors(template);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return text;
    }

    static String colors(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(chars[i + 1]) >= 0) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }
}
