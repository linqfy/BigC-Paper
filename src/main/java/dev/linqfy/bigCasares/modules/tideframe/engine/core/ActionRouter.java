package dev.linqfy.bigCasares.modules.tideframe.engine.core;
import java.util.function.Consumer;
public final class ActionRouter {
    private ActionRouter() {}
    public static void dispatch(String action,boolean permitted,Runnable close,Consumer<String> open,Consumer<String> message) {
        dispatch(action,permitted,close,open,message,command->{});
    }
    public static void dispatch(String action,boolean permitted,Runnable close,Consumer<String> open,Consumer<String> message,Consumer<String> command) {
        if(action.equals("close")){close.run();return;}
        if(!permitted)return;
        if(action.startsWith("open:"))open.accept(action.substring(5));
        else if(action.startsWith("message:"))message.accept(action.substring(8));
        else if(action.startsWith("command:")){
            String line=action.substring(8);
            command.accept(line.startsWith("/")?line.substring(1):line);
        }
    }
}
