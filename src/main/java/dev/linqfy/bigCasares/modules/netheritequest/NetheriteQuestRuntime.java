package dev.linqfy.bigCasares.modules.netheritequest;

import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.Activation;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.ActiveQuest;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.PendingSoul;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.PlayerView;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.Progress;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.ProgressStatus;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Applies the quest rules to live players: inventories, XP levels, rewards and messages. */
final class NetheriteQuestRuntime {

    /** Blocks between walking progress notices. */
    private static final int WALK_NOTICE_BLOCKS = 500;

    private final Server server;
    private final Logger logger;
    private final NetheriteQuestBook book;
    private final NetheriteQuestStore store;
    private final BossSoulItem souls;
    private final LongSupplier clock;
    private boolean dirty;

    NetheriteQuestRuntime(Server server, Logger logger, NetheriteQuestBook book, NetheriteQuestStore store,
                          BossSoulItem souls, LongSupplier clock) {
        this.server = server;
        this.logger = logger;
        this.book = book;
        this.store = store;
        this.souls = souls;
        this.clock = clock;
    }

    NetheriteQuestBook book() {
        return book;
    }

    BossSoulItem souls() {
        return souls;
    }

    // ---------------------------------------------------------------- activation

    void activate(Player player, ArmorQuestPiece piece) {
        UUID playerId = player.getUniqueId();
        List<String> held = heldSoulBosses(player.getInventory());
        Activation activation = book.activate(playerId, piece, held, player.getLevel(), clock.getAsLong());
        switch (activation.status()) {
            case ALREADY_ACTIVE -> player.sendMessage("§cYa tenés activa la misión del " + piece.displayName() + ".");
            case NO_SOUL -> player.sendMessage("§cNecesitás un §5Alma de Boss §cpara activar esta misión. "
                + "Se consigue quedando en el top 3 de daño de un boss.");
            case SOUL_ALREADY_USED -> player.sendMessage("§cTus almas son de bosses que ya usaste para otra pieza. "
                + "Cada pieza necesita el alma de un boss distinto.");
            case NOT_ENOUGH_LEVELS -> player.sendMessage("§cActivar esta misión cuesta §e" + activation.cost()
                + " niveles§c y tenés §e" + player.getLevel() + "§c.");
            case STARTED -> {
                if (!removeOneSoul(player.getInventory(), activation.soulBoss())) {
                    // the soul vanished between the scan and the removal: undo instead of starting for free
                    book.cancelActivation(playerId, piece);
                    player.sendMessage("§cNo se encontró el alma. Probá de nuevo.");
                    return;
                }
                if (activation.cost() > 0) {
                    player.giveExpLevels(-activation.cost());
                }
                dirty = true;
                player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.8f);
                player.sendMessage("§6§lMisión activada: §e" + piece.displayName());
                player.sendMessage("§7Objetivo: §f" + objective(piece) + "§7. Tenés §f"
                    + book.settings().durationHours() + " horas§7.");
                player.sendMessage("§7Costo: §e" + (activation.cost() == 0 ? "gratis" : activation.cost() + " niveles")
                    + "§7. La próxima cuesta §e" + book.nextActivationCost(playerId) + " niveles§7.");
            }
        }
    }

    // ---------------------------------------------------------------- progress

    void recordKill(Player killer, String entityKey) {
        UUID playerId = killer.getUniqueId();
        for (ArmorQuestPiece piece : ArmorQuestPiece.values()) {
            if (piece.killTarget().map(entityKey::equals).orElse(false)) {
                book.addProgress(playerId, piece, 1.0, clock.getAsLong())
                    .ifPresent(progress -> announce(killer, progress, true));
            }
        }
    }

    boolean walking(UUID playerId) {
        return book.hasActive(playerId, ArmorQuestPiece.BOOTS);
    }

    void recordWalk(Player player, double blocks) {
        book.addProgress(player.getUniqueId(), ArmorQuestPiece.BOOTS, blocks, clock.getAsLong())
            .ifPresent(progress -> announce(player, progress,
                (int) (progress.current() / WALK_NOTICE_BLOCKS) > (int) (progress.previous() / WALK_NOTICE_BLOCKS)));
    }

    private void announce(Player player, Progress progress, boolean notice) {
        dirty = true;
        ArmorQuestPiece piece = progress.piece();
        switch (progress.status()) {
            case EXPIRED -> player.sendMessage("§cSe venció el tiempo de la misión del " + piece.displayName() + ".");
            case COMPLETED -> complete(player, piece);
            case ADVANCED -> {
                if (notice) {
                    player.sendActionBar("§6" + piece.displayName() + ": §e" + (long) progress.current()
                        + "§7/§e" + progress.target() + " §7" + piece.objectiveName());
                }
            }
        }
    }

    private void complete(Player player, ArmorQuestPiece piece) {
        giveOrDrop(player, armor(piece));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.sendMessage("§6§l¡Misión completada! §eRecibiste: " + piece.displayName() + "§e.");
        server.broadcastMessage("§6" + player.getName() + " §7consiguió el §e" + piece.displayName() + "§7.");
    }

    void expireQuests() {
        long now = clock.getAsLong();
        for (NetheriteQuestBook.Expired expired : book.expireAll(now)) {
            dirty = true;
            Player player = server.getPlayer(expired.playerId());
            if (player != null) {
                player.sendMessage("§cSe venció el tiempo de la misión del " + expired.piece().displayName() + ".");
            }
        }
    }

    // ---------------------------------------------------------------- souls

    /** Gives one soul of {@code bossId} to each of the top damage dealers, or keeps it until they log in. */
    void awardBossSouls(String bossId, String bossName, List<UUID> topDamagers) {
        Set<UUID> winners = new LinkedHashSet<>(topDamagers);
        int placement = 0;
        for (UUID playerId : winners) {
            if (++placement > 3) {
                break;
            }
            Player player = server.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                giveOrDrop(player, souls.create(bossId, bossName, 1));
                player.sendMessage("§5Recibiste un §lAlma de " + bossName + "§5 por quedar #" + placement
                    + " en daño.");
            } else {
                book.addPendingSoul(playerId, bossId, bossName, 1);
            }
            dirty = true;
        }
    }

    void deliverPending(Player player) {
        Map<String, PendingSoul> pending = book.drainPendingSouls(player.getUniqueId());
        if (pending.isEmpty()) {
            return;
        }
        dirty = true;
        pending.forEach((bossId, soul) -> {
            giveOrDrop(player, souls.create(bossId, soul.bossName(), soul.amount()));
            player.sendMessage("§5Te guardamos §l" + soul.amount() + "x Alma de " + soul.bossName() + "§5.");
        });
    }

    List<String> heldSoulBosses(PlayerInventory inventory) {
        List<String> held = new ArrayList<>();
        for (ItemStack stack : inventory.getStorageContents()) {
            souls.bossOf(stack).filter(boss -> !held.contains(boss)).ifPresent(held::add);
        }
        return held;
    }

    private boolean removeOneSoul(PlayerInventory inventory, String bossId) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (souls.bossOf(stack).map(bossId::equals).orElse(false)) {
                if (stack.getAmount() <= 1) {
                    contents[slot] = null;
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                }
                inventory.setStorageContents(contents);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- status

    List<String> status(UUID playerId) {
        long now = clock.getAsLong();
        book.expireAll(now);
        PlayerView view = book.view(playerId);
        List<String> lines = new ArrayList<>();
        lines.add("§6§lMisiones de Netherita");
        for (ArmorQuestPiece piece : ArmorQuestPiece.values()) {
            ActiveQuest active = view.active().get(piece);
            String line;
            if (active != null) {
                line = "§e" + piece.displayName() + "§7: §f" + (long) active.progress() + "/"
                    + book.settings().target(piece) + " " + piece.objectiveName() + " §7(quedan §f"
                    + duration(book.remainingMillis(active, now)) + "§7)";
            } else if (view.souls().containsKey(piece)) {
                line = "§a" + piece.displayName() + "§7: completada §8(alma: " + view.souls().get(piece) + ")";
            } else {
                line = "§7" + piece.displayName() + ": §f" + objective(piece);
            }
            lines.add(line);
        }
        lines.add("§7Próxima activación: §e" + book.nextActivationCost(playerId) + " niveles§7 + 1 Alma de Boss.");
        lines.add("§7Usá §f/netherita activar <casco|peto|pantalones|botas>§7.");
        return lines;
    }

    String objective(ArmorQuestPiece piece) {
        int target = book.settings().target(piece);
        return piece.killTarget().isPresent()
            ? "matar " + target + " " + piece.objectiveName()
            : "caminar " + target + " bloques";
    }

    private static String duration(long millis) {
        long minutes = millis / 60_000L;
        return String.format(Locale.ROOT, "%dh %02dm", minutes / 60L, minutes % 60L);
    }

    // ---------------------------------------------------------------- items

    static ItemStack armor(ArmorQuestPiece piece) {
        Material material = switch (piece) {
            case HELMET -> Material.NETHERITE_HELMET;
            case CHESTPLATE -> Material.NETHERITE_CHESTPLATE;
            case LEGGINGS -> Material.NETHERITE_LEGGINGS;
            case BOOTS -> Material.NETHERITE_BOOTS;
        };
        Enchantment enchantment = switch (piece) {
            case HELMET -> Enchantment.RESPIRATION;
            case CHESTPLATE -> Enchantment.BLAST_PROTECTION;
            case LEGGINGS -> Enchantment.SWIFT_SNEAK;
            case BOOTS -> Enchantment.UNBREAKING;
        };
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.addEnchant(enchantment, piece.enchantmentLevel(), true);
        meta.setLore(List.of("§7Ganada en una misión de netherita."));
        stack.setItemMeta(meta);
        return stack;
    }

    void giveOrDrop(Player player, ItemStack stack) {
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    // ---------------------------------------------------------------- storage

    void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    void save() {
        try {
            store.save(book);
            dirty = false;
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Netherita] No se pudieron guardar las misiones.", failure);
        }
    }

    void markDirty() {
        dirty = true;
    }
}
