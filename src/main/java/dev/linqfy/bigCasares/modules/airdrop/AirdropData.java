package dev.linqfy.bigCasares.modules.airdrop;

import java.util.Objects;

public record AirdropData(
        AirdropPosition position,
        AirdropType type,
        AirdropPhase phase
) {
    public AirdropData {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(phase, "phase");
    }

    public boolean isActive() {
        return phase != AirdropPhase.CLAIMED;
    }

    public AirdropData withPhase(AirdropPhase phase) {
        return new AirdropData(position, type, phase);
    }

    public AirdropData withPositionAndPhase(AirdropPosition position, AirdropPhase phase) {
        return new AirdropData(position, type, phase);
    }
}
