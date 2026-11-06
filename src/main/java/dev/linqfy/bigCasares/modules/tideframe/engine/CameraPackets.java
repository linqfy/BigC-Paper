package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

/**
 * Original protocol adapter, deliberately gated to the inspected Paper/Folia 26.2 bindings.
 * A remote camera suppresses vanilla's ordinary movement packets. A client-only passenger
 * still sends rotation packets; the separate virtual seat keeps its local feet at the server
 * position. No real passenger relationship or world entity is created.
 */
public final class CameraPackets {
    private static volatile Bindings bindings;
    private CameraPackets() { }

    /** Resolves and exercises packet constructors without a player, world, or network connection. */
    public static synchronized void initialize(String minecraftVersion) {
        bindings=null;models=null;modelFailure="the fixed camera is unavailable";
        // 26.2 and later 26.x releases: the self-test below verifies every binding (real packets through a real
        // Netty pipeline) before anything is used, so a release that changed them is refused here, not in game.
        if(!supportedVersion(minecraftVersion))
            throw new IllegalStateException("The fixed-camera adapter requires Minecraft 26.2 or a later 26.x release (found "+minecraftVersion+").");
        try {
            var candidate=new Bindings();candidate.selfTest();bindings=candidate;
        } catch(ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("The Minecraft 26.2 fixed-camera packet bindings are unavailable.",failure);
        }
        // The Mannequin is optional: if its bindings fail, the camera still works and only the model is off.
        try {var candidate=new ModelBindings();candidate.selfTest(bindings);models=candidate;modelFailure=null;}
        catch(ReflectiveOperationException | LinkageError | RuntimeException failure) {modelFailure=String.valueOf(failure);}
    }
    public static boolean available() {return bindings!=null;}

    // ---- Mannequin proof of concept (experimental.mannequin-test) ----
    private static volatile ModelBindings models;
    private static volatile String modelFailure="not initialized";
    /** True when a client-only Mannequin can be shown with the fixed camera on this server. */
    public static boolean modelsAvailable() {return bindings!=null && models!=null;}
    /** Why {@link #modelsAvailable()} is false, or null. */
    public static String modelFailure() {return modelFailure;}
    /**
     * Server thread: builds a real Mannequin and its spawn, turn and removal packets in {@code world} (nothing is added
     * to the world or sent). Null when it works; otherwise the reason, and models stay off.
     */
    public static synchronized String probeModel(org.bukkit.World world) {
        var camera=bindings;var model=models;
        if(camera==null||model==null)return modelFailure;
        try {model.probe(camera,camera.worldHandle.invoke(world));return null;}
        catch(ReflectiveOperationException | LinkageError | RuntimeException failure) {
            models=null;modelFailure="the Mannequin probe failed ("+failure+")";return modelFailure;
        }
    }
    /**
     * A client-only player model centred in front of the fixed camera ({@link dev.linqfy.bigCasares.modules.tideframe.engine.core.ModelPlacement}).
     * {@code profile}: null shows the player's own profile; a name lets the client look that skin up itself.
     */
    public record Model(String profile,boolean spin) {}
    /** The camera transport, plus the model that travels in its packets (spawned with it, removed with it). */
    public interface ModelChannel extends CameraRig.Transport {
        /** Null when the model is in the packets; otherwise why it was left out (the camera opens anyway). */
        String problem();
        /** Network-only: turns the shown model to {@code yaw}. No-op without a model. */
        void turn(float yaw) throws Exception;
    }
    /** 26.2, 26.2.x and later 26.x versions; the packet self-test decides whether a later one really works. */
    static boolean supportedVersion(String version) {
        if(version==null)return false;
        var m=java.util.regex.Pattern.compile("26\\.(\\d+)(\\.\\d+)?").matcher(version);
        return m.matches() && Integer.parseInt(m.group(1))>=2;
    }
    /** True only on the exact release the bindings were written against (later ones passed the self-test). */
    static boolean inspectedVersion(String version) {return version!=null && version.matches("26\\.2(\\.\\d+)?");}
    /**
     * Netty-thread input callbacks; each MUST only schedule work on the player's owning thread.
     * {@code key} receives "Q", "F" or "L" when the player presses drop, swap hands or advancements.
     */
    public record InputCallbacks(Runnable leftClick,Runnable rightClick,java.util.function.Consumer<String> key) {
        public InputCallbacks {
            Objects.requireNonNull(leftClick);Objects.requireNonNull(rightClick);Objects.requireNonNull(key);
        }
    }
    /** True when the rotation fast path passed its self-test on this server. */
    public static boolean rotationFastPath() {var current=bindings;return current!=null && current.input.rotationFastPath();}
    /** True when the Q, F and L key packets passed their self-test on this server. */
    public static boolean keysAvailable() {var current=bindings;return current!=null && current.input.keysAvailable();}

    /** Called on the Netty event loop with the raw rotation of an incoming move packet. Must not touch Bukkit state. */
    @FunctionalInterface public interface RotationListener { void accept(float yaw,float pitch); }

    /** Network-only cleanup. No checked exceptions or Bukkit access during shutdown. */
    public interface InputLease extends AutoCloseable { @Override void close(); }

    /**
     * Install BEFORE opening the synthetic camera, on the player's owning thread.
     * The callback runs on Netty: it MUST only dispatch work to the player's owning scheduler.
     * Close AFTER queueing camera restoration. Only this player's self-target packets are consumed.
     */
    public static InputLease installInput(Player player,Runnable dispatchClick) {return installInput(player,dispatchClick,null);}
    public static InputLease installInput(Player player,Runnable dispatchClick,RotationListener rotation) {
        Objects.requireNonNull(dispatchClick);
        return installInput(player,new InputCallbacks(dispatchClick,()->{},key->{}),rotation);
    }
    public static InputLease installInput(Player player,InputCallbacks callbacks,RotationListener rotation) {
        var current=bindings;
        if(current==null)throw new IllegalStateException("The fixed-camera adapter is unavailable on this server.");
        Objects.requireNonNull(player);Objects.requireNonNull(callbacks);
        try {
            Object real=current.playerHandle.invoke(player);
            Object connection=current.network.get(current.listener.get(real));
            return current.input.install(connection,((Number)current.id.invoke(real)).intValue(),callbacks,rotation);
        } catch(ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Could not install fixed-camera input; no camera packets were sent.",failure);
        }
    }

    /** Call only on the player's owning thread. Nothing is spawned into the server world. */
    public static CameraRig.Transport channel(Player player) {return channel(player,null);}
    /**
     * Like {@link #channel(Player)}; with a {@code model}, a client-only Mannequin is spawned in the same opening bundle
     * and removed by the same closing one. A model that cannot be shown (bindings, a wall in the way) is left out and
     * reported by {@link ModelChannel#problem()}: the camera never fails because of it.
     */
    public static ModelChannel channel(Player player,Model model) {
        var current=bindings;
        if(current==null)throw new IllegalStateException("The fixed-camera adapter is unavailable on this server.");
        Objects.requireNonNull(player);
        if(player.isInsideVehicle())throw new IllegalStateException("Leave your vehicle before opening a menu.");
        if(player.getPose()!=Pose.STANDING)
            throw new IllegalStateException("Stand upright before opening a fixed-camera menu.");
        if(player.getGameMode()==GameMode.SPECTATOR)
            throw new IllegalStateException("Fixed-camera menus are unavailable in spectator mode.");
        try {return current.channel(player,model,models);}
        catch(ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Could not prepare the fixed camera; no camera packets were sent.",failure);
        }
    }

    /**
     * The optional Mannequin (26.2's player-shaped entity: any profile's skin, skin layers, main hand). Built in memory
     * like the camera's armour stands and sent by packets only; it never joins the world.
     */
    private static final class ModelBindings {
        final Class<?> mannequin=Bindings.type("net.minecraft.world.entity.decoration.Mannequin");
        final Class<?> living=Bindings.type("net.minecraft.world.entity.LivingEntity");
        final Class<?> avatar=Bindings.type("net.minecraft.world.entity.Avatar");
        final Class<?> profileType=Bindings.type("net.minecraft.world.item.component.ResolvableProfile");
        final Class<?> accessor=Bindings.type("net.minecraft.network.syncher.EntityDataAccessor");
        final Class<?> synched=Bindings.type("net.minecraft.network.syncher.SynchedEntityData");
        final Object mannequinType=Bindings.type("net.minecraft.world.entity.EntityTypes").getField("MANNEQUIN").get(null);
        final Constructor<?> create=mannequin.getConstructor(Bindings.type("net.minecraft.world.entity.EntityType"),
            Bindings.type("net.minecraft.world.level.Level"));
        final Method resolved=profileType.getMethod("createResolved",Bindings.type("com.mojang.authlib.GameProfile"));
        final Method unresolved=profileType.getMethod("createUnresolved",String.class);
        final Method setProfile=mannequin.getMethod("setProfile",profileType);
        final Method hideDescription=mannequin.getMethod("setHideDescription",boolean.class);
        final Method immovable=mannequin.getMethod("setImmovable",boolean.class);
        final Method gameProfile=Bindings.type("net.minecraft.world.entity.player.Player").getMethod("getGameProfile");
        final Object layers=avatar.getField("DATA_PLAYER_MODE_CUSTOMISATION").get(null);
        final Method dataGet=synched.getMethod("get",accessor),dataSet=synched.getMethod("set",accessor,Object.class);
        final Method mainArm=living.getMethod("getMainArm");
        final Method setMainArm=avatar.getMethod("setMainArm",Bindings.type("net.minecraft.world.entity.HumanoidArm"));
        final Method headYaw=living.getMethod("setYHeadRot",float.class),bodyYaw=living.getMethod("setYBodyRot",float.class);
        final Constructor<?> rotation=Bindings.type("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot")
            .getConstructor(int.class,byte.class,byte.class,boolean.class);
        final Constructor<?> head=Bindings.type("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket")
            .getConstructor(Bindings.type("net.minecraft.world.entity.Entity"),byte.class);

        ModelBindings() throws ReflectiveOperationException { }
        void selfTest(Bindings camera) throws ReflectiveOperationException {
            if(camera==null)throw new ReflectiveOperationException("the fixed camera is unavailable");
            Bindings.require(camera.entityType.isInstance(mannequinType),"mannequin type");
            Bindings.require(accessor.isInstance(layers),"skin layers accessor");
            Bindings.require(profileType.isInstance(unresolved.invoke(null,"Tideframe")),"unresolved profile");
            Object turn=rotation.newInstance(77,angle(90),(byte)0,false);
            Bindings.require(camera.packet.isInstance(camera.bundle.newInstance(List.of(turn))),"rotation packet");
        }
        /** Builds a Mannequin in {@code world} (never added to it) and every packet it needs, without a player or a network. */
        void probe(Bindings b,Object world) throws ReflectiveOperationException {
            var spot=new dev.linqfy.bigCasares.modules.tideframe.engine.core.ModelPlacement.Spot(.5,320,.5,90);
            Object value=create.newInstance(mannequinType,world);
            b.setPos.invoke(value,spot.x(),spot.y(),spot.z());b.setNoGravity.invoke(value,true);
            setProfile.invoke(value,unresolved.invoke(null,"Tideframe"));hideDescription.invoke(value,true);immovable.invoke(value,true);
            dataSet.invoke(b.entityData.invoke(value),layers,(byte)0x7F);
            var packets=spawnPackets(b,value,spot);
            Bindings.require(packets.size()==2 && packets.stream().allMatch(b.packet::isInstance),"mannequin spawn packets");
            Bindings.require(b.packet.isInstance(turnPacket(b,value,45)),"mannequin turn packet");
            Bindings.require(b.packet.isInstance(b.remove.newInstance((Object)new int[]{((Number)b.id.invoke(value)).intValue()})),"mannequin removal");
        }
        /** The Mannequin at {@code spot}: the player's (or {@code model.profile()}'s) skin, the player's layers and hand. */
        Object mannequin(Bindings b,Object world,Object player,Model model,dev.linqfy.bigCasares.modules.tideframe.engine.core.ModelPlacement.Spot spot) throws ReflectiveOperationException {
            Object value=create.newInstance(mannequinType,world);
            b.setPos.invoke(value,spot.x(),spot.y(),spot.z());
            b.setYaw.invoke(value,spot.yaw());b.setPitch.invoke(value,0f);
            headYaw.invoke(value,spot.yaw());bodyYaw.invoke(value,spot.yaw());
            b.setNoGravity.invoke(value,true);
            setProfile.invoke(value,model.profile()==null?resolved.invoke(null,gameProfile.invoke(player)):unresolved.invoke(null,model.profile()));
            // Without this the client draws the default description tag under the model.
            hideDescription.invoke(value,true);immovable.invoke(value,true);
            dataSet.invoke(b.entityData.invoke(value),layers,dataGet.invoke(b.entityData.invoke(player),layers));
            setMainArm.invoke(value,mainArm.invoke(player));
            return value;
        }
        List<Object> spawnPackets(Bindings b,Object value,dev.linqfy.bigCasares.modules.tideframe.engine.core.ModelPlacement.Spot spot) throws ReflectiveOperationException {
            Object spawn=b.add.newInstance(b.id.invoke(value),b.uuid.invoke(value),spot.x(),spot.y(),spot.z(),
                0f,spot.yaw(),b.getType.invoke(value),0,b.zero,(double)spot.yaw());
            return List.of(spawn,b.data(value));
        }
        /** Head and body to {@code yaw}, in one bundle. */
        Object turnPacket(Bindings b,Object value,float yaw) throws ReflectiveOperationException {
            headYaw.invoke(value,yaw);
            int entityId=((Number)b.id.invoke(value)).intValue();
            return b.bundle.newInstance(List.of(rotation.newInstance(entityId,angle(yaw),(byte)0,false),head.newInstance(value,angle(yaw))));
        }
        /** The protocol's angle byte (256 steps per turn). */
        static byte angle(float degrees) {return (byte)Math.floor(degrees*256f/360f);}
    }

    private static final class Bindings {
        final CameraInputPackets input=new CameraInputPackets();
        final Class<?> entity=type("net.minecraft.world.entity.Entity");
        final Class<?> level=type("net.minecraft.world.level.Level");
        final Class<?> entityType=type("net.minecraft.world.entity.EntityType");
        final Class<?> vector=type("net.minecraft.world.phys.Vec3");
        final Class<?> packet=type("net.minecraft.network.protocol.Packet");
        final Class<?> bufferType=type("net.minecraft.network.FriendlyByteBuf");
        final Class<?> byteBuffer=type("io.netty.buffer.ByteBuf");
        final Class<?> stand=type("net.minecraft.world.entity.decoration.ArmorStand");
        final Class<?> addType=type("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
        final Class<?> passengerType=type("net.minecraft.network.protocol.game.ClientboundSetPassengersPacket");
        final Class<?> cameraType=type("net.minecraft.network.protocol.game.ClientboundSetCameraPacket");
        final Constructor<?> armorStand=stand.getConstructor(level,double.class,double.class,double.class);
        final Constructor<?> add=addType.getConstructor(int.class,UUID.class,double.class,double.class,double.class,
            float.class,float.class,entityType,int.class,vector,double.class);
        final Constructor<?> metadata=type("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
            .getConstructor(int.class,List.class);
        final Constructor<?> camera=cameraType.getConstructor(entity);
        final Constructor<?> cameraRaw=privateConstructor(cameraType,bufferType);
        final Constructor<?> passengers=privateConstructor(passengerType,bufferType);
        final Constructor<?> remove=type("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket")
            .getConstructor(int[].class);
        final Constructor<?> bundle=type("net.minecraft.network.protocol.game.ClientboundBundlePacket")
            .getConstructor(Iterable.class);
        final Constructor<?> buffer=bufferType.getConstructor(byteBuffer);
        final Method allocate=type("io.netty.buffer.Unpooled").getMethod("buffer");
        final Method writeInt=bufferType.getMethod("writeVarInt",int.class);
        final Method writeInts=bufferType.getMethod("writeVarIntArray",int[].class);
        final Method release=byteBuffer.getMethod("release");
        final Method setPos=entity.getMethod("setPos",double.class,double.class,double.class);
        final Method setYaw=entity.getMethod("setYRot",float.class),setPitch=entity.getMethod("setXRot",float.class);
        final Method setInvisible=stand.getMethod("setInvisible",boolean.class);
        final Method setMarker=stand.getMethod("setMarker",boolean.class);
        final Method setNoGravity=entity.getMethod("setNoGravity",boolean.class);
        final Method eyeHeight=entity.getMethod("getEyeHeight");
        final Method id=entity.getMethod("getId"),uuid=entity.getMethod("getUUID"),getType=entity.getMethod("getType");
        final Method entityData=entity.getMethod("getEntityData");
        final Method nonDefaults=type("net.minecraft.network.syncher.SynchedEntityData").getMethod("getNonDefaultValues");
        final Method ridingPosition=entity.getMethod("getPassengerRidingPosition",entity);
        final Method attachmentPoint=entity.getMethod("getVehicleAttachmentPoint",entity);
        final Field x=vector.getField("x"),y=vector.getField("y"),z=vector.getField("z");
        final Method playerHandle=type("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
        final Method worldHandle=type("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
        final Field listener=type("net.minecraft.server.level.ServerPlayer").getField("connection");
        final Field network=type("net.minecraft.server.network.ServerCommonPacketListenerImpl").getField("connection");
        final Method send=type("net.minecraft.network.Connection").getMethod("send",packet);
        final Object zero=vector.getField("ZERO").get(null);
        final Object armorType=type("net.minecraft.world.entity.EntityTypes").getField("ARMOR_STAND").get(null);
        final Field cameraId=privateField(cameraType,"cameraId");

        Bindings() throws ReflectiveOperationException { }
        private static Class<?> type(String name) throws ClassNotFoundException {
            return Class.forName(name,true,CameraPackets.class.getClassLoader());
        }
        private static Constructor<?> privateConstructor(Class<?> owner,Class<?> argument) throws NoSuchMethodException {
            var constructor=owner.getDeclaredConstructor(argument);constructor.setAccessible(true);return constructor;
        }
        private static Field privateField(Class<?> owner,String name) throws NoSuchFieldException {
            var field=owner.getDeclaredField(name);field.setAccessible(true);return field;
        }
        private Object passengerPacket(int vehicle,int[] riders) throws ReflectiveOperationException {
            Object bytes=allocate.invoke(null);
            try {
                Object friendly=buffer.newInstance(bytes);writeInt.invoke(friendly,vehicle);writeInts.invoke(friendly,(Object)riders);
                return passengers.newInstance(friendly);
            } finally {release.invoke(bytes);}
        }
        private Object cameraPacket(int target) throws ReflectiveOperationException {
            Object bytes=allocate.invoke(null);
            try {
                Object friendly=buffer.newInstance(bytes);writeInt.invoke(friendly,target);return cameraRaw.newInstance(friendly);
            } finally {release.invoke(bytes);}
        }
        void selfTest() throws ReflectiveOperationException {
            UUID probeId=UUID.fromString("9e5b9913-2494-4f82-8ec9-ded80eae7777");
            Object spawn=add.newInstance(77,probeId,1.25,2.5,-3.75,12f,34f,armorType,0,zero,34d);
            require(((Number)addType.getMethod("getId").invoke(spawn)).intValue()==77,"spawn id");
            require(probeId.equals(addType.getMethod("getUUID").invoke(spawn)),"spawn uuid");
            require(((Number)addType.getMethod("getX").invoke(spawn)).doubleValue()==1.25,"spawn x");
            require(((Number)addType.getMethod("getY").invoke(spawn)).doubleValue()==2.5,"spawn y");
            require(((Number)addType.getMethod("getZ").invoke(spawn)).doubleValue()==-3.75,"spawn z");
            require(Math.abs(((Number)addType.getMethod("getXRot").invoke(spawn)).floatValue()-12)<1.407,"spawn pitch");
            require(Math.abs(((Number)addType.getMethod("getYRot").invoke(spawn)).floatValue()-34)<1.407,"spawn yaw");
            Object mount=passengerPacket(77,new int[]{123});
            require(((Number)passengerType.getMethod("getVehicle").invoke(mount)).intValue()==77,"vehicle id");
            require(Arrays.equals((int[])passengerType.getMethod("getPassengers").invoke(mount),new int[]{123}),"passenger ids");
            Object restore=cameraPacket(123);require(cameraId.getInt(restore)==123,"restore camera id");
            Object despawn=remove.newInstance((Object)new int[]{77,78});
            Object meta=metadata.newInstance(77,List.of());
            require(packet.isInstance(bundle.newInstance(List.of(spawn,meta,mount,restore,despawn))),"bundle packet");
            input.selfTest();
            input.rotationSelfTest();
            input.keySelfTest();
        }
        private static void require(boolean condition,String binding) throws ReflectiveOperationException {
            if(!condition)throw new ReflectiveOperationException("Fixed-camera binding self-test failed: "+binding);
        }
        private Object stand(Object world,Location at) throws ReflectiveOperationException {
            Object value=armorStand.newInstance(world,at.getX(),at.getY(),at.getZ());
            setInvisible.invoke(value,true);setMarker.invoke(value,true);setNoGravity.invoke(value,true);
            setYaw.invoke(value,at.getYaw());setPitch.invoke(value,at.getPitch());return value;
        }
        private double[] riderFeet(Object vehicle,Object player) throws ReflectiveOperationException {
            Object seat=ridingPosition.invoke(vehicle,player),attachment=attachmentPoint.invoke(player,vehicle);
            return new double[]{x.getDouble(seat)-x.getDouble(attachment),y.getDouble(seat)-y.getDouble(attachment),
                z.getDouble(seat)-z.getDouble(attachment)};
        }
        private Object spawn(Object value,Location at) throws ReflectiveOperationException {
            return add.newInstance(id.invoke(value),uuid.invoke(value),at.getX(),at.getY(),at.getZ(),
                at.getPitch(),at.getYaw(),getType.invoke(value),0,zero,(double)at.getYaw());
        }
        private Object data(Object value) throws ReflectiveOperationException {
            Object values=nonDefaults.invoke(entityData.invoke(value));
            if(values==null)throw new ReflectiveOperationException("Virtual camera metadata was unexpectedly empty.");
            return metadata.newInstance(id.invoke(value),List.copyOf((List<?>)values));
        }
        ModelChannel channel(Player player,Model model,ModelBindings modelBindings) throws ReflectiveOperationException {
            Location feet=player.getLocation(),eye=player.getEyeLocation();feet.checkFinite();eye.checkFinite();
            Object real=playerHandle.invoke(player),world=worldHandle.invoke(player.getWorld());
            Object vehicle=stand(world,feet),cameraEntity=stand(world,eye);
            // Match the standing client's attachment geometry before creating any packets.
            double[] projected=riderFeet(vehicle,real);
            Location seat=feet.clone().add(feet.getX()-projected[0],feet.getY()-projected[1],feet.getZ()-projected[2]);
            setPos.invoke(vehicle,seat.getX(),seat.getY(),seat.getZ());
            double[] aligned=riderFeet(vehicle,real);
            require(Math.abs(aligned[0]-feet.getX())<1e-6 && Math.abs(aligned[1]-feet.getY())<1e-6
                && Math.abs(aligned[2]-feet.getZ())<1e-6,"virtual passenger position");
            Location cameraAt=eye.clone().subtract(0,((Number)eyeHeight.invoke(cameraEntity)).doubleValue(),0);
            setPos.invoke(cameraEntity,cameraAt.getX(),cameraAt.getY(),cameraAt.getZ());
            int vehicleId=((Number)id.invoke(vehicle)).intValue(),cameraEntityId=((Number)id.invoke(cameraEntity)).intValue();
            int playerId=((Number)id.invoke(real)).intValue();
            List<Object> openingPackets=new ArrayList<>(List.of(spawn(vehicle,seat),data(vehicle),spawn(cameraEntity,cameraAt),
                data(cameraEntity),passengerPacket(vehicleId,new int[]{playerId}),camera.newInstance(cameraEntity)));
            int[] removed={vehicleId,cameraEntityId};
            // The optional model: spawned before the camera switch, removed with the camera's own entities.
            Object shown=null;String problem=null;
            if(model!=null) {
                if(modelBindings==null)problem="the Mannequin bindings are unavailable on this server ("+modelFailure+")";
                else {
                    var spot=dev.linqfy.bigCasares.modules.tideframe.engine.core.ModelPlacement.centered(eye.getX(),eye.getY(),eye.getZ(),eye.getYaw(),eye.getPitch(),
                        dev.linqfy.bigCasares.modules.tideframe.engine.core.ModelPlacement.DISTANCE);
                    var blocks=player.getWorld();
                    if(!dev.linqfy.bigCasares.modules.tideframe.engine.core.ModelPlacement.clear(eye.getX(),eye.getY(),eye.getZ(),spot,(bx,by,bz)->!blocks.getBlockAt(bx,by,bz).isPassable()))
                        problem="blocked";
                    else {
                        try {
                            shown=modelBindings.mannequin(this,world,real,model,spot);
                            openingPackets.addAll(openingPackets.size()-1,modelBindings.spawnPackets(this,shown,spot));
                            removed=new int[]{vehicleId,cameraEntityId,((Number)id.invoke(shown)).intValue()};
                        } catch(ReflectiveOperationException | LinkageError | RuntimeException failure) {
                            shown=null;problem="the Mannequin could not be built ("+failure+")";
                        }
                    }
                }
            }
            Object opening=bundle.newInstance(List.copyOf(openingPackets));
            Object closing=bundle.newInstance(List.of(camera.newInstance(real),passengerPacket(vehicleId,new int[0]),
                remove.newInstance((Object)removed)));
            Object connection=network.get(listener.get(real));
            Object figure=shown;String reason=problem;
            // Connection.send queues writes on Netty's event loop. Both bundles contain only copied
            // ids/metadata/coordinates; shutdown restoration never reads a player or a world.
            return new ModelChannel() {
                public void open() throws ReflectiveOperationException {send.invoke(connection,opening);}
                public void close() throws ReflectiveOperationException {send.invoke(connection,closing);}
                public String problem() {return model==null?null:reason;}
                public void turn(float yaw) throws ReflectiveOperationException {
                    if(figure!=null)send.invoke(connection,modelBindings.turnPacket(Bindings.this,figure,yaw));
                }
            };
        }
    }
}
