package dev.linqfy.bigCasares.modules.forex;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class FakeBank implements ForexBank {

    private final Map<UUID, Long> balances = new HashMap<>();

    FakeBank give(UUID player, long amount) {
        balances.merge(player, amount, Long::sum);
        return this;
    }

    @Override
    public long balance(UUID player) {
        return balances.getOrDefault(player, 0L);
    }

    @Override
    public boolean withdraw(UUID player, long amount) {
        if (balance(player) < amount) {
            return false;
        }
        balances.put(player, balance(player) - amount);
        return true;
    }

    @Override
    public boolean deposit(UUID player, long amount) {
        balances.merge(player, amount, Long::sum);
        return true;
    }
}
