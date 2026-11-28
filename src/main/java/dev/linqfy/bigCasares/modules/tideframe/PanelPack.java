package dev.linqfy.bigCasares.modules.tideframe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Generates the bitmap font of the two panels inside the existing Java pack assembly. */
public final class PanelPack {
    private PanelPack() {}
    private static final int[] ALPHAS = {56, 128, 242, 255};
    static final int TITLE_START = 0xe400;
    static final int TITLE_END = 0xe401;
    static final int POINTER_START = 0xe402;
    static final int POINTER_END = 0xe403;
    /**
     * Marks around one run. Inside the title's 40/256 GUI pixel shift they add (8 + 8 * (row % 4) + power) / 256 more,
     * which tells the shader the run's row and width.
     */
    static final int RUN_START = 0xe404;
    /** Bumped whenever the generated panel assets change, so an existing pack is rebuilt. */
    private static final int ASSET_VERSION = 3;
    private static final int[] ICON_TOPS = {53, 54, 73, 83, 92, 116};
    /**
     * An item icon, drawn from the client's own textures (the pack only names them). A flat icon is one texture,
     * as the inventory shows items. A cube lists its top, left and right faces: the text shader moves the corners of
     * three glyphs onto the inventory's block projection, so blocks show their volume and faces. As in the vanilla
     * inventory, an oriented block shows its front on the right face.
     */
    record Icon(String id, int columns, List<String> textures, boolean cube) {
        String texture(int face) { return textures.get(cube ? face : 0); }
        /** The first face drawing the same texture, so a repeated texture needs no extra font provider. */
        int source(int face) {
            for (int earlier = 0; earlier < face; earlier++) if (texture(earlier).equals(texture(face))) return earlier;
            return face;
        }
        int faces() { return cube ? 3 : 1; }
    }
    static final List<Icon> ICONS = List.of(
        cube("stone"), cube("cobblestone"), cube("sponge"), cube("red_wool"),
        flatBlock("ladder",15), cube("obsidian"), cube("barrel", "block/barrel_top", "block/barrel_side", "block/barrel_side"),
        cube("beacon"), cube("andesite"), cube("diorite"), cube("granite"), cube("sand"), cube("tuff"), cube("bricks"),
        cube("carved_pumpkin", "block/pumpkin_top", "block/pumpkin_side", "block/carved_pumpkin"),
        item("kelp",15), item("netherite_hoe",14), item("wooden_sword",16), item("apple",14), item("barrier",15), item("bone",16), item("bow",16),
        item("bread",15), item("bundle",14), new Icon("clock",15,List.of("item/clock_00"),false),
        new Icon("compass",15,List.of("item/compass_16"),false), item("copper_ingot",16), item("diamond",14),
        item("diamond_sword",16), item("emerald",13), item("filled_map",16), item("fishing_rod",15),
        item("goat_horn",15), item("golden_apple",14), item("golden_carrot",16), item("heart_of_the_sea",15),
        item("honeycomb",14), item("iron_chestplate",15), item("iron_helmet",14), item("iron_hoe",14), item("iron_sword",16),
        item("leather_boots",13), item("mace",16), item("name_tag",15), item("nether_star",15),
        item("paper",16), item("potato",14), item("saddle",16), item("shears",14), item("slime_ball",14),
        item("spider_eye",12), item("stick",15), item("wooden_hoe",14), item("writable_book",16)
    );
    private static Icon item(String id, int width) { return new Icon(id,width,List.of("item/"+id),false); }
    /** Blocks the inventory draws as a flat sprite (ladders, rails...). */
    private static Icon flatBlock(String id, int width) { return new Icon(id,width,List.of("block/"+id),false); }
    private static Icon cube(String id) { return cube(id, "block/"+id, "block/"+id, "block/"+id); }
    private static Icon cube(String id, String top, String left, String right) {
        return new Icon(id,16,List.of(top,left,right),true);
    }
    static int iconIndex(String id) {
        for (int i=0; i<ICONS.size(); i++) if (ICONS.get(i).id().equals(id)) return i;
        return -1;
    }
    static int space(int quarters) { return 0xe200 + quarters; }
    static int background(boolean shop,int selected,int count) { return 0xe500 + (shop ? count*9+selected+1 : 80+selected+1); }
    static int run(int y,int power,int opacity) { return 0x10000 + (opacity*144+y)*8+power; }
    static int runStart(int y, int power) { return RUN_START + 2 * ((y & 3) * 8 + power); }
    static int runEnd(int y, int power) { return runStart(y, power) + 1; }
    static int opacity(int alpha) { return alpha < 92 ? 0 : alpha < 185 ? 1 : alpha < 250 ? 2 : 3; }
    static int cursor(int y) { return 0xf500 + y; }
    static int icon(int index,int y) { return icon(index, 0, y); }
    static int icon(int index,int face,int y) { return 0xf0000 + (index*3 + ICONS.get(index).source(face))*144 + y; }
    static int advance(int inkWidth) { return (Math.round(inkWidth/4f)+1)*4; }
    static String glyph(int code) { return Character.toString(code); }

    public static void assemble(Path javaRoot) throws IOException {
        Path assets=javaRoot.resolve("assets/bigcasares");
        if (!Files.isRegularFile(assets.resolve("textures/font/fuente.png"))) {
            throw new IOException("Missing native menu font: assets/bigcasares/textures/font/fuente.png");
        }
        installFitShader(javaRoot, shaderTemplate());
        Path textures=assets.resolve("textures/font/pantalla");
        Files.createDirectories(textures);
        JsonArray providers=new JsonArray();
        JsonObject spaces=new JsonObject();
        spaces.addProperty("type","space");
        JsonObject advances=new JsonObject();
        for (int q=-500;q<=500;q++) advances.addProperty(glyph(space(q)),q/4.0);
        // The title and subtitle draw at 4x and 2x. Both marks shift the vertices by 5/32 GUI pixels.
        advances.addProperty(glyph(TITLE_START), 5 / 128.0);
        advances.addProperty(glyph(TITLE_END), -5 / 128.0);
        advances.addProperty(glyph(POINTER_START), 5 / 64.0);
        advances.addProperty(glyph(POINTER_END), -5 / 64.0);
        for (int row = 0; row < 4; row++) {
            for (int power = 0; power < 8; power++) {
                // Drawn at 4x: 1/1024 of a font unit is 1/256 of a GUI pixel.
                advances.addProperty(glyph(runStart(row, power)), (8 + 8 * row + power) / 1024.0);
                advances.addProperty(glyph(runEnd(row, power)), -(8 + 8 * row + power) / 1024.0);
            }
        }
        spaces.add("advances",advances);
        providers.add(spaces);
        for (int count=0;count<=7;count++) {
            for(int selected=-1;selected<count;selected++) addBackground(textures,providers,new Panel(true,selected,count));
        }
        for(int selected=-1;selected<2;selected++) addBackground(textures,providers,new Panel(false,selected,2));
        // Run cells are solid: the shader cuts each quad to its run's row and width, so runs meet edge to edge by
        // geometry, never at a texel border the sampler may round either way (one-pixel seams at some GUI scales).
        for (int opacity=0;opacity<ALPHAS.length;opacity++) {
            BufferedImage strips=new BufferedImage(8*128,4*64,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<4*64;y++) {
                for(int x=0;x<8*128;x++) strips.setRGB(x,y,ALPHAS[opacity]<<24|0xffffff);
            }
            String name="trazos_"+opacity;
            save(textures,name,strips);
            for(int top=0;top<144;top+=4) {
                JsonArray rows=new JsonArray();
                for(int row=0;row<4;row++) {
                    StringBuilder chars=new StringBuilder();
                    for(int power=0;power<8;power++) chars.appendCodePoint(run(top+row,power,opacity));
                    rows.add(chars.toString());
                }
                bitmap(providers,asset(name),16,15-top/4,rows);
            }
        }
        for (int index=0;index<ICONS.size();index++) {
            Icon icon = ICONS.get(index);
            for (int face=0; face<icon.faces(); face++) {
                if (icon.source(face) != face) continue;
                for (int y : ICON_TOPS) {
                    bitmap(providers, "minecraft:" + icon.texture(face) + ".png", 4, 15 - y / 4, one(icon(index, face, y)));
                }
            }
        }
        BufferedImage arrow=new BufferedImage(5,2*100,BufferedImage.TYPE_INT_ARGB);
        for(int parity=0;parity<2;parity++) {
            int top=100*parity+parity;
            for(int y=0;y<6;y++) for(int x=0;x<=Math.min(y,3);x++)
                arrow.setRGB(x,top+y,x==0 || x==y || y==5 ? 0xff202020 : 0xffffffff);
        }
        save(textures,"cursor",arrow);
        for(int y=0;y<144;y+=2) {
            JsonArray rows=one(cursor(y)); rows.add(glyph(cursor(y+1)));
            bitmap(providers,asset("cursor"),50,48-y/2,rows);
        }
        JsonObject font=new JsonObject();
        font.add("providers",providers);
        Files.createDirectories(assets.resolve("font"));
        Files.writeString(assets.resolve("font/pantalla.json"),font.toString(),StandardCharsets.UTF_8);
    }
    /** Everything the panel assets depend on besides the editable pack sources. */
    public static String fingerprint() throws IOException {
        return ASSET_VERSION + "\n" + shaderTemplate() + '\n' + PanelColours.shaderCases() + '\n' + ICONS;
    }

    /** The shader template shipped in the plugin jar, matching this build's markers. */
    static String shaderTemplate() throws IOException {
        try (var input = PanelPack.class.getResourceAsStream("/pantalla/text.vsh")) {
            if (input == null) throw new IOException("Missing panel shader template: pantalla/text.vsh");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void installFitShader(Path javaRoot, String template) throws IOException {
        String overlay = "bigc_pantallas_26_2";
        Path shader = javaRoot.resolve(overlay + "/assets/minecraft/shaders/core/text.vsh");
        Files.createDirectories(shader.getParent());
        String source = template.replace("/* PANEL_COLOURS */", PanelColours.shaderCases());
        Files.writeString(shader, source, StandardCharsets.UTF_8);
        Path metadata = javaRoot.resolve("pack.mcmeta");
        JsonObject pack = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        if (!pack.has("overlays")) pack.add("overlays", new JsonObject());
        var overlays = pack.getAsJsonObject("overlays");
        if (!overlays.has("entries")) overlays.add("entries", new JsonArray());
        JsonObject entry = new JsonObject();
        entry.addProperty("directory", overlay);
        entry.addProperty("min_format", 88);
        entry.addProperty("max_format", 88);
        overlays.getAsJsonArray("entries").add(entry);
        Files.writeString(metadata, pack.toString(), StandardCharsets.UTF_8);
    }
    private static JsonArray one(int code) { JsonArray a=new JsonArray(); a.add(new JsonPrimitive(glyph(code))); return a; }
    private static String asset(String name) { return "bigcasares:font/pantalla/"+name+".png"; }
    private static void addBackground(Path textures, JsonArray providers,Panel panel) throws IOException {
        int code=background(panel.shop,panel.selected,panel.tabs);
        PixelImage image=panel.background();
        BufferedImage png=new BufferedImage(256,144,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<144;y++) for(int x=0;x<256;x++) png.setRGB(x,y,image.get(x,y));
        String name="fondo_"+Integer.toHexString(code);
        save(textures,name,png);
        bitmap(providers,asset(name),36,15,one(code));
    }
    private static void save(Path textures,String name,BufferedImage png) throws IOException {
        ImageIO.write(png,"png",textures.resolve(name+".png").toFile());
    }
    private static void bitmap(JsonArray providers,String file,int height,int ascent,JsonArray chars) {
        JsonObject p=new JsonObject();
        p.addProperty("type","bitmap"); p.addProperty("file",file);
        p.addProperty("height",height); p.addProperty("ascent",ascent);
        p.add("chars",chars); providers.add(p);
    }
}
