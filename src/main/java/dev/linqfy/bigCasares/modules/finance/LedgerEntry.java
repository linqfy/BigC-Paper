package dev.linqfy.bigCasares.modules.finance;

import java.util.UUID;

public record LedgerEntry(UUID operationId, LedgerAccount account, String asset, long delta) {
}
