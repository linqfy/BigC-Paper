package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Original, version-gated Netty adapter. No additional dependency or injected server entity. */
final class CameraInputPackets {
    private final Class<?> channel=type("io.netty.channel.Channel");
    private final Class<?> pipeline=type("io.netty.channel.ChannelPipeline");
    private final Class<?> handler=type("io.netty.channel.ChannelHandler");
    private final Class<?> inbound=type("io.netty.channel.ChannelInboundHandler");
    private final Class<?> context=type("io.netty.channel.ChannelHandlerContext");
    private final Class<?> attack=type("net.minecraft.network.protocol.game.ServerboundAttackPacket");
    private final Class<?> interact=type("net.minecraft.network.protocol.game.ServerboundInteractPacket");
    private final Field connectionChannel=type("net.minecraft.network.Connection").getField("channel");
    private final Method channelPipeline=channel.getMethod("pipeline"),eventLoop=channel.getMethod("eventLoop");
    private final Method findContext=pipeline.getMethod("context",handler);
    private final Method addBefore=pipeline.getMethod("addBefore",String.class,String.class,handler);
    private final Method remove=pipeline.getMethod("remove",handler),contextName=context.getMethod("name");
    private final Method attackId=attack.getMethod("entityId"),interactId=interact.getMethod("entityId");
    // Optional: right-click hand and key packets (Q, F, L). Clicks and the camera work without them.
    private final Method interactHand=optionalMethod(interact,"hand");
    private final Class<?> playerAction=optionalType("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket");
    private final Method playerActionType=optionalMethod(playerAction,"getAction");
    private final Class<?> seenAdvancements=optionalType("net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket");
    private final Method seenAdvancementsAction=optionalMethod(seenAdvancements,"getAction");
    private final Map<String,Method> forward=new HashMap<>();
    // Optional: rotation fast path. The camera still works if these bindings change.
    private final Class<?> move=optionalType("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket");
    private final Method hasRotation=optionalMethod(move,"hasRotation"),yRot=optionalMethod(move,"getYRot",float.class),xRot=optionalMethod(move,"getXRot",float.class);
    private record ConnectionKey(Object channel,int playerId) { }
    private final ConcurrentMap<ConnectionKey,Hook> hooks=new ConcurrentHashMap<>();

    CameraInputPackets() throws ReflectiveOperationException {
        for(String name:List.of("channelRegistered","channelUnregistered","channelActive","channelInactive",
                "channelReadComplete","channelWritabilityChanged"))
            forward.put(name,context.getMethod("fire"+Character.toUpperCase(name.charAt(0))+name.substring(1)));
        forward.put("channelRead",context.getMethod("fireChannelRead",Object.class));
        forward.put("userEventTriggered",context.getMethod("fireUserEventTriggered",Object.class));
        forward.put("exceptionCaught",context.getMethod("fireExceptionCaught",Throwable.class));
    }
    private static Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName(name,false,CameraInputPackets.class.getClassLoader());
    }
    private static Class<?> optionalType(String name) {
        try {return type(name);} catch(ClassNotFoundException | LinkageError absent) {return null;}
    }
    private static Method optionalMethod(Class<?> owner,String name,Class<?>... parameters) {
        try {return owner==null?null:owner.getMethod(name,parameters);} catch(NoSuchMethodException | LinkageError absent) {return null;}
    }
    private volatile boolean rotationVerified,verifyingRotation;
    boolean rotationFastPath() {return rotationVerified && hasRotation!=null && yRot!=null && xRot!=null;}
    private volatile boolean keysVerified,verifyingKeys;
    /** True when Q, F and L passed their self-test (real packets through the real handler). */
    boolean keysAvailable() {return keysVerified;}

    CameraPackets.InputLease install(Object connection,int playerId,Runnable click) throws ReflectiveOperationException {
        return installOnChannel(connectionChannel.get(connection),connection,playerId,click,null);
    }
    CameraPackets.InputLease install(Object connection,int playerId,Runnable click,CameraPackets.RotationListener rotation)
            throws ReflectiveOperationException {
        return installOnChannel(connectionChannel.get(connection),connection,playerId,new CameraPackets.InputCallbacks(click,()->{},key->{}),rotation);
    }
    CameraPackets.InputLease install(Object connection,int playerId,CameraPackets.InputCallbacks callbacks,CameraPackets.RotationListener rotation)
            throws ReflectiveOperationException {
        return installOnChannel(connectionChannel.get(connection),connection,playerId,callbacks,rotation);
    }

    private CameraPackets.InputLease installOnChannel(Object channelValue,Object before,int playerId,Runnable click)
            throws ReflectiveOperationException {
        return installOnChannel(channelValue,before,playerId,new CameraPackets.InputCallbacks(click,()->{},key->{}),null);
    }
    private CameraPackets.InputLease installOnChannel(Object channelValue,Object before,int playerId,Runnable click,
            CameraPackets.RotationListener rotation) throws ReflectiveOperationException {
        return installOnChannel(channelValue,before,playerId,new CameraPackets.InputCallbacks(click,()->{},key->{}),rotation);
    }
    private CameraPackets.InputLease installOnChannel(Object channelValue,Object before,int playerId,CameraPackets.InputCallbacks click,
            CameraPackets.RotationListener rotation) throws ReflectiveOperationException {
        Objects.requireNonNull(channelValue,"The player's network channel is not initialized.");
        Objects.requireNonNull(click);
        Object pipe=channelPipeline.invoke(channelValue),base=findContext.invoke(pipe,before);
        if(base==null)throw new ReflectiveOperationException("The active connection handler is missing from its pipeline.");
        var key=new ConnectionKey(channelValue,playerId);
        Hook previous=hooks.get(key);
        if(previous!=null && previous.gate.isActive())
            throw new ReflectiveOperationException("Another fixed-camera input lease is still active.");
        var installed=new Hook(key,pipe,click,previous,rotation);
        boolean reserved=previous==null?hooks.putIfAbsent(key,installed)==null:hooks.replace(key,previous,installed);
        if(!reserved)
            throw new ReflectiveOperationException("Another fixed-camera input lease was installed concurrently.");
        try {
            // Netty's pipeline mutation is thread-safe. Registration is synchronous, with no wait.
            // Add before the restoring guard BEFORE removing it: reopening has no unguarded gap.
            Object anchor=previous==null?null:findContext.invoke(pipe,previous.filtering);
            addBefore.invoke(pipe,contextName.invoke(anchor==null?base:anchor),"tideframe_input_"+UUID.randomUUID(),installed.filtering);
            if(findContext.invoke(pipe,installed.filtering)==null)
                throw new ReflectiveOperationException("The fixed-camera input handler was not installed.");
            // ADD_PENDING can already have a context off-loop. The new handler
            // retires the old guard from handlerAdded, after Netty activates it.
        } catch(ReflectiveOperationException | RuntimeException failure) {
            try {installed.dispose();}
            catch(RuntimeException cleanup) {failure.addSuppressed(cleanup);}
            if(previous!=null && previous.gate.isRestoring())hooks.putIfAbsent(key,previous);
            throw failure;
        }
        return installed;
    }

    private final class Hook implements CameraPackets.InputLease {
        final ConnectionKey key;
        final Object pipe,filtering;
        final CameraInputGate gate;
        final AtomicBoolean requested=new AtomicBoolean();
        volatile ScheduledFuture<?> removalTask;
        Hook(ConnectionKey key,Object pipe,CameraPackets.InputCallbacks click,Hook previous,CameraPackets.RotationListener rotation) {
            this.key=key;this.pipe=pipe;
            gate=new CameraInputGate(key.playerId(),click.leftClick(),click.rightClick(),System::nanoTime);
            filtering=inboundHandler(gate,this::detached,()->{if(previous!=null)previous.dispose();},rotation,click.key());
        }
        void detached() {
            gate.close();hooks.remove(key,this);
            ScheduledFuture<?> task=removalTask;if(task!=null)task.cancel(false);
        }
        void dispose() {
            detached();
            try {if(findContext.invoke(pipe,filtering)!=null)remove.invoke(pipe,filtering);}
            catch(ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not remove the fixed-camera input handler.",failure);
            }
        }
        @Override public void close() {
            if(!requested.compareAndSet(false,true))return;
            // Stop clicks immediately, but cover self packets already in flight from the old camera.
            gate.beginRestoration();
            try {
                removalTask=((ScheduledExecutorService)eventLoop.invoke(key.channel()))
                    .schedule(this::dispose,CameraInputGate.RESTORATION_NANOS,TimeUnit.NANOSECONDS);
            } catch(ReflectiveOperationException | RejectedExecutionException failure) {
                dispose();
            }
        }
    }

    private Object inboundHandler(CameraInputGate gate) {
        return inboundHandler(gate,()->{});
    }
    private Object inboundHandler(CameraInputGate gate,Runnable detached) {
        return inboundHandler(gate,detached,()->{});
    }
    private Object inboundHandler(CameraInputGate gate,Runnable detached,Runnable added) {
        return inboundHandler(gate,detached,added,null);
    }
    private Object inboundHandler(CameraInputGate gate,Runnable detached,Runnable added,CameraPackets.RotationListener rotation) {
        return inboundHandler(gate,detached,added,rotation,key->{});
    }
    private Object inboundHandler(CameraInputGate gate,Runnable detached,Runnable added,CameraPackets.RotationListener rotation,
            java.util.function.Consumer<String> keys) {
        var fastPath=new java.util.concurrent.atomic.AtomicBoolean(rotation!=null && (rotationFastPath() || verifyingRotation));
        var keyPath=new java.util.concurrent.atomic.AtomicBoolean(keysVerified || verifyingKeys);
        var lastAdvancements=new java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE);
        return Proxy.newProxyInstance(inbound.getClassLoader(),new Class<?>[]{inbound},(proxy,method,args) -> {
            String name=method.getName();
            if(method.getDeclaringClass()==Object.class)return switch(name) {
                case "toString" -> "TideframeSelfTargetInput";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy==args[0];
                default -> throw new UnsupportedOperationException(name);
            };
            if(name.equals("handlerAdded")){added.run();return null;}
            if(name.equals("handlerRemoved")){gate.close();detached.run();return null;}
            if(name.equals("channelInactive")){gate.close();detached.run();}
            if(name.equals("channelRead")) {
                Object message=args[1];
                CameraInputGate.Kind kind=CameraInputGate.Kind.OTHER;int target=0;
                if(attack.isInstance(message)){kind=CameraInputGate.Kind.ATTACK;target=(int)attackId.invoke(message);}
                else if(interact.isInstance(message)){
                    target=(int)interactId.invoke(message);
                    boolean offHand=false;
                    try {offHand=interactHand!=null && String.valueOf(interactHand.invoke(message)).equals("OFF_HAND");}
                    catch(Throwable ignored) { }
                    kind=offHand?CameraInputGate.Kind.INTERACT_OFFHAND:CameraInputGate.Kind.INTERACT;
                }
                if(gate.consume(kind,target))return null;
                // Q, F and L are observed, never consumed: the server still handles (and plugins may cancel) them.
                if(keyPath.get() && gate.isActive()) {
                    try {
                        if(playerAction!=null && playerActionType!=null && playerAction.isInstance(message)) {
                            String action=String.valueOf(playerActionType.invoke(message));
                            if(action.equals("DROP_ITEM") || action.equals("DROP_ALL_ITEMS"))keys.accept("Q");
                            else if(action.equals("SWAP_ITEM_WITH_OFFHAND"))keys.accept("F");
                        } else if(seenAdvancements!=null && seenAdvancementsAction!=null && seenAdvancements.isInstance(message)
                                && String.valueOf(seenAdvancementsAction.invoke(message)).equals("OPENED_TAB")) {
                            long now=System.nanoTime(),last=lastAdvancements.get();
                            // Opening the screen selects a tab; switching tabs inside it must not repeat L.
                            if(last==Long.MIN_VALUE || now-last>=1_000_000_000L){lastAdvancements.set(now);keys.accept("L");}
                        }
                    } catch(Throwable failure) {keyPath.set(false);} // Keys stop; clicks and the camera keep working.
                }
                // Rotation is observed, never consumed: the server still applies it on its own tick.
                if(fastPath.get() && gate.isActive() && move.isInstance(message)) {
                    try {
                        if((boolean)hasRotation.invoke(message))
                            rotation.accept((float)yRot.invoke(message,0f),(float)xRot.invoke(message,0f));
                    } catch(Throwable failure) {fastPath.set(false);} // Fall back to tick sampling.
                }
            }
            Method next=forward.get(name);
            if(next==null)throw new UnsupportedOperationException("Unrecognized inbound event: "+name);
            try {
                if(next.getParameterCount()==0)next.invoke(args[0]);else next.invoke(args[0],args[1]);
            } catch(InvocationTargetException failure) {throw failure.getCause();}
            return null;
        });
    }

    /** Real 26.2 packets and Netty EmbeddedChannel, without a player or a world. */
    void selfTest() throws ReflectiveOperationException {
        Class<?> embedded=type("io.netty.channel.embedded.EmbeddedChannel");
        Object base=inboundHandler(new CameraInputGate(Integer.MIN_VALUE,()->{},System::nanoTime));
        Object handlers=Array.newInstance(handler,1);Array.set(handlers,0,base);
        Object probe=embedded.getConstructor(handlers.getClass()).newInstance(handlers);
        Method write=embedded.getMethod("writeInbound",Object[].class),read=embedded.getMethod("readInbound");
        Method runPending=embedded.getMethod("runPendingTasks");
        Method runScheduled=embedded.getMethod("runScheduledPendingTasks");
        Method advance=embedded.getMethod("advanceTimeBy",long.class,java.util.concurrent.TimeUnit.class);
        embedded.getMethod("freezeTime").invoke(probe);
        var clicks=new java.util.concurrent.atomic.AtomicInteger();
        var rightClicks=new java.util.concurrent.atomic.AtomicInteger();
        CameraPackets.InputLease lease=null;
        try {
            lease=installOnChannel(probe,base,123,new CameraPackets.InputCallbacks(clicks::incrementAndGet,rightClicks::incrementAndGet,key->{}),null);
            Object selfAttack=attack.getConstructor(int.class).newInstance(123);
            write.invoke(probe,(Object)new Object[]{selfAttack});
            require(read.invoke(probe)==null && clicks.get()==1,"self attack consumed and dispatched");
            Object otherAttack=attack.getConstructor(int.class).newInstance(124);
            write.invoke(probe,(Object)new Object[]{otherAttack});
            require(read.invoke(probe)==otherAttack,"other entity attack passed unchanged");
            Class<?> hand=type("net.minecraft.world.InteractionHand"),vec=type("net.minecraft.world.phys.Vec3");
            Object selfInteract=interact.getConstructor(int.class,hand,vec,boolean.class)
                .newInstance(123,hand.getField("MAIN_HAND").get(null),vec.getField("ZERO").get(null),false);
            write.invoke(probe,(Object)new Object[]{selfInteract});
            require(read.invoke(probe)==null && clicks.get()==1 && rightClicks.get()==1,"right click consumed and dispatched without left click");
            Object offInteract=interact.getConstructor(int.class,hand,vec,boolean.class)
                .newInstance(123,hand.getField("OFF_HAND").get(null),vec.getField("ZERO").get(null),false);
            write.invoke(probe,(Object)new Object[]{offInteract});
            require(read.invoke(probe)==null && clicks.get()==1 && rightClicks.get()==1,"off-hand echo consumed without a click");
            Object keepalive=new Object();write.invoke(probe,(Object)new Object[]{keepalive});
            require(read.invoke(probe)==keepalive,"unrelated packet passed unchanged");
            lease.close();lease.close();runPending.invoke(probe);
            write.invoke(probe,(Object)new Object[]{selfAttack});
            require(read.invoke(probe)==null && clicks.get()==1,"late self attack consumed without callback while restoring");
            write.invoke(probe,(Object)new Object[]{otherAttack});
            require(read.invoke(probe)==otherAttack,"restoring lease preserves other entity protection");
            CameraPackets.InputLease previous=lease;
            lease=installOnChannel(probe,base,123,clicks::incrementAndGet);
            write.invoke(probe,(Object)new Object[]{selfAttack});
            require(read.invoke(probe)==null && clicks.get()==2,"immediate reopening dispatches through new lease");
            previous.close();advance.invoke(probe,1500L,java.util.concurrent.TimeUnit.MILLISECONDS);
            runScheduled.invoke(probe);runPending.invoke(probe);
            // Also cover a timer already executing when its cancellation raced with reopening.
            ((Hook)previous).dispose();
            write.invoke(probe,(Object)new Object[]{selfAttack});
            require(read.invoke(probe)==null,"old removal timer cannot delete reopened handler");
            lease.close();advance.invoke(probe,1500L,java.util.concurrent.TimeUnit.MILLISECONDS);
            runScheduled.invoke(probe);runPending.invoke(probe);
            write.invoke(probe,(Object)new Object[]{selfAttack});
            require(read.invoke(probe)==selfAttack,"expired restoration lease restored server protection");
        } finally {
            if(lease!=null)lease.close();runPending.invoke(probe);
            embedded.getMethod("finishAndReleaseAll").invoke(probe);
        }
    }
    /** Real 26.2 rotation packet through the real handler. Failure only disables the fast path. */
    void rotationSelfTest() {
        rotationVerified=false;
        if(hasRotation==null || yRot==null || xRot==null) return;
        Object probe=null;
        try {
            Class<?> embedded=type("io.netty.channel.embedded.EmbeddedChannel");
            Object base=inboundHandler(new CameraInputGate(Integer.MIN_VALUE,()->{},System::nanoTime));
            Object handlers=Array.newInstance(handler,1);Array.set(handlers,0,base);
            probe=embedded.getConstructor(handlers.getClass()).newInstance(handlers);
            Method write=embedded.getMethod("writeInbound",Object[].class),read=embedded.getMethod("readInbound");
            float[] seen={Float.NaN,Float.NaN};
            verifyingRotation=true;
            CameraPackets.InputLease lease;
            try {lease=installOnChannel(probe,base,123,()->{},(yaw,pitch)->{seen[0]=yaw;seen[1]=pitch;});}
            finally {verifyingRotation=false;}
            Object rot=type("net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Rot")
                .getConstructor(float.class,float.class,boolean.class,boolean.class).newInstance(33.5f,-12.25f,true,false);
            write.invoke(probe,(Object)new Object[]{rot});
            boolean passed=read.invoke(probe)==rot;
            ((Hook)lease).dispose();
            rotationVerified=passed && seen[0]==33.5f && seen[1]==-12.25f;
        } catch(ReflectiveOperationException | RuntimeException | LinkageError failure) {
            rotationVerified=false;
        } finally {
            try {if(probe!=null)probe.getClass().getMethod("finishAndReleaseAll").invoke(probe);}
            catch(ReflectiveOperationException | RuntimeException ignored) { }
        }
    }
    /**
     * Real drop (Q), swap-hands (F) and advancements (L) packets through the real handler: each is seen as its key
     * and still passed on to the server, and a second advancements packet right away is not a second L. Failure only
     * turns the keys off; clicks and the camera keep working.
     */
    void keySelfTest() {
        keysVerified=false;
        if(playerAction==null || playerActionType==null || seenAdvancements==null || seenAdvancementsAction==null) return;
        Object probe=null;
        try {
            Class<?> embedded=type("io.netty.channel.embedded.EmbeddedChannel");
            Object base=inboundHandler(new CameraInputGate(Integer.MIN_VALUE,()->{},System::nanoTime));
            Object handlers=Array.newInstance(handler,1);Array.set(handlers,0,base);
            probe=embedded.getConstructor(handlers.getClass()).newInstance(handlers);
            Method write=embedded.getMethod("writeInbound",Object[].class),read=embedded.getMethod("readInbound");
            var keys=new java.util.concurrent.CopyOnWriteArrayList<String>();
            verifyingKeys=true;
            CameraPackets.InputLease lease;
            try {lease=installOnChannel(probe,base,123,new CameraPackets.InputCallbacks(()->{},()->{},keys::add),null);}
            finally {verifyingKeys=false;}
            Class<?> actions=type("net.minecraft.network.protocol.game.ServerboundPlayerActionPacket$Action");
            Class<?> blockPos=type("net.minecraft.core.BlockPos"),direction=type("net.minecraft.core.Direction");
            var make=playerAction.getConstructor(actions,blockPos,direction);
            Object origin=blockPos.getField("ZERO").get(null),down=direction.getField("DOWN").get(null);
            boolean passed=true;
            for(String action:new String[]{"DROP_ITEM","DROP_ALL_ITEMS","SWAP_ITEM_WITH_OFFHAND"}) {
                Object packetValue=make.newInstance(actions.getField(action).get(null),origin,down);
                write.invoke(probe,(Object)new Object[]{packetValue});
                passed&=read.invoke(probe)==packetValue;
            }
            Class<?> seenActions=type("net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket$Action");
            Class<?> identifier=seenAdvancements.getConstructors()[0].getParameterTypes()[1];
            Object opened=seenActions.getField("OPENED_TAB").get(null);
            for(int i=0;i<2;i++) {
                Object packetValue=seenAdvancements.getConstructor(seenActions,identifier).newInstance(opened,null);
                write.invoke(probe,(Object)new Object[]{packetValue});
                passed&=read.invoke(probe)==packetValue;
            }
            ((Hook)lease).dispose();
            keysVerified=passed && keys.equals(List.of("Q","Q","F","L"));
        } catch(ReflectiveOperationException | RuntimeException | LinkageError failure) {
            keysVerified=false;
        } finally {
            try {if(probe!=null)probe.getClass().getMethod("finishAndReleaseAll").invoke(probe);}
            catch(ReflectiveOperationException | RuntimeException ignored) { }
        }
    }
    private static void require(boolean condition,String binding) throws ReflectiveOperationException {
        if(!condition)throw new ReflectiveOperationException("Fixed-camera input self-test failed: "+binding);
    }
}
