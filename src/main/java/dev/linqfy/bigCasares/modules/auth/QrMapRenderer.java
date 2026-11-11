package dev.linqfy.bigCasares.modules.auth;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;

/** Draws a QR code on a 128×128 map so a phone can scan the authenticator setup directly from the screen. */
final class QrMapRenderer extends MapRenderer {

    private static final int MAP_SIZE = 128;
    private static final int QUIET_ZONE = 4;
    private final QrCode code;
    private boolean drawn;

    QrMapRenderer(QrCode code) {
        super(false);
        this.code = code;
    }

    /** Pixels per module, or 0 when the code does not fit on one map. */
    static int scale(QrCode code) {
        return MAP_SIZE / (code.size() + QUIET_ZONE * 2);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (drawn) {
            return;
        }
        int scale = Math.max(1, scale(code));
        int offset = (MAP_SIZE - code.size() * scale) / 2;
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int moduleX = Math.floorDiv(x - offset, scale);
                int moduleY = Math.floorDiv(y - offset, scale);
                canvas.setPixelColor(x, y, code.module(moduleX, moduleY) ? Color.BLACK : Color.WHITE);
            }
        }
        drawn = true;
    }
}
