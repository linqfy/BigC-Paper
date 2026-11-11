package dev.linqfy.bigCasares.modules.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(10_000, new SecureRandom());

    @Test
    void verifiesOnlyTheOriginalSecret() {
        PasswordHasher.HashedSecret hash = hasher.hash("caballo-bateria-grapa");

        assertTrue(PasswordHasher.verify("caballo-bateria-grapa", hash));
        assertFalse(PasswordHasher.verify("caballo-bateria-grap", hash));
        assertFalse(PasswordHasher.verify("caballo-bateria-grapa", null));
    }

    @Test
    void saltsEveryHash() {
        assertNotEquals(hasher.hash("igual123").hash(), hasher.hash("igual123").hash());
    }

    @Test
    void rejectsWeakPasswords() {
        assertNotNull(PasswordHasher.rejectionReason("abc", "Steve", 6));
        assertNotNull(PasswordHasher.rejectionReason("steve2024", "Steve", 6));
        assertNotNull(PasswordHasher.rejectionReason("123456", "Steve", 6));
        assertNotNull(PasswordHasher.rejectionReason("x".repeat(73), "Steve", 6));
        assertNull(PasswordHasher.rejectionReason("pollo frito 77", "Steve", 6));
    }
}
