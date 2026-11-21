package dev.linqfy.bigCasares.modules.forex;

import java.util.UUID;

/** Whole units of the server's Vault money. */
public interface ForexBank {

    long balance(UUID player);

    boolean withdraw(UUID player, long amount);

    boolean deposit(UUID player, long amount);
}
