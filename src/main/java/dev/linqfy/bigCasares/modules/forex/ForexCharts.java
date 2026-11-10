package dev.linqfy.bigCasares.modules.forex;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Live candlestick charts on maps. Every asset has one shared map, so every copy of it (in hands or item frames)
 * shows the same chart and redraws only when the market moves. Map ids are kept in {@code forex/charts.yml}
 * so the charts come back after a restart.
 */
final class ForexCharts {

    private final Server server;
    private final ChartFeed feed;
    private final File file;
    private final Logger logger;
    private final Map<String, Integer> mapIds = new LinkedHashMap<>();
    private final Map<Integer, ChartRenderer> renderers = new HashMap<>();

    ForexCharts(Server server, ChartFeed feed, File file, Logger logger) {
        this.server = server;
        this.feed = feed;
        this.file = file;
        this.logger = logger;
    }

    void start() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException failure) {
            logger.log(Level.WARNING, "[Forex] Could not read " + file + "; charts start over", failure);
            return;
        }
        for (String asset : yaml.getKeys(false)) {
            int id = yaml.getInt(asset, -1);
            MapView view = id < 0 ? null : server.getMap(id);
            if (view != null) {
                mapIds.put(asset, id);
                attach(view, asset);
            }
        }
    }

    /** Gives the vanilla look back to nothing: our renderers simply leave, so a reload can attach new ones. */
    void stop() {
        for (Map.Entry<Integer, ChartRenderer> entry : renderers.entrySet()) {
            MapView view = server.getMap(entry.getKey());
            if (view != null) {
                view.removeRenderer(entry.getValue());
            }
        }
        renderers.clear();
    }

    ItemStack item(Player player, String rawAsset) {
        String asset = ChartFeed.asset(rawAsset);
        MapView view = view(player.getWorld(), asset);
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);
        meta.displayName(text((ForexMarket.EMERALD_TICKER.equals(asset) ? "§a$vEMR" : "§e$" + asset)
            + " §7· gráfico en vivo"));
        meta.lore(List.of(
            text("§7Velas de " + feed.chart(asset).timeframe() + " · volumen · precio actual"),
            text("§8Colgalo en un marco: armá tu mesa de trading.")));
        item.setItemMeta(meta);
        return item;
    }

    private MapView view(World world, String asset) {
        Integer id = mapIds.get(asset);
        MapView view = id == null ? null : server.getMap(id);
        if (view == null) {
            view = server.createMap(world);
            mapIds.put(asset, view.getId());
            save();
        }
        if (!renderers.containsKey(view.getId())) {
            attach(view, asset);
        }
        return view;
    }

    private void attach(MapView view, String asset) {
        for (MapRenderer existing : List.copyOf(view.getRenderers())) {
            view.removeRenderer(existing);
        }
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        ChartRenderer renderer = new ChartRenderer(asset);
        view.addRenderer(renderer);
        renderers.put(view.getId(), renderer);
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        mapIds.forEach(yaml::set);
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "[Forex] Could not save chart maps", failure);
        }
    }

    private static Component text(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    /** Not contextual: one canvas per map, redrawn only when the feed key changes. */
    private final class ChartRenderer extends MapRenderer {

        private final String asset;
        private final Map<Integer, Color> colors = new HashMap<>();
        private long drawn = Long.MAX_VALUE;
        private boolean painted;

        private ChartRenderer(String asset) {
            super(false);
            this.asset = asset;
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            long key = feed.key(asset);
            if (painted && key == drawn) {
                return;
            }
            int[] pixels = ChartPainter.paint(feed.chart(asset));
            for (int y = 0; y < ChartPainter.SIZE; y++) {
                for (int x = 0; x < ChartPainter.SIZE; x++) {
                    int rgb = pixels[y * ChartPainter.SIZE + x];
                    canvas.setPixelColor(x, y, colors.computeIfAbsent(rgb, Color::new));
                }
            }
            drawn = key;
            painted = true;
        }
    }
}
