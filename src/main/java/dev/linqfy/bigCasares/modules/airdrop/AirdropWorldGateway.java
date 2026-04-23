package dev.linqfy.bigCasares.modules.airdrop;

public interface AirdropWorldGateway {
    int getHighestBlockY(int x, int z);

    boolean isSafeLandingBlock(int x, int y, int z);

    boolean isSafeOpenSpace(int x, int y, int z);
}
