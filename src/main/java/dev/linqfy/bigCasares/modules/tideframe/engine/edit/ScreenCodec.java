package dev.linqfy.bigCasares.modules.tideframe.engine.edit;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ElementLogic;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.LogicJson;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic;
import java.math.BigDecimal;
import java.util.*;

/**
 * Own bounded JSON reader. No scripts, polymorphic objects or configuration tags. Schema 9 adds an optional
 * {@code logic} object to the screen and to each element (actions, variables, keybinds...; see ActionParser).
 */
public final class ScreenCodec {
    public static final int MAX_JSON_BYTES=134_217_728;
    private ScreenCodec() {}
    public static Screen decode(String json) {
        if (json==null || exceedsUtf8Budget(json)) throw bad("JSON size exceeds 128 MiB");
        var p=new Parser(json);Object value=p.value(0);p.space();if(p.at!=json.length())throw bad("Trailing JSON data");
        if(!(value instanceof Map<?,?> candidate))throw bad("Expected JSON object");
        int version=integer(candidate.get("version"),"version");
        if(version<1 || version>LATEST)throw bad("Unsupported schema version");
        Map<String,Object> root=version>=9?object(value,Set.of("version","id","title","quality","presentation","elements"),Set.of("logic"))
                :object(value,version>=6?Set.of("version","id","title","quality","presentation","elements"):version>=4?Set.of("version","id","title","quality","elements"):Set.of("version","id","title","elements"));
        RenderQuality quality=RenderQuality.legacy();
        if(version>=4){
            var q=object(root.get("quality"),Set.of("preset","width","height"));
            String preset=string(q,"preset");if(preset.equals("legacy"))throw bad("Legacy quality is internal only");
            quality=new RenderQuality(preset,integer(q.get("width"),"quality width"),integer(q.get("height"),"quality height"));
        }
        Screen.Presentation presentation=Screen.Presentation.defaults();
        if(version>=6){
            var pr=version>=9?object(root.get("presentation"),Set.of("mode","scale","cursor"),Set.of("cursorSize","cursorSpeed","stack")):object(root.get("presentation"),Set.of("mode","scale","cursor"));
            if(pr.get("stack")!=null && !(pr.get("stack") instanceof Boolean))throw bad("presentation stack must be true or false");
            presentation=new Screen.Presentation(string(pr,"mode"),integer(pr.get("scale"),"presentation scale"),string(pr,"cursor"),
                    pr.get("cursorSize")==null?null:number(pr,"cursorSize"),pr.get("cursorSpeed")==null?1:number(pr,"cursorSpeed"),Boolean.TRUE.equals(pr.get("stack")));
        }
        if (!(root.get("elements") instanceof List<?> list) || list.size()>(version>=4?128:24)) throw bad("Invalid elements");
        var elements=new ArrayList<Screen.Element>();
        for(Object item:list) {
            var elementKeys=version==1?Set.of("id","text","x","y","width","height","kind","action"):version==2?
                    Set.of("id","text","x","y","width","height","kind","action","shape","border","messageCondition"):
                    version==3?Set.of("id","text","x","y","width","height","kind","action","shape","border","messageCondition","background","image"):
                    Set.of("id","text","x","y","width","height","kind","action","shape","border","messageCondition","background","image","style","layer","actionArea");
            var e=version>=9?object(item,elementKeys,Set.of("logic","hoverImage","item")):object(item,elementKeys);
            Screen.MessageCondition condition=null;
            if(version>=2 && e.get("messageCondition")!=null){
                var c=object(e.get("messageCondition"),Set.of("permission","ifTrue","ifFalse"));
                condition=new Screen.MessageCondition(string(c,"permission"),string(c,"ifTrue"),string(c,"ifFalse"));
            }
            boolean border=true;if(version>=2){if(!(e.get("border") instanceof Boolean b))throw bad("Expected border boolean");border=b;}
            boolean background=false;ImageAsset image=null;
            if(version>=3){
                if(!(e.get("background") instanceof Boolean b))throw bad("Expected background boolean");background=b;
                if(e.get("image")!=null)image=ImageAsset.fromBase64(string(e,"image"));
            }
            Screen.Style style=Screen.Style.defaults();int layer=0;Screen.ActionArea area=null;
            if(version>=4){
                Set<String> v8Style=Set.of("opacity","fillColor","borderColor","textColor","shadow","rotation","radius","outlineWidth","hoverColor","hoverEffect","textAlign","wrapWidth","brightness","textScale","textOutline","textShadow","gradientColor","gradientDirection","textGradient");
                var s=version>=9?object(e.get("style"),v8Style,Set.of("textCase","blur","backdropBlur","shadowBlur","slice","sliceScale","imageClip")):object(e.get("style"),version>=8?v8Style:version==7?Set.of("opacity","fillColor","borderColor","textColor","shadow","rotation","radius","outlineWidth","hoverColor","hoverEffect","textAlign","wrapWidth","brightness","textScale","textOutline","textShadow"):version==6?Set.of("opacity","fillColor","borderColor","textColor","shadow","rotation","radius","outlineWidth","hoverColor","hoverEffect","textAlign","wrapWidth","brightness"):version==5?Set.of("opacity","fillColor","borderColor","textColor","shadow","rotation","radius","outlineWidth","hoverColor","hoverEffect","textAlign","wrapWidth")
                        :Set.of("opacity","fillColor","borderColor","textColor","shadow"));
                style=version>=5 ? new Screen.Style(number(s,"opacity"),string(s,"fillColor"),string(s,"borderColor"),string(s,"textColor"),bool(s,"shadow"),
                        number(s,"rotation"),number(s,"radius"),integer(s.get("outlineWidth"),"outlineWidth"),s.get("hoverColor")==null?null:string(s,"hoverColor"),
                        string(s,"hoverEffect"),string(s,"textAlign"),integer(s.get("wrapWidth"),"wrapWidth"),version>=6?number(s,"brightness"):1,
                        version>=7?integer(s.get("textScale"),"textScale"):1,version>=7&&s.get("textOutline")!=null?string(s,"textOutline"):null,version>=7&&bool(s,"textShadow"),
                        version>=8&&s.get("gradientColor")!=null?string(s,"gradientColor"):null,version>=8?string(s,"gradientDirection"):"vertical",
                        version>=8&&s.get("textGradient")!=null?string(s,"textGradient"):null)
                        :new Screen.Style(number(s,"opacity"),string(s,"fillColor"),string(s,"borderColor"),string(s,"textColor"),bool(s,"shadow"));
                if(version>=9 && s.containsKey("textCase"))style=style.withTextCase(string(s,"textCase"));
                if(version>=9 && (s.containsKey("blur") || s.containsKey("backdropBlur") || s.containsKey("shadowBlur")))
                    style=style.withBlur(optionalInteger(s,"blur"),optionalInteger(s,"backdropBlur"),optionalInteger(s,"shadowBlur"));
                if(version>=9 && (s.containsKey("slice") || s.containsKey("sliceScale")))
                    style=style.withSlice(optionalInteger(s,"slice"),s.containsKey("sliceScale")?optionalInteger(s,"sliceScale"):1);
                if(version>=9 && s.containsKey("imageClip")) {
                    if(!(s.get("imageClip") instanceof Boolean clip)) throw new IllegalArgumentException("imageClip must be true or false");
                    style=style.withImageClip(clip);
                }
                layer=integer(e.get("layer"),"layer");
                if(e.get("actionArea")!=null){
                    var a=object(e.get("actionArea"),Set.of("x","y","width","height"));
                    area=new Screen.ActionArea(number(a,"x"),number(a,"y"),number(a,"width"),number(a,"height"));
                }
            }
            String text=string(e,"text");if(version<5 && (text.length()>64 || !text.matches("[ -~]*")))throw bad("Legacy text requires at most64 printableASCII characters");
            ElementLogic logic=ElementLogic.EMPTY;
            if(version>=9 && e.get("logic")!=null){
                try{logic=ActionParser.element(e.get("logic"));}
                catch(IllegalArgumentException invalid){throw bad("Element "+e.get("id")+": "+invalid.getMessage());}
            }
            ImageAsset hoverImage=version>=9&&e.get("hoverImage")!=null?ImageAsset.fromBase64(string(e,"hoverImage")):null;
            elements.add(new Screen.Element(string(e,"id"),text,number(e,"x"),number(e,"y"),
                    number(e,"width"),number(e,"height"),string(e,"kind"),string(e,"action"),
                    version==1?"rectangle":string(e,"shape"),border,condition,background,image,style,layer,area,logic,hoverImage,
                    version>=9&&e.get("item")!=null?string(e,"item"):null));
        }
        ScreenLogic screenLogic=ScreenLogic.EMPTY;
        if(version>=9 && root.get("logic")!=null){
            try{screenLogic=ActionParser.screen(root.get("logic"));}
            catch(IllegalArgumentException invalid){throw bad("Screen logic: "+invalid.getMessage());}
        }
        // Schema 1-8 texts keep drawing as written ("<gold>" and "{1+1}" stay text, and a line wider than its box is cut,
        // not drawn smaller): see Screen.legacyText. Only marked when some text has such a tag or token, or may shrink
        // (schema 4-8: older pages have the legacy quality, which never shrinks), so every other old page reads (and
        // compares) exactly as before.
        boolean literal=version<9&&elements.stream().anyMatch(e->dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.hasTags(e.text())||dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Expressions.contains(e.text())
                ||version>=4&&dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.mayShrink(e,e.text()));
        var screen=new Screen(string(root,"id"),string(root,"title"),elements,quality,presentation,screenLogic,literal);
        if(version>=4)screen.validateActionAreas();
        return screen;
    }
    /** Parses any JSON value with the same limits as screen files (maps, lists, strings, BigDecimal, booleans, null). */
    public static Object parse(String json) {
        if (json==null || exceedsUtf8Budget(json)) throw bad("JSON size exceeds 128 MiB");
        var p=new Parser(json);Object value=p.value(0);p.space();if(p.at!=json.length())throw bad("Trailing JSON data");
        return value;
    }
    /** Newest schema version this build reads and writes. */
    public static final int LATEST=9;
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object o,Set<String> keys) {
        if (!(o instanceof Map<?,?> m) || !m.keySet().equals(keys)) throw bad("Unknown or missing JSON field");
        return (Map<String,Object>)m;
    }
    /** All {@code required} keys, plus any of {@code optional}; nothing else. */
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object o,Set<String> required,Set<String> optional) {
        if (!(o instanceof Map<?,?> m) || !m.keySet().containsAll(required)) throw bad("Unknown or missing JSON field");
        for(Object key:m.keySet())if(!required.contains(key) && !optional.contains(key))throw bad("Unknown JSON field: "+key);
        return (Map<String,Object>)m;
    }
    private static String string(Map<String,Object> o,String key) { if(!(o.get(key) instanceof String s))throw bad("Expected string: "+key);return s; }
    private static double number(Map<String,Object> o,String key) { if(!(o.get(key) instanceof BigDecimal n))throw bad("Expected number: "+key);return n.doubleValue(); }
    private static int integer(Object value,String name) {
        if(!(value instanceof BigDecimal number))throw bad("Expected integer: "+name);
        try{return number.intValueExact();}catch(ArithmeticException malformed){throw bad("Expected exact integer: "+name);}
    }
    /** An optional integer field (absent = 0). */
    private static int optionalInteger(Map<String,Object> o,String key) { return o.containsKey(key)?integer(o.get(key),key):0; }
    private static boolean bool(Map<String,Object> o,String key) { if(!(o.get(key) instanceof Boolean b))throw bad("Expected boolean: "+key);return b; }
    private static boolean exceedsUtf8Budget(String text) {
        if(text.length()>MAX_JSON_BYTES)return true;
        long size=0;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c<=0x7f)size++;else if(c<=0x7ff)size+=2;
            else if(Character.isHighSurrogate(c) && i+1<text.length() && Character.isLowSurrogate(text.charAt(i+1))){size+=4;i++;}
            else size+=Character.isSurrogate(c)?1:3;
            if(size>MAX_JSON_BYTES)return true;
        }
        return false;
    }
    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException(message); }
    public static String encode(Screen screen) {
        boolean advanced=!screen.quality().preset().equals("legacy");
        // Schema 9 also carries text drawn as written, characters beyond printable ASCII (accents, symbols) and text that
        // shrinks to fit its box (an old page that was cut stays cut: it keeps its schema).
        boolean logic=screen.hasLogic() || screen.presentation().customCursor() || screen.presentation().stack() || dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.extended(screen.title())
                || screen.elements().stream().anyMatch(e->!e.style().upperCase() || e.style().hasBlur() || e.style().hasSlice() || e.style().imageClip() || e.hoverImage()!=null || e.item()!=null || dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.extended(e.text())
                    || !screen.legacyText() && (dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.hasTags(e.text()) || dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Expressions.contains(e.text())
                        || advanced && dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.mayShrink(e,e.text())));
        if(logic && !advanced)throw bad("Select an explicit quality to export screen logic, accented text, mixed-case text or blurs");
        boolean presented=logic || !screen.presentation().isDefault() || screen.elements().stream().anyMatch(e->e.style().brightness()!=1);
        boolean gradient=logic || screen.elements().stream().anyMatch(e->e.style().hasGradient());
        boolean styled=gradient || screen.elements().stream().anyMatch(e->e.style().isStyledText() || e.style().hoverEffect().equals("glow"));
        presented|=styled;
        boolean extended=logic || screen.elements().stream().anyMatch(e->e.style().isExtended() || e.text().length()>64 || e.text().contains("\n"));
        if(advanced)screen.validateActionAreas();
        else if(presented || extended || screen.elements().size()>24 || screen.elements().stream().anyMatch(e->!e.style().equals(Screen.Style.defaults()) || e.layer()!=0 || e.actionArea()!=null || e.kind().equals("decoration")))
            throw bad("Select an explicit quality to export advanced elements or more than 24 elements");
        var out=new StringBuilder("{\"version\":").append(logic?9:gradient?8:styled?7:presented?6:extended?5:advanced?4:3).append(",\"id\":").append(quote(screen.id())).append(",\"title\":").append(quote(screen.title()));
        if(logic && !screen.logic().isEmpty())out.append(",\"logic\":").append(LogicJson.screen(screen.logic()));
        if(advanced){var q=screen.quality();out.append(",\"quality\":{\"preset\":").append(quote(q.preset())).append(",\"width\":").append(q.width()).append(",\"height\":").append(q.height()).append('}');}
        if(presented){var pr=screen.presentation();out.append(",\"presentation\":{\"mode\":").append(quote(pr.mode())).append(",\"scale\":").append(pr.scale()).append(",\"cursor\":").append(quote(pr.cursor()));
            if(pr.cursorSize()!=null)out.append(",\"cursorSize\":").append(pr.cursorSize());
            if(pr.cursorSpeed()!=1)out.append(",\"cursorSpeed\":").append(pr.cursorSpeed());
            if(pr.stack())out.append(",\"stack\":true");
            out.append('}');}
        out.append(",\"elements\":[");
        boolean comma=false;
        for(var e:screen.elements()) {
            if(comma)out.append(',');comma=true;
            // An old page written as schema 9 (it gained logic) keeps its "<gold>" as text: escaped, as the editor does.
            out.append("{\"id\":").append(quote(e.id())).append(",\"text\":").append(quote(logic && screen.legacyText() ? dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.escape(e.text()) : e.text()))
                .append(",\"x\":").append(e.x()).append(",\"y\":").append(e.y()).append(",\"width\":").append(e.width())
                .append(",\"height\":").append(e.height()).append(",\"kind\":").append(quote(e.kind())).append(",\"action\":").append(quote(e.action()))
                .append(",\"shape\":").append(quote(e.shape())).append(",\"border\":").append(e.border()).append(",\"messageCondition\":");
            var c=e.messageCondition();if(c==null)out.append("null");else out.append("{\"permission\":").append(quote(c.permission()))
                    .append(",\"ifTrue\":").append(quote(c.ifTrue())).append(",\"ifFalse\":").append(quote(c.ifFalse())).append('}');
            out.append(",\"background\":").append(e.background()).append(",\"image\":");
            if(e.image()==null)out.append("null");else out.append('"').append(e.image().base64()).append('"');
            if(advanced){
                var s=e.style();out.append(",\"style\":{\"opacity\":").append(s.opacity()).append(",\"fillColor\":").append(quote(s.fillColor()))
                        .append(",\"borderColor\":").append(quote(s.borderColor())).append(",\"textColor\":").append(quote(s.textColor())).append(",\"shadow\":").append(s.shadow());
                if(presented || extended)out.append(",\"rotation\":").append(s.rotation()).append(",\"radius\":").append(s.radius()).append(",\"outlineWidth\":").append(s.outlineWidth())
                        .append(",\"hoverColor\":").append(s.hoverColor()==null?"null":quote(s.hoverColor())).append(",\"hoverEffect\":").append(quote(s.hoverEffect()))
                        .append(",\"textAlign\":").append(quote(s.textAlign())).append(",\"wrapWidth\":").append(s.wrapWidth());
                if(presented)out.append(",\"brightness\":").append(s.brightness());
                if(styled)out.append(",\"textScale\":").append(s.textScale()).append(",\"textOutline\":").append(s.textOutline()==null?"null":quote(s.textOutline()))
                        .append(",\"textShadow\":").append(s.textShadow());
                if(gradient)out.append(",\"gradientColor\":").append(s.gradientColor()==null?"null":quote(s.gradientColor()))
                        .append(",\"gradientDirection\":").append(quote(s.gradientDirection()))
                        .append(",\"textGradient\":").append(s.textGradient()==null?"null":quote(s.textGradient()));
                if(!s.upperCase())out.append(",\"textCase\":").append(quote(s.textCase()));
                if(s.blur()!=0)out.append(",\"blur\":").append(s.blur());
                if(s.backdropBlur()!=0)out.append(",\"backdropBlur\":").append(s.backdropBlur());
                if(s.shadowBlur()!=0)out.append(",\"shadowBlur\":").append(s.shadowBlur());
                if(s.slice()!=0)out.append(",\"slice\":").append(s.slice());
                if(s.sliceScale()!=1)out.append(",\"sliceScale\":").append(s.sliceScale());
                if(s.imageClip())out.append(",\"imageClip\":true");
                out.append('}').append(",\"layer\":").append(e.layer()).append(",\"actionArea\":");
                var a=e.actionArea();if(a==null)out.append("null");else out.append("{\"x\":").append(a.x()).append(",\"y\":").append(a.y()).append(",\"width\":").append(a.width()).append(",\"height\":").append(a.height()).append('}');
                if(logic && !e.logic().isEmpty())out.append(",\"logic\":").append(LogicJson.element(e.logic()));
                if(e.hoverImage()!=null)out.append(",\"hoverImage\":\"").append(e.hoverImage().base64()).append('"');
                if(e.item()!=null)out.append(",\"item\":\"").append(e.item()).append('"');
            }
            out.append('}');
            if(out.length()>MAX_JSON_BYTES-2)throw bad("JSON size exceeds 128 MiB");
        }
        String json=out.append("]}").toString();
        if(json.length()>MAX_JSON_BYTES)throw bad("JSON size exceeds 128 MiB");
        return json;
    }
    private static String quote(String s) { return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\""; }
    /** Schema 9 nests action lists (conditions inside functions inside logic): deep enough for 8 action levels. */
    private static final int MAX_DEPTH=24;
    private static final class Parser {
        final String s;int at;
        Parser(String s){this.s=s;}
        void space(){while(at<s.length() && " \r\n\t".indexOf(s.charAt(at))>=0)at++;}
        char next(){if(at==s.length())throw bad("Unexpected end of JSON");return s.charAt(at++);}
        boolean take(char c){space();if(at<s.length()&&s.charAt(at)==c){at++;return true;}return false;}
        Object value(int depth) {
            if(depth>MAX_DEPTH)throw bad("JSON nesting exceeds "+MAX_DEPTH);space();if(at==s.length())throw bad("Missing JSON value");char c=s.charAt(at);
            if(c=='"')return text();
            if(c=='{'){
                at++;var map=new LinkedHashMap<String,Object>();if(take('}'))return map;
                do{space();if(at==s.length()||s.charAt(at)!='"')throw bad("Expected key");String key=text();
                    if(!take(':'))throw bad("Expected colon");if(map.containsKey(key))throw bad("Duplicate JSON key");map.put(key,value(depth+1));
                }while(take(','));if(!take('}'))throw bad("Expected object end");return map;
            }
            if(c=='['){at++;var list=new ArrayList<Object>();if(take(']'))return list;do{list.add(value(depth+1));if(list.size()>128)throw bad("JSON array too long");}while(take(','));if(!take(']'))throw bad("Expected array end");return list;}
            for(String literal:List.of("true","false","null"))if(s.startsWith(literal,at)){at+=literal.length();return literal.equals("null")?null:literal.equals("true");}
            int start=at;
            while(at<s.length() && "-+0123456789.eE".indexOf(s.charAt(at))>=0)at++;
            String token=s.substring(start,at);
            if(token.length()>100 || !token.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))throw bad("Invalid JSON number");
            try{return new BigDecimal(token);}catch(NumberFormatException e){throw bad("Invalid number");}
        }
        String text(){
            if(next()!='"')throw bad("Expected string");var out=new StringBuilder();
            while(true){char c=next();if(c=='"')return out.toString();if(c<32)throw bad("Control in JSON string");
                if(c=='\\'){char escape=next();c=switch(escape){case '"','\\','/'->escape;case 'b'->'\b';case 'f'->'\f';case 'n'->'\n';case 'r'->'\r';case 't'->'\t';case 'u'->{
                    if(at+4>s.length())throw bad("Truncated Unicode escape");String hex=s.substring(at,at+4);if(!hex.matches("[0-9a-fA-F]{4}"))throw bad("Invalid Unicode escape");at+=4;yield(char)Integer.parseInt(hex,16);
                }default->throw bad("Invalid string escape");};}
                out.append(c);if(out.length()>((ImageAsset.MAX_PNG_BYTES+2)/3)*4)throw bad("JSON string too long");
            }
        }
    }
}
