package dev.linqfy.bigCasares.modules.tideframe.engine;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ActionRouter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
class ActionRouterTest {
    @Test void revokedPermissionStillClosesAndReleasesTheSession() {
        var effects=new ArrayList<String>();
        ActionRouter.dispatch("close",false,() -> effects.add("closed"),x -> effects.add("open:"+x),effects::add);
        assertEquals(java.util.List.of("closed"),effects);
    }
    @Test void revokedPermissionDoesNotOpenOrSendMessages() {
        var effects=new ArrayList<String>();
        ActionRouter.dispatch("open:profile",false,() -> effects.add("closed"),effects::add,effects::add);
        ActionRouter.dispatch("message:hello",false,() -> effects.add("closed"),effects::add,effects::add);
        assertTrue(effects.isEmpty());
    }
    @Test void permittedActionsDispatchOnlyTheirOwnEffect() {
        var effects=new ArrayList<String>();
        ActionRouter.dispatch("open:profile",true,() -> effects.add("closed"),x -> effects.add("open:"+x),effects::add);
        ActionRouter.dispatch("message:hello",true,() -> effects.add("closed"),x -> effects.add("open:"+x),effects::add);
        assertEquals(java.util.List.of("open:profile","hello"),effects);
    }
    @Test void commandActionsRunWithoutTheirSlashAndOnlyWhenPermitted() {
        var effects=new ArrayList<String>();
        ActionRouter.dispatch("command:/spawn",true,() -> effects.add("closed"),effects::add,effects::add,x -> effects.add("cmd:"+x));
        ActionRouter.dispatch("command:warp shop {player}",true,() -> effects.add("closed"),effects::add,effects::add,x -> effects.add("cmd:"+x));
        ActionRouter.dispatch("command:/spawn",false,() -> effects.add("closed"),effects::add,effects::add,x -> effects.add("cmd:"+x));
        assertEquals(java.util.List.of("cmd:spawn","cmd:warp shop {player}"),effects);
    }
    @Test void screensAcceptCommandActionsButRejectMalformedOnes() {
        var ok=new dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen.Element("go","GO",0,0,.4,.2,"button","command:/warp shop");
        assertEquals("command:/warp shop",ok.action());
        for(String bad:new String[]{"command:","command:/","command:/Spawn","command:/x\ny","console:op"})
            assertThrows(IllegalArgumentException.class,()->new dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen.Element("go","GO",0,0,.4,.2,"button",bad),bad);
    }
}
