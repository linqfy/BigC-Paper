package dev.linqfy.bigCasares.modules.missions;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardClaimsTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void cobrarTodoPaysEachCompletedMissionOnceAcrossBothTabs() {
        Wallet wallet = new Wallet();
        List<MissionPlayerState> saved = new ArrayList<>();
        var state = state(Map.of("a", done("a", 120, false), "b", open("b", 50)), Map.of("w", done("w", 1000, false)));

        var first = RewardClaims.claim(state, wallet, saved::add);
        assertEquals(RewardClaims.Outcome.PAID, first.outcome());
        assertEquals(1120.0, first.paid(), 1e-9);
        assertEquals(1120.0, wallet.balance, 1e-9);
        assertEquals(2, saved.size(), "each receipt is stored before its deposit");
        assertTrue(first.state().dailyAssignments().get("a").snapshot().claimed());
        assertFalse(first.state().dailyAssignments().get("b").snapshot().claimed());

        // A double click, or the menu's claim after "Cobrar todo", finds nothing left to pay.
        var again = RewardClaims.claim(first.state(), wallet, saved::add);
        assertEquals(0.0, again.paid(), 1e-9);
        assertEquals(0, again.count());
        assertEquals(1120.0, wallet.balance, 1e-9);
    }

    @Test
    void aClaimFollowedByCobrarTodoOnlyPaysTheNewlyCompletedMission() {
        Wallet wallet = new Wallet();
        var single = RewardClaims.claim(state(Map.of("a", done("a", 120, false), "b", open("b", 50)), Map.of()), wallet, s -> {});
        var completedLater = new LinkedHashMap<>(single.state().dailyAssignments());
        completedLater.put("b", done("b", 50, false));
        var later = RewardClaims.claim(state(completedLater, Map.of()), wallet, s -> {});
        assertEquals(50.0, later.paid(), 1e-9);
        assertEquals(170.0, wallet.balance, 1e-9);
    }

    @Test
    void anUnsavedReceiptPaysNothing() {
        Wallet wallet = new Wallet();
        var state = state(Map.of("a", done("a", 120, false)), Map.of());
        var result = RewardClaims.claim(state, wallet, s -> { throw new IllegalStateException("disk full"); });
        assertEquals(RewardClaims.Outcome.NOT_SAVED, result.outcome());
        assertEquals(0.0, wallet.balance, 1e-9);
        assertFalse(result.state().dailyAssignments().get("a").snapshot().claimed());
    }

    @Test
    void aRefusedDepositWithdrawsTheReceiptSoTheRewardStaysClaimable() {
        Wallet wallet = new Wallet();
        wallet.refuse = true;
        List<MissionPlayerState> saved = new ArrayList<>();
        var state = state(Map.of("a", done("a", 120, false)), Map.of());
        var result = RewardClaims.claim(state, wallet, saved::add);
        assertEquals(RewardClaims.Outcome.REFUSED, result.outcome());
        assertEquals(0.0, wallet.balance, 1e-9);
        assertFalse(result.state().dailyAssignments().get("a").snapshot().claimed());
        assertFalse(saved.getLast().dailyAssignments().get("a").snapshot().claimed(), "the stored file no longer claims it");
        assertTrue(result.stored());
    }

    /** The receipt could not be withdrawn after a refusal: the result says so, and the reward is still claimable. */
    @Test
    void aRefusalWhoseReceiptCannotBeWithdrawnIsReportedAsNotStored() {
        Wallet wallet = new Wallet();
        wallet.refuse = true;
        List<MissionPlayerState> disk = new ArrayList<>();
        var state = state(Map.of("a", done("a", 120, false)), Map.of());
        var result = RewardClaims.claim(state, wallet, saved -> {
            if (!disk.isEmpty()) throw new IllegalStateException("disk full");
            disk.add(saved);
        });
        assertEquals(RewardClaims.Outcome.REFUSED, result.outcome());
        assertFalse(result.stored(), "the file still claims a reward that was never paid");
        assertTrue(disk.getLast().dailyAssignments().get("a").snapshot().claimed());
        assertFalse(result.state().dailyAssignments().get("a").snapshot().claimed());

        wallet.refuse = false;
        var retry = RewardClaims.claim(result.state(), wallet, saved -> {});
        assertEquals(120.0, retry.paid(), 1e-9, "the reward stays claimable from the state kept in memory");
    }

    /** Vault failed without saying whether it paid: the reward is never paid a second time. */
    @Test
    void anUnconfirmedDepositKeepsTheReceiptAndALateRetryPaysNothing() {
        Wallet wallet = new Wallet();
        wallet.error = true;
        var daily = new LinkedHashMap<String, MissionAssignment>();
        daily.put("a", done("a", 120, false));
        daily.put("b", done("b", 30, false));
        var result = RewardClaims.claim(state(daily, Map.of()), wallet, s -> {});
        assertEquals(RewardClaims.Outcome.UNCONFIRMED, result.outcome());
        assertEquals("a", result.failedMission());
        assertTrue(result.state().dailyAssignments().get("a").snapshot().claimed());
        assertFalse(result.state().dailyAssignments().get("b").snapshot().claimed(), "the run stops at the doubtful payment");

        wallet.error = false;
        var retry = RewardClaims.claim(result.state(), wallet, s -> {});
        assertEquals(30.0, retry.paid(), 1e-9, "only the mission that was never attempted is paid");
        assertEquals(30.0, wallet.balance, 1e-9);
    }

    /** The real gateway: Vault's failure answer is a refusal; an exception from the provider is not. */
    @Test
    void theVaultGatewayReportsARefusalDistinctlyFromOtherErrors() {
        var answer = new EconomyResponse[1];
        var error = new RuntimeException[1];
        Economy economy = (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(), new Class<?>[] {Economy.class},
            (proxy, method, args) -> {
                if (method.getName().equals("depositPlayer") && args.length == 2 && args[0] instanceof OfflinePlayer) {
                    if (error[0] != null) throw error[0];
                    return answer[0];
                }
                throw new UnsupportedOperationException(method.getName());
            });
        OfflinePlayer offline = (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(),
            new Class<?>[] {OfflinePlayer.class}, (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            });
        var gateway = new VaultEconomyGateway(id -> offline, economy);

        answer[0] = new EconomyResponse(120, 0, EconomyResponse.ResponseType.FAILURE, "account locked");
        assertThrows(RewardClaims.Refused.class, () -> gateway.deposit(PLAYER, 120));

        answer[0] = new EconomyResponse(120, 120, EconomyResponse.ResponseType.SUCCESS, null);
        assertDoesNotThrow(() -> gateway.deposit(PLAYER, 120));

        error[0] = new IllegalStateException("database timeout");
        var unknown = assertThrows(IllegalStateException.class, () -> gateway.deposit(PLAYER, 120));
        assertFalse(unknown instanceof RewardClaims.Refused, "an error is not a refusal: the receipt must stay");
    }

    private static MissionAssignment done(String id, double reward, boolean claimed) {
        return assignment(id, reward, new MissionProgressSnapshot(1, true, claimed));
    }

    private static MissionAssignment open(String id, double reward) {
        return assignment(id, reward, new MissionProgressSnapshot(0, false, false));
    }

    private static MissionAssignment assignment(String id, double reward, MissionProgressSnapshot snapshot) {
        return new MissionAssignment(new MissionDefinition(id, MissionScope.DAILY, id, "", MissionType.PLACE_BLOCK_COUNT,
            1, reward, 1, true, Map.of()), snapshot);
    }

    private static MissionPlayerState state(Map<String, MissionAssignment> daily, Map<String, MissionAssignment> weekly) {
        Instant now = Instant.parse("2026-11-27T12:00:00Z");
        return new MissionPlayerState(PLAYER, now, now.plusSeconds(3600), now.plusSeconds(86400),
            new LinkedHashMap<>(daily), new LinkedHashMap<>(weekly), null);
    }

    private static final class Wallet implements RewardGateway {
        double balance;
        boolean refuse;
        boolean error;

        @Override
        public void deposit(UUID playerId, double amount) {
            assertEquals(PLAYER, playerId);
            if (refuse) throw new RewardClaims.Refused("refused");
            if (error) throw new RuntimeException("timeout");
            balance += amount;
        }
    }
}
