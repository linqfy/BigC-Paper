package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.Property;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.PropertyModifier;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The packetevents side of the mining prediction fix ({@link MiningPredictionSync}): sends a player's
 * {@code block_break_speed} property and, while {@link MiningFactors} holds a factor for them, adds our multiplier to
 * every outgoing {@code UPDATE_ATTRIBUTES} that carries that property (the server's own, or other plugins'), keeping
 * the sender's values. Packets only: the server's attribute is never modified.
 */
final class PacketEventsMining implements PacketListener, MiningPredictionSync.Sender {
    private static final ResourceLocation MODIFIER = new ResourceLocation(AttributeModifierMerge.ID);

    private final MiningFactors factors;
    private final BedrockPlayers bedrock;
    private final Logger logger;
    private final ClientVersion serverVersion;
    private volatile boolean errorLogged;

    private PacketEventsMining(MiningFactors factors, BedrockPlayers bedrock, Logger logger) {
        this.factors = factors;
        this.bedrock = bedrock;
        this.logger = logger;
        this.serverVersion = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    }

    static PacketEventsMining create(MiningFactors factors, BedrockPlayers bedrock, Logger logger) {
        return new PacketEventsMining(factors, bedrock, logger);
    }

    AutoCloseable register() {
        PacketListenerCommon registered = PacketEvents.getAPI().getEventManager()
            .registerListener(this, PacketListenerPriority.HIGHEST);
        return () -> PacketEvents.getAPI().getEventManager().unregisterListener(registered);
    }

    @Override
    public boolean applicable(Player player) {
        User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        return user != null && user.getClientVersion() == serverVersion
            && !bedrock.isBedrock(user.getChannel(), user.getUUID());
    }

    @Override
    public void refresh(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (instance == null) {
            return;
        }
        List<PropertyModifier> modifiers = new ArrayList<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            modifiers.add(new PropertyModifier(
                new ResourceLocation(modifier.getKey().getNamespace(), modifier.getKey().getKey()),
                modifier.getAmount(), operation(modifier.getOperation())));
        }
        Property property = new Property(Attributes.BLOCK_BREAK_SPEED, instance.getBaseValue(), modifiers);
        // The listener below adds the active factor when this goes out.
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
            new WrapperPlayServerUpdateAttributes(player.getEntityId(), List.of(property)));
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.UPDATE_ATTRIBUTES || event.isCancelled()) {
            return;
        }
        User user = event.getUser();
        UUID id = user.getUUID();
        MiningFactors.Active active = id == null ? null : factors.get(id);
        if (active == null) {
            return;
        }
        try {
            WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes(event);
            if (wrapper.getEntityId() != active.entityId()) {
                return;
            }
            boolean changed = false;
            for (Property property : wrapper.getProperties()) {
                if (isBreakSpeed(property)) {
                    apply(property, active.factor());
                    changed = true;
                }
            }
            if (changed) {
                event.markForReEncode(true);
            }
        } catch (RuntimeException failure) {
            event.markForReEncode(false);
            if (!errorLogged) {
                errorLogged = true;
                logger.log(Level.WARNING, "[AlexsCaves] Predicción de minado: falló un update_attributes "
                    + "(sale sin cambios)", failure);
            }
        }
    }

    static boolean isBreakSpeed(Property property) {
        return Attributes.BLOCK_BREAK_SPEED.equals(property.getAttribute())
            || Attributes.PLAYER_BLOCK_BREAK_SPEED.equals(property.getAttribute());
    }

    /** Keeps the property's modifiers and adds ours (factor limited to the attribute's range). */
    static void apply(Property property, double factor) {
        List<AttributeModifierMerge.Modifier> theirs = new ArrayList<>();
        for (PropertyModifier modifier : property.getModifiers()) {
            theirs.add(new AttributeModifierMerge.Modifier(modifier.getName().toString(), modifier.getAmount(),
                fromProtocol(modifier.getOperation())));
        }
        double value = AttributeModifierMerge.value(property.getValue(), AttributeModifierMerge.merge(theirs, null));
        double limited = MiningFactor.clamp(factor, value);
        property.removeModifier(MODIFIER);
        property.addModifier(new PropertyModifier(MODIFIER, limited - 1.0, PropertyModifier.Operation.MULTIPLY_TOTAL));
        property.setDirty();
    }

    private static AttributeModifierMerge.Operation fromProtocol(PropertyModifier.Operation operation) {
        return switch (operation) {
            case ADDITION -> AttributeModifierMerge.Operation.ADDITION;
            case MULTIPLY_BASE -> AttributeModifierMerge.Operation.MULTIPLY_BASE;
            case MULTIPLY_TOTAL -> AttributeModifierMerge.Operation.MULTIPLY_TOTAL;
        };
    }

    private static PropertyModifier.Operation operation(AttributeModifier.Operation operation) {
        return switch (operation) {
            case ADD_NUMBER -> PropertyModifier.Operation.ADDITION;
            case ADD_SCALAR -> PropertyModifier.Operation.MULTIPLY_BASE;
            case MULTIPLY_SCALAR_1 -> PropertyModifier.Operation.MULTIPLY_TOTAL;
        };
    }
}
