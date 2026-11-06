package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.CloseControl;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.LiveTextTest;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scratch preview (not part of Tideframe): BigCasares' screens drawn by the 26.2 title simulator at 1x with sample data,
 * as the client would draw the title glyphs. Writes PNGs next to the BigCasares resources' parent (preview folder).
 */
class BigcPreviewTest {
    static final Path SCREENS = Path.of("src", "main", "resources", "tideframe", "screens");
    static final Path OUT = Path.of("build", "tideframe-preview");

    private static void draw(LiveTextTest.Sim sim, Component component, Key font, int tint) {
        Key own = component.font() != null ? component.font() : font;
        int colour = component.color() != null ? component.color().value() : tint;
        // The simulator replaces texels with the tint (right for live text's white grid). The client multiplies, so a
        // white component leaves a coloured picture (runtime layers) as it is: draw those untinted.
        int drawn = (colour & 0xffffff) == 0xffffff ? 0 : colour;
        if (component instanceof TextComponent text && !text.content().isEmpty()) sim.draw(own.asString(), text.content(), drawn);
        for (var child : component.children()) draw(sim, child, own, colour);
    }

    private static String fill(String template, Map<String, String> values) {
        String out = template;
        for (var entry : values.entrySet()) out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        return out;
    }

    private static void render(String id, Map<String, String> variables, List<Map<String, String>> rows, String name) throws Exception {
        // The simulator draws the 1x atlas: the canvas quality only changes the baked picture's resolution, not the layout.
        Screen screen = CloseControl.prepare(ScreenCodec.decode(Files.readString(SCREENS.resolve(id + ".json"))))
                .withQuality(dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality.custom(1024, 576));
        var assets = HudAssets.create(List.of(screen));
        var sim = new LiveTextTest.Sim(assets.entries());
        sim.draw("tideframe:hud", assets.frame(screen, null, 8), 0);
        Map<String, String> values = new LinkedHashMap<>();
        for (var element : screen.elements()) {
            var spec = element.logic().list();
            if (spec != null) {
                for (int i = 0; i < Math.min(rows.size(), spec.rows()); i++) {
                    Map<String, String> scope = new LinkedHashMap<>();
                    rows.get(i).forEach((key, value) -> scope.put("item." + key, value));
                    values.put(ListRows.rowId(element.id(), i), fill(element.text(), scope));
                }
            } else if (LiveText.isLive(element)) values.put(element.id(), fill(element.text(), variables));
        }
        var view = dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState.initial(screen, 0);
        for (var element : screen.elements()) if (element.logic().list() != null) view = view.rows(element.id(), Math.min(rows.size(), element.logic().list().rows()));
        // visible-if, as ScreenRuntime gates it, with the variables BigCasares would have sent.
        var gated = new java.util.HashSet<String>();
        for (var element : screen.elements()) {
            String condition = element.logic().visibleIf();
            if (condition != null && !dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Conditions.test(fill(condition, variables))) gated.add(element.id());
        }
        view = view.gate(gated);
        // Runtime layers first (list rows' buttons, drawn only for rows with data), then the live text on top.
        draw(sim, DynamicLayer.build(assets, screen, view, null, 0, 4), null, 0);
        draw(sim, LiveLayer.build(assets, screen, values, 4, view), null, 0);
        Files.createDirectories(OUT);
        var flat = new BufferedImage(1024, 576, BufferedImage.TYPE_INT_RGB);
        var g = flat.createGraphics();
        g.setColor(new java.awt.Color(0x5a7d4a)); g.fillRect(0, 0, 1024, 576);
        g.drawImage(sim.canvas, 0, 0, null); g.dispose();
        ImageIO.write(flat, "png", OUT.resolve(name + ".png").toFile());
    }

    @Test void previews() throws Exception {
        render("bigc_misiones", Map.of("bigc_titulo", "Diarias", "bigc_reinicio", "se renuevan en 5h 12m",
                "bigc_pendientes", "1 para cobrar", "bigc_vacio", ""), List.of(
                Map.of("titulo", "Picar 64 de piedra", "progreso", "23/64", "premio", "$250"),
                Map.of("titulo", "Matar 10 zombis", "progreso", "¡Lista!", "premio", "$400"),
                Map.of("titulo", "Pescar 5 peces", "progreso", "Cobrada", "premio", "$150")), "bigc_misiones");
        render("bigc_misiones", Map.of("bigc_titulo", "Semanales", "bigc_reinicio", "se renuevan en 74h 2m",
                "bigc_pendientes", "Nada para cobrar", "bigc_vacio", "No hay misiones activas"), List.of(), "bigc_misiones_vacia");
        render("bigc_tienda", Map.of("bigc_tienda_titulo", "Categorías", "bigc_tienda_saldo", "$1,250",
                "bigc_tienda_ayuda", "Tocá una para ver sus productos", "bigc_tienda_pagina", "", "bigc_tienda_aviso", "",
                "bigc_tienda_paginas", "1", "bigc_tienda_productos", "0"), List.of(
                Map.of("nombre", "Bloques", "precio", "12 productos"),
                Map.of("nombre", "Herramientas", "precio", "8 productos"),
                Map.of("nombre", "Comida", "precio", "6 productos"),
                Map.of("nombre", "Especiales", "precio", "4 productos")), "bigc_tienda_categorias");
        render("bigc_tienda", Map.of("bigc_tienda_titulo", "Herramientas", "bigc_tienda_saldo", "$1,250",
                "bigc_tienda_ayuda", "Izquierdo: comprar · Derecho: vender", "bigc_tienda_pagina", "1 de 2",
                "bigc_tienda_aviso", "Compraste Gancho (nivel 1) por $300.", "bigc_tienda_paginas", "2", "bigc_tienda_productos", "1"), List.of(
                Map.of("nombre", "Pico de hierro", "precio", "$120 / $30"),
                Map.of("nombre", "Gancho (nivel 1)", "precio", "$300 / —"),
                Map.of("nombre", "Planeador (nivel 1)", "precio", "$450 / —"),
                Map.of("nombre", "Antorchas x16", "precio", "$16 / $4"),
                Map.of("nombre", "Manzana de cobre", "precio", "$1,500 / $50"),
                Map.of("nombre", "Cubeta de agua", "precio", "$40 / $10")), "bigc_tienda_productos");
    }
}
