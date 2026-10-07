package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DonPollosModuleWiringTest {

    @Test
    void moduleIdIsStable() {
        assertEquals("don-pollos", new DonPollosModule(null).getId());
        assertEquals("don-pollos", DonPollosModule.MODULE_ID);
    }

    @Test
    void enablingWithoutAPluginIsANoOp() {
        DonPollosModule module = new DonPollosModule(null);
        module.onEnable();
        module.onDisable();
    }
}
