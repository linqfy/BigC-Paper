package dev.linqfy.bigCasares.modules.auctionhouse;

/** Loads and saves the auction house state. */
public interface AuctionStore<I> {

    AuctionSnapshot<I> load();

    void save(AuctionSnapshot<I> snapshot);

    /** Persist a new listing before the seller's item is removed. Fail if it was not saved. */
    default void saveListing(AuctionSnapshot<I> snapshot) {
        save(snapshot);
    }
}
