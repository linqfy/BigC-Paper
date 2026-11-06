package dev.linqfy.bigCasares.modules.legendary;

/** Delivery must be idempotent across every save/ack crash boundary. */
public final class LegendaryReturnPolicy {
    public enum Decision { ACKNOWLEDGE_EXISTING, REPLACE_RECEIPT, USE_EMPTY_SLOT, KEEP_PENDING }
    private LegendaryReturnPolicy() {}
    public static Decision decide(boolean alreadyOwned, boolean reserved, boolean hasEmptySlot) {
        if (alreadyOwned) return Decision.ACKNOWLEDGE_EXISTING;
        if (reserved) return Decision.REPLACE_RECEIPT;
        if (hasEmptySlot) return Decision.USE_EMPTY_SLOT;
        return Decision.KEEP_PENDING;
    }
}
