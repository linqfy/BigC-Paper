package dev.linqfy.bigCasares.modules.duels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.BoundingBox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Runs challenges, the kit draft and the fights. Players keep their own inventory during the draft; once
 * both have chosen, their state is saved to disk, they get the kit and are taken to an arena in the duel world.
 * When the fight ends they are sent back exactly as they were.
 */
final class DuelService {

    private final Server server;
    private final Logger logger;
    private final DuelSettings settings;
    private final DuelArenaWorld arenas;
    private final DuelStateStore store;
    private final DuelEscrow escrow;
    private final DuelKitItems kitItems;
    private final LongSupplier clock;
    private final Map<UUID, DuelSession> sessions = new HashMap<>();
    /** Pending challenges by target, then by challenger. */
    private final Map<UUID, Map<UUID, DuelRequest>> requests = new HashMap<>();
    /** Players whose saved state must be given back as soon as they are online and alive. */
    private final Set<UUID> pendingRestore = new HashSet<>();
    private final Map<DuelSession, Long> returning = new LinkedHashMap<>();

    DuelService(Server server, Logger logger, DuelSettings settings, DuelArenaWorld arenas, DuelStateStore store,
                DuelEscrow escrow, DuelKitItems kitItems, LongSupplier clock) {
        this.server = server;
        this.logger = logger;
        this.settings = settings;
        this.arenas = arenas;
        this.store = store;
        this.escrow = escrow;
        this.kitItems = kitItems;
        this.clock = clock;
    }

    DuelSettings settings() {
        return settings;
    }

    DuelSession sessionOf(UUID player) {
        return sessions.get(player);
    }

    boolean inArena(Player player) {
        DuelSession session = sessions.get(player.getUniqueId());
        return session != null && session.inArena();
    }

    boolean isDuelWorld(World world) {
        return arenas.isDuelWorld(world);
    }

    // ---------------------------------------------------------------- challenges

    void challenge(Player challenger, Player target, String kitId, DuelArenaSize arena, long wager) {
        if (challenger.equals(target)) {
            challenger.sendMessage(Component.text("No podés retarte a vos mismo.", NamedTextColor.RED));
            return;
        }
        if (busy(challenger.getUniqueId()) || busy(target.getUniqueId())) {
            challenger.sendMessage(Component.text("Alguno de los dos ya está en un duelo.", NamedTextColor.RED));
            return;
        }
        if (settings.kits().isEmpty()) {
            challenger.sendMessage(Component.text("No hay kits de duelo configurados.", NamedTextColor.RED));
            return;
        }
        if (kitId != null && !settings.kits().containsKey(kitId)) {
            challenger.sendMessage(Component.text("Ese kit no existe. Kits: " + String.join(", ", settings.kitIds()),
                NamedTextColor.RED));
            return;
        }
        if (wager > 0) {
            if (!escrow.available()) {
                challenger.sendMessage(Component.text("Las apuestas necesitan Vault y una economía.", NamedTextColor.RED));
                return;
            }
            if (wager > settings.maxWager()) {
                challenger.sendMessage(Component.text("La apuesta máxima es $" + settings.maxWager() + ".",
                    NamedTextColor.RED));
                return;
            }
            if (!escrow.canAfford(challenger.getUniqueId(), wager)) {
                challenger.sendMessage(Component.text("No tenés $" + wager + " para apostar.", NamedTextColor.RED));
                return;
            }
        }
        DuelRequest request = new DuelRequest(challenger.getUniqueId(), target.getUniqueId(), kitId, arena, wager,
            clock.getAsLong() + settings.requestSeconds() * 1000L);
        requests.computeIfAbsent(target.getUniqueId(), ignored -> new LinkedHashMap<>())
            .put(challenger.getUniqueId(), request);
        String terms = describe(request);
        challenger.sendMessage(Component.text("Desafiaste a " + target.getName() + " (" + terms + ").",
            NamedTextColor.GOLD));
        String accept = "/duel aceptar " + challenger.getName();
        String deny = "/duel rechazar " + challenger.getName();
        target.sendMessage(Component.text("⚔ " + challenger.getName() + " te desafía a un duelo (" + terms + "). ",
                NamedTextColor.GOLD)
            .append(Component.text("[Aceptar]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand(accept))
                .hoverEvent(HoverEvent.showText(Component.text(accept))))
            .append(Component.text(" "))
            .append(Component.text("[Rechazar]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand(deny))
                .hoverEvent(HoverEvent.showText(Component.text(deny)))));
        target.playSound(target.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_0, 0.6f, 1.2f);
    }

    void accept(Player target, String challengerName) {
        DuelRequest request = takeRequest(target, challengerName);
        if (request == null) {
            return;
        }
        Player challenger = server.getPlayer(request.challenger());
        if (challenger == null) {
            target.sendMessage(Component.text("Quien te desafió ya no está conectado.", NamedTextColor.RED));
            return;
        }
        if (busy(challenger.getUniqueId()) || busy(target.getUniqueId())) {
            target.sendMessage(Component.text("Alguno de los dos ya está en un duelo.", NamedTextColor.RED));
            return;
        }
        if (!escrow.canAfford(challenger.getUniqueId(), request.wager())
            || !escrow.canAfford(target.getUniqueId(), request.wager())
            || !escrow.collect(challenger.getUniqueId(), target.getUniqueId(), request.wager())) {
            Component broke = Component.text("Alguno de los dos no tiene $" + request.wager() + " para la apuesta.",
                NamedTextColor.RED);
            target.sendMessage(broke);
            challenger.sendMessage(broke);
            return;
        }
        DuelKitDraft draft = request.kitId() == null
            ? new DuelKitDraft(settings.kitIds(), challenger.getUniqueId(), target.getUniqueId(),
                () -> ThreadLocalRandom.current().nextDouble())
            : null;
        DuelSession session = new DuelSession(challenger.getUniqueId(), target.getUniqueId(), request.arena(),
            request.wager(), request.kitId(), draft);
        sessions.put(challenger.getUniqueId(), session);
        sessions.put(target.getUniqueId(), session);
        // Nobody else can keep a challenge open against someone who is now dueling.
        requests.remove(challenger.getUniqueId());
        requests.remove(target.getUniqueId());
        Component accepted = Component.text("¡Duelo aceptado! " + challenger.getName() + " vs " + target.getName()
            + (request.wager() > 0 ? " por $" + request.wager() : ""), NamedTextColor.GOLD);
        challenger.sendMessage(accepted);
        target.sendMessage(accepted);
        if (draft == null || draft.phase() == DuelKitDraft.Phase.DONE) {
            session.kitId(draft == null ? request.kitId() : draft.result());
            begin(session);
        } else {
            session.phase(DuelSession.Phase.DRAFT, clock.getAsLong() + settings.draftSeconds() * 1000L);
            openDraft(session);
        }
    }

    void deny(Player target, String challengerName) {
        DuelRequest request = takeRequest(target, challengerName);
        if (request == null) {
            return;
        }
        target.sendMessage(Component.text("Rechazaste el duelo.", NamedTextColor.GRAY));
        Player challenger = server.getPlayer(request.challenger());
        if (challenger != null) {
            challenger.sendMessage(Component.text(target.getName() + " rechazó tu duelo.", NamedTextColor.RED));
        }
    }

    /** Withdraws outgoing challenges, or abandons a duel still in the kit menu. */
    void cancel(Player player) {
        DuelSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            if (session.phase() == DuelSession.Phase.DRAFT) {
                abortDraft(session, player.getName() + " canceló el duelo.");
            } else {
                player.sendMessage(Component.text("No podés cancelar un duelo en curso.", NamedTextColor.RED));
            }
            return;
        }
        int removed = 0;
        for (Map<UUID, DuelRequest> byChallenger : requests.values()) {
            if (byChallenger.remove(player.getUniqueId()) != null) {
                removed++;
            }
        }
        player.sendMessage(Component.text(removed == 0 ? "No tenés desafíos pendientes."
            : "Cancelaste " + removed + " desafío(s).", NamedTextColor.GRAY));
    }

    private DuelRequest takeRequest(Player target, String challengerName) {
        Map<UUID, DuelRequest> pending = requests.get(target.getUniqueId());
        if (pending == null || pending.isEmpty()) {
            target.sendMessage(Component.text("No tenés desafíos pendientes.", NamedTextColor.RED));
            return null;
        }
        DuelRequest request;
        if (challengerName == null) {
            if (pending.size() > 1) {
                target.sendMessage(Component.text("Tenés varios desafíos: indicá el jugador.", NamedTextColor.RED));
                return null;
            }
            request = pending.values().iterator().next();
        } else {
            Player challenger = server.getPlayerExact(challengerName);
            request = challenger == null ? null : pending.get(challenger.getUniqueId());
        }
        if (request == null || request.expired(clock.getAsLong())) {
            target.sendMessage(Component.text("Ese desafío no existe o expiró.", NamedTextColor.RED));
            if (request != null) {
                pending.remove(request.challenger());
            }
            return null;
        }
        pending.remove(request.challenger());
        return request;
    }

    private boolean busy(UUID player) {
        return sessions.containsKey(player) || pendingRestore.contains(player) || store.has(player);
    }

    private String describe(DuelRequest request) {
        String kit = request.kitId() == null ? "kit a elegir"
            : "kit " + settings.kits().get(request.kitId()).displayName();
        return kit + ", arena " + request.arena().displayName()
            + (request.wager() > 0 ? ", apuesta $" + request.wager() : "");
    }

    // ---------------------------------------------------------------- draft

    void openDraft(DuelSession session) {
        for (UUID id : session.players()) {
            Player player = server.getPlayer(id);
            if (player != null) {
                openDraft(session, player);
            }
        }
    }

    void openDraft(DuelSession session, Player player) {
        DuelDraftMenu menu = new DuelDraftMenu(session, player.getUniqueId());
        menu.render(settings, kitItems, secondsLeft(session));
        player.openInventory(menu.getInventory());
    }

    void clickDraft(Player player, DuelDraftMenu menu, int slot) {
        DuelSession session = menu.session();
        if (sessions.get(player.getUniqueId()) != session || session.phase() != DuelSession.Phase.DRAFT) {
            player.closeInventory();
            return;
        }
        String kit = menu.kitAt(slot);
        if (kit == null) {
            return;
        }
        DuelKitDraft draft = session.draft();
        DuelKitDraft.Phase before = draft.phase();
        boolean accepted = before == DuelKitDraft.Phase.BAN
            ? draft.ban(player.getUniqueId(), kit)
            : draft.pick(player.getUniqueId(), kit);
        if (!accepted) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        afterDraftChange(session, before);
    }

    private void afterDraftChange(DuelSession session, DuelKitDraft.Phase before) {
        DuelKitDraft draft = session.draft();
        if (draft.phase() == DuelKitDraft.Phase.DONE) {
            session.kitId(draft.result());
            announceKit(session);
            begin(session);
            return;
        }
        if (draft.phase() != before) {
            session.phaseEndsAt(clock.getAsLong() + settings.draftSeconds() * 1000L);
        }
        refreshDraftMenus(session);
    }

    private void refreshDraftMenus(DuelSession session) {
        for (UUID id : session.players()) {
            Player player = server.getPlayer(id);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder(false) instanceof DuelDraftMenu menu
                && menu.session() == session) {
                menu.render(settings, kitItems, secondsLeft(session));
            }
        }
    }

    private void announceKit(DuelSession session) {
        DuelKit kit = settings.kits().get(session.kitId());
        DuelKitDraft draft = session.draft();
        for (UUID id : session.players()) {
            Player player = server.getPlayer(id);
            if (player == null) {
                continue;
            }
            String opponentPick = draft.pickOf(session.opponent(id));
            String opponentBan = draft.banOf(session.opponent(id));
            player.sendMessage(Component.text("Kit del duelo: " + kit.displayName()
                + (opponentBan == null ? "" : " (el rival baneó " + name(opponentBan) + ", eligió "
                    + name(opponentPick) + ")"), NamedTextColor.AQUA));
        }
    }

    private String name(String kitId) {
        DuelKit kit = kitId == null ? null : settings.kits().get(kitId);
        return kit == null ? "-" : kit.displayName();
    }

    private long secondsLeft(DuelSession session) {
        return Math.max(0L, (session.phaseEndsAt() - clock.getAsLong() + 999L) / 1000L);
    }

    private void abortDraft(DuelSession session, String reason) {
        end(session);
        for (UUID id : session.players()) {
            escrow.refund(id, session.wager());
            Player player = server.getPlayer(id);
            if (player != null) {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof DuelDraftMenu) {
                    player.closeInventory();
                }
                player.sendMessage(Component.text(reason + (session.wager() > 0 ? " Se devolvió la apuesta." : ""),
                    NamedTextColor.RED));
            }
        }
    }

    // ---------------------------------------------------------------- fight

    private void begin(DuelSession session) {
        DuelKit kit = settings.kits().get(session.kitId());
        Player first = server.getPlayer(session.first());
        Player second = server.getPlayer(session.second());
        if (kit == null || first == null || second == null || first.isDead() || second.isDead()) {
            abortDraft(session, "El duelo no pudo empezar.");
            return;
        }
        if (arenas.world() == null) {
            logger.severe("[Duelos] No se pudo crear el mundo de duelos " + settings.worldName());
            abortDraft(session, "El mundo de duelos no está disponible.");
            return;
        }
        if (!store.save(first, session.wager())) {
            abortDraft(session, "No se pudo guardar tu inventario; duelo cancelado.");
            return;
        }
        if (!store.save(second, session.wager())) {
            store.restore(first);
            abortDraft(session, "No se pudo guardar tu inventario; duelo cancelado.");
            return;
        }
        int slot = arenas.claimSlot();
        session.slot(slot);
        arenas.build(slot, session.arena());
        prepare(first, kit, arenas.spawn(slot, session.arena(), true));
        prepare(second, kit, arenas.spawn(slot, session.arena(), false));
        session.phase(DuelSession.Phase.COUNTDOWN, clock.getAsLong() + settings.countdownSeconds() * 1000L);
        for (Player player : List.of(first, second)) {
            player.sendMessage(Component.text("Arena " + session.arena().displayName() + " · kit " + kit.displayName()
                + " · rival " + server.getOfflinePlayer(session.opponent(player.getUniqueId())).getName(),
                NamedTextColor.GOLD));
        }
    }

    private void prepare(Player player, DuelKit kit, Location spawn) {
        player.closeInventory();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        kitItems.equip(player, kit);
        player.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(10f);
        player.setExhaustion(0f);
    }

    /** The player took a hit that would kill them, quit or forfeited: the opponent wins. */
    void lose(UUID loser, String reason) {
        DuelSession session = sessions.get(loser);
        if (session == null) {
            return;
        }
        if (session.phase() == DuelSession.Phase.DRAFT) {
            abortDraft(session, reason);
            return;
        }
        if (session.phase() == DuelSession.Phase.ENDING) {
            return;
        }
        finish(session, session.opponent(loser), reason);
    }

    private void finish(DuelSession session, UUID winner, String reason) {
        session.phase(DuelSession.Phase.ENDING, clock.getAsLong() + settings.returnDelaySeconds() * 1000L);
        if (session.wager() > 0) {
            if (winner == null) {
                session.players().forEach(id -> escrow.refund(id, session.wager()));
            } else {
                escrow.payWinner(winner, session.wager());
            }
            session.players().forEach(store::clearEscrow);
        }
        String winnerName = winner == null ? null : server.getOfflinePlayer(winner).getName();
        String loserName = winner == null ? null : server.getOfflinePlayer(session.opponent(winner)).getName();
        for (UUID id : session.players()) {
            Player player = server.getPlayer(id);
            if (player == null) {
                continue;
            }
            Component headline;
            if (winner == null) {
                headline = Component.text("Empate", NamedTextColor.YELLOW);
            } else if (winner.equals(id)) {
                headline = Component.text("¡Victoria!", NamedTextColor.GREEN);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            } else {
                headline = Component.text("Derrota", NamedTextColor.RED);
            }
            player.showTitle(Title.title(headline, Component.text(reason, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(2), Duration.ofMillis(400))));
            String money = session.wager() <= 0 ? ""
                : winner == null ? " Se devolvió la apuesta."
                : winner.equals(id) ? " Ganaste $" + session.wager() * 2 + "." : " Perdiste $" + session.wager() + ".";
            player.sendMessage(Component.text(winner == null ? "Duelo empatado: " + reason + "." + money
                : winnerName + " le ganó a " + loserName + " (" + reason + ")." + money, NamedTextColor.GOLD));
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            if (!player.isDead()) {
                player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
            }
        }
        returning.put(session, session.phaseEndsAt());
    }

    /** Sends both players home and frees the arena. */
    private void sendHome(DuelSession session) {
        end(session);
        if (session.slot() >= 0) {
            arenas.clear(session.slot());
            arenas.releaseSlot(session.slot());
        }
        pendingRestore.addAll(session.players());
        processRestores();
    }

    private void end(DuelSession session) {
        for (UUID id : session.players()) {
            sessions.remove(id, session);
        }
        returning.remove(session);
    }

    // ---------------------------------------------------------------- restore

    void scheduleRestoreIfSaved(UUID player) {
        if (!sessions.containsKey(player) && store.has(player)) {
            pendingRestore.add(player);
        }
    }

    private void processRestores() {
        Iterator<UUID> iterator = pendingRestore.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            if (sessions.containsKey(id)) {
                iterator.remove();
                continue;
            }
            Player player = server.getPlayer(id);
            if (player == null || player.isDead()) {
                continue;
            }
            iterator.remove();
            long owed = store.restore(player);
            if (owed > 0) {
                // The server stopped mid-duel: the stake was never settled, so it goes back.
                escrow.refund(id, owed);
                player.sendMessage(Component.text("Se te devolvió la apuesta de un duelo interrumpido ($" + owed + ").",
                    NamedTextColor.YELLOW));
            }
        }
    }

    // ---------------------------------------------------------------- loop

    void tick() {
        long now = clock.getAsLong();
        for (Map<UUID, DuelRequest> pending : requests.values()) {
            pending.values().removeIf(request -> request.expired(now));
        }
        requests.values().removeIf(Map::isEmpty);
        for (DuelSession session : new ArrayList<>(new HashSet<>(sessions.values()))) {
            switch (session.phase()) {
                case DRAFT -> tickDraft(session, now);
                case COUNTDOWN -> tickCountdown(session, now);
                case FIGHT -> tickFight(session, now);
                case ENDING -> {
                    if (now >= session.phaseEndsAt()) {
                        sendHome(session);
                    }
                }
            }
        }
        processRestores();
    }

    private void tickDraft(DuelSession session, long now) {
        if (now < session.phaseEndsAt()) {
            refreshDraftMenus(session);
            return;
        }
        DuelKitDraft.Phase before = session.draft().phase();
        session.draft().autoComplete();
        afterDraftChange(session, before);
    }

    private void tickCountdown(DuelSession session, long now) {
        long left = (session.phaseEndsAt() - now + 999L) / 1000L;
        if (now >= session.phaseEndsAt()) {
            session.phase(DuelSession.Phase.FIGHT, now + settings.maxDurationSeconds() * 1000L);
            forEachOnline(session, player -> {
                player.showTitle(Title.title(Component.text("¡PELEEN!", NamedTextColor.RED), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(300))));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.4f);
            });
            return;
        }
        forEachOnline(session, player -> player.showTitle(Title.title(
            Component.text(String.valueOf(left), NamedTextColor.GOLD), Component.text("Preparate", NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO))));
    }

    private void tickFight(DuelSession session, long now) {
        if (now >= session.phaseEndsAt()) {
            finish(session, null, "se acabó el tiempo");
            return;
        }
        long left = (session.phaseEndsAt() - now) / 1000L;
        BoundingBox bounds = arenas.bounds(session.slot(), session.arena()).expand(1.0);
        forEachOnline(session, player -> {
            Location at = player.getLocation();
            if (!arenas.isDuelWorld(at.getWorld()) || !bounds.contains(at.toVector())) {
                player.teleport(arenas.spawn(session.slot(), session.arena(), session.first().equals(player.getUniqueId())),
                    PlayerTeleportEvent.TeleportCause.PLUGIN);
                player.setFallDistance(0f);
            }
            player.sendActionBar(Component.text("⚔ " + (left / 60) + ":" + String.format("%02d", left % 60),
                NamedTextColor.YELLOW));
        });
    }

    private void forEachOnline(DuelSession session, Consumer<Player> action) {
        for (UUID id : session.players()) {
            Player player = server.getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        }
    }

    /** Module shutdown: every duel ends without a winner, stakes go back and online players go home now. */
    void shutdown() {
        for (DuelSession session : new ArrayList<>(new HashSet<>(sessions.values()))) {
            if (session.phase() == DuelSession.Phase.DRAFT) {
                abortDraft(session, "Los duelos se reiniciaron.");
            } else {
                if (session.phase() != DuelSession.Phase.ENDING) {
                    finish(session, null, "los duelos se reiniciaron");
                }
                sendHome(session);
            }
        }
        for (Player player : server.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder(false);
            if (holder instanceof DuelDraftMenu) {
                player.closeInventory();
            }
        }
        requests.clear();
        pendingRestore.clear();
    }
}
