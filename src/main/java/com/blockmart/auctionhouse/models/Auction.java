package com.blockmart.auctionhouse.models;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Auction {
    private final int id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final double startPrice;
    private double currentBid;
    private UUID highestBidderUuid;
    private String highestBidderName;
    private final long endTime;
    private boolean active;
    private final long creationTime;

    public Auction(int id, UUID sellerUuid, String sellerName, ItemStack item, double startPrice, double currentBid, UUID highestBidderUuid, String highestBidderName, long endTime, boolean active, long creationTime) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item;
        this.startPrice = startPrice;
        this.currentBid = currentBid;
        this.highestBidderUuid = highestBidderUuid;
        this.highestBidderName = highestBidderName;
        this.endTime = endTime;
        this.active = active;
        this.creationTime = creationTime;
    }

    public int getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
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

    public UUID getHighestBidderUuid() {
        return highestBidderUuid;
    }

    public void setHighestBidderUuid(UUID highestBidderUuid) {
        this.highestBidderUuid = highestBidderUuid;
    }

    public String getHighestBidderName() {
        return highestBidderName;
    }

    public void setHighestBidderName(String highestBidderName) {
        this.highestBidderName = highestBidderName;
    }

    public long getEndTime() {
        return endTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getCreationTime() {
        return creationTime;
    }
}