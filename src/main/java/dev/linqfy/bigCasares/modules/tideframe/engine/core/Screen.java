package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ElementLogic;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.LogicIndex;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic;
import java.util.*;

/** {@code logic} (schema 9) holds behaviour: variables, functions, keybinds, close rules, open/close actions. */
public record Screen(String id, String title, List<Element> elements, RenderQuality quality, Presentation presentation, ScreenLogic logic, boolean legacyText) {
    /**
     * {@code legacyText}: read from a schema 1-8 file, whose texts are drawn exactly as written: no word colours
     * ({@code <gold>}), no calculated tokens ({@code {1+1}}), and a line wider than its box is cut instead of drawn
     * smaller (TextFit), as before schema 9 had them. Screens built in code and schema 9 files are false.
     */
    public Screen(String id,String title,List<Element> elements,RenderQuality quality,Presentation presentation,ScreenLogic logic) { this(id,title,elements,quality,presentation,logic,false); }
    public Screen(String id,String title,List<Element> elements,RenderQuality quality,Presentation presentation) { this(id,title,elements,quality,presentation,ScreenLogic.EMPTY); }
    public Screen(String id,String title,List<Element> elements,RenderQuality quality) { this(id,title,elements,quality,Presentation.defaults()); }
    /**
     * {@code cursorSize} (schema 9, null = config display.cursor-size) scales this page's cursor sprite (0.25-1.5);
     * {@code cursorSpeed} multiplies the player's own sensitivity on this page (0.25-4). {@code stack} (schema 9, HUD
     * pages only): opening the page adds it over the HUD pages already shown instead of replacing them.
     */
    public record Presentation(String mode,int scale,String cursor,Double cursorSize,double cursorSpeed,boolean stack) {
        public Presentation(String mode,int scale,String cursor) { this(mode,scale,cursor,null,1); }
        public Presentation(String mode,int scale,String cursor,Double cursorSize,double cursorSpeed) { this(mode,scale,cursor,cursorSize,cursorSpeed,false); }
        public Presentation {
            if(mode==null || !Set.of("menu","overlay").contains(mode) || scale<1 || scale>3
                    || cursor==null || !Set.of("cross","block","text","arrow").contains(cursor))
                throw new IllegalArgumentException("Invalid screen presentation");
            if(cursorSize!=null && (!Double.isFinite(cursorSize) || cursorSize<.25 || cursorSize>1.5)) throw new IllegalArgumentException("cursorSize must be 0.25 to 1.5");
            if(!Double.isFinite(cursorSpeed) || cursorSpeed<.25 || cursorSpeed>4) throw new IllegalArgumentException("cursorSpeed must be 0.25 to 4");
            if(stack && !mode.equals("overlay")) throw new IllegalArgumentException("stack is for HUD pages (mode overlay)");
        }
        public static Presentation defaults() { return new Presentation("menu",1,"cross"); }
        public boolean isDefault() { return equals(defaults()); }
        /** True when the page sets its own cursor size or speed (schema 9). */
        public boolean customCursor() { return cursorSize!=null || cursorSpeed!=1; }
        public Presentation withCursor(Double size,double speed) { return new Presentation(mode,scale,cursor,size,speed,stack); }
        /** The same presentation added over other HUD pages ({@code true}) or replacing them. */
        public Presentation withStack(boolean value) { return new Presentation(mode,scale,cursor,cursorSize,cursorSpeed,value); }
    }
    public Screen(String id,String title,List<Element> elements) { this(id,title,elements,RenderQuality.legacy()); }
    /**
     * {@code textCase} (schema 9): "upper" draws capitals like every earlier schema, "original" draws the text as written.
     * Blurs (schema 9, logical pixels of standard deviation, 0 = off): {@code blur} softens the whole element,
     * {@code backdropBlur} softens what is underneath it inside its shape (frosted glass), {@code shadowBlur} gives
     * the drop shadow a blurred edge of that size instead of the classic one.
     */
    public record Style(double opacity,String fillColor,String borderColor,String textColor,boolean shadow,
                        double rotation,double radius,int outlineWidth,String hoverColor,String hoverEffect,String textAlign,int wrapWidth,double brightness,
                        int textScale,String textOutline,boolean textShadow,String gradientColor,String gradientDirection,String textGradient,String textCase,
                        int blur,int backdropBlur,int shadowBlur,int slice,int sliceScale,boolean imageClip) {
        public static final Set<String> HOVER_EFFECTS = Set.of("none","translate","scale","glow");
        public static final Set<String> GRADIENT_DIRECTIONS = Set.of("vertical","horizontal","diagonal","radial");
        /** Largest blur, backdrop blur or shadow blur (logical pixels). */
        public static final int MAX_BLUR = 16;
        /** Largest 9-slice border (image pixels) and densest image (image pixels per logical pixel). */
        public static final int MAX_SLICE = 64, MAX_SLICE_SCALE = 4;
        public Style(double opacity,String fillColor,String borderColor,String textColor,boolean shadow,
                     double rotation,double radius,int outlineWidth,String hoverColor,String hoverEffect,String textAlign,int wrapWidth,double brightness,
                     int textScale,String textOutline,boolean textShadow,String gradientColor,String gradientDirection,String textGradient,String textCase,
                     int blur,int backdropBlur,int shadowBlur,int slice,int sliceScale) {
            this(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,textGradient,textCase,blur,backdropBlur,shadowBlur,slice,sliceScale,false);
        }
        public Style(double opacity,String fillColor,String borderColor,String textColor,boolean shadow,
                     double rotation,double radius,int outlineWidth,String hoverColor,String hoverEffect,String textAlign,int wrapWidth,double brightness,
                     int textScale,String textOutline,boolean textShadow,String gradientColor,String gradientDirection,String textGradient,String textCase,
                     int blur,int backdropBlur,int shadowBlur) {
            this(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,textGradient,textCase,blur,backdropBlur,shadowBlur,0,1);
        }
        public Style(double opacity,String fillColor,String borderColor,String textColor,boolean shadow,
                     double rotation,double radius,int outlineWidth,String hoverColor,String hoverEffect,String textAlign,int wrapWidth,double brightness,
                     int textScale,String textOutline,boolean textShadow,String gradientColor,String gradientDirection,String textGradient,String textCase) {
            this(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,textGradient,textCase,0,0,0);
        }
        public Style(double opacity,String fillColor,String borderColor,String textColor,boolean shadow,
                     double rotation,double radius,int outlineWidth,String hoverColor,String hoverEffect,String textAlign,int wrapWidth,double brightness,
                     int textScale,String textOutline,boolean textShadow,String gradientColor,String gradientDirection,String textGradient) {
            this(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,textGradient,"upper");
        }
        public Style(double opacity,String fillColor,String borderColor,String textColor,boolean shadow,
                     double rotation,double radius,int outlineWidth,String hoverColor,String hoverEffect,String textAlign,int wrapWidth,double brightness,
                     int textScale,String textOutline,boolean textShadow) {
            this(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,null,"vertical",null);
        }
        public Style(double opacity,String fillColor,String borderColor,String textColor,boolean shadow,
                     double rotation,double radius,int outlineWidth,String hoverColor,String hoverEffect,String textAlign,int wrapWidth,double brightness) {
            this(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,1,null,false);
        }
        public Style(double opacity,String fillColor,String borderColor,String textColor,boolean shadow,
                     double rotation,double radius,int outlineWidth,String hoverColor,String hoverEffect,String textAlign,int wrapWidth) {
            this(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,1);
        }
        public Style(double opacity,String fillColor,String borderColor,String textColor,boolean shadow) {
            this(opacity,fillColor,borderColor,textColor,shadow,0,.08,1,null,"none","center",0);
        }
        public Style {
            if(!Double.isFinite(opacity) || opacity<0 || opacity>1 || !color(fillColor) || !color(borderColor) || !color(textColor))
                throw new IllegalArgumentException("Invalid UI element style");
            if(!Double.isFinite(rotation) || rotation < -180 || rotation > 180 || !Double.isFinite(radius) || radius < 0 || radius > .5
                    || outlineWidth < 0 || outlineWidth > 8 || hoverColor!=null && !color(hoverColor)
                    || hoverEffect==null || !HOVER_EFFECTS.contains(hoverEffect)
                    || textAlign==null || !Set.of("left","center","right").contains(textAlign) || wrapWidth < 0 || wrapWidth > 256
                    || !Double.isFinite(brightness) || brightness < 0 || brightness > 2
                    || textScale < 1 || textScale > 4 || textOutline!=null && !color(textOutline)
                    || gradientColor!=null && !color(gradientColor) || gradientDirection==null || !GRADIENT_DIRECTIONS.contains(gradientDirection)
                    || textGradient!=null && !color(textGradient) || textCase==null || !LiveText.TEXT_CASES.contains(textCase))
                throw new IllegalArgumentException("Invalid extended UI appearance");
            if(blur<0 || blur>MAX_BLUR || backdropBlur<0 || backdropBlur>MAX_BLUR || shadowBlur<0 || shadowBlur>MAX_BLUR)
                throw new IllegalArgumentException("blur, backdropBlur and shadowBlur must be 0 to "+MAX_BLUR);
            if(slice<0 || slice>MAX_SLICE || sliceScale<1 || sliceScale>MAX_SLICE_SCALE)
                throw new IllegalArgumentException("slice must be 0 to "+MAX_SLICE+" and sliceScale 1 to "+MAX_SLICE_SCALE);
        }
        /** 9-slice (schema 9): the image's outer {@code slice} pixels keep their size, the edges and centre stretch to the box. */
        public Style withSlice(int value,int scale) {
            return new Style(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,textGradient,textCase,blur,backdropBlur,shadowBlur,value,scale,imageClip);
        }
        /** Schema 9: the image takes the element's shape (rounded corners or an ellipse); a rectangle shows it whole. */
        public Style withImageClip(boolean value) {
            return new Style(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,textGradient,textCase,blur,backdropBlur,shadowBlur,slice,sliceScale,value);
        }
        /** True when the style uses schema 9's 9-slice fields. */
        public boolean hasSlice() { return slice!=0 || sliceScale!=1; }
        /** True when any blur is set (schema 9). */
        public boolean hasBlur() { return blur!=0 || backdropBlur!=0 || shadowBlur!=0; }
        /** The same style with these blurs (logical pixels, 0 = off). */
        public Style withBlur(int element,int backdrop,int shadowEdge) {
            return new Style(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,textGradient,textCase,element,backdrop,shadowEdge,slice,sliceScale,imageClip);
        }
        /** True when the font draws this element's text in capitals (the default). */
        public boolean upperCase() { return textCase.equals("upper"); }
        /** Text drawn as written ("original") or in capitals ("upper"). */
        public Style withTextCase(String value) {
            return new Style(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,textGradient,value,blur,backdropBlur,shadowBlur,slice,sliceScale,imageClip);
        }
        private static boolean color(String color) { return color!=null && color.matches("#[0-9a-fA-F]{6}"); }
        public static Style defaults() { return new Style(1,"#28384b","#ffffff","#ffffff",false); }
        public boolean isExtended() { return rotation!=0 || radius!=.08 || outlineWidth!=1 || hoverColor!=null
                || !hoverEffect.equals("none") || !textAlign.equals("center") || wrapWidth!=0; }
        /** Text decoration (schema 7: big text, outline, drop shadow; schema 8: text gradient). */
        public boolean isStyledText() { return textScale!=1 || textOutline!=null || textShadow || textGradient!=null; }
        /** Schema 8: gradient fill or text gradient. */
        public boolean hasGradient() { return gradientColor!=null || !gradientDirection.equals("vertical") || textGradient!=null; }
        /** Outline thickness in logical pixels for this text scale. */
        public int outlineThickness() { return textOutline==null ? 0 : (textScale + 1) / 2; }
        public Style withText(int scale,String outline,boolean dropShadow) {
            return new Style(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    scale,outline,dropShadow,gradientColor,gradientDirection,textGradient,textCase,blur,backdropBlur,shadowBlur,slice,sliceScale,imageClip);
        }
        public Style withHoverEffect(String effect) {
            return new Style(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,effect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,textGradient,textCase,blur,backdropBlur,shadowBlur,slice,sliceScale,imageClip);
        }
        /** Background fades from fillColor to {@code color} (null = flat fill). */
        public Style withGradient(String color,String direction) {
            return new Style(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,color,direction,textGradient,textCase,blur,backdropBlur,shadowBlur,slice,sliceScale,imageClip);
        }
        /** Text fades from textColor (top) to {@code color} (bottom); null = flat text. */
        public Style withTextGradient(String color) {
            return new Style(opacity,fillColor,borderColor,textColor,shadow,rotation,radius,outlineWidth,hoverColor,hoverEffect,textAlign,wrapWidth,brightness,
                    textScale,textOutline,textShadow,gradientColor,gradientDirection,color,textCase,blur,backdropBlur,shadowBlur,slice,sliceScale,imageClip);
        }
    }
    public record ActionArea(double x,double y,double width,double height) {
        public ActionArea {
            if(!bounds(x,y,width,height)) throw new IllegalArgumentException("Invalid action area");
        }
    }
    public record MessageCondition(String permission,String ifTrue,String ifFalse){
        public MessageCondition {
            if(permission==null || !permission.matches("[a-z0-9_.-]{1,128}") || !message(ifTrue) || !message(ifFalse))
                throw new IllegalArgumentException("Invalid conditional message");
        }
        private static boolean message(String text){return text!=null && text.matches("[ -~]{0,100}");}
    }
    /** Runs as the clicking player (their own permissions apply); {player} is replaced with their name. */
    public static final String COMMAND_ACTION = "command:/?[a-z0-9_.:-]{1,32}( [ -~]{0,96})?";
    /**
     * {@code logic} (schema 9): action lists, right click, visibility, animation frames, data lists, drag and drop.
     * {@code hoverImage} (schema 9, buttons only, null = none): the image the button shows while the cursor is on it.
     */
    public record Element(String id, String text, double x, double y, double width, double height, String kind, String action,
                          String shape,boolean border,MessageCondition messageCondition,boolean background,ImageAsset image,
                          Style style,int layer,ActionArea actionArea,ElementLogic logic,ImageAsset hoverImage,String item) {
        public Element(String id,String text,double x,double y,double width,double height,String kind,String action,
                       String shape,boolean border,MessageCondition messageCondition,boolean background,ImageAsset image,
                       Style style,int layer,ActionArea actionArea,ElementLogic logic,ImageAsset hoverImage){
            this(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,image,style,layer,actionArea,logic,hoverImage,null);
        }
        public Element(String id,String text,double x,double y,double width,double height,String kind,String action,
                       String shape,boolean border,MessageCondition messageCondition,boolean background,ImageAsset image,
                       Style style,int layer,ActionArea actionArea,ElementLogic logic){
            this(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,image,style,layer,actionArea,logic,null);
        }
        public Element(String id,String text,double x,double y,double width,double height,String kind,String action,
                       String shape,boolean border,MessageCondition messageCondition,boolean background,ImageAsset image,
                       Style style,int layer,ActionArea actionArea){
            this(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,image,style,layer,actionArea,ElementLogic.EMPTY);
        }
        public Element(String id,String text,double x,double y,double width,double height,String kind,String action){
            this(id,text,x,y,width,height,kind,action,"rectangle",!"decoration".equals(kind),null);
        }
        public Element(String id,String text,double x,double y,double width,double height,String kind,String action,
                       String shape,boolean border,MessageCondition messageCondition){
            this(id,text,x,y,width,height,kind,action,shape,border,messageCondition,false,null);
        }
        public Element(String id,String text,double x,double y,double width,double height,String kind,String action,
                       String shape,boolean border,MessageCondition messageCondition,boolean background,ImageAsset image) {
            this(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,image,Style.defaults(),0,null);
        }
        public Element {
            validId(id);
            if (text == null || text.length() > 512 || !LiveText.drawable(text)
                    || !bounds(x,y,width,height)
                    || kind==null || !(kind.equals("label") || kind.equals("button") || kind.equals("decoration"))
                    || action == null || !(action.equals("") || action.equals("close") || action.matches("open:[a-z][a-z0-9_-]{0,31}")
                    || action.equals("conditional-message") || action.matches("message:[ -~]{0,100}")
                    || action.matches(COMMAND_ACTION))) throw new IllegalArgumentException("Invalid UI element");
            if(shape==null || !Set.of("rectangle","rounded","ellipse").contains(shape)
                    || action.equals("conditional-message")!=(messageCondition!=null)
                    || (messageCondition!=null && !kind.equals("button"))) throw new IllegalArgumentException("Invalid shape or message condition");
            if(style==null || layer < -128 || layer > 128 || kind.equals("decoration") && (!action.isEmpty() || messageCondition!=null))
                throw new IllegalArgumentException("Invalid style, layer or decoration action");
            if(logic==null)logic=ElementLogic.EMPTY;
            if(logic.interactive() && !kind.equals("button"))
                throw new IllegalArgumentException("Element "+id+": actions, right actions, hover actions, tooltips, drag and drop and list selection need a button");
            if(id.equals(logic.tooltip())) throw new IllegalArgumentException("Element "+id+" cannot be its own tooltip");
            if(hoverImage!=null && !kind.equals("button")) throw new IllegalArgumentException("Element "+id+": a hover image needs a button (only buttons are hovered)");
            if(style.slice()>0) for(var picture:new ImageAsset[]{image,hoverImage})
                if(picture!=null && 2*style.slice()>=Math.min(picture.width(),picture.height()))
                    throw new IllegalArgumentException("Element "+id+": the 9-slice border ("+style.slice()+" px) must leave the image a middle (it is "+picture.width()+" x "+picture.height()+")");
            if(item!=null) {
                if(ItemIcons.find(item).isEmpty()) throw new IllegalArgumentException("Element "+id+": unknown item "+item+" (use a game item id such as diamond)");
                if(logic.list()!=null || logic.frames()!=null || logic.motion()!=null)
                    throw new IllegalArgumentException("Element "+id+": an item icon cannot go on a list, a PNG/GIF animation or keyframes");
                if(!ItemIcons.drawable(y,width,height))
                    throw new IllegalArgumentException("Element "+id+": an item icon must sit in the lower part of the menu (its bottom edge at y 64 or lower of 144); the game cannot draw it higher");
                // The title cannot fade, turn, blur, tint by brightness, shadow or move a client texture: refuse looks it would not follow.
                if(!ItemIcons.followsStyle(style))
                    throw new IllegalArgumentException("Element "+id+": an item icon is the game's own texture and cannot follow the element's look: use opacity 1 (or 0 to hide it), no rotation, blur, shadow or brightness change, and a hover effect of none or glow");
            }
        }
        public Element moved(double x, double y) {
            ActionArea movedArea=actionArea==null?null:new ActionArea(actionArea.x()+(x-this.x),actionArea.y()+(y-this.y),actionArea.width(),actionArea.height());
            return new Element(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,image,style,layer,movedArea,logic,hoverImage,item);
        }
        public Element withLogic(ElementLogic value) {
            return new Element(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,image,style,layer,actionArea,value,hoverImage,item);
        }
        public Element withImage(ImageAsset value) {
            return new Element(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,value,style,layer,actionArea,logic,hoverImage,item);
        }
        public Element withText(String value) {
            return new Element(id,value,x,y,width,height,kind,action,shape,border,messageCondition,background,image,style,layer,actionArea,logic,hoverImage,item);
        }
        public Element withStyle(Style value) {
            return new Element(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,image,value,layer,actionArea,logic,hoverImage,item);
        }
        /** The image shown while the cursor is on this button (null = the normal image). */
        public Element withHoverImage(ImageAsset value) {
            return new Element(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,image,style,layer,actionArea,logic,value,item);
        }
        /** The game item whose own texture is drawn centred in this box (null = none); see {@link ItemIcons}. */
        public Element withItem(String value) {
            return new Element(id,text,x,y,width,height,kind,action,shape,border,messageCondition,background,image,style,layer,actionArea,logic,hoverImage,value);
        }
    }
    public Screen {
        validId(id);
        if (title == null || title.isEmpty() || title.length() > 40 || title.contains("\n") || !LiveText.drawable(title)
                || elements == null || elements.size()>128 || quality==null || presentation==null)
            throw new IllegalArgumentException("Invalid screen");
        elements = List.copyOf(elements);
        if (logic == null) logic = ScreenLogic.EMPTY;
        var ids = new HashSet<String>();
        for (var e : elements) if (!ids.add(e.id())) throw new IllegalArgumentException("Duplicate element id");
        for (var e : elements) if (!ListRows.fits(e)) throw new IllegalArgumentException("List " + e.id() + " runs off the bottom of the screen (fewer rows or less spacing)");
        if (!legacyText) for (var e : elements)
            if (RichText.colors(e.text()).size() > RichText.MAX_COLORS) throw new IllegalArgumentException(e.id() + ": at most " + RichText.MAX_COLORS + " word colours in one text");
    }
    public Screen withLogic(ScreenLogic value) { return new Screen(id,title,elements,quality,presentation,value,legacyText); }
    public Screen withElements(List<Element> value) { return new Screen(id,title,value,quality,presentation,logic,legacyText); }
    public Screen withQuality(RenderQuality value) { return new Screen(id,title,elements,value,presentation,logic,legacyText); }
    public Screen withPresentation(Presentation value) { return new Screen(id,title,elements,quality,value,logic,legacyText); }
    /** True when the screen or any element carries schema-9 logic. */
    public boolean hasLogic() { return !logic.isEmpty() || elements.stream().anyMatch(e -> !e.logic().isEmpty()); }
    public Optional<Element> element(String id) { return elements.stream().filter(e -> e.id().equals(id)).findFirst(); }
    /**
     * Elements that can change while the screen is open (shown/hidden, moved sideways, tinted, animated, repeated as
     * list rows, dragged): they are baked as separate layers instead of into the static canvas.
     */
    public Set<String> runtimeIds() {
        var lists = new ArrayList<List<dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.UiAction>>(logic.actionLists());
        var ids = new TreeSet<String>();
        for (var e : elements) {
            lists.addAll(e.logic().actionLists());
            if (e.logic().changesAtRuntime()) ids.add(e.id());
            if (e.logic().tooltip() != null && element(e.logic().tooltip()).isPresent()) ids.add(e.logic().tooltip());
        }
        for (String target : LogicIndex.targets(lists)) if (element(target).isPresent()) ids.add(target);
        return Collections.unmodifiableSet(ids);
    }
    /** Logic that refers to things this screen does not have (unknown element ids, functions); for load-time warnings. */
    public List<String> logicProblems() { return LogicIndex.problems(this); }
    private static boolean bounds(double x,double y,double width,double height) {
        return Double.isFinite(x) && Double.isFinite(y) && Math.abs(x)<=1 && Math.abs(y)<=1
                && Double.isFinite(width) && Double.isFinite(height) && width>0 && width<=2 && height>0 && height<=2;
    }
    private static void validId(String id) { if (id == null || !id.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Invalid id"); }
    public Screen move(String id, double x, double y) {
        if (elements.stream().noneMatch(e -> e.id().equals(id))) throw new IllegalArgumentException("Unknown element");
        return new Screen(this.id,title,elements.stream().map(e -> e.id().equals(id) ? e.moved(x,y) : e).toList(),quality,presentation,logic,legacyText);
    }
    public Screen delete(String id) { return new Screen(this.id,title,elements.stream().filter(e -> !e.id().equals(id)).toList(),quality,presentation,logic,legacyText); }
    public Screen add(Element e) { var list = new ArrayList<>(elements); list.add(e); return new Screen(id,title,list,quality,presentation,logic,legacyText); }
    /** Stable ascending layers: the hit tester visits the last (topmost) target first. */
    public List<HitTester.Rect> actionTargets() {
        return elements.stream().filter(e->e.kind().equals("button")).sorted(Comparator.comparingInt(Element::layer)).map(e->{
            ActionArea a=e.actionArea();return a==null ? new HitTester.Rect(e.id(),e.x(),e.y(),e.width(),e.height())
                    : new HitTester.Rect(e.id(),a.x(),a.y(),a.width(),a.height());
        }).toList();
    }
    /** Strict interior intersection; touching edges are allowed. Visual decoration may overlap freely. */
    public void validateActionAreas() {
        var targets=actionTargets();
        for(int i=0;i<targets.size();i++) for(int j=i+1;j<targets.size();j++) {
            var a=targets.get(i);var b=targets.get(j);
            // Match the editor's exact operation order, including decimal floating-point edges.
            if(Math.abs(a.x()-b.x())<(a.width()+b.width())/2
                    && Math.abs(a.y()-b.y())<(a.height()+b.height())/2)
                throw new IllegalArgumentException("Action areas overlap: "+a.id()+" and "+b.id());
        }
    }
    public static Map<String,Screen> demos() {
        var menu = new Screen("menu","TIDEFRAME",List.of(
            new Element("heading","TIDEFRAME",0,.7,1,.16,"label",""),
            new Element("profile","PROFILE",0,.35,.8,.18,"button","open:profile","rounded",true,null),
            new Element("popup","POPUP",0,.05,.8,.18,"button","open:popup"),
            new Element("overlay","OVERLAY",0,-.25,.8,.18,"button","open:overlay"),
            new Element("close","CLOSE",0,-.65,.8,.18,"button","close")));
        var map = new LinkedHashMap<String,Screen>(); map.put("menu",menu);
        for (String id : List.of("profile","popup","overlay")) map.put(id,new Screen(id,id.toUpperCase(Locale.ROOT),List.of(
            new Element("title",id.toUpperCase(Locale.ROOT),0,.5,1,.16,"label",""),
            new Element("details",id.equals("profile")?"YOUR PROFILE":id.equals("popup")?"WELCOME":"STATUS OVERLAY",0,.15,1.2,.16,"label","",id.equals("overlay")?"ellipse":"rectangle",true,null),
            id.equals("popup")?new Element("confirm","ACCESS",0,-.2,.8,.18,"button","conditional-message","rounded",true,
                new MessageCondition("tideframe.edit","Editor access granted.","Editor access denied.")):
                new Element("confirm","OK",0,-.2,.8,.18,"button","message:Selection received."),
            new Element("back","BACK",0,-.6,.8,.18,"button","open:menu"))));
        return Map.copyOf(map);
    }
}
