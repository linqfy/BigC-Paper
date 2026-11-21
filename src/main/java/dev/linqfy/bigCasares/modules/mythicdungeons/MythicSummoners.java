package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Los items del modulo en uso: el Invocador se usa con click derecho sobre el altar de la sala del jefe (y en cualquier
 * otro lado explica por que no anda), y la mesa de crafteo solo acepta Fragmentos e Invocadores en la receta del
 * Invocador (nunca como el item vanilla que tienen de base).
 */
final class MythicSummoners implements Listener {

    private final CustomItemRegistry items;
    private final DungeonLocator locator;
    private final MythicDungeonSettings settings;
    private final MythicBossHost bosses;
    private final DungeonBossRegistry summons;
    private final Logger logger;
    private final NamespacedKey moduleRecipe;

    /**
     * @param moduleRecipe la clave de la receta propia del modulo (cuatro items del material de los fragmentos), la
     *                     que deja craftear aunque la receta exacta del catalogo no reconozca los fragmentos
     */
    MythicSummoners(CustomItemRegistry items, DungeonLocator locator, MythicDungeonSettings settings,
                    MythicBossHost bosses, DungeonBossRegistry summons, Logger logger, NamespacedKey moduleRecipe) {
        this.moduleRecipe = moduleRecipe;
        this.items = items;
        this.locator = locator;
        this.settings = settings;
        this.bosses = bosses;
        this.summons = summons;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack item = event.getItem();
        Optional<MythicCulture> summoner = itemId(item).flatMap(MythicCulture::bySummonerItemId);
        if (summoner.isEmpty() || MythicInteractions.deniedByOthers(event)) {
            return; // otro plugin ya nego usar el item (por ejemplo una proteccion de zona)
        }
        // nada de la interaccion vanilla del material de base ni del bloque clickeado
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Location where = clicked != null ? clicked.getLocation().add(0.5, 0.5, 0.5) : player.getLocation();
        Optional<DungeonBounds> dungeon = locator.dungeonBounds(where);
        if (dungeon.isEmpty() && clicked != null) {
            dungeon = locator.dungeonBounds(player.getLocation());
        }
        MythicCulture culture = summoner.get();
        boolean altarClicked = clicked != null && clicked.getType() == settings.altarMaterial();
        boolean altarInDungeon = altarClicked && dungeon.isPresent()
            && dungeon.get().contains(clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ())
            && locator.isAltarSpot(clicked.getLocation());
        boolean bossAlive = dungeon.isPresent() && summons.alive(dungeon.get().key(), bosses::isActive).isPresent();
        SummonerUse.Outcome outcome = SummonerUse.decide(new SummonerUse.Request(culture,
            dungeon.map(DungeonBounds::culture), altarClicked, altarInDungeon, bossAlive, bosses.available(culture)));
        if (!outcome.success()) {
            player.sendMessage(outcome.message());
            return;
        }
        Location spawn = clicked.getLocation().add(0.5, settings.bossOffsetY() + 1.0, 0.5);
        UUID boss;
        try {
            boss = bosses.spawn(culture, spawn);
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "[Mazmorras] No se pudo invocar a " + culture.requireBoss().id(), failure);
            player.sendMessage("§cEl altar tiembla, pero " + culture.requireBoss().displayName()
                + " no puede aparecer ahora (" + failure.getMessage() + "). El invocador no se gastó.");
            return;
        }
        summons.remember(dungeon.get().key(), new DungeonBossRegistry.Summon(boss, culture,
            spawn.getWorld().getName(), spawn.getX(), spawn.getY(), spawn.getZ(),
            player.getGameMode() != GameMode.CREATIVE ? player.getUniqueId() : null));
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
        player.sendMessage(outcome.message());
        spawn.getWorld().spawnParticle(Particle.SCULK_SOUL, spawn, 80, 1.0, 1.5, 1.0, 0.05);
        spawn.getWorld().playSound(spawn, Sound.ENTITY_WITHER_SPAWN, 1.5F, 0.6F);
    }

    /** El altar de la sala del jefe no se rompe: sin el la mazmorra se queda sin invocacion. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAltarBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == settings.altarMaterial() && locator.isAltarSpot(block.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cEl altar del jefe no se puede romper.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAltarExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == settings.altarMaterial()
            && locator.isAltarSpot(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAltarBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == settings.altarMaterial()
            && locator.isAltarSpot(block.getLocation()));
    }

    /** Un piston tampoco lo saca de su lugar. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAltarPush(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> block.getType() == settings.altarMaterial()
            && locator.isAltarSpot(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAltarPull(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> block.getType() == settings.altarMaterial()
            && locator.isAltarSpot(block.getLocation()))) {
            event.setCancelled(true);
        }
    }

    /** Los invocadores y fragmentos no se ponen como bloque (por si la base algun dia es colocable). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (itemId(event.getItemInHand()).filter(SummonerRecipeRule::isMythicItem).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        SummonerRecipeRule.Decision decision = decide(event.getInventory().getMatrix(), event.getRecipe(),
            event.getInventory().getResult());
        switch (decision.action()) {
            case KEEP -> {
            }
            case CLEAR -> event.getInventory().setResult(null);
            case SET -> event.getInventory().setResult(summonerStack(decision.culture().orElseThrow()).orElse(null));
        }
    }

    /** El crafter (bloque) sigue la misma regla que la mesa. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (!(event.getBlock().getState(false) instanceof Crafter crafter)) {
            return;
        }
        SummonerRecipeRule.Decision decision = decide(crafter.getInventory().getContents(), event.getRecipe(),
            event.getResult());
        switch (decision.action()) {
            case KEEP -> {
            }
            case CLEAR -> event.setCancelled(true);
            case SET -> summonerStack(decision.culture().orElseThrow())
                .ifPresentOrElse(event::setResult, () -> event.setCancelled(true));
        }
    }

    private SummonerRecipeRule.Decision decide(ItemStack[] matrix, Recipe recipe, ItemStack result) {
        List<String> grid = new ArrayList<>();
        for (ItemStack ingredient : matrix) {
            if (ingredient == null || ingredient.getType().isAir()) {
                continue;
            }
            grid.add(itemId(ingredient).orElse(null));
        }
        boolean ownRecipe = recipe instanceof Keyed keyed && keyed.getKey().equals(moduleRecipe);
        Optional<MythicCulture> resultSummoner = itemId(result).flatMap(MythicCulture::bySummonerItemId);
        return SummonerRecipeRule.decide(grid, resultSummoner, ownRecipe);
    }

    private Optional<ItemStack> summonerStack(MythicCulture culture) {
        try {
            return culture.summonerItemId().map(id -> items.createItemStack(id, 1));
        } catch (RuntimeException unavailable) {
            logger.warning("[Mazmorras] No se pudo crear el Invocador de " + culture.id() + ": "
                + unavailable.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        return items.resolveItemId(stack);
    }
}
