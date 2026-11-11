package dev.linqfy.bigCasares.modules.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expected SHA-256 digests of the module matrix come from the python-qrcode reference encoder (byte mode, level M,
 * same version and forced mask).
 */
final class QrCodeTest {

    private static final String URI = "otpauth://totp/BigCasares:Steve?secret=JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
        + "&issuer=BigCasares&algorithm=SHA1&digits=6&period=30";

    @Test
    void matchesReferenceForSingleBlockVersion() throws Exception {
        assertMatches("hello", 1, new String[] {
            "52a7aa67e7296ede539d6be86579c7180e3b6d417445712ae8bd314c1818458a",
            "b5607dcfbd80e2bc0b80d411972d3040856f159f49f2932fe5e9f24181ee1225",
            "f67f2bc632df800dbc2c111f69cd9af3812f0488abf74fb397641a9c00b86c64",
            "75b99c66edebbd9092e05881d5108e41c788e2e7f48bea1cc443b640895efa98",
            "c81027800a8044d0762e47033b67cf0ead403170b24e9e4ff25203a9f92c7020",
            "aab8a0a72b21c8c63c7d29fb79889a5cf663a94745c683d332f8ca36b6c93d79",
            "073eb88268c25799dc8d72588f97027756b7fedd3a9a09bcc7e81a54fb29d3ef",
            "683921f56b2988dd454e5f08cc85d949682bd846d510fe13aa028a95d1427918"});
    }

    @Test
    void matchesReferenceForAuthenticatorUriWithVersionInformation() throws Exception {
        assertMatches(URI, 8, new String[] {
            "5b9f2d32e720981700cfa9237f57daaa4c8e970d5093ae230027741d0ab44730",
            "ad84018816cd3c8e5348cef883aab866123b3df249b432f76e2a7af31edff64e",
            "aa640a36b6ff7f2102bd6c33327890ff11b8cf96d33eb1b2fef75818741d5d2b",
            "8e7d7aaf4b4c56b564cf5c4e66ed86c4e4492bc6dc4d8c94fd88bae38a2c2577",
            "ded8c52425dd74bf21b97d9e68d08df701d2d6ff6caf3f437170a8d1b15f2bb4",
            "f52f3950cd8288048db6bca3f75ecac4e5ca8afcb37e77bde945a2c6fd068817",
            "4974eca309976abaab37faa88ce82e1a74a5160302a0fa1e1e1cb8b01addf6e2",
            "2653c5bc1717e642eaaeeb6070ba10fe0a54634e93b46824ab05ac67d7f83b4c"});
    }

    @Test
    void matchesReferenceForMixedBlockLengths() throws Exception {
        assertMatches("x".repeat(300), 13, new String[] {
            "a6c4c99a50e77d18b6ddfdcbd85fffbd3b4da81e4eabc96b030a3f559d2b7fbb",
            "dd47780c6a9c164478044242b2819a05fd06ba561fc318901ca86055b9a0bc28",
            "0dde90aaf0e932eefc8d91efa3758f9fbfd0185edfc7308242c467729ab52406",
            "06540e7d3086d1a1c7ae802f6c37eb982dcabd79b88808fcc24dfcc97fd1df3b",
            "0973c7fc115da102159099b8c5e0f132547f7f35798421aa2c31d7b9cc6e2a5a",
            "7f9032829c49bb37a3892d610787b3c0fe01ada55c942c44dccf1cd5cdd1dfcd",
            "966f409b0f95894f7a52849253ebfd129d14a6abf7e6dd6091a8018054425b58",
            "0cfd1fee6e5cc6901de086d61debc646d3f80f5180898a1b724548d6c42006af"});
    }

    @Test
    void authenticatorUriFitsOnAMap() {
        QrCode code = QrCode.encodeText(URI);
        assertTrue(QrMapRenderer.scale(code) >= 2, "QR modules must be at least 2 map pixels");
        assertThrows(IllegalArgumentException.class, () -> QrCode.encodeText("x".repeat(3000)));
    }

    private static void assertMatches(String text, int version, String[] digestsByMask) throws Exception {
        for (int mask = 0; mask < 8; mask++) {
            QrCode code = QrCode.encode(text.getBytes(StandardCharsets.UTF_8), mask);
            assertEquals(version, code.version());
            StringBuilder matrix = new StringBuilder();
            for (int y = 0; y < code.size(); y++) {
                if (y > 0) {
                    matrix.append('\n');
                }
                for (int x = 0; x < code.size(); x++) {
                    matrix.append(code.module(x, y) ? '1' : '0');
                }
            }
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(matrix.toString().getBytes(StandardCharsets.US_ASCII)));
            assertEquals(digestsByMask[mask], digest, "mask " + mask);
        }
    }
}
