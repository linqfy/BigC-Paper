package dev.linqfy.bigCasares.modules.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LoginRulesTest {

    @Test
    void singleFactorAccountsNeedOneMethod() {
        LoginProgress progress = new LoginProgress(false);
        assertFalse(progress.complete());
        assertTrue(progress.satisfy(AuthMethod.PATTERN));
    }

    @Test
    void twoFactorAccountsNeedTwoDistinctManualMethods() {
        LoginProgress progress = new LoginProgress(true);
        assertFalse(progress.satisfy(AuthMethod.PASSWORD));
        assertFalse(progress.satisfy(AuthMethod.PASSWORD));
        assertTrue(progress.satisfy(AuthMethod.TOTP));
    }

    @Test
    void automaticMethodsAlwaysSuffice() {
        assertTrue(new LoginProgress(true).satisfy(AuthMethod.PREMIUM));
        assertTrue(new LoginProgress(true).satisfy(AuthMethod.BEDROCK));
    }

    @Test
    void limiterLocksAfterRepeatedFailuresAndExpires() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, 60_000L, 600_000L);

        assertFalse(limiter.recordFailure("a", 0L));
        assertFalse(limiter.recordFailure("a", 1_000L));
        assertTrue(limiter.recordFailure("a", 2_000L));
        assertEquals(599_000L, limiter.lockRemainingMillis("a", 3_000L));
        assertEquals(0L, limiter.lockRemainingMillis("b", 3_000L));
        assertEquals(0L, limiter.lockRemainingMillis("a", 602_001L));
    }

    @Test
    void limiterForgetsFailuresOutsideTheWindow() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, 10_000L, 60_000L);

        assertFalse(limiter.recordFailure("a", 0L));
        assertFalse(limiter.recordFailure("a", 20_000L));
        limiter.recordSuccess("a");
        assertFalse(limiter.recordFailure("a", 21_000L));
    }

    @Test
    void accountListsConfiguredMethods() {
        AuthAccount account = new AuthAccount("Steve", 0L);
        assertTrue(account.configuredMethods().isEmpty());

        account.password(new PasswordHasher.HashedSecret("pbkdf2-sha256", 1000, "c2FsdA==", "aGFzaA=="));
        account.trust("Alex");
        assertTrue(account.trusts("ALEX"));
        assertEquals(java.util.Set.of(AuthMethod.PASSWORD, AuthMethod.FRIEND), account.configuredMethods());
    }
}
