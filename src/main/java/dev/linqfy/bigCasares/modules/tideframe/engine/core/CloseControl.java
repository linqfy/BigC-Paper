package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.Objects;

/**
 * Immutable runtime preparation. Never overwrites the player's source JSON.
 * Minimal legacy menus retain schema 3: their X is appended with default style/layer 0.
 * Legacy menus already containing 24 elements need explicit 1024x576 quality to publish
 * the extra element. Every quality shares the same centred HUD frame (see HudAssets).
 */
public final class CloseControl {
    public static final String RESERVED_ID="tideframe_close";
    private CloseControl() { }
    public static Screen prepare(Screen screen) {
        Objects.requireNonNull(screen,"Screen must be specified");
        if(screen.presentation().mode().equals("overlay"))return screen;
        var reserved=screen.elements().stream().filter(e->e.id().equals(RESERVED_ID)).findFirst();
        if(reserved.isPresent() && !isClose(reserved.get()))throw new IllegalArgumentException("Reserved close control id: "+RESERVED_ID);
        if(screen.elements().stream().anyMatch(CloseControl::isClose))return screen;
        if(screen.elements().size()>=128)throw new IllegalArgumentException("Close control requires a free element slot (maximum128)");
        boolean legacy=screen.quality().preset().equals("legacy");
        boolean preserveLegacy=legacy && screen.elements().size()<24;
        var quality=legacy && !preserveLegacy?RenderQuality.custom(1024,576):screen.quality();
        var style=preserveLegacy?Screen.Style.defaults():new Screen.Style(1,"#173b48","#8bdfc9","#edf8f4",false);
        var close=new Screen.Element(RESERVED_ID,"X",.84,.79,.12,.16,"button","close","rectangle",true,null,true,null,style,preserveLegacy?0:128,null);
        var prepared=screen.withQuality(quality).add(close);
        if(legacy) {
            // v1-v3 allowed pre-existing overlaps. Preserve their hit priority,
            // but never introduce an overlap between the new X and an old button.
            for(var element:screen.elements())if(element.kind().equals("button"))
                new Screen(screen.id(),screen.title(),java.util.List.of(element,close),quality,screen.presentation()).validateActionAreas();
        } else prepared.validateActionAreas();
        return prepared;
    }
    private static boolean isClose(Screen.Element element) {
        return element.kind().equals("button") && element.text().equals("X") && element.action().equals("close");
    }
}
