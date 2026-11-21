package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class PortalListener implements Listener {

    private static final NamespacedKey RETURN_KEY = new NamespacedKey("caichet", "return");

    /** Un bloque puesto desde el item especial pasa a ser portal. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        PortalKind kind = PortalKind.ofItem(event.getItemInHand());
        if (kind != null) {
            PortalRegistry.put(event.getBlockPlaced(), kind);
        } else if (isPortalMaterial(event.getBlockPlaced().getType())) {
            // concreto comun: si en ese lugar quedo anotado un portal viejo, se borra
            PortalRegistry.remove(event.getBlockPlaced());
        }
    }

    private static boolean isPortalMaterial(Material material) {
        for (PortalKind kind : PortalKind.values()) {
            if (kind.material == material) {
                return true;
            }
        }
        return false;
    }

    /** Los portales no vuelan con explosiones (asi no se pierde el item ni queda la anotacion suelta). */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isPortalMaterial(block.getType()) && PortalRegistry.get(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isPortalMaterial(block.getType()) && PortalRegistry.get(block) != null);
    }

    /** Ni se empujan con pistones (el bloque movido dejaria de ser portal). */
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesPortal(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesPortal(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private static boolean movesPortal(List<Block> blocks) {
        for (Block block : blocks) {
            if (isPortalMaterial(block.getType()) && PortalRegistry.get(block) != null) {
                return true;
            }
        }
        return false;
    }

    /** Al romper un portal se suelta el item especial (no concreto normal). */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        PortalKind kind = PortalRegistry.get(block);
        if (kind == null) {
            return;
        }
        PortalRegistry.remove(block);
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), kind.createItem(1));
        }
    }

    /** Tocar (clic derecho) un portal. Agachado con un bloque en la mano permite construir contra el portal. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        PortalKind kind = block == null ? null : PortalRegistry.get(block);
        if (kind == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() && player.getInventory().getItemInMainHand().getType().isBlock()) {
            return;
        }
        event.setCancelled(true);
        switch (kind) {
            case DIMENSION -> enter(player);
            case MUNDO -> leave(player);
        }
    }

    public static World dimension() {
        return Bukkit.getWorld(CaiChetModule.DIMENSION);
    }

    public static void enter(Player player) {
        enter(player, player.getLocation());
    }

    /** Igual, pero guarda returnTo como lugar de regreso (por ejemplo el borde del pozo, no su fondo). */
    public static void enter(Player player, Location returnTo) {
        World target = dimension();
        if (target == null) {
            player.sendMessage("La dimensión no está cargada (reinicia el servidor).");
            return;
        }
        if (player.getWorld().equals(target)) {
            return;
        }
        saveReturn(player, returnTo, RETURN_KEY);
        // el chunk del origen se carga sin frenar el servidor; recien despues se arma la llegada y se viaja
        target.getChunkAtAsync(0, 0).thenAccept(chunk -> {
            if (player.isOnline() && !player.getWorld().equals(target)) {
                player.teleport(arrival(target));
            }
        });
    }

    public static void leave(Player player) {
        World dimension = dimension();
        if (dimension == null || !player.getWorld().equals(dimension)) {
            return;
        }
        player.teleport(safe(loadReturn(player, RETURN_KEY)));
        CaiChetSounds.playTo(player, player.getLocation(), CaiChetSounds.PIT_RETURN, SoundCategory.AMBIENT, 1f, 1f);
    }

    /**
     * El lugar de regreso puede haber cambiado desde que se guardo (por ejemplo rompieron el borde del pozo y se
     * volveria a caer al fondo negro, en bucle): si ya no se puede estar parado ahi, se vuelve arriba de lo mas alto de
     * esa columna.
     */
    static Location safe(Location target) {
        World world = target.getWorld();
        Block feet = target.getBlock();
        Block floor = feet.getRelative(BlockFace.DOWN);
        if (feet.isPassable() && feet.getRelative(BlockFace.UP).isPassable() && floor.getType().isSolid()
                && !feet.isLiquid() && !floor.isLiquid()) {
            return target;
        }
        Location top = world.getHighestBlockAt(target).getLocation().add(0.5, 1, 0.5);
        top.setYaw(target.getYaw());
        top.setPitch(target.getPitch());
        return top;
    }

    /**
     * Punto de llegada: junto al origen de la dimension. Si no existe, pone ahi un Bloque de Mundo para poder volver.
     * Usa el heightmap de generacion, que no cambia cuando se construye encima, asi que el punto es siempre el mismo.
     */
    private static Location arrival(World world) {
        world.getChunkAt(0, 0).load(true);
        int y = world.getHighestBlockYAt(0, 0, HeightMap.OCEAN_FLOOR_WG) + 1;
        Block back = world.getBlockAt(0, y, 0);
        if (PortalRegistry.get(back) == null) {
            // si alguien construyo ahi, el Bloque de Mundo va encima de lo construido (nunca se pisa nada)
            if (!(back.isEmpty() || back.isReplaceable())) {
                back = world.getHighestBlockAt(0, 0).getRelative(BlockFace.UP);
            }
            if (PortalRegistry.get(back) == null && (back.isEmpty() || back.isReplaceable())) {
                back.setType(PortalKind.MUNDO.material);
                PortalRegistry.put(back, PortalKind.MUNDO);
            }
        }
        // un poco al costado, en un lugar con dos bloques libres (sin romper nada de lo construido)
        for (int r = 2; r <= 6; r++) {
            for (int[] d : new int[][]{{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
                Block floor = world.getHighestBlockAt(d[0], d[1]);
                Block feet = floor.getRelative(BlockFace.UP);
                if (floor.getType().isSolid() && feet.isPassable()
                        && feet.getRelative(BlockFace.UP).isPassable()) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
        }
        // todo tapado: arriba de lo mas alto, al costado del portal
        return world.getHighestBlockAt(2, 0).getLocation().add(0.5, 1, 0.5);
    }

    static void saveReturn(Player player, NamespacedKey key) {
        saveReturn(player, player.getLocation(), key);
    }

    static void saveReturn(Player player, Location l, NamespacedKey key) {
        String value = l.getWorld().getKey() + "|" + l.getX() + "|" + l.getY() + "|" + l.getZ()
                + "|" + l.getYaw() + "|" + l.getPitch();
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
    }

    static Location loadReturn(Player player, NamespacedKey key) {
        String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw != null) {
            try {
                String[] p = raw.split("\\|");
                NamespacedKey worldKey = NamespacedKey.fromString(p[0]);
                World world = worldKey == null ? null : Bukkit.getWorld(worldKey);
                if (world != null) {
                    return new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                            Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
                }
            } catch (RuntimeException ignored) {
                // dato corrupto: cae al spawn
            }
        }
        return Bukkit.getWorlds().getFirst().getSpawnLocation();
    }
}
