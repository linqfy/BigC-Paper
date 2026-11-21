package dev.linqfy.bigCasares.modules.auth;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Minimal OpenPGP (RFC 4880 / RFC 9580) reader that verifies v4 detached signatures with the JDK's own
 * providers: RSA (algorithm 1), legacy EdDSA over Ed25519 (22, GnuPG's default) and Ed25519 (27).
 *
 * <p>The key ring a player registers is the trust anchor: every supported primary key or subkey in it may sign
 * login challenges. Binding signatures are not checked because nobody but the authenticated owner can change
 * the stored ring.
 */
public final class OpenPgp {

    static final int MAX_KEY_RING_BYTES = 64 * 1024;
    static final int MAX_SIGNATURE_BYTES = 4 * 1024;
    private static final int TAG_SIGNATURE = 2;
    private static final int TAG_PUBLIC_KEY = 6;
    private static final int TAG_PUBLIC_SUBKEY = 14;
    private static final int ALGO_RSA = 1;
    private static final int ALGO_RSA_SIGN = 3;
    private static final int ALGO_EDDSA_LEGACY = 22;
    private static final int ALGO_ED25519 = 27;
    private static final byte[] ED25519_OID = {0x2B, 0x06, 0x01, 0x04, 0x01, (byte) 0xDA, 0x47, 0x0F, 0x01};
    private static final byte[] ED25519_X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    private OpenPgp() {
    }

    /** Parses an ASCII-armored or base64/binary transferable public key. */
    public static KeyRing parseKeyRing(String text) throws PgpException {
        byte[] binary = decodeText(text, MAX_KEY_RING_BYTES);
        return parseKeyRing(binary);
    }

    public static KeyRing parseKeyRing(byte[] binary) throws PgpException {
        if (binary.length > MAX_KEY_RING_BYTES) {
            throw new PgpException("La clave es demasiado grande.");
        }
        List<SigningKey> keys = new ArrayList<>();
        String primaryFingerprint = null;
        for (Packet packet : packets(binary)) {
            if (packet.tag() != TAG_PUBLIC_KEY && packet.tag() != TAG_PUBLIC_SUBKEY) {
                continue;
            }
            byte[] body = packet.body();
            if (body.length < 6) {
                throw new PgpException("Paquete de clave pública truncado.");
            }
            if (body[0] != 4) {
                if (packet.tag() == TAG_PUBLIC_KEY) {
                    throw new PgpException("Solo se admiten claves OpenPGP v4 (versión " + body[0] + ").");
                }
                continue;
            }
            String fingerprint = fingerprintV4(body);
            if (packet.tag() == TAG_PUBLIC_KEY && primaryFingerprint == null) {
                primaryFingerprint = fingerprint;
            }
            readPublicKey(body).ifPresent(key -> keys.add(new SigningKey(
                fingerprint, Long.parseUnsignedLong(fingerprint.substring(24), 16), body[5] & 0xFF, key)));
        }
        if (primaryFingerprint == null) {
            throw new PgpException("No se encontró ninguna clave pública OpenPGP.");
        }
        if (keys.isEmpty()) {
            throw new PgpException("La clave no tiene subclaves de firma compatibles (usá Ed25519 o RSA).");
        }
        return new KeyRing(binary, primaryFingerprint, List.copyOf(keys));
    }

    /** Decodes a detached signature given as armor (newlines optional) or base64 of the binary packet. */
    public static byte[] decodeSignature(String text) throws PgpException {
        return decodeText(text, MAX_SIGNATURE_BYTES);
    }

    /**
     * Verifies that {@code signature} is a binary/text v4 signature over one of {@code candidates} made by a key
     * in {@code ring}. Several candidates cover {@code echo} adding a trailing newline.
     */
    public static Optional<String> verifyDetached(KeyRing ring, byte[] signature, List<byte[]> candidates)
        throws PgpException {
        List<Packet> packets = packets(signature);
        if (packets.isEmpty() || packets.getFirst().tag() != TAG_SIGNATURE) {
            throw new PgpException("No es una firma OpenPGP.");
        }
        ParsedSignature parsed = parseSignature(packets.getFirst().body());
        List<SigningKey> signers = ring.keys().stream()
            .filter(key -> parsed.issuerFingerprint() == null
                ? parsed.issuerKeyId() == null || key.keyId() == parsed.issuerKeyId()
                : key.fingerprint().equals(parsed.issuerFingerprint()))
            .filter(key -> compatible(key.algorithm(), parsed.publicKeyAlgorithm()))
            .toList();
        if (signers.isEmpty()) {
            throw new PgpException("La firma no es de la clave registrada en tu cuenta.");
        }
        for (byte[] candidate : candidates) {
            byte[] data = parsed.signatureType() == 0x01 ? canonicalText(candidate) : candidate;
            byte[] hashInput = concat(data, parsed.hashedPortion(), trailer(parsed.hashedPortion().length));
            byte[] digest = digest(parsed.hashAlgorithm(), hashInput);
            if (((digest[0] & 0xFF) << 8 | (digest[1] & 0xFF)) != parsed.hashLeft16()) {
                continue;
            }
            for (SigningKey signer : signers) {
                if (verifyMaterial(signer, parsed, hashInput, digest)) {
                    return Optional.of(signer.fingerprint());
                }
            }
        }
        return Optional.empty();
    }

    static byte[] decodeText(String text, int maxBytes) throws PgpException {
        if (text == null || text.isBlank()) {
            throw new PgpException("No se recibió ningún dato OpenPGP.");
        }
        // Chat strips newlines, so armor may arrive glued together; the markers are cut out either way. Armor
        // header lines ("Version: ...", "Comment: ...") are dropped because ':' never occurs in base64.
        String unmarked = text.replace("\r", "").replaceAll("-----(BEGIN|END) PGP [A-Z ,]+-----", "\n");
        StringBuilder base64 = new StringBuilder(unmarked.length());
        for (String line : unmarked.split("\n")) {
            if (!line.contains(":")) {
                base64.append(line);
            }
        }
        String compact = base64.toString().replaceAll("\\s", "");
        if (compact.length() % 4 == 1 && compact.length() > 5 && compact.charAt(compact.length() - 5) == '=') {
            compact = compact.substring(0, compact.length() - 5);
        }
        if (compact.length() > (maxBytes * 4 / 3) + 8) {
            throw new PgpException("Los datos OpenPGP son demasiado grandes.");
        }
        try {
            return Base64.getDecoder().decode(compact);
        } catch (IllegalArgumentException ex) {
            throw new PgpException("Base64 inválido: copiá la salida completa de gpg.");
        }
    }

    private static boolean compatible(int keyAlgorithm, int signatureAlgorithm) {
        if (signatureAlgorithm == ALGO_RSA || signatureAlgorithm == ALGO_RSA_SIGN) {
            return keyAlgorithm == ALGO_RSA || keyAlgorithm == ALGO_RSA_SIGN;
        }
        return keyAlgorithm == signatureAlgorithm;
    }

    private static boolean verifyMaterial(SigningKey signer, ParsedSignature parsed, byte[] hashInput, byte[] digest)
        throws PgpException {
        try {
            if (signer.algorithm() == ALGO_RSA || signer.algorithm() == ALGO_RSA_SIGN) {
                Signature rsa = Signature.getInstance(rsaAlgorithm(parsed.hashAlgorithm()));
                rsa.initVerify(signer.key());
                rsa.update(hashInput);
                int modulusBytes = (((java.security.interfaces.RSAPublicKey) signer.key()).getModulus().bitLength() + 7) / 8;
                return rsa.verify(leftPad(parsed.material().getFirst(), modulusBytes));
            }
            byte[] raw;
            if (signer.algorithm() == ALGO_EDDSA_LEGACY) {
                raw = concat(leftPad(parsed.material().get(0), 32), leftPad(parsed.material().get(1), 32));
            } else {
                raw = parsed.material().getFirst();
            }
            if (raw.length != 64) {
                return false;
            }
            Signature ed25519 = Signature.getInstance("Ed25519");
            ed25519.initVerify(signer.key());
            ed25519.update(digest);
            return ed25519.verify(raw);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }

    private static ParsedSignature parseSignature(byte[] body) throws PgpException {
        Reader reader = new Reader(body);
        int version = reader.u8();
        if (version != 4) {
            throw new PgpException("Solo se admiten firmas OpenPGP v4.");
        }
        int type = reader.u8();
        if (type != 0x00 && type != 0x01) {
            throw new PgpException("Usá una firma separada (gpg --detach-sign).");
        }
        int publicKeyAlgorithm = reader.u8();
        int hashAlgorithm = reader.u8();
        int hashedLength = reader.u16();
        byte[] hashed = reader.bytes(hashedLength);
        byte[] hashedPortion = Arrays.copyOfRange(body, 0, 6 + hashedLength);
        int unhashedLength = reader.u16();
        byte[] unhashed = reader.bytes(unhashedLength);
        int left16 = reader.u16();
        List<byte[]> material = new ArrayList<>();
        switch (publicKeyAlgorithm) {
            case ALGO_RSA, ALGO_RSA_SIGN -> material.add(reader.mpi());
            case ALGO_EDDSA_LEGACY -> {
                material.add(reader.mpi());
                material.add(reader.mpi());
            }
            case ALGO_ED25519 -> material.add(reader.bytes(64));
            default -> throw new PgpException("Algoritmo de firma no compatible: " + publicKeyAlgorithm);
        }
        Long issuerKeyId = null;
        String issuerFingerprint = null;
        for (byte[] area : List.of(hashed, unhashed)) {
            Reader sub = new Reader(area);
            while (sub.remaining() > 0) {
                int length = sub.subpacketLength();
                if (length < 1 || length > sub.remaining()) {
                    throw new PgpException("Subpaquete de firma inválido.");
                }
                int subType = sub.u8() & 0x7F;
                byte[] data = sub.bytes(length - 1);
                if (subType == 16 && data.length == 8) {
                    issuerKeyId = ByteBuffer.wrap(data).getLong();
                } else if (subType == 33 && data.length == 21 && data[0] == 4) {
                    issuerFingerprint = HexFormat.of().withUpperCase().formatHex(data, 1, 21);
                }
            }
        }
        return new ParsedSignature(type, publicKeyAlgorithm, hashAlgorithm, hashedPortion, left16, material,
            issuerKeyId, issuerFingerprint);
    }

    private static Optional<PublicKey> readPublicKey(byte[] body) throws PgpException {
        Reader reader = new Reader(body);
        reader.bytes(6);
        int algorithm = body[5] & 0xFF;
        try {
            return switch (algorithm) {
                case ALGO_RSA, ALGO_RSA_SIGN -> {
                    BigInteger modulus = new BigInteger(1, reader.mpi());
                    BigInteger exponent = new BigInteger(1, reader.mpi());
                    yield Optional.of(KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent)));
                }
                case ALGO_EDDSA_LEGACY -> {
                    int oidLength = reader.u8();
                    byte[] oid = reader.bytes(oidLength);
                    if (!Arrays.equals(oid, ED25519_OID)) {
                        yield Optional.empty();
                    }
                    byte[] point = reader.mpi();
                    if (point.length != 33 || point[0] != 0x40) {
                        throw new PgpException("Clave Ed25519 con formato inesperado.");
                    }
                    yield Optional.of(ed25519(Arrays.copyOfRange(point, 1, 33)));
                }
                case ALGO_ED25519 -> Optional.of(ed25519(reader.bytes(32)));
                default -> Optional.empty();
            };
        } catch (GeneralSecurityException ex) {
            throw new PgpException("No se pudo leer la clave pública.");
        }
    }

    private static PublicKey ed25519(byte[] raw) throws GeneralSecurityException {
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(concat(ED25519_X509_PREFIX, raw)));
    }

    private static String fingerprintV4(byte[] body) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update((byte) 0x99);
            sha1.update((byte) (body.length >> 8));
            sha1.update((byte) body.length);
            sha1.update(body);
            return HexFormat.of().withUpperCase().formatHex(sha1.digest());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-1 unavailable", ex);
        }
    }

    private static List<Packet> packets(byte[] data) throws PgpException {
        List<Packet> packets = new ArrayList<>();
        Reader reader = new Reader(data);
        while (reader.remaining() > 0) {
            int header = reader.u8();
            if ((header & 0x80) == 0) {
                throw new PgpException("Datos OpenPGP inválidos.");
            }
            int tag;
            int length;
            if ((header & 0x40) != 0) {
                tag = header & 0x3F;
                int first = reader.u8();
                if (first < 192) {
                    length = first;
                } else if (first < 224) {
                    length = ((first - 192) << 8) + reader.u8() + 192;
                } else if (first == 255) {
                    length = reader.s32();
                } else {
                    throw new PgpException("Longitudes parciales no admitidas.");
                }
            } else {
                tag = (header >> 2) & 0x0F;
                length = switch (header & 0x03) {
                    case 0 -> reader.u8();
                    case 1 -> reader.u16();
                    case 2 -> reader.s32();
                    default -> reader.remaining();
                };
            }
            if (length < 0 || length > reader.remaining()) {
                throw new PgpException("Paquete OpenPGP truncado.");
            }
            packets.add(new Packet(tag, reader.bytes(length)));
        }
        return packets;
    }

    private static byte[] trailer(int hashedPortionLength) {
        return ByteBuffer.allocate(6).put((byte) 4).put((byte) 0xFF).putInt(hashedPortionLength).array();
    }

    private static byte[] digest(int hashAlgorithm, byte[] input) throws PgpException {
        String name = switch (hashAlgorithm) {
            case 8 -> "SHA-256";
            case 9 -> "SHA-384";
            case 10 -> "SHA-512";
            case 11 -> "SHA-224";
            default -> throw new PgpException("Hash de firma no admitido (usá SHA-256 o superior).");
        };
        try {
            return MessageDigest.getInstance(name).digest(input);
        } catch (GeneralSecurityException ex) {
            throw new PgpException("Hash no disponible: " + name);
        }
    }

    private static String rsaAlgorithm(int hashAlgorithm) throws PgpException {
        return switch (hashAlgorithm) {
            case 8 -> "SHA256withRSA";
            case 9 -> "SHA384withRSA";
            case 10 -> "SHA512withRSA";
            case 11 -> "SHA224withRSA";
            default -> throw new PgpException("Hash de firma no admitido (usá SHA-256 o superior).");
        };
    }

    /** RFC 9580 text-mode canonical form: CRLF line endings. */
    private static byte[] canonicalText(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
        return text.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] leftPad(byte[] value, int length) {
        if (value.length >= length) {
            return value.length == length ? value : Arrays.copyOfRange(value, value.length - length, value.length);
        }
        byte[] padded = new byte[length];
        System.arraycopy(value, 0, padded, length - value.length, value.length);
        return padded;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    public record KeyRing(byte[] encoded, String primaryFingerprint, List<SigningKey> keys) {
        public String shortId() {
            return primaryFingerprint.substring(primaryFingerprint.length() - 16);
        }
    }

    public record SigningKey(String fingerprint, long keyId, int algorithm, PublicKey key) {
        public String algorithmName() {
            return switch (algorithm) {
                case ALGO_RSA, ALGO_RSA_SIGN -> "RSA";
                case ALGO_EDDSA_LEGACY, ALGO_ED25519 -> "Ed25519";
                default -> "algoritmo " + algorithm;
            };
        }
    }

    private record Packet(int tag, byte[] body) {
    }

    private record ParsedSignature(int signatureType, int publicKeyAlgorithm, int hashAlgorithm, byte[] hashedPortion,
                                   int hashLeft16, List<byte[]> material, Long issuerKeyId,
                                   String issuerFingerprint) {
    }

    public static final class PgpException extends Exception {
        public PgpException(String message) {
            super(message);
        }
    }

    private static final class Reader {
        private final byte[] data;
        private int position;

        Reader(byte[] data) {
            this.data = data;
        }

        int remaining() {
            return data.length - position;
        }

        int u8() throws PgpException {
            require(1);
            return data[position++] & 0xFF;
        }

        int u16() throws PgpException {
            return (u8() << 8) | u8();
        }

        int s32() throws PgpException {
            return (u8() << 24) | (u8() << 16) | (u8() << 8) | u8();
        }

        byte[] bytes(int length) throws PgpException {
            if (length < 0) {
                throw new PgpException("Longitud negativa en datos OpenPGP.");
            }
            require(length);
            byte[] out = Arrays.copyOfRange(data, position, position + length);
            position += length;
            return out;
        }

        byte[] mpi() throws PgpException {
            int bits = u16();
            return bytes((bits + 7) / 8);
        }

        int subpacketLength() throws PgpException {
            int first = u8();
            if (first < 192) {
                return first;
            }
            if (first < 255) {
                return ((first - 192) << 8) + u8() + 192;
            }
            return s32();
        }

        private void require(int length) throws PgpException {
            if (length > remaining()) {
                throw new PgpException("Datos OpenPGP truncados.");
            }
        }
    }

    static String describe(KeyRing ring) {
        return ring.keys().stream()
            .map(key -> key.algorithmName() + " " + key.fingerprint().substring(24).toLowerCase(Locale.ROOT))
            .distinct()
            .reduce((left, right) -> left + ", " + right)
            .orElse("-");
    }
}
