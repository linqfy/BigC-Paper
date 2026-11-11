package dev.linqfy.bigCasares.modules.duels;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import java.util.UUID;

final class VaultDuelBank implements DuelBank {

    private final Server server;
    private final Economy economy;

    VaultDuelBank(Server server, Economy economy) {
        this.server = server;
        this.economy = economy;
    }

    @Override
    public long balance(UUID player) {
        return (long) Math.floor(economy.getBalance(offline(player)));
    }

    @Override
    public boolean withdraw(UUID player, long amount) {
        if (amount <= 0) {
            return amount == 0;
        }
        OfflinePlayer offline = offline(player);
        return economy.getBalance(offline) >= amount && economy.withdrawPlayer(offline, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(UUID player, long amount) {
        if (amount <= 0) {
            return amount == 0;
        }
        return economy.depositPlayer(offline(player), amount).transactionSuccess();
    }

    private OfflinePlayer offline(UUID player) {
        return server.getOfflinePlayer(player);
    }
}
