package dev.linqfy.bigCasares.modules.auctionhouse;

/** Loads and saves the auction house state. */
public interface AuctionStore<I> {

    AuctionSnapshot<I> load();

    void save(AuctionSnapshot<I> snapshot);
}
