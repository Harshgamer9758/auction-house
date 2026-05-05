package com.blockmart.auctionhouse.models;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionItem {

    private int id;
    private UUID sellerUUID;
    private ItemStack itemStack;
    private double startPrice;
    private double currentBid;
    private UUID highestBidderUUID;
    private long endTime;
    private boolean active;

    public AuctionItem(int id, UUID sellerUUID, ItemStack itemStack, double startPrice, double currentBid, UUID highestBidderUUID, long endTime, boolean active) {
        this.id = id;
        this.sellerUUID = sellerUUID;
        this.itemStack = itemStack;
        this.startPrice = startPrice;
        this.currentBid = currentBid;
        this.highestBidderUUID = highestBidderUUID;
        this.endTime = endTime;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getSellerUUID() {
        return sellerUUID;
    }

    public ItemStack getItemStack() {
        return itemStack;
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

    public UUID getHighestBidderUUID() {
        return highestBidderUUID;
    }

    public void setHighestBidderUUID(UUID highestBidderUUID) {
        this.highestBidderUUID = highestBidderUUID;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}