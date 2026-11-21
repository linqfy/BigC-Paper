package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Arma los patrones de rayos de la fase 1 sobre la grilla cuadrada {@code -radius..radius} (sin Bukkit). Cada tanda
 * deja baldosas libres y se marca en el piso al menos {@code telegraph} ticks antes de caer, asi siempre se puede
 * esquivar: es "Binding of Isaac" con rayos, no una lotería. Las baldosas que en la arena real son pared o vacio las
 * descarta despues la capa de Bukkit.
 */
public final class ZeusPatterns {

    /** Ticks entre tandas de cada patron (las tandas se solapan: se ve venir la siguiente). */
    static final int LINES_STEP = 16;
    static final int CROSS_STEP = 24;
    static final int RINGS_STEP = 26;
    static final int SPIRAL_STEP = 6;
    static final int SPIRAL_WAVES = 10;
    static final int CHECKER_STEP = 28;
    static final int SWEEP_STEP = 5;
    static final int RAIN_STEP = 12;
    static final int RAIN_WAVES = 5;
    static final double RAIN_DENSITY = 0.22;

    private ZeusPatterns() {
    }

    /**
     * @param radius    radio de la grilla en baldosas
     * @param telegraph ticks que se ve cada tanda en el piso antes de caer
     * @param zeus      la baldosa de Zeus (centro de anillos y espiral)
     * @param targets   las baldosas de los jugadores (centro de la cruz, primera tanda de la lluvia)
     */
    public static ZeusPattern build(ZeusPatternKind kind, int radius, int telegraph, ZeusCell zeus,
                                    List<ZeusCell> targets, RandomGenerator random) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(zeus, "zeus");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(random, "random");
        if (radius < 2) {
            throw new IllegalArgumentException("radius must be at least 2");
        }
        if (telegraph < 1) {
            throw new IllegalArgumentException("telegraph must be positive");
        }
        ZeusCell center = clamp(zeus, radius);
        ZeusCell target = targets.isEmpty() ? center
            : clamp(targets.get(random.nextInt(targets.size())), radius);
        List<ZeusStrikeWave> waves = switch (kind) {
            case LINES -> lines(radius, telegraph, random);
            case CROSS -> cross(radius, telegraph, target);
            case RINGS -> rings(radius, telegraph, center);
            case SPIRAL -> spiral(radius, telegraph, center, random);
            case CHECKERBOARD -> checkerboard(radius, telegraph);
            case SWEEP -> sweep(radius, telegraph, random);
            case RAIN -> rain(radius, telegraph, targets, random);
        };
        return new ZeusPattern(kind, waves);
    }

    private static List<ZeusStrikeWave> lines(int radius, int telegraph, RandomGenerator random) {
        boolean rows = random.nextBoolean();
        int offset = random.nextInt(3);
        List<ZeusStrikeWave> waves = new ArrayList<>(3);
        for (int k = 0; k < 3; k++) {
            int phase = offset + k;
            waves.add(wave(k * LINES_STEP, telegraph, radius,
                cell -> Math.floorMod((rows ? cell.z() : cell.x()) - phase, 3) == 0));
        }
        return waves;
    }

    private static List<ZeusStrikeWave> cross(int radius, int telegraph, ZeusCell target) {
        int cx = target.x();
        int cz = target.z();
        return List.of(
            wave(0, telegraph, radius, cell -> cell.x() == cx || cell.z() == cz),
            wave(CROSS_STEP, telegraph, radius,
                cell -> Math.abs(cell.x() - cx) == Math.abs(cell.z() - cz)),
            wave(2 * CROSS_STEP, telegraph, radius,
                cell -> Math.abs(cell.x() - cx) == 2 || Math.abs(cell.z() - cz) == 2));
    }

    private static List<ZeusStrikeWave> rings(int radius, int telegraph, ZeusCell center) {
        List<ZeusStrikeWave> waves = new ArrayList<>(3);
        for (int k = 0; k < 3; k++) {
            int parity = k % 2;
            // la baldosa de Zeus nunca: pegarle de cerca es la forma de ganar
            waves.add(wave(k * RINGS_STEP, telegraph, radius, cell -> {
                long ring = Math.round(Math.hypot(cell.x() - center.x(), cell.z() - center.z()));
                return ring >= 1 && ring % 2 == parity;
            }));
        }
        return waves;
    }

    private static List<ZeusStrikeWave> spiral(int radius, int telegraph, ZeusCell center, RandomGenerator random) {
        double start = random.nextDouble() * Math.PI * 2.0;
        double turn = random.nextBoolean() ? 1.0 : -1.0;
        List<ZeusStrikeWave> waves = new ArrayList<>(SPIRAL_WAVES);
        for (int n = 0; n < SPIRAL_WAVES; n++) {
            double theta = start + turn * n * Math.PI / 9.0;
            waves.add(wave(n * SPIRAL_STEP, telegraph, radius, cell -> onSpiralArm(cell, center, theta)));
        }
        return waves;
    }

    /** Tres brazos curvos: una baldosa cae si su arco al brazo mas cercano es de menos de 0,6 baldosas. */
    static boolean onSpiralArm(ZeusCell cell, ZeusCell center, double theta) {
        double dx = cell.x() - center.x();
        double dz = cell.z() - center.z();
        double distance = Math.hypot(dx, dz);
        if (distance < 0.5) {
            return false;
        }
        double angle = Math.atan2(dz, dx);
        for (int arm = 0; arm < 3; arm++) {
            double armAngle = theta + arm * (2.0 * Math.PI / 3.0) + 0.35 * distance;
            double diff = Math.IEEEremainder(angle - armAngle, 2.0 * Math.PI);
            if (Math.abs(diff) * distance <= 0.6) {
                return true;
            }
        }
        return false;
    }

    private static List<ZeusStrikeWave> checkerboard(int radius, int telegraph) {
        List<ZeusStrikeWave> waves = new ArrayList<>(3);
        for (int k = 0; k < 3; k++) {
            int parity = k % 2;
            waves.add(wave(k * CHECKER_STEP, telegraph, radius,
                cell -> Math.floorMod(cell.x() + cell.z(), 2) == parity));
        }
        return waves;
    }

    /** Dos paredes que cruzan la grilla de punta a punta, cada una con un hueco de tres baldosas. */
    private static List<ZeusStrikeWave> sweep(int radius, int telegraph, RandomGenerator random) {
        List<ZeusStrikeWave> waves = new ArrayList<>(2 * (2 * radius + 1));
        int lines = 2 * radius + 1;
        int secondStart = lines * SWEEP_STEP + telegraph / 2;
        for (int pass = 0; pass < 2; pass++) {
            boolean alongX = pass == 0;
            int direction = random.nextBoolean() ? 1 : -1;
            int gap = random.nextInt(2 * radius - 1) - (radius - 1);
            int base = pass == 0 ? 0 : secondStart;
            for (int i = 0; i < lines; i++) {
                int line = direction * (i - radius);
                waves.add(wave(base + i * SWEEP_STEP, telegraph, radius, cell -> {
                    int along = alongX ? cell.x() : cell.z();
                    int across = alongX ? cell.z() : cell.x();
                    return along == line && Math.abs(across - gap) > 1;
                }));
            }
        }
        return waves;
    }

    private static List<ZeusStrikeWave> rain(int radius, int telegraph, List<ZeusCell> targets,
                                             RandomGenerator random) {
        List<ZeusStrikeWave> waves = new ArrayList<>(RAIN_WAVES);
        for (int n = 0; n < RAIN_WAVES; n++) {
            Set<ZeusCell> cells = new HashSet<>();
            for (ZeusCell cell : square(radius)) {
                if (random.nextDouble() < RAIN_DENSITY) {
                    cells.add(cell);
                }
            }
            if (n == 0) {
                // la primera tanda apunta a donde estan parados: hay que moverse desde el arranque
                for (ZeusCell target : targets) {
                    cells.add(clamp(target, radius));
                }
            }
            if (cells.isEmpty()) {
                cells.add(new ZeusCell(random.nextInt(2 * radius + 1) - radius,
                    random.nextInt(2 * radius + 1) - radius));
            }
            waves.add(new ZeusStrikeWave(n * RAIN_STEP, telegraph, cells));
        }
        return waves;
    }

    private static ZeusStrikeWave wave(int start, int telegraph, int radius, Predicate<ZeusCell> struck) {
        Set<ZeusCell> cells = new HashSet<>();
        for (ZeusCell cell : square(radius)) {
            if (struck.test(cell)) {
                cells.add(cell);
            }
        }
        return new ZeusStrikeWave(start, telegraph, cells);
    }

    static List<ZeusCell> square(int radius) {
        List<ZeusCell> cells = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                cells.add(new ZeusCell(x, z));
            }
        }
        return cells;
    }

    private static ZeusCell clamp(ZeusCell cell, int radius) {
        return new ZeusCell(Math.max(-radius, Math.min(radius, cell.x())),
            Math.max(-radius, Math.min(radius, cell.z())));
    }
}
