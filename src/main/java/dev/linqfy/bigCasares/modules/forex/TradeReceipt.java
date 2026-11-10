package dev.linqfy.bigCasares.modules.forex;

/**
 * Result of a trade. {@code money} is what the player paid (buy) or received (sell); {@code fees} is what the
 * creator, the stakers and the platform took; {@code shortfall} is money the pool could not pay a seller.
 */
public record TradeReceipt(String asset, boolean buy, double quantity, long money, long fees, double priceAfter,
                           long shortfall) {
}
