package dev.linqfy.bigCasares.modules.finance;

import java.util.Objects;
import java.util.UUID;

public record LedgerAccount(String id, AccountKind kind) {

    public LedgerAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        boolean valid = switch (kind) {
            case FEES -> id.equals("fees");
            case EXTERNAL -> id.equals("external");
            case WALLET -> hasUuid(id, "wallet:");
            case POOL -> hasUuid(id, "pool:");
            case ISSUANCE -> hasUuid(id, "issuance:");
        };
        if (!valid) {
            throw new ExchangeException(ExchangeException.Code.INVALID_REQUEST, "Account identity does not match kind");
        }
    }

    public static LedgerAccount wallet(UUID player) {
        return new LedgerAccount("wallet:" + Objects.requireNonNull(player), AccountKind.WALLET);
    }

    public static LedgerAccount pool(UUID market) {
        return new LedgerAccount("pool:" + Objects.requireNonNull(market), AccountKind.POOL);
    }

    public static LedgerAccount fees() {
        return new LedgerAccount("fees", AccountKind.FEES);
    }

    public static LedgerAccount external() {
        return new LedgerAccount("external", AccountKind.EXTERNAL);
    }

    public static LedgerAccount issuance(UUID market) {
        return new LedgerAccount("issuance:" + Objects.requireNonNull(market), AccountKind.ISSUANCE);
    }

    private static boolean hasUuid(String id, String prefix) {
        if (!id.startsWith(prefix)) {
            return false;
        }
        try {
            return UUID.fromString(id.substring(prefix.length())).toString().equals(id.substring(prefix.length()));
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }
}
