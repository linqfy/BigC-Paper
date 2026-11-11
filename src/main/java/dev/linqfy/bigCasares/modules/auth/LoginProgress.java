package dev.linqfy.bigCasares.modules.auth;

import java.util.EnumSet;
import java.util.Set;

/** Tracks which methods a player proved in the current login and whether that is enough. */
public final class LoginProgress {

    private final boolean requireTwoFactors;
    private final EnumSet<AuthMethod> satisfied = EnumSet.noneOf(AuthMethod.class);

    public LoginProgress(boolean requireTwoFactors) {
        this.requireTwoFactors = requireTwoFactors;
    }

    /** Records a proven method and returns whether the login is now complete. */
    public boolean satisfy(AuthMethod method) {
        satisfied.add(method);
        return complete();
    }

    public boolean complete() {
        if (satisfied.stream().anyMatch(AuthMethod::automatic)) {
            return true;
        }
        return requireTwoFactors ? satisfied.size() >= 2 : !satisfied.isEmpty();
    }

    public boolean satisfied(AuthMethod method) {
        return satisfied.contains(method);
    }

    public Set<AuthMethod> satisfied() {
        return Set.copyOf(satisfied);
    }
}
