package com.blockmart.auctionhouse.models;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionItem {

    private int id;
    private String sellerName;
    private UUID sellerUuid;
    private ItemStack itemStack;
    private double startingPrice;
    private double currentBid;
    private UUID highestBidderUuid;
    private String highestBidderName;
    private long startTime;
    private long endTime;
    private AuctionStatus status;

    public AuctionItem(int id, String sellerName, UUID sellerUuid, ItemStack itemStack, double startingPrice, double currentBid, UUID highestBidderUuid, String highestBidderName, long startTime, long endTime, AuctionStatus status) {
        this.id = id;
        this.sellerName = sellerName;
        this.sellerUuid = sellerUuid;
        this.itemStack = itemStack;
        this.startingPrice = startingPrice;
        this.currentBid = currentBid;
        this.highestBidderUuid = highestBidderUuid;
        this.highestBidderName = highestBidderName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public String getSellerName() {
        return sellerName;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public double getStartingPrice() {
        return startingPrice;
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

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public enum AuctionStatus {
        LISTED,
        SOLD,
        EXPIRED,
        CANCELLED
    }
}