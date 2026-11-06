package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The "server" action: the proxy's own plugin message ("Connect" on the BungeeCord channel, which Velocity also
 * understands with its default bungee-plugin-message-channel). Without a proxy the client simply ignores it.
 */
final class ProxyConnect {
    private ProxyConnect() { }
    static final String CHANNEL = "BungeeCord";

    /** The message bytes: two Java modified-UTF strings, "Connect" and the server name. */
    static byte[] connect(String server) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { out.writeUTF("Connect"); out.writeUTF(server); }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return bytes.toByteArray();
    }
}
