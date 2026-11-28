package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El Sueño: praderas infinitas de pasto verde intenso bajo un cielo azul, con arcoiris y cosas fuera de lugar.
 * El mundo se divide en zonas grandes de forma irregular (Voronoi), cada una con el tema de una foto:
 * <ul>
 *   <li>OJOS (dc1): lomas suaves, casitas rosas con un camino de tierra que serpentea;</li>
 *   <li>PUERTAS (dc2): lomas grandes, puertas blancas sueltas abiertas (al negro, con un "?" rojo), arcoiris;</li>
 *   <li>PLAZA (dc3): llanura perfectamente plana con juegos de plaza solitarios;</li>
 *   <li>CAMPO (images (9)): surcos de siembra, casitas lejanas (y la mancha negra en el cielo);</li>
 *   <li>COLLAGE (el collage): arboles, flores, puertas abiertas al rojo (y hongos con ojo, ojos con alas...);</li>
 *   <li>BARRIO (ab67): calle curva con veredas y casas pastel en fila.</li>
 * </ul>
 * Las criaturas (figuras planas) las pone {@link DreamEntities}; aca solo van bloques. Todo sale de la semilla.
 */
final class DreamLayout {

    interface Sink {
        void set(int x, int y, int z, Material material);

        /** Una puerta de dos bloques, abierta, mirando hacia {@code facing} (0 N, 1 E, 2 S, 3 W). */
        void door(int x, int y, int z, Material door, int facing);
    }

    static final int FLOOR = 64;
    private static final int SITE = 150;

    enum Theme {
        OJOS("sueno_ojos", 3),
        PUERTAS("sueno_puertas", 16),
        PLAZA("sueno_plaza", 0),
        CAMPO("sueno_campo", 1),
        COLLAGE("sueno_collage", 6),
        BARRIO("sueno_barrio", 0);

        final String biome;
        final int hills;

        Theme(String biome, int hills) {
            this.biome = biome;
            this.hills = hills;
        }
    }

    record Site(int gx, int gz, int x, int z, Theme theme) {
    }

    record Probe(Site a, Site b, double border) {
    }

    /** Una puerta suelta (para que {@link DreamProps} le ponga el hueco negro o rojo). */
    record DoorSpot(int x, int y, int z, int facing, boolean red) {
    }

    private DreamLayout() {
    }

    // ------------------------------------------------------------------ azar y ruido

    private static long hash(long seed, int a, int b, int salt) {
        return HotelLayout.hash(seed ^ 0x5EED_D2EA_11L, a, b, salt);
    }

    static int roll(long seed, int a, int b, int salt, int bound) {
        return (int) Math.floorMod(hash(seed, a, b, salt), (long) bound);
    }

    static boolean chance(long seed, int a, int b, int salt, int percent) {
        return roll(seed, a, b, salt, 100) < percent;
    }

    private static double lattice(long seed, int x, int z, int salt) {
        return (Math.floorMod(hash(seed, x, z, salt), 10000L) / 10000.0) * 2 - 1;
    }

    /** Ruido de valor suave (-1..1) con celdas de lado {@code size}. */
    private static double noise(long seed, double x, double z, double size, int salt) {
        double fx = x / size;
        double fz = z / size;
        int x0 = (int) Math.floor(fx);
        int z0 = (int) Math.floor(fz);
        double tx = smooth(fx - x0);
        double tz = smooth(fz - z0);
        double a = lattice(seed, x0, z0, salt);
        double b = lattice(seed, x0 + 1, z0, salt);
        double c = lattice(seed, x0, z0 + 1, salt);
        double d = lattice(seed, x0 + 1, z0 + 1, salt);
        return lerp(lerp(a, b, tx), lerp(c, d, tx), tz);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------ zonas

    /** La zona (0,0) es OJOS y esta centrada en el origen, donde se aparece. */
    static Site site(long seed, int gx, int gz) {
        if (gx == 0 && gz == 0) {
            return new Site(0, 0, 0, 0, Theme.OJOS);
        }
        int x = gx * SITE + 20 + roll(seed, gx, gz, 1, SITE - 40);
        int z = gz * SITE + 20 + roll(seed, gx, gz, 2, SITE - 40);
        Theme theme = Theme.values()[roll(seed, gx, gz, 3, Theme.values().length)];
        return new Site(gx, gz, x, z, theme);
    }

    static Probe probe(long seed, int wx, int wz) {
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
        return new Probe(best, second, (d2 - d1) / (2 * Math.max(ab, 1e-6)));
    }

    static Theme theme(long seed, int wx, int wz) {
        return probe(seed, wx, wz).a.theme;
    }

    /** Altura del pasto. Las lomas de cada tema se mezclan suave cerca de los bordes entre zonas. */
    static int height(long seed, int wx, int wz) {
        Probe p = probe(seed, wx, wz);
        double t = smooth(Math.min(1, p.border / 30));
        double hills = lerp((p.a.theme.hills + p.b.theme.hills) / 2.0, p.a.theme.hills, t);
        double n = noise(seed, wx, wz, 56, 10) * 0.65 + noise(seed, wx, wz, 23, 11) * 0.35;
        double up = Math.max(0, (n + 0.15) / 1.15);
        return FLOOR + (int) Math.round(hills * Math.pow(up, 1.4));
    }

    // ------------------------------------------------------------------ generacion

    static void generate(long seed, int chunkX, int chunkZ, Sink sink) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = chunkX * 16 + x;
                int wz = chunkZ * 16 + z;
                Column c = new Column(sink, x, z);
                Probe p = probe(seed, wx, wz);
                int h = height(seed, wx, wz);
                c.fill(FLOOR - 24, h - 4, Material.STONE);
                c.fill(h - 3, h - 1, Material.DIRT);
                c.set(h, Material.GRASS_BLOCK);
                int[] top = {h};
                boolean built = switch (p.a.theme) {
                    case OJOS -> house(seed, c, wx, wz, p, top);
                    case PUERTAS -> standaloneDoor(seed, c, wx, wz, p, top, false);
                    case PLAZA -> playground(seed, c, wx, wz, p, top);
                    case CAMPO -> field(seed, c, wx, wz, p, top);
                    case COLLAGE -> collage(seed, c, wx, wz, p, top);
                    case BARRIO -> street(seed, c, wx, wz, p, top);
                };
                if (!built) {
                    decorate(seed, c, wx, wz, p.a.theme, top[0]);
                }
            }
        }
    }

    private record Column(Sink sink, int x, int z) {
        void set(int y, Material m) {
            sink.set(x, y, z, m);
        }

        void fill(int from, int to, Material m) {
            for (int y = from; y <= to; y++) {
                sink.set(x, y, z, m);
            }
        }
    }

    /** El pasto es una alfombra lisa (como en las fotos); solo el collage tiene flores sueltas. */
    private static void decorate(long seed, Column c, int wx, int wz, Theme theme, int h) {
        if (theme != Theme.COLLAGE) {
            return;
        }
        int r = roll(seed, wx, wz, 20, 1000);
        if (r < 30) {
            Material[] flowers = {Material.POPPY, Material.CORNFLOWER, Material.PINK_TULIP,
                    Material.DANDELION, Material.ALLIUM};
            c.set(h + 1, flowers[r % flowers.length]);
        }
    }

    /** Lugar de una construccion dentro de una parcela, solo si queda bien adentro de su zona. */
    private record Plot(int ix, int iz, int ox, int oz) {
    }

    private static Plot plot(long seed, int wx, int wz, int size, int salt, int percent, int margin, int span) {
        int ix = Math.floorDiv(wx, size);
        int iz = Math.floorDiv(wz, size);
        if (!chance(seed, ix, iz, salt, percent)) {
            return null;
        }
        int ox = ix * size + margin + roll(seed, ix, iz, salt + 1, Math.max(1, size - 2 * margin - span));
        int oz = iz * size + margin + roll(seed, ix, iz, salt + 2, Math.max(1, size - 2 * margin - span));
        return new Plot(ix, iz, ox, oz);
    }

    private static boolean deepInside(long seed, int x, int z, Theme theme, double margin) {
        Probe p = probe(seed, x, z);
        return p.a.theme == theme && p.border > margin;
    }

    // ---- OJOS: casita rosa con camino de tierra (dc1)

    private static boolean house(long seed, Column c, int wx, int wz, Probe p, int[] top) {
        Plot plot = plot(seed, wx, wz, 64, 30, 55, 6, 12);
        if (plot == null) {
            return false;
        }
        int hx = plot.ox;
        int hz = plot.oz - 8;
        if (Math.abs(hx + 4) < 16 && Math.abs(hz + 3) < 16 || !deepInside(seed, hx + 4, hz + 3, Theme.OJOS, 14)) {
            return false; // nunca encima de donde se aparece, ni pisando otra zona
        }
        int base = height(seed, hx + 4, hz + 3);
        int lx = wx - hx;
        int lz = wz - hz;
        // camino que serpentea desde la puerta hacia el frente
        if (lz >= 7 && lz <= 34) {
            int pathX = 4 + (int) Math.round(3 * Math.sin((lz - 7) / 6.0) * Math.min(1, (lz - 7) / 4.0));
            if (Math.abs(lx - pathX) <= 1) {
                c.set(top[0], Material.DIRT_PATH);
                return true;
            }
            return false;
        }
        if (lx < 0 || lx > 8 || lz < 0 || lz > 6) {
            return false;
        }
        // base pareja
        c.fill(base - 3, base - 1, Material.DIRT);
        c.fill(base + 1, top[0] + 2, Material.AIR);
        c.set(base, Material.OAK_PLANKS);
        top[0] = base;
        boolean edgeX = lx == 0 || lx == 8;
        boolean edgeZ = lz == 0 || lz == 6;
        for (int y = base + 1; y <= base + 5; y++) {
            int rel = y - base;
            if (!edgeX && !edgeZ) {
                continue;
            }
            Material m = edgeX && edgeZ ? Material.WHITE_CONCRETE : Material.PINK_CONCRETE;
            boolean front = lz == 6;
            if (front && (lx == 1 || lx == 2 || lx == 6 || lx == 7) && (rel == 2 || rel == 3)) {
                m = Material.BLACK_CONCRETE; // ventanas oscuras
            }
            if (front && lx == 4 && rel <= 2) {
                continue; // hueco de la puerta
            }
            c.set(y, m);
        }
        if (lz == 6 && lx == 4) {
            c.sink.door(c.x, base + 1, c.z, Material.BIRCH_DOOR, 2);
        }
        // techo a dos aguas, con los frentes triangulares rosas y una ventanita arriba
        int peak = Math.min(lx, 8 - lx);
        for (int k = 0; k <= peak; k++) {
            int y = base + 6 + k;
            if (k == peak) {
                c.set(y, Material.PINK_TERRACOTTA);
            } else if (edgeZ) {
                c.set(y, lz == 6 && lx == 4 && k == 2 ? Material.BLACK_CONCRETE : Material.PINK_CONCRETE);
            }
        }
        if (lz == 6 || lz == 0) {
            c.set(base + 6 + peak, Material.WHITE_CONCRETE);
        }
        return true;
    }

    // ---- cofres dentro de las casas (los pone DreamChests)

    /** Porcentaje de casas con un cofre. */
    static final int CHEST_PERCENT = 30;

    /**
     * Donde va el cofre de una casa: la celda del piso (el aire donde se apoya), el material del piso que tiene que
     * haber abajo (para no poner un cofre si el terreno no salio como se calculo) y hacia donde mira (0 N, 1 E, 2 S,
     * 3 W).
     */
    record ChestSpot(int x, int y, int z, int facing, Theme theme, Material floor) {
    }

    /**
     * Los cofres de las casas de este chunk. Sale solo de la semilla y repite las cuentas de {@link #house}, {@link
     * #street} y {@link #field}: el cofre cae en una celda del interior hueco, nunca delante de la puerta de la casa
     * de OJOS ni del cartel negro de la de CAMPO. Las casas de BARRIO y CAMPO no tienen puerta que se abra, asi que
     * su cofre queda cerrado (se llega rompiendo una pared o un vidrio).
     */
    static List<ChestSpot> chestSpotsInChunk(long seed, int chunkX, int chunkZ) {
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        List<ChestSpot> spots = new ArrayList<>();
        ojosChests(seed, minX, minZ, spots);
        barrioChests(seed, minX, minZ, spots);
        campoChests(seed, minX, minZ, spots);
        spots.removeIf(spot -> spot.x < minX || spot.x > minX + 15 || spot.z < minZ || spot.z > minZ + 15);
        return spots;
    }

    private static void ojosChests(long seed, int minX, int minZ, List<ChestSpot> out) {
        for (int ix = Math.floorDiv(minX, 64); ix <= Math.floorDiv(minX + 15, 64); ix++) {
            for (int iz = Math.floorDiv(minZ, 64); iz <= Math.floorDiv(minZ + 15, 64); iz++) {
                Plot plot = plot(seed, ix * 64, iz * 64, 64, 30, 55, 6, 12);
                if (plot == null) {
                    continue;
                }
                int hx = plot.ox;
                int hz = plot.oz - 8;
                // house() solo arma la parte que cae en la parcela de la columna: una casa corrida al norte queda cortada
                if (hz < iz * 64 || Math.abs(hx + 4) < 16 && Math.abs(hz + 3) < 16
                    || !deepInside(seed, hx + 4, hz + 3, Theme.OJOS, 14) || !chance(seed, ix, iz, 90, CHEST_PERCENT)) {
                    continue;
                }
                // interior: lx 1..7, lz 1..5; la fila lz 5 es la de la puerta y se deja libre
                int x = hx + 1 + roll(seed, ix, iz, 91, 7);
                int z = hz + 1 + roll(seed, ix, iz, 92, 4);
                out.add(new ChestSpot(x, height(seed, hx + 4, hz + 3) + 1, z, 2, Theme.OJOS, Material.OAK_PLANKS));
            }
        }
    }

    private static void barrioChests(long seed, int minX, int minZ, List<ChestSpot> out) {
        for (int gx = Math.floorDiv(minX, SITE) - 1; gx <= Math.floorDiv(minX + 15, SITE) + 1; gx++) {
            for (int gz = Math.floorDiv(minZ, SITE) - 1; gz <= Math.floorDiv(minZ + 15, SITE) + 1; gz++) {
                Site s = site(seed, gx, gz);
                if (s.theme != Theme.BARRIO) {
                    continue;
                }
                int zone = s.gx * 97 + s.gz;
                int firstLot = Math.floorDiv(minX - s.x - 12, 13) - 1;
                int lastLot = Math.floorDiv(minX + 15 - s.x, 13) + 1;
                for (int lot = firstLot; lot <= lastLot; lot++) {
                    for (int side = -1; side <= 1; side += 2) {
                        if (!chance(seed, lot * side, zone, 80, 85) || !chance(seed, lot * side, zone, 95, CHEST_PERCENT)) {
                            continue; // sin casa en este lote, o sin cofre
                        }
                        // casa de lx 2..10 y depth 0..7: el cofre queda lejos de las paredes
                        int wx = s.x + lot * 13 + 4 + roll(seed, lot * side, zone, 96, 5);
                        int roadZ = s.z + (int) Math.round(9 * Math.sin((wx - s.x) / 26.0));
                        int wz = roadZ + side * (9 + 2 + roll(seed, lot * side, zone, 97, 3));
                        Probe p = probe(seed, wx, wz);
                        if (!p.a.equals(s) || p.border < 10) {
                            continue;
                        }
                        out.add(new ChestSpot(wx, height(seed, wx, wz) + 1, wz, side > 0 ? 0 : 2, Theme.BARRIO,
                            Material.SMOOTH_STONE));
                    }
                }
            }
        }
    }

    private static void campoChests(long seed, int minX, int minZ, List<ChestSpot> out) {
        for (int ix = Math.floorDiv(minX, 70); ix <= Math.floorDiv(minX + 15, 70); ix++) {
            for (int iz = Math.floorDiv(minZ, 70); iz <= Math.floorDiv(minZ + 15, 70); iz++) {
                Plot plot = plot(seed, ix * 70, iz * 70, 70, 60, 50, 14, 6);
                if (plot == null || !deepInside(seed, plot.ox + 2, plot.oz + 2, Theme.CAMPO, 12)
                    || !chance(seed, ix, iz, 100, CHEST_PERCENT)) {
                    continue;
                }
                // interior de 3 x 3 (x 1..3, z 1..3); la fila z 3 es la del cartel negro y se deja libre
                int x = plot.ox + 1 + roll(seed, ix, iz, 101, 3);
                int z = plot.oz + 1 + roll(seed, ix, iz, 102, 2);
                out.add(new ChestSpot(x, height(seed, x, z) + 1, z, 2, Theme.CAMPO, Material.GRASS_BLOCK));
            }
        }
    }

    // ---- PUERTAS: una puerta suelta abierta (dc2); tambien la usa COLLAGE con el hueco rojo

    private static boolean standaloneDoor(long seed, Column c, int wx, int wz, Probe p, int[] top, boolean red) {
        DoorSpot spot = doorAt(seed, wx, wz, red ? Theme.COLLAGE : Theme.PUERTAS);
        if (spot == null || spot.x != wx || spot.z != wz) {
            return false;
        }
        c.sink.door(c.x, spot.y, c.z, Material.BIRCH_DOOR, spot.facing);
        return true;
    }

    /** La puerta de la parcela de este punto, si la hay. */
    private static DoorSpot doorAt(long seed, int wx, int wz, Theme theme) {
        int size = theme == Theme.PUERTAS ? 44 : 60;
        int salt = theme == Theme.PUERTAS ? 40 : 45;
        Plot plot = plot(seed, wx, wz, size, salt, theme == Theme.PUERTAS ? 60 : 35, 8, 1);
        if (plot == null || !deepInside(seed, plot.ox, plot.oz, theme, 8)) {
            return null;
        }
        int facing = roll(seed, plot.ix, plot.iz, salt + 3, 4);
        return new DoorSpot(plot.ox, height(seed, plot.ox, plot.oz) + 1, plot.oz, facing, theme == Theme.COLLAGE);
    }

    /**
     * Las puertas sueltas ya buscadas, por chunk: salen solo de la semilla, asi que cada chunk se recorre una vez (son
     * 256 columnas). Se vacia si cambia la semilla o si crece demasiado.
     */
    private static final Map<Long, List<DoorSpot>> DOORS_BY_CHUNK = new HashMap<>();
    private static final int MAX_CACHED_DOOR_CHUNKS = 4096;
    private static long doorsSeed;

    /** Las puertas sueltas de este chunk (para el hueco negro/rojo y el "?"). */
    static synchronized List<DoorSpot> doorsInChunk(long seed, int chunkX, int chunkZ) {
        if (seed != doorsSeed || DOORS_BY_CHUNK.size() >= MAX_CACHED_DOOR_CHUNKS) {
            DOORS_BY_CHUNK.clear();
            doorsSeed = seed;
        }
        long key = (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
        return DOORS_BY_CHUNK.computeIfAbsent(key, ignored -> List.copyOf(findDoorsInChunk(seed, chunkX, chunkZ)));
    }

    static synchronized void clearDoorCache() {
        DOORS_BY_CHUNK.clear();
    }

    private static List<DoorSpot> findDoorsInChunk(long seed, int chunkX, int chunkZ) {
        List<DoorSpot> doors = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = chunkX * 16 + x;
                int wz = chunkZ * 16 + z;
                Theme theme = theme(seed, wx, wz);
                if (theme != Theme.PUERTAS && theme != Theme.COLLAGE) {
                    continue;
                }
                DoorSpot spot = doorAt(seed, wx, wz, theme);
                if (spot != null && spot.x == wx && spot.z == wz) {
                    doors.add(spot);
                }
            }
        }
        return doors;
    }

    /** Es este bloque una de las puertas sueltas (no la de una casa)? Mira las dos mitades. */
    static boolean isStandaloneDoor(long seed, int x, int y, int z) {
        for (DoorSpot spot : doorsInChunk(seed, x >> 4, z >> 4)) {
            if (spot.x == x && spot.z == z && (y == spot.y || y == spot.y + 1)) {
                return true;
            }
        }
        return false;
    }

    // ---- PLAZA: juegos de plaza solitarios (dc3)

    private static boolean playground(long seed, Column c, int wx, int wz, Probe p, int[] top) {
        Plot plot = plot(seed, wx, wz, 80, 50, 80, 14, 22);
        if (plot == null || !deepInside(seed, plot.ox + 8, plot.oz + 2, Theme.PLAZA, 12)) {
            return false;
        }
        int x = wx - plot.ox;
        int z = wz - plot.oz;
        int y = top[0];
        boolean built = false;
        // dos torres con plataforma y techito, unidas por un puente
        for (int tower = 0; tower < 2; tower++) {
            int tx = x - tower * 8;
            if (tx < 0 || tx > 3 || z < 0 || z > 3) {
                continue;
            }
            built = true;
            boolean post = (tx == 0 || tx == 3) && (z == 0 || z == 3);
            if (post) {
                c.fill(y + 1, y + 6, Material.IRON_BARS);
            }
            c.set(y + 4, tower == 0 ? Material.BLUE_CONCRETE : Material.RED_CONCRETE);
            if ((tx == 0 || tx == 3 || z == 0 || z == 3) && !post) {
                c.set(y + 5, Material.YELLOW_CONCRETE);
            }
            c.set(y + 7, tower == 0 ? Material.LIGHT_BLUE_CONCRETE : Material.LIME_CONCRETE);
            if (tx >= 1 && tx <= 2 && z >= 1 && z <= 2) {
                c.set(y + 8, tower == 0 ? Material.CYAN_CONCRETE : Material.GREEN_CONCRETE);
            }
        }
        if (x >= 4 && x <= 7 && z >= 1 && z <= 2) {
            c.set(y + 4, Material.YELLOW_CONCRETE); // puente
            built = true;
        }
        if (x >= 12 && x <= 17 && z >= 1 && z <= 2) {
            c.set(y + Math.max(1, 4 - (x - 12) * 3 / 5), Material.LIME_CONCRETE); // tobogan
            built = true;
        }
        if (x >= -6 && x <= -1 && z >= 1 && z <= 2) {
            int ty = y + Math.max(1, 4 + x * 3 / 6);
            c.set(ty, (x & 1) == 0 ? Material.ORANGE_CONCRETE : Material.RED_CONCRETE); // tubo
            c.set(ty + 1, z == 1 ? Material.ORANGE_CONCRETE : Material.RED_CONCRETE);
            built = true;
        }
        return built;
    }

    // ---- CAMPO: surcos de siembra y casitas lejanas (images (9))

    private static boolean field(long seed, Column c, int wx, int wz, Probe p, int[] top) {
        Plot plot = plot(seed, wx, wz, 70, 60, 50, 14, 6);
        if (plot != null && deepInside(seed, plot.ox + 2, plot.oz + 2, Theme.CAMPO, 12)) {
            int x = wx - plot.ox;
            int z = wz - plot.oz;
            if (x >= 0 && x <= 4 && z >= 0 && z <= 4) {
                int base = top[0];
                boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
                if (edge) {
                    c.fill(base + 1, base + 3, (z == 4 && x == 2) ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE);
                }
                int roof = Math.min(x, 4 - x);
                c.set(base + 4 + roof, Material.RED_TERRACOTTA);
                if (z == 0 || z == 4) {
                    for (int k = 0; k < roof; k++) {
                        c.set(base + 4 + k, Material.WHITE_CONCRETE);
                    }
                }
                return true;
            }
        }
        // surcos: hileras de brotes cada dos bloques
        if (Math.floorMod(wx, 2) == 0 && p.border > 2) {
            c.set(top[0] + 1, Material.SHORT_GRASS);
            return true;
        }
        return false;
    }

    // ---- COLLAGE: arboles, flores y puertas abiertas al rojo

    private static boolean collage(long seed, Column c, int wx, int wz, Probe p, int[] top) {
        if (standaloneDoor(seed, c, wx, wz, p, top, true)) {
            return true;
        }
        int size = 13;
        int ix = Math.floorDiv(wx, size);
        int iz = Math.floorDiv(wz, size);
        boolean built = false;
        // un arbol por parcela (a veces): tronco y copa redonda; mira tambien las parcelas vecinas por la copa
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int px = ix + ox;
                int pz = iz + oz;
                if (!chance(seed, px, pz, 70, 45)) {
                    continue;
                }
                int tx = px * size + 3 + roll(seed, px, pz, 71, size - 6);
                int tz = pz * size + 3 + roll(seed, px, pz, 72, size - 6);
                if (!deepInside(seed, tx, tz, Theme.COLLAGE, 4)) {
                    continue;
                }
                int ground = height(seed, tx, tz);
                int trunk = 5 + roll(seed, px, pz, 73, 3);
                int cy = ground + trunk;
                if (wx == tx && wz == tz) {
                    c.fill(ground + 1, ground + trunk, Material.OAK_LOG);
                    built = true;
                }
                double dx = wx - tx;
                double dz = wz - tz;
                for (int y = cy - 2; y <= cy + 3; y++) {
                    double dy = (y - cy) * 1.2;
                    if (dx * dx + dz * dz + dy * dy <= 10.5 && !(wx == tx && wz == tz && y <= cy)) {
                        c.set(y, Material.OAK_LEAVES);
                        built = true;
                    }
                }
            }
        }
        return built;
    }

    // ---- BARRIO: calle curva, veredas y casas pastel (ab67)

    private static final Material[] PASTEL = {Material.PINK_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.WHITE_CONCRETE, Material.PINK_TERRACOTTA};

    private static boolean street(long seed, Column c, int wx, int wz, Probe p, int[] top) {
        Site s = p.a;
        if (p.border < 6) {
            return false;
        }
        int roadZ = s.z + (int) Math.round(9 * Math.sin((wx - s.x) / 26.0));
        int dz = wz - roadZ;
        int ad = Math.abs(dz);
        int y = top[0];
        if (ad <= 3) {
            c.set(y, Material.GRAY_CONCRETE);
            return true;
        }
        if (ad <= 5) {
            c.set(y, Material.LIGHT_GRAY_CONCRETE);
            return true;
        }
        if (ad < 9 || ad > 16) {
            return false;
        }
        // lotes de 13 a lo largo de la calle, casa de 9 de ancho y 7 de fondo a cada lado
        int lot = Math.floorDiv(wx - s.x, 13);
        int lx = Math.floorMod(wx - s.x, 13);
        int side = dz > 0 ? 1 : -1;
        if (lx < 2 || lx > 10 || !chance(seed, lot * side, s.gx * 97 + s.gz, 80, 85)) {
            return false;
        }
        int depth = ad - 9; // 0 = frente a la calle
        Material wall = PASTEL[roll(seed, lot * side, s.gx * 97 + s.gz, 81, PASTEL.length)];
        boolean edge = lx == 2 || lx == 10 || depth == 0 || depth == 7;
        c.set(y, Material.SMOOTH_STONE);
        if (edge) {
            for (int yy = y + 1; yy <= y + 4; yy++) {
                Material m = wall;
                int rel = yy - y;
                if (depth == 0 && lx >= 6 && lx <= 9 && rel <= 2) {
                    m = Material.WHITE_CONCRETE; // garage
                } else if (depth == 0 && lx >= 3 && lx <= 4 && rel >= 2 && rel <= 3) {
                    m = Material.LIGHT_BLUE_STAINED_GLASS; // ventana
                }
                c.set(yy, m);
            }
        }
        // techo gris a dos aguas a lo largo de la calle
        int ridge = Math.min(depth, 7 - depth);
        c.set(y + 5 + ridge / 2, Material.GRAY_CONCRETE);
        return true;
    }
}
