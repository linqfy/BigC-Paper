package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

/**
 * Las formas de los golpes de la fase 2 y donde se para cada señor para darlos, sin Bukkit. Las areas son
 * horizontales (los pies del jugador) con una tolerancia de altura para saltos.
 */
public final class CameStrikes {

    /** Daño (antes de armadura) de cada golpe: con diamante completo ~4, con hierro ~7-8. */
    public static final double DASH_DAMAGE = 10.0;
    public static final double ROOT_SLAM_DAMAGE = 6.0;
    public static final double LEAP_DAMAGE = 12.0;
    public static final double STRIKE_DAMAGE = 8.0;
    public static final double SWEEP_DAMAGE = 11.0;

    public static final double DASH_WIDTH = 1.3;
    public static final double DASH_START = 7.0;
    public static final double DASH_LENGTH = 13.0;
    public static final double ROOT_RADIUS = 3.0;
    public static final double LEAP_RADIUS = 2.5;
    public static final double STRIKE_RADIUS = 2.2;
    public static final double STRIKE_STAND_OFF = 1.8;
    public static final double SWEEP_HALF_ANGLE = 38.0;
    public static final double SWEEP_RANGE = 6.0;
    public static final double SWEEP_STAND_OFF = 3.5;
    /** Bloques por encima del piso del golpe en los que todavia pega (un salto no alcanza para esquivar). */
    public static final double HEIGHT_TOLERANCE = 2.5;

    private CameStrikes() {
    }

    public static boolean sameHeight(double groundY, double feetY) {
        return feetY >= groundY - 1.0 && feetY <= groundY + HEIGHT_TOLERANCE;
    }

    public static boolean inCircle(CameVec center, double radius, CameVec feet) {
        return sameHeight(center.y(), feet.y()) && center.horizontalDistance(feet) <= radius;
    }

    /** Un cono horizontal desde {@code origin} hacia {@code facing}. */
    public static boolean inCone(CameVec origin, CameVec facing, double halfAngleDegrees, double range, CameVec feet) {
        if (!sameHeight(origin.y(), feet.y())) {
            return false;
        }
        CameVec offset = new CameVec(feet.x() - origin.x(), 0.0, feet.z() - origin.z());
        double distance = offset.length();
        if (distance > range) {
            return false;
        }
        if (distance < 0.8) {
            return true; // pegado al que barre
        }
        return offset.angleTo(new CameVec(facing.x(), 0.0, facing.z())) <= halfAngleDegrees;
    }

    /** Un carril horizontal de {@code from} a {@code to} de ancho {@code 2 * halfWidth}. */
    public static boolean inLane(CameVec from, CameVec to, double halfWidth, CameVec feet) {
        if (!sameHeight(from.y(), feet.y())) {
            return false;
        }
        double ax = to.x() - from.x();
        double az = to.z() - from.z();
        double lengthSquared = ax * ax + az * az;
        double px = feet.x() - from.x();
        double pz = feet.z() - from.z();
        double t = lengthSquared < 1.0e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, (px * ax + pz * az) / lengthSquared));
        double dx = px - ax * t;
        double dz = pz - az * t;
        return dx * dx + dz * dz <= halfWidth * halfWidth;
    }

    /**
     * Donde arranca cada uno en la pinza y la embestida: a {@link #DASH_START} bloques del jugador, Hun por un lado
     * del eje y Vucub por el otro.
     */
    public static CameVec pincerStart(CameVec target, CameVec axis, CameLord lord) {
        CameVec flat = axis.horizontal();
        double sign = lord == CameLord.HUN ? 1.0 : -1.0;
        return target.add(flat.multiply(DASH_START * sign));
    }

    /** El final del carril de una embestida que sale de {@code start} y pasa por {@code target}. */
    public static CameVec dashEnd(CameVec start, CameVec target) {
        CameVec direction = new CameVec(target.x() - start.x(), 0.0, target.z() - start.z()).horizontal();
        return new CameVec(start.x(), start.y(), start.z()).add(direction.multiply(DASH_LENGTH));
    }

    /** Donde se para cada uno en el barrido doble: uno de cada lado del jugador. */
    public static CameVec flankStart(CameVec target, CameVec axis, CameLord lord) {
        CameVec flat = axis.horizontal();
        double sign = lord == CameLord.HUN ? 1.0 : -1.0;
        return target.add(flat.multiply(SWEEP_STAND_OFF * sign));
    }

    /** Un paso al lado del jugador, del lado en que viene el señor (para un golpe cuerpo a cuerpo). */
    public static CameVec besides(CameVec lord, CameVec target, double standOff) {
        CameVec away = new CameVec(lord.x() - target.x(), 0.0, lord.z() - target.z());
        CameVec direction = away.length() < 1.0e-6 ? new CameVec(0.0, 0.0, 1.0) : away.horizontal();
        return new CameVec(target.x(), target.y(), target.z()).add(direction.multiply(standOff));
    }
}
