package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real Bukkit events; proxy only supplies the player boundary without a login/server. */
class MenuMovementGuardTest {
    private static final World WORLD=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class[]{World.class},
        (proxy,method,args)->switch(method.getName()) {
            case "toString" -> "Movement test world";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy==args[0];
            default -> throw new AssertionError("Unexpected world operation: "+method.getName());
        });
    private static final class Fixture {
        final UUID id=UUID.randomUUID();
        final Map<UUID,String> menus=new HashMap<>();
        boolean permitted=true,dead,mounted;
        int closed;
        final Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},
            (proxy,method,args)->switch(method.getName()) {
                case "getUniqueId" -> id;
                case "hasPermission" -> permitted;
                case "isDead" -> dead;
                case "isInsideVehicle" -> mounted;
                case "toString" -> "Movement test boundary";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> throw new AssertionError("Unexpected player operation: "+method.getName());
            });
        final MenuMovementGuard guard=new MenuMovementGuard(menus,p->{closed++;menus.remove(id);});
        void open(){menus.put(id,"menu");}
    }
    private static Location pose(double x,double y,double z,float yaw,float pitch) {
        return new Location(WORLD,x,y,z,yaw,pitch);
    }
    @Test void walkingJumpingFallingAndTinyMovesFreezeXYZButKeepRequestedMouseAngles() {
        var f=new Fixture();f.open();
        for(var to:List.of(pose(10.3,64,20,30,15),pose(10,65.2,20,45,-20),
                pose(10,63,20,80,35),pose(10.000001,64,20,90,40))) {
            var from=pose(10,64,20,0,0);var event=new PlayerMoveEvent(f.player,from,to.clone());
            f.guard.move(event);
            assertTrue(event.isCancelled());assertSame(from,event.getFrom());
            assertEquals(10,from.getX());assertEquals(64,from.getY());assertEquals(20,from.getZ());
            assertEquals(to.getYaw(),from.getYaw());assertEquals(to.getPitch(),from.getPitch());
            assertEquals(to,event.getTo());assertEquals(0,f.closed);
        }
    }
    @Test void rotationOnlyAndNoDestinationAreUntouched() {
        var f=new Fixture();f.open();var to=pose(10,64,20,135,-35);
        var event=new PlayerMoveEvent(f.player,pose(10,64,20,0,0),to);f.guard.move(event);
        assertSame(to,event.getTo());assertFalse(event.isCancelled());
        var absent=new PlayerMoveEvent(f.player,pose(0,0,0,0,0),null);f.guard.move(absent);assertNull(absent.getTo());
    }
    @Test void noMenuIncludingHealthHudDoesNotRestrictMovement() {
        var f=new Fixture();var to=pose(11,65,21,30,15);
        var event=new PlayerMoveEvent(f.player,pose(10,64,20,0,0),to);f.guard.move(event);
        assertSame(to,event.getTo());assertFalse(event.isCancelled());assertEquals(0,f.closed);
    }
    @Test void removingMenuStateImmediatelyRestoresMovement() {
        var f=new Fixture();f.open();var from=pose(10,64,20,0,0);
        var locked=new PlayerMoveEvent(f.player,from,pose(11,64,20,0,0));f.guard.move(locked);
        assertTrue(locked.isCancelled());assertEquals(10,locked.getFrom().getX());f.menus.clear();
        var to=pose(11,64,20,0,0);var free=new PlayerMoveEvent(f.player,from,to);f.guard.move(free);
        assertSame(to,free.getTo());assertFalse(free.isCancelled());
    }
    @Test void cursorAndFallbackMenusShareTheSameLockState() {
        var f=new Fixture();
        for(String menu:List.of("cursor_menu","fallback_menu")) {
            f.menus.put(f.id,menu);
            var event=new PlayerMoveEvent(f.player,pose(10,64,20,0,0),pose(11,64,20,30,15));f.guard.move(event);
            assertTrue(event.isCancelled(),menu);assertEquals(10,event.getFrom().getX(),menu);
        }
    }
    @Test void serverCancellationBranchKeepsMenuOpenAndMouseAnglesAcrossRepeatedMoves() {
        var f=new Fixture();f.open();var accepted=pose(10,64,20,0,0);
        for(int i=1;i<=20;i++) {
            var requested=pose(11,65,21,i*10,i-10);var originalTo=requested.clone();
            var from=accepted.clone();var event=new PlayerMoveEvent(f.player,from,requested);
            f.guard.move(event);
            // Model ONLY the documented Paper decision branches, not a live network/client.
            // Paper 26.2 uses internalTeleport(from) on cancel; setTo changes trigger a PLUGIN teleport.
            if(event.isCancelled())accepted=from.clone();
            else if(!originalTo.equals(event.getTo())) {
                f.guard.teleport(new PlayerTeleportEvent(f.player,from,event.getTo(),PlayerTeleportEvent.TeleportCause.PLUGIN));
                accepted=event.getTo().clone();
            } else accepted=requested.clone();
            assertEquals(10,accepted.getX());assertEquals(64,accepted.getY());assertEquals(20,accepted.getZ());
            assertEquals(requested.getYaw(),accepted.getYaw());assertEquals(requested.getPitch(),accepted.getPitch());
            assertEquals(0,f.closed);assertFalse(f.menus.isEmpty());
        }
    }
    @Test void teleportIsNeverClampedAndAcceptedTeleportClosesMenu() {
        var f=new Fixture();f.open();var to=pose(100,90,-100,135,45);
        var event=new PlayerTeleportEvent(f.player,pose(10,64,20,0,0),to,PlayerTeleportEvent.TeleportCause.PLUGIN);
        f.guard.move(event);assertSame(to,event.getTo());assertEquals(0,f.closed);
        f.guard.teleport(event);assertSame(to,event.getTo());assertFalse(event.isCancelled());
        assertEquals(1,f.closed);assertTrue(f.menus.isEmpty());
    }
    @Test void alreadyCancelledMovementAndTeleportAreNotOverridden() {
        var f=new Fixture();f.open();var to=pose(11,64,20,30,15);
        var move=new PlayerMoveEvent(f.player,pose(10,64,20,0,0),to);move.setCancelled(true);f.guard.move(move);
        assertTrue(move.isCancelled());assertSame(to,move.getTo());
        var teleport=new PlayerTeleportEvent(f.player,pose(10,64,20,0,0),to);teleport.setCancelled(true);
        f.guard.teleport(teleport);assertEquals(0,f.closed);assertFalse(f.menus.isEmpty());
        assertEquals(0,move.getFrom().getYaw());assertEquals(0,move.getFrom().getPitch());
    }
    @Test void knockbackVelocityIsCancelledOnlyWhileMenuIsActive() {
        var f=new Fixture();f.open();var impulse=new Vector(1,.4,-1);
        var locked=new PlayerVelocityEvent(f.player,impulse.clone());f.guard.velocity(locked);
        assertTrue(locked.isCancelled());assertEquals(impulse,locked.getVelocity());
        f.menus.clear();var free=new PlayerVelocityEvent(f.player,impulse.clone());f.guard.velocity(free);
        assertFalse(free.isCancelled());assertEquals(impulse,free.getVelocity());
    }
    @Test void invalidPermissionDeathAndMountReleaseInsteadOfLeavingAStaleLock() {
        var f=new Fixture();
        for(int condition=0;condition<3;condition++) {
            f.open();f.permitted=condition!=0;f.dead=condition==1;f.mounted=condition==2;
            var to=pose(11,64,20,30,15);var event=new PlayerMoveEvent(f.player,pose(10,64,20,0,0),to);
            f.guard.move(event);assertSame(to,event.getTo());assertTrue(f.menus.isEmpty());
        }
        assertEquals(3,f.closed);
    }
    @Test void worldChangeRespawnDeathAndAcceptedMountClearTheMenuState() {
        var f=new Fixture();f.open();f.guard.worldChanged(new PlayerChangedWorldEvent(f.player,WORLD));
        f.open();f.guard.respawn(new PlayerRespawnEvent(f.player,pose(0,64,0,0,0),false));
        f.open();f.guard.death(new PlayerDeathEvent(f.player,null,List.of(),0,(String)null));
        f.open();f.guard.mount(new EntityMountEvent(f.player,f.player));
        assertEquals(4,f.closed);assertTrue(f.menus.isEmpty());
    }
}
