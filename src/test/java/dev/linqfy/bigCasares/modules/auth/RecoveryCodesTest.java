package dev.linqfy.bigCasares.modules.auth;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecoveryCodesTest {

    @Test
    void generatesDistinctReadableCodes() {
        List<String> codes = RecoveryCodes.generate(new SecureRandom(), 10);

        assertEquals(10, new HashSet<>(codes).size());
        codes.forEach(code -> assertTrue(code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"), code));
    }

    @Test
    void digestIgnoresCaseAndSeparators() {
        assertEquals(RecoveryCodes.digest("ABCD-EF23"), RecoveryCodes.digest("abcdef23"));
        assertTrue(RecoveryCodes.looksLikeCode("abcd ef23"));
        assertFalse(RecoveryCodes.looksLikeCode("123456"));
        assertFalse(RecoveryCodes.looksLikeCode("ABCD-EF0O"));
    }
}
