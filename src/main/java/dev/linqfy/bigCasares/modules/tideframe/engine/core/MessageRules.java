package dev.linqfy.bigCasares.modules.tideframe.engine.core;
import java.util.function.Predicate;
public final class MessageRules {
    private MessageRules(){}
    public static String action(Screen.Element element,Predicate<String> permissions){
        var rule=element.messageCondition();
        if(rule==null)return element.action();
        return "message:"+(permissions.test(rule.permission())?rule.ifTrue():rule.ifFalse());
    }
}
