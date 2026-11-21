package dev.linqfy.bigCasares.modules.legendary;

/** Landing is a single transition; repeated grounded ticks cannot postpone cleanup. */
final class LegendaryFlightWindow {
    private boolean airborne;
    private boolean landed;

    boolean update(int age, boolean onGround, boolean actionDone) {
        if (!onGround) airborne = true;
        if (!landed && age > 2 && onGround && (airborne || actionDone)) {
            landed = true;
            return true;
        }
        return false;
    }

    void land() {
        landed = true;
    }

    boolean protectsFall() {
        return !landed;
    }
}
