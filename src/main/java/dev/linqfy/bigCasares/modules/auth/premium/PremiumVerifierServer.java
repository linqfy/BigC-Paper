package dev.linqfy.bigCasares.modules.auth.premium;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A tiny online-mode Java Edition login endpoint. An offline-mode Paper server cannot ask Mojang to authenticate
 * one specific player, so premium accounts are transferred here: this endpoint performs the encryption handshake,
 * lets the vanilla client prove account ownership to Mojang's session server, stores a signed ticket as a cookie
 * and transfers the client straight back. Packet ids follow the 1.20.5+ login and configuration protocol.
 */
public final class PremiumVerifierServer implements AutoCloseable {

    public static final String COOKIE_KEY = "bigcasares:premium";
    static final int LOGIN_DISCONNECT = 0x00;
    static final int LOGIN_ENCRYPTION_REQUEST = 0x01;
    static final int LOGIN_SUCCESS = 0x02;
    static final int LOGIN_ACKNOWLEDGED = 0x03;
    static final int CONFIG_STORE_COOKIE = 0x0A;
    static final int CONFIG_TRANSFER = 0x0B;
    private static final int MAX_CONNECTIONS = 32;
    private static final int MAX_ATTEMPTS_PER_MINUTE = 10;

    private final InetSocketAddress bindAddress;
    private final MojangSessionClient sessions;
    private final Verdict verdict;
    private final Logger logger;
    private final SecureRandom random = new SecureRandom();
    private final Semaphore connections = new Semaphore(MAX_CONNECTIONS);
    private final Map<InetAddress, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final KeyPair keyPair;
    private volatile ServerSocket socket;
    private volatile boolean closed;

    public PremiumVerifierServer(InetSocketAddress bindAddress, MojangSessionClient sessions, Verdict verdict,
                                 Logger logger) {
        this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.verdict = Objects.requireNonNull(verdict, "verdict");
        this.logger = Objects.requireNonNull(logger, "logger");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024, random);
            this.keyPair = generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("RSA unavailable", ex);
        }
    }

    public void start() throws IOException {
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(bindAddress, 64);
        this.socket = server;
        Thread acceptor = new Thread(this::acceptLoop, "BigCasares-premium-verifier");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public int localPort() {
        ServerSocket server = socket;
        return server == null ? -1 : server.getLocalPort();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        ServerSocket server = socket;
        if (server != null) {
            server.close();
        }
    }

    private void acceptLoop() {
        ServerSocket server = socket;
        while (!closed) {
            Socket client;
            try {
                client = server.accept();
            } catch (IOException ex) {
                if (!closed) {
                    logger.log(Level.WARNING, "Premium verifier stopped accepting connections", ex);
                }
                return;
            }
            if (!connections.tryAcquire() || !allowAttempt(client.getInetAddress())) {
                closeQuietly(client);
                continue;
            }
            Thread.ofVirtual().name("BigCasares-premium-login").start(() -> {
                try (client) {
                    handle(client);
                } catch (IOException | GeneralSecurityException | InterruptedException ex) {
                    if (ex instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    logger.fine("Premium verification aborted: " + ex);
                } catch (RuntimeException ex) {
                    logger.log(Level.WARNING, "Premium verification failed unexpectedly", ex);
                } finally {
                    connections.release();
                }
            });
        }
    }

    private boolean allowAttempt(InetAddress address) {
        long now = System.currentTimeMillis();
        Deque<Long> recent = attempts.computeIfAbsent(address, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && now - recent.peekFirst() > 60_000L) {
                recent.pollFirst();
            }
            if (recent.size() >= MAX_ATTEMPTS_PER_MINUTE) {
                return false;
            }
            recent.addLast(now);
        }
        if (attempts.size() > 4096) {
            attempts.clear();
        }
        return true;
    }

    private void handle(Socket client) throws IOException, GeneralSecurityException, InterruptedException {
        client.setSoTimeout(15_000);
        client.setTcpNoDelay(true);
        InputStream in = new BufferedInputStream(client.getInputStream());
        OutputStream out = client.getOutputStream();

        InputStream handshake = new ByteArrayInputStream(MinecraftWire.readFrame(in));
        if (MinecraftWire.readVarInt(handshake) != 0x00) {
            return;
        }
        MinecraftWire.readVarInt(handshake);
        String handshakeHost = MinecraftWire.readString(handshake, 255);
        MinecraftWire.readUnsignedShort(handshake);
        int intent = MinecraftWire.readVarInt(handshake);
        if (intent != 2 && intent != 3) {
            return;
        }

        InputStream loginStart = new ByteArrayInputStream(MinecraftWire.readFrame(in));
        if (MinecraftWire.readVarInt(loginStart) != 0x00) {
            return;
        }
        String requestedName = MinecraftWire.readString(loginStart, 16);

        byte[] verifyToken = new byte[4];
        random.nextBytes(verifyToken);
        byte[] publicKey = keyPair.getPublic().getEncoded();
        ByteArrayOutputStream request = packet(LOGIN_ENCRYPTION_REQUEST);
        MinecraftWire.writeString(request, "");
        MinecraftWire.writeByteArray(request, publicKey);
        MinecraftWire.writeByteArray(request, verifyToken);
        request.write(1);
        MinecraftWire.writeFrame(out, request.toByteArray());

        InputStream response = new ByteArrayInputStream(MinecraftWire.readFrame(in));
        if (MinecraftWire.readVarInt(response) != 0x01) {
            return;
        }
        byte[] encryptedSecret = MinecraftWire.readByteArray(response, 512);
        byte[] encryptedToken = MinecraftWire.readByteArray(response, 512);
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        rsa.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        byte[] sharedSecret = rsa.doFinal(encryptedSecret);
        rsa.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
        if (!MessageDigest.isEqual(verifyToken, rsa.doFinal(encryptedToken)) || sharedSecret.length != 16) {
            return;
        }
        SecretKeySpec key = new SecretKeySpec(sharedSecret, "AES");
        Cipher decrypt = Cipher.getInstance("AES/CFB8/NoPadding");
        decrypt.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(sharedSecret));
        Cipher encrypt = Cipher.getInstance("AES/CFB8/NoPadding");
        encrypt.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(sharedSecret));
        InputStream secureIn = MinecraftWire.decrypting(in, decrypt);
        OutputStream secureOut = MinecraftWire.encrypting(out, encrypt);

        String serverHash = MinecraftWire.serverHash("", sharedSecret, publicKey);
        PremiumProfile profile = sessions.hasJoined(requestedName, serverHash).orElse(null);
        if (profile == null || !profile.name().equalsIgnoreCase(requestedName)) {
            disconnect(secureOut, "No se pudo verificar tu cuenta premium con Mojang.\n"
                + "Si no sos premium, pedí a un admin que desactive el modo premium de tu cuenta.");
            return;
        }
        Decision decision = verdict.decide(profile, client.getInetAddress(), handshakeHost);
        if (!decision.accepted()) {
            disconnect(secureOut, decision.message());
            return;
        }

        ByteArrayOutputStream success = packet(LOGIN_SUCCESS);
        MinecraftWire.writeUuid(success, profile.id());
        MinecraftWire.writeString(success, profile.name());
        MinecraftWire.writeVarInt(success, profile.properties().size());
        for (PremiumProfile.Property property : profile.properties()) {
            MinecraftWire.writeString(success, property.name());
            MinecraftWire.writeString(success, property.value());
            success.write(property.signature() == null ? 0 : 1);
            if (property.signature() != null) {
                MinecraftWire.writeString(success, property.signature());
            }
        }
        MinecraftWire.writeFrame(secureOut, success.toByteArray());

        boolean acknowledged = false;
        for (int frame = 0; frame < 8 && !acknowledged; frame++) {
            InputStream next = new ByteArrayInputStream(MinecraftWire.readFrame(secureIn));
            acknowledged = MinecraftWire.readVarInt(next) == LOGIN_ACKNOWLEDGED;
        }
        if (!acknowledged) {
            return;
        }

        ByteArrayOutputStream cookie = packet(CONFIG_STORE_COOKIE);
        MinecraftWire.writeString(cookie, COOKIE_KEY);
        MinecraftWire.writeByteArray(cookie, decision.cookie());
        MinecraftWire.writeFrame(secureOut, cookie.toByteArray());

        ByteArrayOutputStream transfer = packet(CONFIG_TRANSFER);
        MinecraftWire.writeString(transfer, decision.returnHost());
        MinecraftWire.writeVarInt(transfer, decision.returnPort());
        MinecraftWire.writeFrame(secureOut, transfer.toByteArray());

        // The client closes the socket once it starts the transfer; drain until then.
        client.setSoTimeout(5_000);
        try {
            while (secureIn.read() >= 0) {
                // ignore client information and brand packets
            }
        } catch (SocketException | java.net.SocketTimeoutException ignored) {
            // transfer started or client gone
        }
    }

    private void disconnect(OutputStream out, String message) throws IOException {
        ByteArrayOutputStream packet = packet(LOGIN_DISCONNECT);
        MinecraftWire.writeString(packet, MinecraftWire.chatJson(message));
        MinecraftWire.writeFrame(out, packet.toByteArray());
    }

    private static ByteArrayOutputStream packet(int id) throws IOException {
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        MinecraftWire.writeVarInt(packet, id);
        return packet;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    /** Decides, after Mojang confirmed the account, whether and where the client goes back. */
    @FunctionalInterface
    public interface Verdict {
        Decision decide(PremiumProfile profile, InetAddress address, String handshakeHost);
    }

    public record Decision(boolean accepted, String message, byte[] cookie, String returnHost, int returnPort) {

        public static Decision accept(byte[] cookie, String returnHost, int returnPort) {
            return new Decision(true, "", cookie, returnHost, returnPort);
        }

        public static Decision reject(String message) {
            return new Decision(false, message, new byte[0], "", 0);
        }
    }
}
