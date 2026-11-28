package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Lo que hace cada entidad de los backrooms con su jugador (las reglas con numeros estan en {@link BackroomsRules}) y
 * donde aparece. Cada tick, {@link BackroomsEntities} llama a {@link Encounter#tick()}.
 */
final class BackroomsEncounters {

    private BackroomsEncounters() {
    }

    /** Un encuentro: una entidad persiguiendo (o mirando) a un jugador. */
    abstract static class Encounter {

        final BackroomsEntities env;
        final Player player;
        final BackroomsKind kind;
        final BackroomsFigure figure;
        /** Cuantos ticks lleva el susto (-1: todavia no empezo). */
        int scareTicks = -1;

        Encounter(BackroomsEntities env, Player player, BackroomsKind kind, BackroomsFigure figure) {
            this.env = env;
            this.player = player;
            this.kind = kind;
            this.figure = figure;
            this.blackout = new BackroomsBlackout(player);
        }

        /** Las luces de alrededor, que se apagan mientras te persigue. */
        final BackroomsBlackout blackout;

        /** @return true si el encuentro termino (hay que sacar la figura) */
        abstract boolean tick();

        /** Si ahora te esta persiguiendo (o ya te agarro): ahi se apagan las luces. */
        boolean chasing() {
            return true;
        }

        /** El jugador hizo clic en el aire (sirve para soltarse de Juan Carlos). */
        void onSwing() {
        }

        boolean scaring() {
            return scareTicks >= 0;
        }

        double distance() {
            Location at = figure.position();
            return Math.hypot(player.getX() - at.getX(), player.getZ() - at.getZ());
        }

        void remove() {
            blackout.restore();
            figure.remove();
        }

        /** Apaga o devuelve las luces segun si te persigue; la figura se ve un poco en lo oscuro. */
        void updateBlackout() {
            boolean dark = scaring() || chasing();
            blackout.update(dark);
            figure.setDim(blackout.on());
        }

        void sound(String event, SoundCategory category, float volume) {
            CaiChetSounds.playTo(player, figure.position(), event, category, volume, 1f);
        }

        /** Un paso hacia el jugador en horizontal (los pies no cambian de altura). */
        void walkToward(double step) {
            Location at = figure.position();
            double dx = player.getX() - at.getX();
            double dz = player.getZ() - at.getZ();
            double length = Math.hypot(dx, dz);
            if (length < 1e-6 || step <= 0) {
                return;
            }
            figure.moveTo(at.add(dx / length * step, 0, dz / length * step));
        }

        /** Mirar al jugador y avanzar la animacion un tick. */
        void animate(BackroomsRigs.Mode mode) {
            figure.setMode(mode);
            figure.faceTowards(player.getLocation());
            figure.tick();
        }
    }

    // ------------------------------------------------------------------ 1. sonrisa

    static final class SonrisaEncounter extends Encounter {

        private final BackroomsRules.Sonrisa rules = new BackroomsRules.Sonrisa();

        SonrisaEncounter(BackroomsEntities env, Player player, BackroomsFigure figure) {
            super(env, player, BackroomsKind.SONRISA, figure);
            sound(CaiChetSounds.SONRISA_LAUGH, SoundCategory.HOSTILE, 0.5f);
        }

        @Override
        boolean chasing() {
            return rules.lunging();
        }

        @Override
        boolean tick() {
            Location eye = player.getEyeLocation();
            if (rules.lunging()) {
                Vector to = eye.toVector().subtract(figure.position().toVector());
                double distance = to.length();
                double left = BackroomsRules.Sonrisa.lungeStep(distance);
                if (left <= 0) {
                    env.startScare(this);
                    return false;
                }
                // se lanza a la cara (atraviesa las paredes)
                Location at = figure.position();
                figure.moveTo(at.add(to.normalize().multiply(distance - left)));
            } else {
                boolean seen = Watchers.sees(player, figure.position(), BackroomsRules.Sonrisa.SEEN_ANGLE_DEGREES);
                switch (rules.tick(seen)) {
                    case LUNGE -> sound(CaiChetSounds.SONRISA_LAUGH, SoundCategory.HOSTILE, 1.5f);
                    case VANISH, EXPIRE -> {
                        return true;
                    }
                    default -> { }
                }
            }
            animate(BackroomsRigs.Mode.IDLE);
            return false;
        }
    }

    // ------------------------------------------------------------------ 2. e666 y 4. arana

    static final class ChaseEncounter extends Encounter {

        private final BackroomsRules.Chase rules;
        private int age;
        private int lastBeat;

        ChaseEncounter(BackroomsEntities env, Player player, BackroomsKind kind, BackroomsFigure figure) {
            super(env, player, kind, figure);
            this.rules = kind == BackroomsKind.ARANA ? BackroomsRules.Chase.ARANA : BackroomsRules.Chase.E666;
            if (kind == BackroomsKind.ARANA) {
                sound(CaiChetSounds.ARANA_SCREECH, SoundCategory.HOSTILE, 2.5f);
            }
        }

        @Override
        boolean tick() {
            age++;
            double distance = distance();
            switch (rules.step(distance, age)) {
                case CAUGHT -> {
                    env.startScare(this);
                    return false;
                }
                case GONE -> {
                    if (kind == BackroomsKind.ARANA) {
                        sound(CaiChetSounds.ARANA_SCREECH, SoundCategory.HOSTILE, 2f);
                    }
                    return true;
                }
                default -> { }
            }
            walkToward(rules.advance(distance));
            if (kind == BackroomsKind.ARANA) {
                if (age % 6 == 0) {
                    sound(CaiChetSounds.ARANA_CLICK, SoundCategory.HOSTILE, 1.2f);
                }
            } else {
                if (age - lastBeat >= BackroomsRules.heartbeatEvery(distance)) {
                    lastBeat = age;
                    sound(BackroomsRules.heartbeatSound(distance), SoundCategory.HOSTILE, 1.5f);
                }
                if (age % BackroomsRules.STEP_TICKS == 0) {
                    sound(CaiChetSounds.ENTITY_STEP, SoundCategory.HOSTILE, 1f);
                }
            }
            animate(BackroomsRigs.Mode.WALK);
            return false;
        }
    }

    // ------------------------------------------------------------------ 3. juan carlos

    static final class JuanCarlosEncounter extends Encounter {

        private final BackroomsRules.JuanCarlos rules = new BackroomsRules.JuanCarlos();
        private double handsX;
        private double handsZ;
        private double baseY;
        private double maxLift;

        JuanCarlosEncounter(BackroomsEntities env, Player player, BackroomsFigure figure) {
            super(env, player, BackroomsKind.JUANCARLOS, figure);
            sound(CaiChetSounds.JUANCARLOS_GROWL, SoundCategory.HOSTILE, 1.5f);
        }

        @Override
        boolean tick() {
            BackroomsRules.JuanCarlos.Event event = rules.tick(distance());
            switch (event) {
                case GRABBED -> grab();
                case THROWN -> throwPlayer();
                case SCARE -> {
                    env.startScare(this);
                    return false;
                }
                case GONE -> {
                    return true;
                }
                default -> { }
            }
            switch (rules.phase()) {
                case WALK -> {
                    walkToward(BackroomsRules.JuanCarlos.SPEED);
                    animate(BackroomsRigs.Mode.WALK);
                }
                case GRAB -> {
                    lift();
                    animate(BackroomsRigs.Mode.GRAB);
                }
                default -> animate(BackroomsRigs.Mode.IDLE);
            }
            return false;
        }

        private void grab() {
            Location at = figure.position();
            double dx = player.getX() - at.getX();
            double dz = player.getZ() - at.getZ();
            double length = Math.max(1e-6, Math.hypot(dx, dz));
            // las manos estan a un bloque, hacia el jugador
            handsX = at.getX() + dx / length;
            handsZ = at.getZ() + dz / length;
            baseY = player.getY();
            maxLift = Math.max(0, Watchers.freeHeight(player.getLocation(), 8) - 1.9);
            sound(CaiChetSounds.JUANCARLOS_GRAB, SoundCategory.HOSTILE, 2f);
            env.actionBar(player, grabText());
        }

        private void lift() {
            Location target = player.getLocation();
            target.setX(handsX);
            target.setZ(handsZ);
            target.setY(baseY + BackroomsRules.JuanCarlos.lift(rules.held(), maxLift));
            player.setFallDistance(0f);
            player.teleport(target);
            player.setVelocity(new Vector());
        }

        private void throwPlayer() {
            Location at = figure.position();
            Vector away = new Vector(player.getX() - at.getX(), 0, player.getZ() - at.getZ());
            if (away.lengthSquared() < 1e-6) {
                away = player.getLocation().getDirection().setY(0).multiply(-1);
            }
            away.normalize().multiply(1.2).setY(0.6);
            player.setVelocity(away);
            sound(CaiChetSounds.JUANCARLOS_GROWL, SoundCategory.HOSTILE, 2f);
        }

        @Override
        void onSwing() {
            if (rules.phase() != BackroomsRules.JuanCarlos.Phase.GRAB) {
                return;
            }
            boolean released = rules.click();
            env.actionBar(player, grabText());
            if (released) {
                sound(CaiChetSounds.JUANCARLOS_GROWL, SoundCategory.HOSTILE, 1.5f);
            }
        }

        private Component grabText() {
            return Component.text("hace clic para soltarte " + rules.clicks() + "/"
                + BackroomsRules.JuanCarlos.CLICKS_TO_RELEASE, NamedTextColor.GRAY);
        }
    }

    // ------------------------------------------------------------------ 5. payaso

    static final class PayasoEncounter extends Encounter {

        private final BackroomsRules.Payaso rules = new BackroomsRules.Payaso();

        PayasoEncounter(BackroomsEntities env, Player player, BackroomsFigure figure) {
            super(env, player, BackroomsKind.PAYASO, figure);
        }

        @Override
        boolean chasing() {
            return rules.spotted();
        }

        @Override
        boolean tick() {
            Location chest = figure.position().add(0, 1.4, 0);
            boolean seen = Watchers.sees(player, chest, BackroomsRules.Payaso.SEEN_ANGLE_DEGREES);
            switch (rules.tick(seen, distance())) {
                case SPOTTED -> sound(CaiChetSounds.PAYASO_GIGGLE, SoundCategory.HOSTILE, 1.5f);
                case WIN -> {
                    sound(CaiChetSounds.PAYASO_LAUGH, SoundCategory.HOSTILE, 0.4f);
                    return true;
                }
                case LOSE -> {
                    env.startScare(this);
                    return false;
                }
                case GONE -> {
                    return true;
                }
                default -> { }
            }
            animate(BackroomsRigs.Mode.SPY);
            return false;
        }
    }

    // ------------------------------------------------------------------ aparicion

    /** Arma el encuentro de esta entidad para el jugador, o null si no hay lugar. */
    static Encounter create(BackroomsEntities env, Player player, BackroomsKind kind) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        switch (kind) {
            case SONRISA -> {
                Location spot = floorSpot(player, 10, 18, 60, true, random);
                if (spot == null) {
                    return null;
                }
                Location center = spot.add(0, 1.6, 0);
                return new SonrisaEncounter(env, player,
                    new BackroomsFigure(player, kind, center, 1f, 1));
            }
            case E666 -> {
                Location spot = floorSpot(player, 16, 24, 180, false, random);
                return spot == null ? null : new ChaseEncounter(env, player, kind, figureAt(player, kind, spot, 1));
            }
            case JUANCARLOS -> {
                Location spot = floorSpot(player, 12, 20, 60, false, random);
                return spot == null ? null
                    : new JuanCarlosEncounter(env, player, figureAt(player, kind, spot, 1));
            }
            case ARANA -> {
                Location spot = floorSpot(player, 22, 32, 60, false, random);
                return spot == null ? null : new ChaseEncounter(env, player, kind, figureAt(player, kind, spot, 1));
            }
            default -> {
                return payaso(env, player, world, eye, random);
            }
        }
    }

    private static BackroomsFigure figureAt(Player player, BackroomsKind kind, Location spot, int side) {
        float fit = BackroomsRigs.fit(kind, Watchers.freeHeight(spot, 8));
        return new BackroomsFigure(player, kind, spot, fit, side);
    }

    /**
     * Un lugar con piso y dos bloques libres a entre min y max bloques del jugador, dentro de spreadDegrees a cada lado
     * de adonde mira (180 es cualquier direccion), y si hace falta que se vea desde sus ojos.
     */
    private static Location floorSpot(Player player, double min, double max, double spreadDegrees, boolean needView,
                                      ThreadLocalRandom random) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        double yaw = Math.toRadians(eye.getYaw());
        int y = HotelLayout.FLOOR + 1;
        for (int attempt = 0; attempt < 60; attempt++) {
            double angle = yaw + Math.toRadians(random.nextDouble(-spreadDegrees, spreadDegrees));
            double distance = random.nextDouble(min, max);
            int x = (int) Math.floor(eye.getX() - Math.sin(angle) * distance);
            int z = (int) Math.floor(eye.getZ() + Math.cos(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Location spot = new Location(world, x + 0.5, y, z + 0.5);
            if (!roomFor(spot)) {
                continue;
            }
            if (needView && !Watchers.clearView(eye, spot.clone().add(0, 1.6, 0))) {
                continue;
            }
            return spot;
        }
        return null;
    }

    /** Piso solido debajo y dos bloques libres encima. */
    private static boolean roomFor(Location spot) {
        Block feet = spot.getBlock();
        return feet.getRelative(0, -1, 0).getType().isSolid() && Watchers.standable(feet)
            && Watchers.standable(feet.getRelative(0, 1, 0));
    }

    /** El Payaso: en una esquina (con una pared a un costado) que se ve, corrido hacia la pared. */
    private static Encounter payaso(BackroomsEntities env, Player player, World world, Location eye,
                                    ThreadLocalRandom random) {
        double yaw = Math.toRadians(eye.getYaw());
        int y = HotelLayout.FLOOR + 1;
        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = yaw + Math.toRadians(random.nextDouble(-45, 45));
            double distance = random.nextDouble(6, 14);
            double dirX = -Math.sin(angle);
            double dirZ = Math.cos(angle);
            int x = (int) Math.floor(eye.getX() + dirX * distance);
            int z = (int) Math.floor(eye.getZ() + dirZ * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Location spot = new Location(world, x + 0.5, y, z + 0.5);
            if (!roomFor(spot)) {
                continue;
            }
            // perpendicular a la linea de vista: a un lado o al otro
            double perpX = -dirZ;
            double perpZ = dirX;
            int wallSign = wallSide(world, x, y, z, perpX, perpZ, random.nextBoolean());
            if (wallSign == 0) {
                continue;
            }
            Location hidden = spot.clone().add(perpX * wallSign * 0.35, 0, perpZ * wallSign * 0.35);
            if (!Watchers.clearView(eye, hidden.clone().add(0, 1.2, 0))) {
                continue;
            }
            // la pared a la derecha o a la izquierda de la figura (que mira hacia el jugador)
            double faceX = -dirX;
            double faceZ = -dirZ;
            double rightX = -faceZ;
            double rightZ = faceX;
            int side = perpX * wallSign * rightX + perpZ * wallSign * rightZ > 0 ? 1 : -1;
            return new PayasoEncounter(env, player, figureAt(player, BackroomsKind.PAYASO, hidden, side));
        }
        return null;
    }

    /** +1 si hay un bloque solido junto al lugar en la direccion (perpX, perpZ), -1 si del otro lado, 0 si en ninguno. */
    private static int wallSide(World world, int x, int y, int z, double perpX, double perpZ, boolean preferPositive) {
        int[] order = preferPositive ? new int[] {1, -1} : new int[] {-1, 1};
        for (int sign : order) {
            int wx = (int) Math.floor(x + 0.5 + perpX * sign);
            int wz = (int) Math.floor(z + 0.5 + perpZ * sign);
            if (wx == x && wz == z) {
                continue;
            }
            if (world.getBlockAt(wx, y, wz).getType().isSolid() && world.getBlockAt(wx, y + 1, wz).getType().isSolid()) {
                return sign;
            }
        }
        return 0;
    }
}
