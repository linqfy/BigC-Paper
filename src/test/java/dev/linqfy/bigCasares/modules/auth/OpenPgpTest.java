package dev.linqfy.bigCasares.modules.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fixtures were produced by GnuPG 2.4.4 with throwaway keys ({@code printf '%s' CHALLENGE | gpg --detach-sign}). */
final class OpenPgpTest {

    private static final String CHALLENGE = "bigcasares:Steve:0123456789abcdef";

    private static final String ED_KEY = "mDMEavPYvRYJKwYBBAHaRw8BAQdALlZV8aXvIr2ZJVsSvdBxZbvZUW3rpj8DrwnbqIvqwJm0HFRlc3Qg"
        + "RWQgPGVkQGV4YW1wbGUuaW52YWxpZD6IkwQTFgoAOxYhBCLQ/ZQ9K78kg45zZrew3tkC/bLgBQJq89i9AhsDBQsJCAcCAiICBhUKCQgL"
        + "AgQWAgMBAh4HAheAAAoJELew3tkC/bLge20BAMvc1tOrap8H9PK/TSqJlgPY0/TMJ0chYdRIvN3ewLX+APsHYuKMFGBQ+vavOYi01wgq"
        + "Vjk3MAhIVf8nXFBOSEQ5Ag==";
    private static final String ED_KEY_ARMORED = """
        -----BEGIN PGP PUBLIC KEY BLOCK-----

        mDMEavPYvRYJKwYBBAHaRw8BAQdALlZV8aXvIr2ZJVsSvdBxZbvZUW3rpj8Drwnb
        qIvqwJm0HFRlc3QgRWQgPGVkQGV4YW1wbGUuaW52YWxpZD6IkwQTFgoAOxYhBCLQ
        /ZQ9K78kg45zZrew3tkC/bLgBQJq89i9AhsDBQsJCAcCAiICBhUKCQgLAgQWAgMB
        Ah4HAheAAAoJELew3tkC/bLge20BAMvc1tOrap8H9PK/TSqJlgPY0/TMJ0chYdRI
        vN3ewLX+APsHYuKMFGBQ+vavOYi01wgqVjk3MAhIVf8nXFBOSEQ5Ag==
        =havr
        -----END PGP PUBLIC KEY BLOCK-----
        """;
    private static final String ED_SIG = "iIkEABYKADEWIQQi0P2UPSu/JIOOc2a3sN7ZAv2y4AUCavPYvRMcZWRAZXhhbXBsZS5pbnZhbGlk"
        + "AAoJELew3tkC/bLgVzUBALHoOTXTAwE1w1+juJ9SLCMpSNK8tSnQrDgEjfihj0zSAP4467l2BDOMHde8alaQs7U9XFhiODzsJhe9Qy4te31P"
        + "Cw==";
    private static final String ED_SIG_ARMORED_ONE_LINE = "-----BEGIN PGP SIGNATURE-----"
        + "iIkEABYKADEWIQQi0P2UPSu/JIOOc2a3sN7ZAv2y4AUCavPYvRMcZWRAZXhhbXBs"
        + "ZS5pbnZhbGlkAAoJELew3tkC/bLgVzUBALHoOTXTAwE1w1+juJ9SLCMpSNK8tSnQ"
        + "rDgEjfihj0zSAP4467l2BDOMHde8alaQs7U9XFhiODzsJhe9Qy4te31PCw=="
        + "=qnve-----END PGP SIGNATURE-----";
    private static final String ED_SIG_WITH_NEWLINE = "iIkEABYKADEWIQQi0P2UPSu/JIOOc2a3sN7ZAv2y4AUCavPYvRMcZWRAZXhhbXBs"
        + "ZS5pbnZhbGlkAAoJELew3tkC/bLg2hMA/2SBhZXv4F4Ze3jMDKSvy8J4Obd+ttRIFiqKgza2uV21AP9vWfk0jJXKuW2vY7vbFf9hyE9D/i1C"
        + "MJGqU04KR1oWDA==";
    private static final String ED_SIG_OTHER_CHALLENGE = "iIkEABYKADEWIQQi0P2UPSu/JIOOc2a3sN7ZAv2y4AUCavPYvRMcZWRAZX"
        + "hhbXBsZS5pbnZhbGlkAAoJELew3tkC/bLge68A/0LEikFAHaKMGZi2r5M9pP35nBG0lfj0QpOiB8KMlSDcAP91DGF92dZk39aHMp4zLjoZ"
        + "MLTZUlgG5hNaJvA6Tr0MCQ==";

    private static final String RSA_KEY = "mQENBGrz2L0BCACVTuYbMLgcv7Y2xraSaYbGohfpJfJDwy2+WsRVCEWm0q7XkcFM8FpMnlj06CF/S2g5"
        + "gQsx6m8PrzElfBtvagLSjhUEKuY7gaeoESpXVm/ClT9/ttoxXOldi9vPREpVi37A9t2vfjxAspSRhy9RgvSd4xZe4KLdd0JXAmigb8EvMrB+"
        + "EKG3E/c8ve0BeNjVj+g8AmWPxoDc9k/9o3inTelXcsnEGy6Kr9Bv3WI9GeSRk2494COSUBAafQx59E+7eSmf6hYG/bRRR9TTiY+GLhhRTmWd"
        + "PmBwUBOoLHmqGm0TGnQExw3qNlv2AbU2seuDPQLhzCei/rJ0YAEeA8PxTh0/ABEBAAG0HlRlc3QgUnNhIDxyc2FAZXhhbXBsZS5pbnZhbGlk"
        + "PokBUQQTAQoAOxYhBJVXnJRexJLvEw0W7gDZqURRp0PGBQJq89i9AhsDBQsJCAcCAiICBhUKCQgLAgQWAgMBAh4HAheAAAoJEADZqURRp0PG"
        + "CjkH/3Qv7773y9kEkUQuACyF4hCLSoPsAc1wTRN8tKBYi2QDX1UDdlKP0uI5AMJai/HgoyH+00gwfK/FL2ruRZcfBdIR+80x7MsGGOybgjG3"
        + "s1NX+mVx53CiBebnLHDJpwho3oRonfUs02OFK2eDkMNIRScktIsUeG1IehZST2sgyUC7Lr5/ONrX/1BP865VvKhheXA8511s5xBIrK/6fGUX"
        + "gVpG755fyOySc72HdMLaigOqOIguvUWql+X62at3iD/IUAnqYo+p4JU3l+aEp/SD6wc98yT7X1ek1krnv6p8NB5ZBDxLOr1iq86E20tmhbus"
        + "k6z/T2YYOsdGULFatEXU/80=";
    private static final String RSA_SIG = "iQFIBAABCgAyFiEElVeclF7Eku8TDRbuANmpRFGnQ8YFAmrz2L0UHHJzYUBleGFtcGxlLmludmFs"
        + "aWQACgkQANmpRFGnQ8ZXswf/QShfx8jxX2TRqat3y3IOQWVI/hA4vrECuJHIKiP+WTOdSv8IjEJIOw2KJbNNGb79A1MjD6Czw7UKlhdQ8s8s"
        + "N9Kl3i8PlJoXimY8WgkOlJZlf7f/AP0mBNAPbTcUwGSU5yajRV99VB9UqObaswY9nVMcZhAszfpLk9p8lsQ2ied7BTITzFv23Lv0RgV3AvA4"
        + "gQnUYClKW/+F46OVMwSKAbmDNxXcya3NLbGt2ed56oYss4u/5XpQl/xRfPVIq5y1SBqkDJWuDBbNoLgV3ENtvtPw3K1u4Wa+/Eg2nFDQbXnC"
        + "SB5IxKTpuS5ij8ZZo/jDWAgDSyVzWsgx0Bc/rwj5Ng==";
    private static final String RSA_TEXT_SIG = "iQFIBAEBCgAyFiEElVeclF7Eku8TDRbuANmpRFGnQ8YFAmrz2L0UHHJzYUBleGFtcGxlLmlu"
        + "dmFsaWQACgkQANmpRFGnQ8aGCgf+P/I5/LIS433Fts7cSNGQCtlitnX/nYuHOAkWmm0skGJryOdUySCI3q/ZzrNE1yT8palaD3iYIXhKZJyb"
        + "/6hWCP6KxiYuxXp7pg4NH/ZTnqaptqQp9fp/DnnuuEwJEWa8bAv79ksd3qGeD4juPitZqCLUBOeUGsCROLzobz85/Qwk5XAZ9CfDsWqDl/4m"
        + "MWYr+DjuIrTyMQWJwrf/Q/bo87vNcJrV5OKKE0otFf3BdlTEh1Bu7/C9UGxbrXJPIGFhUtEk0S/Uc3krMGh0LBXrnvPqkA0LilUm7PzGYunM"
        + "NzJrHg406Q0yzziRsANX/egOLmFkvAtvjC3K2g/I8ADEfQ==";

    private static final String SUBKEY_KEY = "mDMEavPYvRYJKwYBBAHaRw8BAQdASaxR65KF9hRpkVqA1hTQiss8cRgrGM9yCcKosz+Y8Qe0HlRl"
        + "c3QgU3ViIDxzdWJAZXhhbXBsZS5pbnZhbGlkPoiTBBMWCgA7FiEE9VT/P+xyOrQcNxua3vBjhcX50WYFAmrz2L0CGwEFCwkIBwICIgIGFQoJ"
        + "CAsCBBYCAwECHgcCF4AACgkQ3vBjhcX50WavzQD+MQPWP6Hdx0ziD1IAqmaPL17QVsUvn2EVUc0spluaFMQBAJ2pn53vwQfuEGJQXCRz9114"
        + "mfMvDM3xR5PA2jeIcTINuDMEavPYvRYJKwYBBAHaRw8BAQdAOAuuX3DxwcmJuXMC7Ms07s+sFpLCoCeoi95ZgZLOZnyI7wQYFgoAIBYhBPVU"
        + "/z/scjq0HDcbmt7wY4XF+dFmBQJq89i9AhsCAIEJEN7wY4XF+dFmdiAEGRYKAB0WIQRNSecfO8xUMiUjoWf7nsVxm4/RRwUCavPYvQAKCRD7"
        + "nsVxm4/RR1ojAQDJopvfMn9zccEGF6fK7NuMsr8hhHjd7bqm6Wat23BICwEAsCXzvmP/IYvrPwwSgodX3V9sJEG4+8q4y2mA5mpEgwUSygEA"
        + "2Yq2IASADonDwqdp+koIBIMzIemljpZ19DyDwkRLxbgBAPo+XrBj36/dkVRwRLknEbtRskO/k3i/7rGNeK94T1gD";
    private static final String SUBKEY_SIG = "iIoEABYKADIWIQRNSecfO8xUMiUjoWf7nsVxm4/RRwUCavPYvRQcc3ViQGV4YW1wbGUuaW52YWxp"
        + "ZAAKCRD7nsVxm4/RR8rYAPoCKzMXtrS3NkPZ8GIDYLdG62hAODqlS+NM1ntmClj2ZwEA4eD4qfPLnWfIfsivUTsDStOjvzlQ20g4P/YU21Bk"
        + "9gE=";

    @Test
    void readsEd25519KeyFingerprintFromBase64AndArmor() throws Exception {
        OpenPgp.KeyRing binary = OpenPgp.parseKeyRing(ED_KEY);
        OpenPgp.KeyRing armored = OpenPgp.parseKeyRing(ED_KEY_ARMORED);

        assertEquals("22D0FD943D2BBF24838E7366B7B0DED902FDB2E0", binary.primaryFingerprint());
        assertEquals(binary.primaryFingerprint(), armored.primaryFingerprint());
        assertEquals("B7B0DED902FDB2E0", binary.shortId());
    }

    @Test
    void verifiesEd25519DetachedSignature() throws Exception {
        assertEquals(Optional.of("22D0FD943D2BBF24838E7366B7B0DED902FDB2E0"),
            verify(ED_KEY, ED_SIG));
    }

    @Test
    void acceptsArmoredSignatureGluedOntoOneChatLine() throws Exception {
        assertEquals(Optional.of("22D0FD943D2BBF24838E7366B7B0DED902FDB2E0"),
            verify(ED_KEY, ED_SIG_ARMORED_ONE_LINE));
    }

    @Test
    void acceptsSignatureOverChallengeWithTrailingNewline() throws Exception {
        assertTrue(verify(ED_KEY, ED_SIG_WITH_NEWLINE).isPresent());
    }

    @Test
    void rejectsSignatureOverAnotherChallenge() throws Exception {
        assertTrue(verify(ED_KEY, ED_SIG_OTHER_CHALLENGE).isEmpty());
    }

    @Test
    void verifiesRsaBinaryAndTextModeSignatures() throws Exception {
        assertEquals(Optional.of("95579C945EC492EF130D16EE00D9A94451A743C6"), verify(RSA_KEY, RSA_SIG));
        assertTrue(verify(RSA_KEY, RSA_TEXT_SIG).isPresent());
    }

    @Test
    void verifiesSignatureMadeBySigningSubkey() throws Exception {
        OpenPgp.KeyRing ring = OpenPgp.parseKeyRing(SUBKEY_KEY);

        assertEquals("F554FF3FEC723AB41C371B9ADEF06385C5F9D166", ring.primaryFingerprint());
        assertEquals(Optional.of("4D49E71F3BCC54322523A167FB9EC5719B8FD147"), verify(SUBKEY_KEY, SUBKEY_SIG));
    }

    @Test
    void rejectsSignatureFromAnotherKey() {
        OpenPgp.PgpException failure = assertThrows(OpenPgp.PgpException.class, () -> verify(RSA_KEY, ED_SIG));
        assertTrue(failure.getMessage().contains("clave registrada"));
    }

    @Test
    void rejectsTamperedSignature() throws Exception {
        byte[] signature = Base64.getDecoder().decode(ED_SIG);
        signature[signature.length - 5] ^= 0x01;
        OpenPgp.KeyRing ring = OpenPgp.parseKeyRing(ED_KEY);

        assertTrue(OpenPgp.verifyDetached(ring, signature, candidates()).isEmpty());
    }

    @Test
    void rejectsGarbageInput() {
        assertThrows(OpenPgp.PgpException.class, () -> OpenPgp.parseKeyRing("not a key!"));
        assertThrows(OpenPgp.PgpException.class, () -> OpenPgp.parseKeyRing(""));
        assertThrows(OpenPgp.PgpException.class,
            () -> OpenPgp.verifyDetached(OpenPgp.parseKeyRing(ED_KEY), new byte[] {(byte) 0xC2, 5, 4, 0}, candidates()));
    }

    private static Optional<String> verify(String key, String signature) throws OpenPgp.PgpException {
        return OpenPgp.verifyDetached(OpenPgp.parseKeyRing(key), OpenPgp.decodeSignature(signature), candidates());
    }

    private static List<byte[]> candidates() {
        return List.of(CHALLENGE.getBytes(StandardCharsets.UTF_8), (CHALLENGE + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
