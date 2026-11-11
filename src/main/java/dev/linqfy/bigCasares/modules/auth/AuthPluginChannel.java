package dev.linqfy.bigCasares.modules.auth;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * {@code bigcasares:auth} plugin-message protocol so a client mod can log in silently with the player's OpenPGP
 * key. Every message starts with a type byte:
 * <ul>
 *   <li>S→C {@code 1 CHALLENGE}: UTF challenge, boolean registered. The mod signs the exact UTF-8 bytes
 *       (detached binary signature).</li>
 *   <li>C→S {@code 2 SIGNATURE}: unsigned short length + raw signature packet.</li>
 *   <li>C→S {@code 3 REGISTER_KEY}: int length + raw transferable public key, only for unregistered names; the
 *       server answers with a fresh CHALLENGE.</li>
 *   <li>S→C {@code 4 RESULT}: boolean success, UTF message.</li>
 * </ul>
 */
final class AuthPluginChannel implements PluginMessageListener {

    static final byte CHALLENGE = 1;
    static final byte SIGNATURE = 2;
    static final byte REGISTER_KEY = 3;
    static final byte RESULT = 4;

    private final AuthModule auth;

    AuthPluginChannel(AuthModule auth) {
        this.auth = auth;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!AuthModule.CHANNEL.equals(channel) || !auth.isPending(player) || message.length < 1) {
            return;
        }
        PlayerAuthState state = auth.state(player);
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            byte type = in.readByte();
            if (type == SIGNATURE) {
                int length = in.readUnsignedShort();
                if (length > OpenPgp.MAX_SIGNATURE_BYTES) {
                    return;
                }
                auth.verifyPgp(player, state, in.readNBytes(length));
            } else if (type == REGISTER_KEY && !state.registered() && auth.settings().pgpEnabled()) {
                int length = in.readInt();
                if (length < 0 || length > OpenPgp.MAX_KEY_RING_BYTES) {
                    return;
                }
                state.pendingRegistrationKey = OpenPgp.parseKeyRing(in.readNBytes(length));
                state.pgpChallenge = null;
                sendChallenge(auth, player, state);
            }
        } catch (IOException | OpenPgp.PgpException ex) {
            sendResult(auth, player, false, ex.getMessage() == null ? "invalid" : ex.getMessage());
        }
    }

    static void sendChallenge(AuthModule auth, Player player, PlayerAuthState state) {
        if (state == null || !auth.settings().pluginChannel() || !auth.settings().pgpEnabled()) {
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(CHALLENGE);
            out.writeUTF(auth.challengeFor(state));
            out.writeBoolean(state.registered());
        } catch (IOException ex) {
            return;
        }
        player.sendPluginMessage(auth.plugin(), AuthModule.CHANNEL, bytes.toByteArray());
    }

    static void sendResult(AuthModule auth, Player player, boolean success, String message) {
        if (!auth.settings().pluginChannel() || !player.getListeningPluginChannels().contains(AuthModule.CHANNEL)) {
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(RESULT);
            out.writeBoolean(success);
            out.writeUTF(message);
        } catch (IOException ex) {
            return;
        }
        player.sendPluginMessage(auth.plugin(), AuthModule.CHANNEL, bytes.toByteArray());
    }
}
