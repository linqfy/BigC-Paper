package dev.linqfy.bigCasares.modules.duels;

import java.util.UUID;

/** Takes both stakes before the fight and pays them out afterwards. */
final class DuelEscrow {

    private final DuelBank bank;

    DuelEscrow(DuelBank bank) {
        this.bank = bank;
    }

    boolean available() {
        return bank != null;
    }

    boolean canAfford(UUID player, long wager) {
        return wager <= 0 || (bank != null && bank.balance(player) >= wager);
    }

    /** Withdraws the wager from both players; if either fails nothing stays taken. */
    boolean collect(UUID first, UUID second, long wager) {
        if (wager <= 0) {
            return true;
        }
        if (bank == null || !bank.withdraw(first, wager)) {
            return false;
        }
        if (!bank.withdraw(second, wager)) {
            bank.deposit(first, wager);
            return false;
        }
        return true;
    }

    /** The winner takes both stakes. */
    void payWinner(UUID winner, long wager) {
        if (wager > 0 && bank != null) {
            bank.deposit(winner, wager * 2);
        }
    }

    void refund(UUID player, long wager) {
        if (wager > 0 && bank != null) {
            bank.deposit(player, wager);
        }
    }
}
