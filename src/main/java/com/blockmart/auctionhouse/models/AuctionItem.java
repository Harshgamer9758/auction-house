package com.blockmart.auctionhouse.models;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuctionItem {
    private final long id;
    private final UUID sellerUuid;
    private final String itemData; // Base64 encoded ItemStack
    private final double startPrice;
    private double currentBid;
    private UUID highestBidderUuid;
    private final LocalDateTime listTime;
    private final LocalDateTime endTime;
    private String status; // LISTED, ENDED, CANCELLED

    public AuctionItem(long id, UUID sellerUuid, String itemData, double startPrice, double currentBid, UUID highestBidderUuid, LocalDateTime listTime, LocalDateTime endTime, String status) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.itemData = itemData;
        this.startPrice = startPrice;
        this.currentBid = currentBid;
        this.highestBidderUuid = highestBidderUuid;
        this.listTime = listTime;
        this.endTime = endTime;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getItemData() {
        return itemData;
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

    public LocalDateTime getListTime() {
        return listTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}