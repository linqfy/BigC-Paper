package dev.linqfy.bigCasares.modules.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordLinkServiceTest {

    @TempDir
    Path folder;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    @Test
    void validCodeLinksAndPersists() {
        File file = folder.resolve("links.yml").toFile();
        DiscordLinkService service = new DiscordLinkService(file, Logger.getAnonymousLogger(), clock);
        UUID player = UUID.randomUUID();

        assertTrue(service.verifyCode(service.generateCode(player), 42L));

        DiscordLinkService reloaded = new DiscordLinkService(file, Logger.getAnonymousLogger(), clock);
        assertEquals(Optional.of(42L), reloaded.getDiscordId(player));
    }

    @Test
    void expiredCodeIsRejected() {
        DiscordLinkService service = service();
        String code = service.generateCode(UUID.randomUUID());

        clock.advance(DiscordLinkService.CODE_LIFETIME.plusSeconds(1));

        assertFalse(service.verifyCode(code, 42L));
    }

    @Test
    void repeatedWrongGuessesLockOutTheDiscordUser() {
        DiscordLinkService service = service();
        UUID victim = UUID.randomUUID();
        String code = service.generateCode(victim);
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int attempt = 0; attempt < DiscordLinkService.MAX_FAILED_ATTEMPTS; attempt++) {
            assertFalse(service.verifyCode(wrong, 7L));
        }

        assertFalse(service.verifyCode(code, 7L));
        assertFalse(service.isLinked(victim));
        assertTrue(service.verifyCode(code, 8L));
    }

    private DiscordLinkService service() {
        return new DiscordLinkService(folder.resolve("links.yml").toFile(), Logger.getAnonymousLogger(), clock);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
