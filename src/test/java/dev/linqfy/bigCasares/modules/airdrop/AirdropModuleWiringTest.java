package dev.linqfy.bigCasares.modules.airdrop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirdropModuleWiringTest {

    @Test
    void moduleIdIsStableKebabCase() {
        AirdropModule module = new AirdropModule(null);
        assertEquals("airdrop-system", module.getId());
    }
}
