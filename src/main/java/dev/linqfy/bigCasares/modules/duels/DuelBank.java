package dev.linqfy.bigCasares.modules.duels;

import java.util.UUID;

/** Whole units of the server economy used for wagers. */
interface DuelBank {

    long balance(UUID player);

    boolean withdraw(UUID player, long amount);

    boolean deposit(UUID player, long amount);
}
