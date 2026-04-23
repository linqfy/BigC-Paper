package dev.linqfy.bigCasares.modules.airdrop;

import java.util.Optional;

public interface AirdropStorage {
    void save(AirdropData data);
    Optional<AirdropData> load();
    void clear();
}
