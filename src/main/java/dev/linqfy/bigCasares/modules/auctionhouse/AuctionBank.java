package dev.linqfy.bigCasares.modules.auctionhouse;

import java.util.UUID;

/** Money gateway used by the auction house; amounts are whole currency units. */
public interface AuctionBank {

    long balance(UUID player);

    boolean withdraw(UUID player, long amount);

    boolean deposit(UUID player, long amount);
}
