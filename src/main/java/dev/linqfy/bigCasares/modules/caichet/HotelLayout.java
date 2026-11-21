package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Material;

/**
 * El hotel: un espacio liminal infinito y techado. El mundo se divide en zonas de forma irregular (celdas de
 * Voronoi con puntos al azar), cada una con un tema (uno por cada foto de referencia) y su propia altura de techo.
 * Adentro de cada zona todo es al azar: grillas de paredes que estan o no, puertas en cualquier lado, salones que
 * se abren de golpe, columnas sueltas, luces quemadas, laberintos de pasillos, piletas de tamaños variables...
 * Todo sale de la semilla, asi que el mismo lugar siempre se genera igual.
 *
 * <p>Escribe en un {@link Sink} con coordenadas locales del chunk, sin depender del servidor.
 */
final class HotelLayout {

    interface Sink {
        void set(int x, int y, int z, Material material);
    }

    // Soportes del resource pack: bloques de cobre encerado (casi nadie los usa) a los que el pack les pone las
    // texturas del hotel (assets/minecraft/blockstates). Asi el resto del juego no cambia.
    static final Material WALLPAPER = Material.WAXED_COPPER_BLOCK;
    static final Material WAINSCOT = Material.WAXED_EXPOSED_COPPER;
    static final Material CARPET_MUSTARD = Material.WAXED_WEATHERED_COPPER;
    static final Material CEILING = Material.WAXED_OXIDIZED_COPPER;
    static final Material WALLPAPER_YELLOW = Material.WAXED_CUT_COPPER;
    static final Material CARPET_YELLOW = Material.WAXED_EXPOSED_CUT_COPPER;
    static final Material CEILING_YELLOW = Material.WAXED_WEATHERED_CUT_COPPER;
    static final Material WOOD_PANEL = Material.WAXED_OXIDIZED_CUT_COPPER;
    static final Material CARPET_OLIVE = Material.WAXED_CHISELED_COPPER;
    static final Material POOL_MOSAIC = Material.WAXED_EXPOSED_CHISELED_COPPER;
    static final Material CREAM_LINES = Material.WAXED_WEATHERED_CHISELED_COPPER;
    static final Material TILES_WHITE = Material.WAXED_OXIDIZED_CHISELED_COPPER;
    /** Apagada en el pack = azulejos sucios. */
    static final Material TILES_DIRTY = Material.WAXED_EXPOSED_COPPER_BULB;
    /** El generador la pone encendida: panel de luz que ilumina de verdad. */
    static final Material LIGHT_PANEL = Material.WAXED_COPPER_BULB;
    /** Panel tenue de los Backrooms: la bombilla erosionada encendida da luz 8 en vez de 15. */
    static final Material LIGHT_DIM = Material.WAXED_WEATHERED_COPPER_BULB;

    /** Altura del piso de todo el hotel. */
    static final int FLOOR = 64;
    /** Separacion media entre los centros de las zonas. */
    private static final int SITE = 44;
    /** Ancho (desde la linea divisoria) de la pared entre dos zonas. */
    private static final double BORDER = 0.8;

    /** Un tema por foto, con la altura de techo minima y maxima (cada zona sortea la suya). */
    enum Theme {
        /** images.jpg: Backrooms nivel 0. */
        LEVEL0(4, 6),
        /** images (1).jpg: todo amarillo, columnas grandes. */
        YELLOW(4, 6),
        /** images (2).jpg: hall lleno de columnas. */
        PILLARS(5, 7),
        /** images (3).jpg: laberinto de pasillos angostos de madera, casi sin luz. */
        CORRIDOR(3, 4),
        /** images (4).jpg: salas altas inundadas, paredes con huecos, techo de luz con vigas. */
        POOLROOM(12, 20),
        /** images (5).jpg: torres y estantes de libros. */
        LIBRARY(14, 24),
        /** images (6).jpg: patio oscuro rodeado de fachadas con ventanas. */
        COURTYARD(22, 28),
        /** images (7).jpg: piletas techadas de azulejos, puertas brillantes, reposeras. */
        INDOOR_POOL(8, 11),
        /** images (8).jpg: piletas abandonadas, columnas gigantes, ventanas redondas, pasto. */
        ABANDONED(12, 18);

        final int minHeight;
        final int maxHeight;

        Theme(int minHeight, int maxHeight) {
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }
    }

    /** Los Backrooms salen mas seguido; las salas especiales, de vez en cuando. */
    private static final Theme[] WEIGHTED = {
            Theme.LEVEL0, Theme.LEVEL0, Theme.LEVEL0, Theme.LEVEL0, Theme.YELLOW, Theme.YELLOW,
            Theme.PILLARS, Theme.PILLARS, Theme.CORRIDOR, Theme.CORRIDOR, Theme.POOLROOM, Theme.LIBRARY,
            Theme.COURTYARD, Theme.INDOOR_POOL, Theme.ABANDONED};

    /** El centro de una zona. */
    record Site(int gx, int gz, int x, int z, Theme theme, int height) {
    }

    /** Las dos zonas mas cercanas a un punto y la distancia a la linea que las separa. */
    private record Probe(Site a, Site b, double border) {
    }

    private HotelLayout() {
    }

    // ------------------------------------------------------------------ azar

    static long hash(long seed, int a, int b, int salt) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    private static int roll(long seed, int a, int b, int salt, int bound) {
        return (int) Math.floorMod(hash(seed, a, b, salt), (long) bound);
    }

    private static boolean chance(long seed, int a, int b, int salt, int percent) {
        return roll(seed, a, b, salt, 100) < percent;
    }

    // ------------------------------------------------------------------ zonas

    /** La zona (0,0) es siempre el nivel 0 y esta centrada donde se aparece. */
    static Site site(long seed, int gx, int gz) {
        if (gx == 0 && gz == 0) {
            return new Site(0, 0, 16, 16, Theme.LEVEL0, 5);
        }
        int x = gx * SITE + 6 + roll(seed, gx, gz, 20, SITE - 12);
        int z = gz * SITE + 6 + roll(seed, gx, gz, 21, SITE - 12);
        Theme theme = WEIGHTED[roll(seed, gx, gz, 22, WEIGHTED.length)];
        int height = theme.minHeight + roll(seed, gx, gz, 23, theme.maxHeight - theme.minHeight + 1);
        return new Site(gx, gz, x, z, theme, height);
    }

    private static Probe probe(long seed, int wx, int wz) {
        int gx = Math.floorDiv(wx, SITE);
        int gz = Math.floorDiv(wz, SITE);
        Site best = null;
        Site second = null;
        double d1 = Double.MAX_VALUE;
        double d2 = Double.MAX_VALUE;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                Site s = site(seed, gx + ox, gz + oz);
                double dx = wx + 0.5 - s.x;
                double dz = wz + 0.5 - s.z;
                double d = dx * dx + dz * dz;
                if (d < d1) {
                    second = best;
                    d2 = d1;
                    best = s;
                    d1 = d;
                } else if (d < d2) {
                    second = s;
                    d2 = d;
                }
            }
        }
        double ab = Math.hypot(best.x - second.x, best.z - second.z);
        double border = (d2 - d1) / (2 * Math.max(ab, 1e-6));
        return new Probe(best, second, border);
    }

    private static int pair(Site a, Site b) {
        int ha = a.gx * 7919 + a.gz;
        int hb = b.gx * 7919 + b.gz;
        return Math.min(ha, hb) * 31 + Math.max(ha, hb);
    }

    /** Hay paso entre dos zonas en este pedazo de pared? (por tramos de 5 bloques, casi la mitad abiertos) */
    private static boolean opening(long seed, int wx, int wz, Site a, Site b) {
        return chance(seed, Math.floorDiv(wx, 5), Math.floorDiv(wz, 5), pair(a, b), 45);
    }

    // ------------------------------------------------------------------ generacion

    static void generate(long seed, int chunkX, int chunkZ, Sink sink) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = chunkX * 16 + x;
                int wz = chunkZ * 16 + z;
                Probe p = probe(seed, wx, wz);
                Column c = new Column(sink, x, z);
                Theme theme = p.a.theme;
                Material base = theme == Theme.INDOOR_POOL ? TILES_WHITE
                        : theme == Theme.POOLROOM ? POOL_MOSAIC : Material.SMOOTH_STONE;
                c.fill(FLOOR - 5, FLOOR - 1, base);
                if (p.border < BORDER) {
                    border(seed, c, wx, wz, p);
                    continue;
                }
                Site a = p.a;
                int lx = wx - a.x;
                int lz = wz - a.z;
                switch (theme) {
                    case LEVEL0 -> level0(seed, c, wx, wz, lx, lz, a);
                    case YELLOW -> yellow(seed, c, lx, lz, a);
                    case PILLARS -> pillars(seed, c, lx, lz, a);
                    case CORRIDOR -> corridor(seed, c, lx, lz, a);
                    case POOLROOM -> poolroom(seed, c, lx, lz, a);
                    case LIBRARY -> library(seed, c, lx, lz, a);
                    case COURTYARD -> courtyard(seed, c, wx, wz, lx, lz, p);
                    case INDOOR_POOL -> indoorPool(seed, c, lx, lz, a);
                    case ABANDONED -> abandoned(seed, c, wx, wz, lx, lz, a);
                }
            }
        }
    }

    /** Una columna del chunk. */
    private record Column(Sink sink, int x, int z) {
        void set(int y, Material material) {
            sink.set(x, y, z, material);
        }

        void fill(int fromY, int toY, Material material) {
            for (int y = fromY; y <= toY; y++) {
                sink.set(x, y, z, material);
            }
        }
    }

    /**
     * La pared entre dos zonas: sigue la linea (diagonal o torcida) que las separa, llega hasta el techo de la mas
     * alta y tiene pasos al azar. Cada lado se ve con el material de su propia zona.
     */
    private static void border(long seed, Column c, int wx, int wz, Probe p) {
        Site a = p.a;
        Site b = p.b;
        int top = FLOOR + Math.max(a.height, b.height) + 1;
        boolean open = opening(seed, wx, wz, a, b);
        int openHeight = Math.min(a.height, b.height) >= 10 ? 6 : 3;
        boolean wet = a.theme == Theme.POOLROOM;
        c.set(FLOOR, wet ? wall(a.theme, FLOOR) : floor(a.theme));
        boolean glowDoor = a.theme == Theme.INDOOR_POOL && !open
                && chance(seed, Math.floorDiv(wx, 3), Math.floorDiv(wz, 3), 30, 14);
        for (int y = FLOOR + 1; y <= top; y++) {
            if (open && y <= FLOOR + openHeight) {
                continue;
            }
            Site owner = y <= FLOOR + a.height + 1 ? a : b;
            Material m = wall(owner.theme, y);
            int rel = y - FLOOR;
            if (owner.theme == Theme.INDOOR_POOL && glowDoor && rel <= 4) {
                m = LIGHT_PANEL;
            } else if (owner.theme == Theme.POOLROOM && Math.floorMod(wx + wz, 4) >= 1
                    && Math.floorMod(wx + wz, 4) <= 2 && rel % 4 >= 2 && rel >= 2 && rel <= owner.height - 2) {
                m = Material.GRAY_CONCRETE; // huecos cuadrados
            } else if (owner.theme == Theme.ABANDONED && roundWindow(wx, wz, y)) {
                m = Material.BLACK_CONCRETE; // ventanas redondas
            }
            c.set(y, m);
        }
    }

    private static boolean roundWindow(int wx, int wz, int y) {
        int cx = Math.round(wx / 12f) * 12;
        int cz = Math.round(wz / 12f) * 12;
        double d = Math.hypot(wx - cx, wz - cz);
        double dy = y - (FLOOR + 8);
        return d * d + dy * dy <= 9.5;
    }

    static Material wall(Theme theme, int y) {
        return switch (theme) {
            case LEVEL0 -> y == FLOOR + 1 ? WAINSCOT : WALLPAPER;
            case YELLOW -> WALLPAPER_YELLOW;
            case PILLARS -> WALLPAPER;
            case CORRIDOR -> WOOD_PANEL;
            case POOLROOM -> CREAM_LINES;
            case LIBRARY -> Material.BOOKSHELF;
            case COURTYARD -> Material.WHITE_CONCRETE;
            case INDOOR_POOL -> TILES_WHITE;
            case ABANDONED -> TILES_DIRTY;
        };
    }

    private static Material floor(Theme theme) {
        return switch (theme) {
            case LEVEL0, PILLARS -> CARPET_MUSTARD;
            case YELLOW -> CARPET_YELLOW;
            case CORRIDOR -> CARPET_OLIVE;
            case POOLROOM -> CREAM_LINES;
            case LIBRARY -> Material.OAK_PLANKS;
            case COURTYARD -> Material.SMOOTH_STONE;
            case INDOOR_POOL -> TILES_WHITE;
            case ABANDONED -> TILES_DIRTY;
        };
    }

    // ------------------------------------------------------------------ piezas al azar

    private enum Solid { NONE, WALL, PILLAR }

    /**
     * Laberinto tipo Backrooms sobre una grilla de lado {@code g}: cada tramo de la grilla es pared o no, las
     * paredes tienen puertas en cualquier lugar, a veces se abre un salon entero y hay columnas sueltas.
     */
    private static Solid maze(long seed, int lx, int lz, int g, int wallPercent, int salt) {
        int mx = Math.floorMod(lx, g);
        int mz = Math.floorMod(lz, g);
        int ix = Math.floorDiv(lx, g);
        int iz = Math.floorDiv(lz, g);
        boolean hall = chance(seed, Math.floorDiv(lx, 26), Math.floorDiv(lz, 26), salt + 1, 22);
        if (hall) {
            return mx == 0 && mz == 0 && chance(seed, ix, iz, salt + 2, 35) ? Solid.PILLAR : Solid.NONE;
        }
        if (mx == 0 && mz == 0) {
            boolean any = segment(seed, ix, iz, g, salt, wallPercent, true) != null
                    || segment(seed, ix, iz, g, salt, wallPercent, false) != null
                    || segment(seed, ix, iz - 1, g, salt, wallPercent, true) != null
                    || segment(seed, ix - 1, iz, g, salt, wallPercent, false) != null;
            return any || chance(seed, ix, iz, salt + 3, 20) ? Solid.PILLAR : Solid.NONE;
        }
        if (mx == 0) {
            int[] gap = segment(seed, ix, iz, g, salt, wallPercent, true);
            return gap != null && (mz < gap[0] || mz > gap[1]) ? Solid.WALL : Solid.NONE;
        }
        if (mz == 0) {
            int[] gap = segment(seed, ix, iz, g, salt, wallPercent, false);
            return gap != null && (mx < gap[0] || mx > gap[1]) ? Solid.WALL : Solid.NONE;
        }
        if (mx == g / 2 && mz == g / 2 && chance(seed, ix, iz, salt + 4, 10)) {
            return Solid.PILLAR;
        }
        return Solid.NONE;
    }

    /** @return null si ese tramo no tiene pared; si tiene, el hueco de la puerta (desde, hasta; vacio si no hay) */
    private static int[] segment(long seed, int ix, int iz, int g, int salt, int wallPercent, boolean vertical) {
        int s = salt + (vertical ? 10 : 20);
        if (!chance(seed, ix, iz, s, wallPercent)) {
            return null;
        }
        if (g >= 5 && chance(seed, ix, iz, s + 1, 55)) {
            int from = 1 + roll(seed, ix, iz, s + 2, g - 3);
            return new int[]{from, from + 1};
        }
        return new int[]{g, g};
    }

    /** Paneles de luz en grilla; algunos estan quemados. */
    private static boolean light(long seed, int lx, int lz, int spacing, int deadPercent, int salt) {
        int mx = Math.floorMod(lx, spacing);
        int mz = Math.floorMod(lz, spacing);
        if (mx != spacing / 2 || (mz != spacing / 2 && mz != spacing / 2 + 1)) {
            return false;
        }
        return !chance(seed, Math.floorDiv(lx, spacing), Math.floorDiv(lz, spacing), salt, deadPercent);
    }

    private static boolean nearSpawn(int wx, int wz) {
        return Math.abs(wx - 16) <= 3 && Math.abs(wz - 16) <= 3;
    }

    private static int salt(Site a) {
        return 100 + Math.floorMod(a.gx * 31 + a.gz, 1000) * 10;
    }

    // ------------------------------------------------------------------ temas

    /** images.jpg */
    private static void level0(long seed, Column c, int wx, int wz, int lx, int lz, Site a) {
        int top = FLOOR + a.height + 1;
        c.set(FLOOR, CARPET_MUSTARD);
        c.set(top, light(seed, lx, lz, 7, 35, 40) ? LIGHT_DIM : CEILING);
        if (nearSpawn(wx, wz)) {
            return;
        }
        int g = 6 + roll(seed, a.gx, a.gz, 30, 4);
        Solid solid = maze(seed, lx, lz, g, 45, salt(a));
        if (solid == Solid.WALL) {
            for (int y = FLOOR + 1; y < top; y++) {
                c.set(y, wall(Theme.LEVEL0, y));
            }
        } else if (solid == Solid.PILLAR) {
            boolean whiteBase = chance(seed, Math.floorDiv(lx, g), Math.floorDiv(lz, g), 31, 30);
            for (int y = FLOOR + 1; y < top; y++) {
                c.set(y, whiteBase && y <= FLOOR + 2 ? Material.WHITE_CONCRETE : WALLPAPER);
            }
        }
    }

    /** images (1).jpg */
    private static void yellow(long seed, Column c, int lx, int lz, Site a) {
        int top = FLOOR + a.height + 1;
        c.set(FLOOR, CARPET_YELLOW);
        c.set(top, light(seed, lx, lz, 6, 30, 41) ? LIGHT_DIM : CEILING_YELLOW);
        int g = 8 + roll(seed, a.gx, a.gz, 32, 5);
        int mx = Math.floorMod(lx, g);
        int mz = Math.floorMod(lz, g);
        // columnas gruesas de 2x2 en algunos cruces
        if (mx <= 1 && mz <= 1 && chance(seed, Math.floorDiv(lx, g), Math.floorDiv(lz, g), 33, 35)) {
            c.fill(FLOOR + 1, top - 1, WALLPAPER_YELLOW);
            return;
        }
        if (maze(seed, lx, lz, g, 32, salt(a)) == Solid.WALL) {
            c.fill(FLOOR + 1, top - 1, WALLPAPER_YELLOW);
        }
    }

    /** images (2).jpg */
    private static void pillars(long seed, Column c, int lx, int lz, Site a) {
        int top = FLOOR + a.height + 1;
        c.set(FLOOR, CARPET_MUSTARD);
        int g = 5 + roll(seed, a.gx, a.gz, 34, 3);
        c.set(top, light(seed, lx + g, lz + g, g * 2, 30, 42) ? LIGHT_DIM : CEILING);
        int mx = Math.floorMod(lx, g);
        int mz = Math.floorMod(lz, g);
        int ix = Math.floorDiv(lx, g);
        int iz = Math.floorDiv(lz, g);
        boolean pillar = mx <= 1 && mz <= 1 && chance(seed, ix, iz, 35, 85);
        boolean wall = maze(seed, lx, lz, g * 3, 6, salt(a)) == Solid.WALL;
        if (pillar || wall) {
            c.fill(FLOOR + 1, top - 1, WALLPAPER);
        }
    }

    /** images (3).jpg: laberinto de pasillos de 3 de ancho, con poca luz. */
    private static void corridor(long seed, Column c, int lx, int lz, Site a) {
        int top = FLOOR + a.height + 1;
        c.set(FLOOR, CARPET_OLIVE);
        c.set(top, light(seed, lx, lz, 6, 60, 43) ? LIGHT_DIM : CEILING);
        int mx = Math.floorMod(lx, 4);
        int mz = Math.floorMod(lz, 4);
        if (mx == 0 && mz == 0) {
            c.fill(FLOOR + 1, top - 1, WOOD_PANEL);
            return;
        }
        if (maze(seed, lx, lz, 4, 72, salt(a)) == Solid.WALL) {
            c.fill(FLOOR + 1, top - 1, WOOD_PANEL);
        }
    }

    /** images (4).jpg: inundada, con paredes gruesas al azar llenas de huecos y techo de luz con vigas. */
    private static void poolroom(long seed, Column c, int lx, int lz, Site a) {
        int h = a.height;
        int top = FLOOR + h + 1;
        c.set(FLOOR - 2, POOL_MOSAIC);
        c.set(FLOOR - 1, Material.WATER);
        c.set(FLOOR, Material.WATER);
        boolean beam = Math.floorMod(lx, 6) == 0 || Math.floorMod(lz, 6) == 0;
        c.set(top, beam ? CREAM_LINES : LIGHT_PANEL);
        int g = 13;
        int mx = Math.floorMod(lx, g);
        int mz = Math.floorMod(lz, g);
        int ix = Math.floorDiv(lx, g);
        int iz = Math.floorDiv(lz, g);
        boolean slabX = mx <= 1 && chance(seed, ix, iz, 36, 40);
        boolean slabZ = mz <= 1 && chance(seed, ix, iz, 37, 40);
        if (!slabX && !slabZ) {
            return;
        }
        int along = slabX ? mz : mx;
        int door = 3 + roll(seed, ix, iz, slabX ? 38 : 39, g - 7);
        for (int y = FLOOR + 1; y < top; y++) {
            int rel = y - FLOOR;
            if (along >= door && along <= door + 2 && rel <= 4) {
                continue; // paso
            }
            boolean hole = along % 4 >= 1 && along % 4 <= 2 && rel % 4 >= 2 && rel >= 2 && rel <= h - 2;
            c.set(y, hole ? Material.GRAY_CONCRETE : CREAM_LINES);
        }
    }

    /** images (5).jpg: torres y estantes desparejos, techo de madera. */
    private static void library(long seed, Column c, int lx, int lz, Site a) {
        int h = a.height;
        int top = FLOOR + h + 1;
        c.set(FLOOR, Material.OAK_PLANKS);
        c.set(top, light(seed, lx, lz, 7, 20, 44) ? LIGHT_PANEL : Material.DARK_OAK_PLANKS);
        int g = 7;
        int mx = Math.floorMod(lx, g);
        int mz = Math.floorMod(lz, g);
        int ix = Math.floorDiv(lx, g);
        int iz = Math.floorDiv(lz, g);
        int kind = roll(seed, ix, iz, 45, 100);
        int height = 0;
        if (kind < 38) {
            int size = 2 + roll(seed, ix, iz, 46, 3);
            int from = 1 + roll(seed, ix, iz, 47, g - size - 1);
            if (mx >= from && mx < from + size && mz >= from && mz < from + size) {
                height = Math.min(h, 5 + roll(seed, ix, iz, 48, h));
            }
        } else if (kind < 62) {
            if (mz == 3 && mx >= 1 && mx <= 5) {
                height = 3;
            }
        } else if (kind < 80) {
            if (mx == 3 && mz >= 1 && mz <= 5) {
                height = 3;
            }
        }
        if (height > 0) {
            c.fill(FLOOR + 1, FLOOR + height, Material.BOOKSHELF);
        }
    }

    /** images (6).jpg: patio de pasto con caminos, fachadas con ventanas contra los bordes, techo oscuro. */
    private static void courtyard(long seed, Column c, int wx, int wz, int lx, int lz, Probe p) {
        Site a = p.a;
        int h = a.height;
        int top = FLOOR + h + 1;
        boolean grid = Math.floorMod(lx, 4) == 0 || Math.floorMod(lz, 4) == 0;
        c.set(top, grid ? Material.GRAY_CONCRETE : Material.BLACK_CONCRETE);
        double d = p.border;
        if (d >= 5.6) {
            boolean path = d < 6.6
                    || (Math.floorMod(lx, 9) == 0 && chance(seed, Math.floorDiv(lx, 9), Math.floorDiv(lz, 9), 49, 55))
                    || (Math.floorMod(lz, 9) == 0 && chance(seed, Math.floorDiv(lx, 9), Math.floorDiv(lz, 9), 50, 55));
            c.set(FLOOR, path ? Material.SMOOTH_STONE : Material.GRASS_BLOCK);
            return;
        }
        // edificio pegado al borde: macizo, con ventanas en la cara que da al patio
        c.set(FLOOR, Material.SMOOTH_STONE);
        boolean tunnel = opening(seed, wx, wz, a, p.b);
        boolean windowCol = Math.floorMod(wx + wz, 4) == 1 || Math.floorMod(wx + wz, 4) == 2;
        for (int y = FLOOR + 1; y < top; y++) {
            int rel = y - FLOOR;
            if (tunnel && rel <= 3) {
                continue;
            }
            boolean window = windowCol && rel >= 5 && rel <= h - 3 && rel % 5 <= 1;
            if (window && d >= 4.6) {
                c.set(y, Material.GLASS);
            } else if (window && d >= 3.6) {
                boolean lit = chance(seed, Math.floorDiv(wx + wz, 4), rel / 5 + 7 * Math.floorDiv(wx - wz, 4), 51, 14);
                c.set(y, lit ? Material.SHROOMLIGHT : Material.BLACK_CONCRETE);
            } else {
                c.set(y, Material.WHITE_CONCRETE);
            }
        }
    }

    /** images (7).jpg: piletas de tamaños al azar, reposeras, techo de azulejos con paneles de luz sobre el agua. */
    private static void indoorPool(long seed, Column c, int lx, int lz, Site a) {
        int top = FLOOR + a.height + 1;
        int g = 15;
        int mx = Math.floorMod(lx, g);
        int mz = Math.floorMod(lz, g);
        int ix = Math.floorDiv(lx, g);
        int iz = Math.floorDiv(lz, g);
        boolean hasPool = chance(seed, ix, iz, 52, 65);
        int x0 = 2 + roll(seed, ix, iz, 53, 3);
        int w = 6 + roll(seed, ix, iz, 54, 5);
        int z0 = 2 + roll(seed, ix, iz, 55, 2);
        int d = 5 + roll(seed, ix, iz, 56, 4);
        boolean pool = hasPool && mx >= x0 && mx < x0 + w && mz >= z0 && mz < z0 + d;
        boolean overPool = hasPool && mx >= x0 + 1 && mx < x0 + w - 1 && mz >= z0 + 1 && mz < z0 + d - 1;
        if (pool) {
            c.fill(FLOOR - 2, FLOOR, Material.WATER);
        } else {
            c.set(FLOOR, TILES_WHITE);
        }
        c.set(top, overPool ? LIGHT_PANEL : TILES_WHITE);
        if (!hasPool) {
            return;
        }
        // reposeras en la fila siguiente a la pileta
        if (mz == z0 + d + 1 && mx >= x0 && mx < x0 + w && (mx - x0) % 3 == 0) {
            c.set(FLOOR + 1, Material.QUARTZ_SLAB);
        } else if (mz == z0 + d + 2 && mx >= x0 && mx < x0 + w && (mx - x0) % 3 == 0) {
            c.set(FLOOR + 1, Material.QUARTZ_STAIRS);
        }
        // baranda de la escalerita
        if (mz == z0 - 1 && (mx == x0 + 1 || mx == x0 + 3)) {
            c.fill(FLOOR + 1, FLOOR + 2, Material.IRON_BARS);
        }
    }

    /** images (8).jpg */
    private static void abandoned(long seed, Column c, int wx, int wz, int lx, int lz, Site a) {
        int h = a.height;
        int top = FLOOR + h + 1;
        boolean tile = chance(seed, Math.floorDiv(wx, 3), Math.floorDiv(wz, 3), 57, 35);
        c.set(FLOOR, tile ? TILES_DIRTY : Material.GRASS_BLOCK);
        c.set(top, TILES_DIRTY);
        if (Math.floorMod(lx, 5) == 0 && Math.floorMod(lz, 5) == 0) {
            c.set(top - 1, Material.LIGHT);
        }
        // columnas gigantes en lugares al azar
        int g = 12;
        int mx = Math.floorMod(lx, g);
        int mz = Math.floorMod(lz, g);
        int ix = Math.floorDiv(lx, g);
        int iz = Math.floorDiv(lz, g);
        if (chance(seed, ix, iz, 58, 55)) {
            int ox = 1 + roll(seed, ix, iz, 59, 6);
            int oz = 1 + roll(seed, ix, iz, 60, 6);
            if (mx >= ox && mx < ox + 4 && mz >= oz && mz < oz + 4) {
                c.fill(FLOOR + 1, top - 1, TILES_DIRTY);
                return;
            }
        }
        // paredes sueltas con ventanas redondas y arcos
        int sg = 17;
        int smx = Math.floorMod(lx, sg);
        int smz = Math.floorMod(lz, sg);
        int six = Math.floorDiv(lx, sg);
        int siz = Math.floorDiv(lz, sg);
        boolean slabX = smx <= 1 && chance(seed, six, siz, 61, 30);
        boolean slabZ = smz <= 1 && chance(seed, six, siz, 62, 30);
        if (!slabX && !slabZ) {
            return;
        }
        int along = slabX ? smz : smx;
        for (int y = FLOOR + 1; y < top; y++) {
            int rel = y - FLOOR;
            if (along >= 7 && along <= 9 && rel <= 5) {
                continue; // arco
            }
            int ac = along < 8 ? 3 : 13;
            boolean round = (along - ac) * (along - ac) + (rel - 9) * (rel - 9) <= 6;
            c.set(y, round ? Material.BLACK_CONCRETE : TILES_DIRTY);
        }
    }
}
