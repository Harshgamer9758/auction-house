package com.blockmart.auctionhouse.models;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionItem {
    private final UUID id;
    private final UUID sellerId;
    private final ItemStack item;
    private final double startPrice;
    private double currentBid;
    private UUID currentBidderId;
    private final long endTime;
    private AuctionStatus status;

    public AuctionItem(UUID id, UUID sellerId, ItemStack item, double startPrice, double currentBid, UUID currentBidderId, long endTime, AuctionStatus status) {
        this.id = id;
        this.sellerId = sellerId;
        this.item = item;
        this.startPrice = startPrice;
        this.currentBid = currentBid;
        this.currentBidderId = currentBidderId;
        this.endTime = endTime;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getStartPrice() {
        return startPrice;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public void setCurrentBid(double currentBid) {
        this.currentBid = currentBid;
    }

    public UUID getCurrentBidderId() {
        return currentBidderId;
    }

    public void setCurrentBidderId(UUID currentBidderId) {
        this.currentBidderId = currentBidderId;
    }

    public long getEndTime() {
        return endTime;
    }

    public AuctionStatus getStatus()
    {
        return status;
    }

    public void setStatus(AuctionStatus status)
    {
        this.status = status;
    }

    public enum AuctionStatus {
        ACTIVE,
        EXPIRED_NO_BIDS, // Item needs to be returned to seller
        PENDING_CLAIM_BIDDER_ITEM, // Item won by bidder, needs to be claimed
        PENDING_CLAIM_SELLER_ITEM, // Seller has money to claim (or item back if no bid?)
        CANCELLED, // Auction cancelled by seller, item returned
        COMPLETED // Item or money fully claimed, auction record can be archived/deleted
    }
}
