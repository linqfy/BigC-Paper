package dev.linqfy.bigCasares.modules.tideframe.engine.edit;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import java.util.Arrays;
public final class InGameEditor {
    private InGameEditor() {}
    public static Screen apply(Screen screen,String[] args,PlaneProjection.Point point) {
        if(args.length<2)throw new IllegalArgumentException("Use edit create ID TEXT, edit move ID [X Y], or edit delete ID.");
        return switch(args[0]) {
            case "create" -> {if(args.length<3)throw new IllegalArgumentException("Text is required.");yield screen.add(new Screen.Element(args[1],String.join(" ",Arrays.copyOfRange(args,2,args.length)),point.x(),point.y(),.8,.18,"button","message:Selection received."));}
            case "move" -> {
                if(args.length!=2 && args.length!=4)throw new IllegalArgumentException("Use move ID [X Y].");
                double x=args.length==4?Double.parseDouble(args[2]):point.x(),y=args.length==4?Double.parseDouble(args[3]):point.y();yield screen.move(args[1],x,y);
            }
            case "delete" -> {if(args.length!=2)throw new IllegalArgumentException("Use delete ID.");yield screen.delete(args[1]);}
            default -> throw new IllegalArgumentException("Unknown edit operation.");
        };
    }
}
