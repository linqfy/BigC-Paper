package dev.linqfy.bigCasares.modules.airdrop;

public record AirdropPosition(int x, int y, int z) {

    public boolean matchesBlock(int blockX, int blockY, int blockZ) {
        return x == blockX && y == blockY && z == blockZ;
    }
}
