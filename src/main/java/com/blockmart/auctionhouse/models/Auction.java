package com.blockmart.auctionhouse.models;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Auction {
    private int id;
    private UUID sellerUUID;
    private String sellerName;
    private ItemStack itemStack;
    private double startPrice;
    private double currentBid;
    private UUID highestBidderUUID;
    private String highestBidderName;
    private long endTime;
    private AuctionStatus status;

    public Auction(int id, UUID sellerUUID, String sellerName, ItemStack itemStack, double startPrice, double currentBid, UUID highestBidderUUID, String highestBidderName, long endTime, AuctionStatus status) {
        this.id = id;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.itemStack = itemStack;
        this.startPrice = startPrice;
        this.currentBid = currentBid;
        this.highestBidderUUID = highestBidderUUID;
        this.highestBidderName = highestBidderName;
        this.endTime = endTime;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public UUID getSellerUUID() {
        return sellerUUID;
    }

    public String getSellerName() {
        return sellerName;
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

    public UUID getHighestBidderUUID() {
        return highestBidderUUID;
    }

    public String getHighestBidderName() {
        return highestBidderName;
    }

    public long getEndTime() {
        return endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setCurrentBid(double currentBid) {
        this.currentBid = currentBid;
    }

    public void setHighestBidderUUID(UUID highestBidderUUID) {
        this.highestBidderUUID = highestBidderUUID;
    }

    public void setHighestBidderName(String highestBidderName) {
        this.highestBidderName = highestBidderName;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }
}