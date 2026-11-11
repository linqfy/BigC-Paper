package dev.linqfy.bigCasares.modules.auctionhouse;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import java.util.UUID;

final class VaultAuctionBank implements AuctionBank {

    private final Server server;
    private final Economy economy;

    VaultAuctionBank(Server server, Economy economy) {
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
        if (economy.getBalance(offline) < amount) {
            return false;
        }
        return economy.withdrawPlayer(offline, amount).transactionSuccess();
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
