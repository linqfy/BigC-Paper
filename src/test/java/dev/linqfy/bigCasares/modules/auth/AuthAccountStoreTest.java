package dev.linqfy.bigCasares.modules.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthAccountStoreTest {

    @Test
    void roundTripsEveryField(@TempDir Path directory) {
        AuthAccount account = new AuthAccount("Steve", 1234L);
        account.password(new PasswordHasher.HashedSecret("pbkdf2-sha256", 1000, "c2FsdA==", "aGFzaA=="));
        account.totpSecret("JBSWY3DPEHPK3PXP");
        account.totpLastStep(42L);
        account.recoveryDigests(List.of("aa", "bb"));
        account.pgpKey(new byte[] {1, 2, 3}, "ABCDEF");
        account.pattern(new PasswordHasher.HashedSecret("pbkdf2-sha256", 1000, "cA==", "cQ=="));
        UUID premiumId = UUID.randomUUID();
        account.premium(true, premiumId);
        account.trust("Alex");
        account.requireTwoFactors(true);
        account.sessionResume(false);
        account.discordLogin(false);
        account.recordLogin("cafe", 99L);
        new AuthAccountStore(directory).put(account);

        AuthAccount loaded = new AuthAccountStore(directory).find("STEVE").orElseThrow();

        assertEquals("Steve", loaded.name());
        assertEquals(1234L, loaded.registeredAtMillis());
        assertEquals(account.password(), loaded.password());
        assertEquals("JBSWY3DPEHPK3PXP", loaded.totpSecret());
        assertEquals(42L, loaded.totpLastStep());
        assertEquals(List.of("aa", "bb"), loaded.recoveryDigests());
        assertArrayEquals(new byte[] {1, 2, 3}, loaded.pgpKeyRing());
        assertEquals("ABCDEF", loaded.pgpFingerprint());
        assertEquals(account.pattern(), loaded.pattern());
        assertTrue(loaded.premium());
        assertEquals(premiumId, loaded.premiumId());
        assertTrue(loaded.trusts("alex"));
        assertTrue(loaded.requireTwoFactors());
        assertFalse(loaded.sessionResume());
        assertFalse(loaded.discordLogin());
        assertEquals("cafe", loaded.lastAddressHash());
        assertEquals(99L, loaded.lastLoginAtMillis());
    }

    @Test
    void keepsUnusualNamesInsideTheDirectory(@TempDir Path directory) throws Exception {
        AuthAccountStore store = new AuthAccountStore(directory);
        store.put(new AuthAccount(".BedrockGuy", 1L));
        store.put(new AuthAccount("../evil", 1L));

        try (var files = Files.list(directory)) {
            assertEquals(2, files.filter(path -> path.getFileName().toString().startsWith("_")).count());
        }
        assertTrue(new AuthAccountStore(directory).find(".bedrockguy").isPresent());
        assertTrue(store.delete("../evil"));
        assertTrue(store.find("../evil").isEmpty());
    }
}
