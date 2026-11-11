package dev.linqfy.bigCasares.modules.auth.premium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plays the vanilla client side of the 1.20.5+ login + configuration protocol against the verifier. */
final class PremiumVerifierServerTest {

    private static final UUID PREMIUM_ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private final AtomicReference<String> joinedHash = new AtomicReference<>();
    private PremiumVerifierServer server;

    @AfterEach
    void stop() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void verifiesWithMojangThenStoresCookieAndTransfersBack() throws Exception {
        byte[] cookie = {9, 8, 7};
        start((profile, address, host) -> PremiumVerifierServer.Decision.accept(cookie, host, 25565));

        try (Client client = new Client(server.localPort())) {
            client.loginAndAuthenticate("Notch", joinedHash);

            InputStream success = client.read();
            assertEquals(0x02, MinecraftWire.readVarInt(success));
            assertEquals(PREMIUM_ID, MinecraftWire.readUuid(success));
            assertEquals("Notch", MinecraftWire.readString(success, 16));
            assertEquals(1, MinecraftWire.readVarInt(success));
            assertEquals("textures", MinecraftWire.readString(success, 64));
            assertEquals("dGV4dHVyZXM=", MinecraftWire.readString(success, 64));
            assertEquals(1, success.read());
            assertEquals("c2ln", MinecraftWire.readString(success, 64));

            client.send(packet(0x03));

            InputStream store = client.read();
            assertEquals(0x0A, MinecraftWire.readVarInt(store));
            assertEquals("bigcasares:premium", MinecraftWire.readString(store, 64));
            assertArrayEquals(cookie, MinecraftWire.readByteArray(store, 64));

            InputStream transfer = client.read();
            assertEquals(0x0B, MinecraftWire.readVarInt(transfer));
            assertEquals("play.example.com", MinecraftWire.readString(transfer, 255));
            assertEquals(25565, MinecraftWire.readVarInt(transfer));
        }
    }

    @Test
    void disconnectsWhenMojangDoesNotConfirmTheJoin() throws Exception {
        joinedHash.set("never-matches");
        start((profile, address, host) -> PremiumVerifierServer.Decision.accept(new byte[0], host, 1));

        try (Client client = new Client(server.localPort())) {
            client.loginAndAuthenticate("Notch", null);

            InputStream disconnect = client.read();
            assertEquals(0x00, MinecraftWire.readVarInt(disconnect));
            assertTrue(MinecraftWire.readString(disconnect, 2048).contains("premium"));
        }
    }

    @Test
    void relaysVerdictRejection() throws Exception {
        start((profile, address, host) -> PremiumVerifierServer.Decision.reject("sin modo premium"));

        try (Client client = new Client(server.localPort())) {
            client.loginAndAuthenticate("Notch", joinedHash);

            InputStream disconnect = client.read();
            assertEquals(0x00, MinecraftWire.readVarInt(disconnect));
            assertEquals("{\"text\":\"sin modo premium\"}", MinecraftWire.readString(disconnect, 2048));
        }
    }

    @Test
    void serverHashMatchesMojangReferenceValues() {
        // Reference digests from wiki.vg for SHA-1 of the bare names, which exercise the signed-hex format.
        assertEquals("4ed1f46bbe04bc756bcb17c0c7ce3e4632f06a48", hashOfName("Notch"));
        assertEquals("-7c9d5b0044c130109a5d7b5fb5c317c02b4e28c1", hashOfName("jeb_"));
        assertEquals("88e16a1019277b15d58faf0541e11910eb756f6", hashOfName("simon"));
    }

    private static String hashOfName(String name) {
        return MinecraftWire.serverHash(name, new byte[0], new byte[0]);
    }

    private void start(PremiumVerifierServer.Verdict verdict) throws Exception {
        MojangSessionClient sessions = (username, serverHash) -> serverHash.equals(joinedHash.get())
            ? Optional.of(new PremiumProfile(PREMIUM_ID, username,
                List.of(new PremiumProfile.Property("textures", "dGV4dHVyZXM=", "c2ln"))))
            : Optional.empty();
        server = new PremiumVerifierServer(new InetSocketAddress("127.0.0.1", 0), sessions, verdict,
            Logger.getLogger("test"));
        server.start();
    }

    private static ByteArrayOutputStream packet(int id) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MinecraftWire.writeVarInt(out, id);
        return out;
    }

    private static final class Client implements AutoCloseable {
        private final Socket socket;
        private InputStream in;
        private OutputStream out;

        Client(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(5_000);
            in = socket.getInputStream();
            out = socket.getOutputStream();
        }

        /** Handshake, login start, encryption; records the hash a real client would send to Mojang. */
        void loginAndAuthenticate(String name, AtomicReference<String> joined) throws Exception {
            ByteArrayOutputStream handshake = packet(0x00);
            MinecraftWire.writeVarInt(handshake, 775);
            MinecraftWire.writeString(handshake, "play.example.com");
            handshake.write(0x63);
            handshake.write(0xDF);
            MinecraftWire.writeVarInt(handshake, 3);
            send(handshake);
            ByteArrayOutputStream start = packet(0x00);
            MinecraftWire.writeString(start, name);
            MinecraftWire.writeUuid(start, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
            send(start);

            InputStream request = read();
            assertEquals(0x01, MinecraftWire.readVarInt(request));
            String serverId = MinecraftWire.readString(request, 20);
            byte[] publicKeyBytes = MinecraftWire.readByteArray(request, 1024);
            byte[] token = MinecraftWire.readByteArray(request, 16);
            assertEquals(1, request.read(), "shouldAuthenticate");

            byte[] secret = new byte[16];
            new SecureRandom().nextBytes(secret);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            rsa.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedSecret = rsa.doFinal(secret);
            rsa.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedToken = rsa.doFinal(token);
            if (joined != null) {
                joined.set(MinecraftWire.serverHash(serverId, secret, publicKeyBytes));
            }
            ByteArrayOutputStream response = packet(0x01);
            MinecraftWire.writeByteArray(response, encryptedSecret);
            MinecraftWire.writeByteArray(response, encryptedToken);
            send(response);

            SecretKeySpec key = new SecretKeySpec(secret, "AES");
            Cipher decrypt = Cipher.getInstance("AES/CFB8/NoPadding");
            decrypt.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(secret));
            Cipher encrypt = Cipher.getInstance("AES/CFB8/NoPadding");
            encrypt.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(secret));
            in = MinecraftWire.decrypting(in, decrypt);
            out = MinecraftWire.encrypting(out, encrypt);
        }

        void send(ByteArrayOutputStream body) throws Exception {
            MinecraftWire.writeFrame(out, body.toByteArray());
        }

        InputStream read() throws Exception {
            return new ByteArrayInputStream(MinecraftWire.readFrame(in));
        }

        @Override
        public void close() throws Exception {
            socket.close();
        }
    }
}
