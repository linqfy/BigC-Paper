package dev.linqfy.bigCasares.modules.finance;

public enum AccountKind {
    WALLET, POOL, FEES, EXTERNAL, ISSUANCE;

    public boolean control() {
        return this == EXTERNAL || this == ISSUANCE;
    }
}
