package dev.linqfy.bigCasares.modules.donpollos;

public interface DonPollosInventoryGateway {

    int count(String material);

    boolean remove(String material, int amount);

    void give(String material, int amount);

    void giveProduct(DonPollosProduct product, int amount);
}
