package dev.linqfy.bigCasares.modules.caichet;

import java.util.List;

/**
 * La tabla de partes de las figuras 3D de los backrooms: de que modelos se arma cada una, donde va el pivote de cada
 * parte y como se mueve. Esta toda aca para corregir pivotes y movimientos sin tocar la logica. Sin Bukkit.
 *
 * <p>Cada parte es un modelo con su pivote en el centro (el punto 8,8,8). {@code x y z} es donde queda ese centro
 * respecto de los pies de la figura, en bloques (x derecha, y arriba, z adelante); {@code scale} es una escala
 * uniforme; {@code yaw} y {@code open} son el giro fijo de la parte (en grados, sobre Y y sobre X).
 */
final class BackroomsRigs {

    /** El modelo mira hacia el lado contrario que el display: se le suma esto al yaw hacia el jugador. */
    static final float FRONT_YAW_OFFSET = 180f;

    /** Que hace cada parte en la animacion. */
    enum Role { BODY, HEAD, ARM_L, ARM_R, LEG_L, LEG_R, LEG_PAIR_A, LEG_PAIR_B }

    /** Como se mueve la figura. */
    enum Mode { IDLE, WALK, GRAB, SPY }

    /** Una parte de la figura. */
    record Part(String model, float x, float y, float z, float scale, Role role, float yaw, float open) {

        Part(String model, float x, float y, float z, float scale, Role role) {
            this(model, x, y, z, scale, role, 0f, 0f);
        }
    }

    /** El giro de una parte, en grados (se aplica Z, despues X, despues Y). */
    record Pose(float x, float y, float z) {
    }

    private static final List<Part> SONRISA = List.of(
        new Part("sonrisa", 0f, 0f, 0f, 2.2f, Role.BODY));

    private static final List<Part> E666 = List.of(
        new Part("e666_cuerpo", 0f, 1.30f, 0f, 1f, Role.BODY),
        new Part("e666_cabeza", 0f, 2.10f, 0f, 1f, Role.HEAD),
        new Part("e666_brazo", -0.36f, 2.00f, 0f, 1f, Role.ARM_L),
        new Part("e666_brazo", 0.36f, 2.00f, 0f, 1f, Role.ARM_R),
        new Part("e666_pierna", -0.14f, 1.30f, 0f, 1f, Role.LEG_L),
        new Part("e666_pierna", 0.14f, 1.30f, 0f, 1f, Role.LEG_R));

    private static final List<Part> JUANCARLOS = List.of(
        new Part("jc_tunica", 0f, 1.40f, 0f, 1f, Role.BODY),
        new Part("jc_cabeza", 0f, 2.60f, 0f, 1f, Role.HEAD),
        new Part("jc_brazo", -0.42f, 2.45f, 0f, 1f, Role.ARM_L),
        new Part("jc_brazo", 0.42f, 2.45f, 0f, 1f, Role.ARM_R));

    /** Las patas de la araña: cada una girada hacia su diagonal y abierta 20 grados hacia afuera. */
    private static final List<Part> ARANA = List.of(
        new Part("arana_cuerpo", 0f, 3.20f, 0f, 1f, Role.BODY),
        new Part("arana_pata", 0.30f, 3.10f, 0.30f, 2.2f, Role.LEG_PAIR_A, 45f, 20f),
        new Part("arana_pata", 0.30f, 3.10f, -0.30f, 2.2f, Role.LEG_PAIR_B, 135f, 20f),
        new Part("arana_pata", -0.30f, 3.10f, -0.30f, 2.2f, Role.LEG_PAIR_A, 225f, 20f),
        new Part("arana_pata", -0.30f, 3.10f, 0.30f, 2.2f, Role.LEG_PAIR_B, 315f, 20f));

    private static final List<Part> PAYASO = List.of(
        new Part("payaso_cuerpo", 0f, 1.00f, 0f, 1f, Role.BODY),
        new Part("payaso_cabeza", 0f, 1.70f, 0f, 1f, Role.HEAD));

    private BackroomsRigs() {
    }

    static List<Part> parts(BackroomsKind kind) {
        return switch (kind) {
            case SONRISA -> SONRISA;
            case E666 -> E666;
            case JUANCARLOS -> JUANCARLOS;
            case ARANA -> ARANA;
            case PAYASO -> PAYASO;
        };
    }

    /** Alto de la figura (bloques); la Sonrisa es una imagen que flota. */
    static float height(BackroomsKind kind) {
        return switch (kind) {
            case SONRISA -> 2.2f;
            case E666 -> 2.9f;
            case JUANCARLOS -> 3.35f;
            case ARANA -> 3.4f;
            case PAYASO -> 2.35f;
        };
    }

    /** A que altura de los pies esta la cara (para pegarla a la del jugador en el susto). */
    static float faceHeight(BackroomsKind kind) {
        return switch (kind) {
            case SONRISA -> 0f;
            case E666 -> 2.10f;
            case JUANCARLOS -> 2.60f;
            case ARANA -> 3.20f;
            case PAYASO -> 1.70f;
        };
    }

    /** El alto de la figura de este tipo donde hay libre bloques hasta el techo, y cuanto hay que achicarla. */
    static float fit(BackroomsKind kind, float free) {
        return BackroomsRules.fit(free, height(kind));
    }

    /**
     * El giro de una parte en el tick t. side es de que lado esta la pared (1 a la derecha, -1 a la izquierda; solo
     * lo usa el Payaso).
     */
    static Pose pose(BackroomsKind kind, Part part, Mode mode, long t, int side) {
        float swing = (float) Math.sin(t * 0.3);
        boolean walking = mode == Mode.WALK;
        return switch (kind) {
            case SONRISA -> new Pose(0f, 0f, 0f);
            case E666 -> switch (part.role()) {
                case LEG_L -> new Pose(walking ? 25f * swing : 0f, 0f, 0f);
                case LEG_R -> new Pose(walking ? -25f * swing : 0f, 0f, 0f);
                case ARM_L -> new Pose(walking ? -12f * swing : 0f, 0f, 0f);
                case ARM_R -> new Pose(walking ? 12f * swing : 0f, 0f, 0f);
                case HEAD -> new Pose(0f, 0f, tic(t));
                default -> new Pose(0f, 0f, 0f);
            };
            case JUANCARLOS -> switch (part.role()) {
                case BODY, HEAD -> new Pose(0f, 0f, walking ? 3f * (float) Math.sin(t * 0.15) : 0f);
                case ARM_L, ARM_R -> new Pose(mode == Mode.GRAB ? -80f : 0f, 0f, 0f);
                default -> new Pose(0f, 0f, 0f);
            };
            case ARANA -> switch (part.role()) {
                case LEG_PAIR_A -> new Pose(part.open() + (walking ? 20f * swing : 0f), part.yaw(), 0f);
                case LEG_PAIR_B -> new Pose(part.open() - (walking ? 20f * swing : 0f), part.yaw(), 0f);
                default -> new Pose(0f, 0f, 0f);
            };
            case PAYASO -> {
                boolean spy = mode == Mode.SPY;
                // lado de la pared: la cabeza se inclina hacia ella
                float lean = -side;
                yield switch (part.role()) {
                    case HEAD -> new Pose(0f, 0f, spy ? lean * 25f : 0f);
                    case BODY -> new Pose(0f, 0f, spy ? lean * 8f : 0f);
                    default -> new Pose(0f, 0f, 0f);
                };
            }
        };
    }

    /** El tic nervioso de la cabeza de la 666: de golpe, cada tanto, un giro de 20 grados para un lado u otro. */
    static float tic(long t) {
        long window = t / 6;
        if (t % 6 >= 3 || window % 9 != 0) {
            return 0f;
        }
        return (window / 9) % 2 == 0 ? 20f : -20f;
    }
}
