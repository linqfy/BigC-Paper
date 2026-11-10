package dev.linqfy.bigCasares.modules.casino.loans;

import dev.linqfy.bigCasares.modules.casino.CasinoBank;
import dev.linqfy.bigCasares.modules.casino.CasinoSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoanCreditRatingTest {

    @TempDir
    File folder;

    private final CasinoBank bank = new CasinoBank() {
        @Override
        public long balance(UUID player) {
            return 0;
        }

        @Override
        public boolean withdraw(UUID player, long amount) {
            return false;
        }

        @Override
        public boolean deposit(UUID player, long amount) {
            return true;
        }

        @Override
        public String format(long amount) {
            return Long.toString(amount);
        }
    };

    @Test
    void donCasaresQuotesEachBorrowerByTheirCreditHistory() {
        LoanService loans = new LoanService(CasinoSettings.defaults().loans(), bank, new File(folder, "loans.yml"),
            Logger.getAnonymousLogger(), () -> 0L);
        UUID prestigious = UUID.randomUUID();
        UUID rugger = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        loans.creditRating(player -> player.equals(prestigious) ? 0.5 : player.equals(rugger) ? 2.0
            : player.equals(broken) ? Double.NaN : 1.0);
        LoanOffer base = new LoanOffer("chico", 1000, 0.40, 60);

        assertEquals(1200, loans.offerFor(prestigious, base).repayment());
        assertEquals(1800, loans.offerFor(rugger, base).repayment());
        assertEquals(1400, loans.offerFor(broken, base).repayment(), "an invalid rating falls back to standard");
        assertEquals(1400, loans.offerFor(UUID.randomUUID(), base).repayment());

        loans.take(rugger, loans.offerFor(rugger, base));
        assertEquals(1800, loans.balance(rugger));
    }
}
