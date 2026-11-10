package dev.linqfy.bigCasares.modules.customcrossbow;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Presents a spyglass to the shooter; the authoritative item is always the crossbow. */
final class NmsSniperScopeView implements SniperScopeView {
    private static final NamespacedKey PROXY = new NamespacedKey("bigcasares", "sniper_scope_proxy");
    private final JavaPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final AtomicBoolean failureReported = new AtomicBoolean();
    private ReflectionAccess access;

    NmsSniperScopeView(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    static boolean isProxy(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(PROXY, PersistentDataType.BYTE);
    }

    @Override
    public boolean show(Player player, EquipmentSlot hand) {
        if (sessions.containsKey(player.getUniqueId())) {
            return true;
        }
        Session session = null;
        try {
            if (access == null) {
                access = new ReflectionAccess();
            }
            ItemMeta original = player.getInventory().getItem(hand).getItemMeta();
            ItemStack proxy = new ItemStack(Material.SPYGLASS);
            ItemMeta meta = proxy.getItemMeta();
            meta.setItemModel(original.hasItemModel() ? original.getItemModel() : NamespacedKey.minecraft("crossbow"));
            if (original.hasDisplayName()) {
                meta.displayName(original.displayName());
            }
            if (original.hasLore()) {
                meta.lore(original.lore());
            }
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(PROXY, PersistentDataType.BYTE, (byte) 1);
            proxy.setItemMeta(meta);
            int slot = hand == EquipmentSlot.OFF_HAND ? 40 : player.getInventory().getHeldItemSlot();
            session = new Session(player, new SniperScopeInventory<>(slot, access.copyItem.invoke(null, proxy)));
            session.install();
            sessions.put(player.getUniqueId(), session);
            access.send(player, access.inventoryPacket.newInstance(slot, session.inventory.proxy()));
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (session != null) {
                session.remove();
            }
            sessions.remove(player.getUniqueId());
            report(failure);
            return false;
        }
    }

    @Override
    public void using(Player player, EquipmentSlot hand, boolean loading) {
        try {
            if (loading) {
                player.startUsingItem(hand);
                // Reset predicted main-hand use (e.g. a shield) before choosing the scoped hand.
                access.using(player, hand, false);
            }
            access.using(player, hand, loading);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            report(failure);
        }
    }

    @Override
    public boolean isUsing(Player player, EquipmentSlot hand) {
        return player.hasActiveItem() && player.getActiveItemHand() == hand;
    }

    @Override
    public void hide(Player player) {
        Session removed = sessions.remove(player.getUniqueId());
        if (removed == null) {
            return;
        }
        removed.remove();
        try {
            player.clearActiveItem();
            access.using(player, EquipmentSlot.HAND, false);
            // Read the current slot after cancellation, never a stale saved copy.
            Object actual = access.copyItem.invoke(null, player.getInventory().getItem(removed.inventory.slot()));
            access.send(player, access.inventoryPacket.newInstance(removed.inventory.slot(), actual));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            report(failure);
            player.updateInventory();
        }
        player.sendActionBar(Component.empty());
    }

    @Override
    public void charge(Player player, int hearts) {
        player.sendActionBar(Component.text("Francotirador: " + hearts + "/6 ♥ | Mantené para cargar · Soltá para disparar"));
    }

    private void report(Throwable failure) {
        if (failureReported.compareAndSet(false, true)) {
            plugin.getLogger().warning("No se pudo actualizar la mira de Francotirador: " + failure);
        }
    }

    private final class Session {
        private final SniperScopeInventory<Object> inventory;
        private final Object pipeline;
        private final String name;
        private volatile boolean active = true;

        Session(Player player, SniperScopeInventory<Object> inventory) throws ReflectiveOperationException {
            this.inventory = inventory;
            Object listener = access.playerConnection.get(access.playerHandle.invoke(player));
            Object connection = access.networkConnection.get(listener);
            Object channel = access.channel.get(connection);
            pipeline = access.channelType.getMethod("pipeline").invoke(channel);
            name = "bigcasares_sniper_" + player.getUniqueId();
        }

        void install() throws ReflectiveOperationException {
            Object handler = Proxy.newProxyInstance(access.outboundHandler.getClassLoader(),
                new Class<?>[]{access.outboundHandler}, (self, method, arguments) -> {
                    String operation = method.getName();
                    if (operation.equals("equals")) {
                        return self == arguments[0];
                    }
                    if (operation.equals("hashCode")) {
                        return System.identityHashCode(self);
                    }
                    if (operation.equals("toString")) {
                        return name;
                    }
                    if (operation.equals("handlerAdded") || operation.equals("handlerRemoved")) {
                        return null;
                    }
                    Object[] forwarded = Arrays.copyOfRange(arguments, 1, arguments.length);
                    if (operation.equals("write") && active) {
                        try {
                            forwarded[0] = access.present(arguments[1], inventory);
                        } catch (ReflectiveOperationException | RuntimeException failure) {
                            report(failure);
                        }
                    }
                    String target = operation.equals("exceptionCaught") ? "fireExceptionCaught" : operation;
                    Class<?>[] types = Arrays.copyOfRange(method.getParameterTypes(), 1, method.getParameterCount());
                    access.contextType.getMethod(target, types).invoke(arguments[0], forwarded);
                    return null;
                });
            access.pipelineType.getMethod("addBefore", String.class, String.class, access.handlerType)
                .invoke(pipeline, "packet_handler", name, handler);
        }

        void remove() {
            active = false;
            try {
                if (access.pipelineType.getMethod("get", String.class).invoke(pipeline, name) != null) {
                    access.pipelineType.getMethod("remove", String.class).invoke(pipeline, name);
                }
            } catch (ReflectiveOperationException | RuntimeException failure) {
                report(failure);
            }
        }
    }

    private static final class ReflectionAccess {
        private final Method playerHandle;
        private final Field playerConnection;
        private final Field networkConnection;
        private final Field channel;
        private final Method sendPacket;
        private final Method copyItem;
        private final Method entityData;
        private final Method dataGet;
        private final Object useFlags;
        private final Method dataValue;
        private final Constructor<?> metadataPacket;
        private final Constructor<?> inventoryPacket;
        private final Class<?> inventoryType;
        private final Class<?> slotType;
        private final Class<?> contentsType;
        private final Constructor<?> slotPacket;
        private final Constructor<?> contentsPacket;
        private final Class<?> outboundHandler;
        private final Class<?> handlerType;
        private final Class<?> contextType;
        private final Class<?> channelType;
        private final Class<?> pipelineType;

        ReflectionAccess() throws ReflectiveOperationException {
            Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> listener = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
            Class<?> item = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> accessor = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            Class<?> living = Class.forName("net.minecraft.world.entity.LivingEntity");
            playerHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
            playerConnection = serverPlayer.getField("connection");
            networkConnection = listener.getField("connection");
            channel = Class.forName("net.minecraft.network.Connection").getDeclaredField("channel");
            channel.setAccessible(true);
            sendPacket = listener.getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
            copyItem = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack").getMethod("asNMSCopy", ItemStack.class);
            entityData = living.getMethod("getEntityData");
            dataGet = Class.forName("net.minecraft.network.syncher.SynchedEntityData").getMethod("get", accessor);
            Field flags = living.getDeclaredField("DATA_LIVING_ENTITY_FLAGS");
            flags.setAccessible(true);
            useFlags = flags.get(null);
            dataValue = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue")
                .getMethod("create", accessor, Object.class);
            metadataPacket = packet("ClientboundSetEntityDataPacket").getConstructor(int.class, List.class);
            inventoryType = packet("ClientboundSetPlayerInventoryPacket");
            slotType = packet("ClientboundContainerSetSlotPacket");
            contentsType = packet("ClientboundContainerSetContentPacket");
            inventoryPacket = inventoryType.getConstructor(int.class, item);
            slotPacket = slotType.getConstructor(int.class, int.class, int.class, item);
            contentsPacket = contentsType.getConstructor(int.class, int.class, List.class, item);
            outboundHandler = Class.forName("io.netty.channel.ChannelOutboundHandler");
            handlerType = Class.forName("io.netty.channel.ChannelHandler");
            contextType = Class.forName("io.netty.channel.ChannelHandlerContext");
            channelType = Class.forName("io.netty.channel.Channel");
            pipelineType = Class.forName("io.netty.channel.ChannelPipeline");
        }

        private static Class<?> packet(String name) throws ClassNotFoundException {
            return Class.forName("net.minecraft.network.protocol.game." + name);
        }

        void send(Player player, Object packet) throws ReflectiveOperationException {
            sendPacket.invoke(playerConnection.get(playerHandle.invoke(player)), packet);
        }

        void using(Player player, EquipmentSlot hand, boolean using) throws ReflectiveOperationException {
            Object handle = playerHandle.invoke(player);
            byte flags = (Byte) dataGet.invoke(entityData.invoke(handle), useFlags);
            byte presented = (byte) ((flags & ~3) | (using ? 1 : 0)
                | (using && hand == EquipmentSlot.OFF_HAND ? 2 : 0));
            Object value = dataValue.invoke(null, useFlags, presented);
            send(player, metadataPacket.newInstance(player.getEntityId(), List.of(value)));
        }

        Object present(Object packet, SniperScopeInventory<Object> inventory) throws ReflectiveOperationException {
            if (inventoryType.isInstance(packet)) {
                int slot = (Integer) inventoryType.getMethod("slot").invoke(packet);
                Object original = inventoryType.getMethod("contents").invoke(packet);
                Object presented = inventory.playerSlot(slot, original);
                if (presented != original) {
                    return inventoryPacket.newInstance(slot, presented);
                }
            } else if (slotType.isInstance(packet)) {
                int container = (Integer) slotType.getMethod("getContainerId").invoke(packet);
                int slot = (Integer) slotType.getMethod("getSlot").invoke(packet);
                Object original = slotType.getMethod("getItem").invoke(packet);
                Object presented = inventory.containerSlot(container, slot, original);
                if (presented != original) {
                    return slotPacket.newInstance(container, slotType.getMethod("getStateId").invoke(packet), slot, presented);
                }
            } else if (contentsType.isInstance(packet)) {
                int container = (Integer) contentsType.getMethod("containerId").invoke(packet);
                @SuppressWarnings("unchecked")
                List<Object> items = (List<Object>) contentsType.getMethod("items").invoke(packet);
                List<Object> presented = inventory.containerContents(container, items);
                if (presented != items) {
                    return contentsPacket.newInstance(container, contentsType.getMethod("stateId").invoke(packet),
                        presented, contentsType.getMethod("carriedItem").invoke(packet));
                }
            }
            return packet;
        }
    }
}
