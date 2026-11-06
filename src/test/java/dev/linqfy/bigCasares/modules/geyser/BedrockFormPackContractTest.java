package dev.linqfy.bigCasares.modules.geyser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Bedrock pack part that draws BigCasares' forms (tools/tideframe-screens/build-bedrock-ui.mjs) matches the Java side. */
class BedrockFormPackContractTest {
    private static final Path BEDROCK = Path.of("resourcepack/bedrock");

    @Test
    void serverFormOnlyAddsAFactoryAndChangesNoStockElement() throws IOException {
        JsonObject serverForm = json(BEDROCK.resolve("ui/server_form.json"));
        assertEquals(Set.of("namespace", "main_screen_content"), serverForm.keySet());
        assertEquals("server_form", serverForm.get("namespace").getAsString());
        JsonArray modifications = serverForm.getAsJsonObject("main_screen_content").getAsJsonArray("modifications");
        assertEquals(1, modifications.size());
        JsonObject modification = modifications.get(0).getAsJsonObject();
        assertEquals("controls", modification.get("array_name").getAsString());
        assertEquals("insert_back", modification.get("operation").getAsString());
        JsonObject factory = modification.getAsJsonArray("value").get(0).getAsJsonObject().getAsJsonObject("bigc_form_factory");
        assertEquals("factory", factory.get("type").getAsString());
        assertEquals(Set.of("long_form"), factory.getAsJsonObject("control_ids").keySet());
        assertEquals("@bigc_forms.long_form", factory.getAsJsonObject("control_ids").get("long_form").getAsString());

        JsonArray defs = json(BEDROCK.resolve("ui/_ui_defs.json")).getAsJsonArray("ui_defs");
        assertTrue(defs.contains(JsonParser.parseString("\"ui/bigc_forms.json\"")));
    }

    @Test
    void panelIsHiddenUnlessTheTitleCarriesTheJavaTag() throws IOException {
        JsonObject forms = json(BEDROCK.resolve("ui/bigc_forms.json"));
        assertEquals("bigc_forms", forms.get("namespace").getAsString());
        JsonObject longForm = forms.getAsJsonObject("long_form");
        assertFalse(longForm.get("visible").getAsBoolean());
        String visibility = viewBinding(longForm, "#visible");
        assertEquals("(not ((#title_text - '" + BedrockFormStyle.TAG + "') = #title_text))", visibility);
        JsonObject title = control(longForm, "title");
        assertEquals("(#title_text - '" + BedrockFormStyle.TAG + "')", viewBinding(title, "#text"));
    }

    @Test
    void lookFollowsTheTideframeShopScreen() throws IOException {
        JsonObject shop = json(Path.of("src/main/resources/tideframe/screens/bigc_tienda.json"));
        JsonObject title = control(json(BEDROCK.resolve("ui/bigc_forms.json")).getAsJsonObject("long_form"), "title");
        assertEquals(rgb(style(shop, "titulo").get("textColor").getAsString()), title.getAsJsonArray("color"));

        BufferedImage row = ImageIO.read(BEDROCK.resolve("textures/ui/bigc/row.png").toFile());
        assertEquals(argb(style(shop, "fila").get("fillColor").getAsString()), row.getRGB(8, 8));
        assertEquals(argb(style(shop, "fila").get("borderColor").getAsString()), row.getRGB(0, 8));
        BufferedImage panel = ImageIO.read(BEDROCK.resolve("textures/ui/bigc/panel.png").toFile());
        assertEquals(argb(style(shop, "panel").get("fillColor").getAsString()), panel.getRGB(8, 8));
        // Opaque: the stock form underneath must not show through.
        assertEquals(0xff, panel.getRGB(8, 8) >>> 24);
    }

    @Test
    void everyReferencedTextureIsAReadablePng() throws IOException {
        String forms = Files.readString(BEDROCK.resolve("ui/bigc_forms.json"), StandardCharsets.UTF_8);
        Matcher textures = Pattern.compile("textures/ui/bigc/[a-z_]+").matcher(forms);
        List<String> found = new ArrayList<>();
        while (textures.find()) {
            Path file = BEDROCK.resolve(textures.group() + ".png");
            assertNotNull(ImageIO.read(file.toFile()), file.toString());
            found.add(textures.group());
        }
        assertTrue(found.containsAll(List.of("textures/ui/bigc/panel", "textures/ui/bigc/row",
            "textures/ui/bigc/row_hover", "textures/ui/bigc/row_pressed")), found.toString());
    }

    @Test
    void manifestVersionIsTheSameForHeaderAndModule() throws IOException {
        JsonObject manifest = json(BEDROCK.resolve("manifest.json"));
        assertEquals(manifest.getAsJsonObject("header").get("version"),
            manifest.getAsJsonArray("modules").get(0).getAsJsonObject().get("version"));
    }

    private static JsonObject style(JsonObject screen, String id) {
        for (JsonElement element : screen.getAsJsonArray("elements")) {
            if (id.equals(element.getAsJsonObject().get("id").getAsString())) {
                return element.getAsJsonObject().getAsJsonObject("style");
            }
        }
        throw new AssertionError("no element " + id);
    }

    private static JsonArray rgb(String hex) {
        JsonArray colour = new JsonArray();
        for (int at = 1; at < 7; at += 2) {
            colour.add(Math.round(Integer.parseInt(hex.substring(at, at + 2), 16) / 255.0 * 1000) / 1000.0);
        }
        return colour;
    }

    private static int argb(String hex) {
        return 0xff000000 | Integer.parseInt(hex.substring(1), 16);
    }

    private static String viewBinding(JsonObject control, String target) {
        for (JsonElement binding : control.getAsJsonArray("bindings")) {
            JsonObject object = binding.getAsJsonObject();
            if (object.has("target_property_name") && target.equals(object.get("target_property_name").getAsString())) {
                return object.get("source_property_name").getAsString();
            }
        }
        throw new AssertionError("no binding to " + target);
    }

    private static JsonObject control(JsonObject parent, String name) {
        for (JsonElement child : parent.getAsJsonArray("controls")) {
            for (String key : child.getAsJsonObject().keySet()) {
                if (key.equals(name) || key.startsWith(name + "@")) {
                    return child.getAsJsonObject().getAsJsonObject(key);
                }
            }
        }
        throw new AssertionError("no control " + name);
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
