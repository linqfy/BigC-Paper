package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.LogicJson;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A read-only description of every menu, written to plugins/BigCasares/tideframe/engine/MENUS.md each time the screens load, so people
 * and agents (Claude, Codex) can read all the menus in one file. Menus are changed by editing their JSON file in
 * plugins/BigCasares/tideframe/engine/screens, never from the game. Pure text: safe on any thread.
 */
public final class MenuCatalog {
    private MenuCatalog() { }
    /** Longest element or screen logic shown inline; the screen's own file has the rest. */
    private static final int MAX_LOGIC = 400;

    /**
     * @param screens every loaded menu by id (before the reserved close button is added)
     * @param files   ids that have a JSON file in screens/ (they are the source of truth); the others are built into the plugin
     */
    public static String render(Map<String, Screen> screens, Set<String> files, String version) {
        var sorted = new TreeMap<>(screens);
        var out = new StringBuilder(8192);
        out.append("# Menús de Tideframe ").append(version).append("\n\n")
           .append("Archivo **generado** por Tideframe cada vez que carga los menús. No lo edites: se sobrescribe.\n\n")
           .append("- Los menús **no se crean ni se modifican desde el juego**. Para cambiar uno, editá su archivo `plugins/BigCasares/tideframe/engine/screens/ID.json` (o publicalo desde el editor web): el servidor lo recarga solo.\n")
           .append("- Los menús \"incorporados\" no tienen archivo: son demos del plugin. Para reemplazar uno, creá `screens/ID.json` con el mismo id.\n")
           .append("- Coordenadas: de -1 a 1 sobre el lienzo (x hacia la derecha, y hacia arriba, 0 = centro); ancho y alto de 0 a 2.\n\n")
           .append("| Menú | Título | Origen | Elementos | Calidad |\n| --- | --- | --- | --- | --- |\n");
        for (var screen : sorted.values())
            out.append("| `").append(screen.id()).append("` | ").append(cell(screen.title())).append(" | ")
               .append(files.contains(screen.id()) ? "archivo" : "incorporado").append(" | ").append(screen.elements().size())
               .append(" | ").append(screen.quality().preset()).append(" |\n");
        for (var screen : sorted.values()) {
            out.append("\n## `").append(screen.id()).append("` — ").append(cell(screen.title())).append("\n\n");
            out.append("- Origen: ").append(files.contains(screen.id()) ? "archivo `screens/" + screen.id() + ".json`" : "incorporado en el plugin (sin archivo)").append('\n');
            var p = screen.presentation();
            out.append("- Presentación: modo `").append(p.mode()).append("`, escala ").append(p.scale()).append(", cursor `").append(p.cursor()).append('`');
            if (p.cursorSize() != null) out.append(", tamaño del cursor ").append(num(p.cursorSize()));
            if (p.cursorSpeed() != 1) out.append(", velocidad ").append(num(p.cursorSpeed()));
            out.append('\n');
            if (!screen.logic().isEmpty()) out.append("- Lógica de la pantalla: `").append(clip(LogicJson.screen(screen.logic()))).append("`\n");
            if (screen.elements().isEmpty()) { out.append("\n(sin elementos)\n"); continue; }
            out.append("\n| Elemento | Tipo | Forma | x | y | ancho | alto | capa | Texto | Acción | Notas |\n| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
            for (var e : screen.elements()) {
                out.append("| `").append(e.id()).append("` | ").append(e.kind()).append(" | ").append(e.shape()).append(" | ")
                   .append(num(e.x())).append(" | ").append(num(e.y())).append(" | ").append(num(e.width())).append(" | ").append(num(e.height()))
                   .append(" | ").append(e.layer()).append(" | ").append(cell(e.text())).append(" | ").append(e.action().isEmpty() ? "" : "`" + cell(e.action()) + "`")
                   .append(" | ").append(notes(e)).append(" |\n");
            }
        }
        return out.toString();
    }

    private static String notes(Screen.Element e) {
        var notes = new java.util.ArrayList<String>();
        if (e.image() != null) notes.add("imagen " + e.image().width() + "x" + e.image().height());
        if (e.hoverImage() != null) notes.add("imagen en hover");
        if (e.item() != null) notes.add("ícono de ítem " + e.item());
        if (e.background()) notes.add("fondo");
        if (e.border()) notes.add("borde");
        var s = e.style();
        if (s.blur() != 0) notes.add("blur " + s.blur());
        if (s.backdropBlur() != 0) notes.add("vidrio " + s.backdropBlur());
        if (s.shadowBlur() != 0) notes.add("sombra difuminada " + s.shadowBlur());
        if (s.slice() != 0) notes.add("9-slice " + s.slice() + (s.sliceScale() != 1 ? " (" + s.sliceScale() + " px de imagen por px)" : ""));
        if (e.actionArea() != null) notes.add("área de clic propia");
        if (!e.logic().isEmpty()) notes.add("lógica `" + cell(clip(LogicJson.element(e.logic()))) + "`");
        return String.join("; ", notes);
    }
    private static String clip(String json) { return json.length() <= MAX_LOGIC ? json : json.substring(0, MAX_LOGIC) + "…"; }
    /** One table cell: no pipes or line breaks. */
    private static String cell(String text) { return text.replace("|", "¦").replace("\r", "").replace("\n", " ⏎ "); }
    private static String num(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        text = text.indexOf('.') < 0 ? text : text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text.equals("-0") ? "0" : text;
    }
}
