package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.models.Auction;
import com.blockmart.auctionhouse.models.EscrowType;
import com.blockmart.auctionhouse.utils.NBTUtils;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AuctionManager {

    private final AuctionHousePlugin plugin;
    private final DatabaseManager databaseManager;
    private final EscrowManager escrowManager;

    public AuctionManager(AuctionHousePlugin plugin, DatabaseManager databaseManager, EscrowManager escrowManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.escrowManager = escrowManager;
        startAuctionCleanupTask();
    }

    public CompletableFuture<Void> createAuction(UUID sellerUuid, String sellerName, ItemStack item, double startPrice, long durationMinutes) {
        long endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes);
        String itemNbt = NBTUtils.itemStackToBase64(item);

        return escrowManager.depositItem(sellerUuid, item, true).thenCompose(success -> {
            if (!success) {
                return CompletableFuture.completedFuture(null);
            }
            String sql = "INSERT INTO auctions (seller_uuid, seller_name, item_nbt, start_price, current_bid, end_time, creation_time) VALUES (?, ?, ?, ?, ?, ?, ?)";
            return databaseManager.executeUpdate(sql, sellerUuid.toString(), sellerName, itemNbt, startPrice, startPrice, endTime, System.currentTimeMillis());
        });
    }

    public CompletableFuture<List<Auction>> getActiveAuctions() {
        return CompletableFuture.supplyAsync(() -> {
            List<Auction> auctions = new ArrayList<>();
            String sql = "SELECT * FROM auctions WHERE active = TRUE AND end_time > ? ORDER BY creation_time DESC";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    auctions.add(createAuctionFromResultSet(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting active auctions: " + e.getMessage());
            }
            return auctions;
        }, Bukkit.getScheduler().getAsyncScheduler());
    }

    public CompletableFuture<Optional<Auction>> getAuctionById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM auctions WHERE id = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return Optional.of(createAuctionFromResultSet(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting auction by ID: " + e.getMessage());
            }
            return Optional.empty();
        }, Bukkit.getScheduler().getAsyncScheduler());
    }

    public CompletableFuture<Boolean> placeBid(int auctionId, UUID bidderUuid, String bidderName, double bidAmount) {
        return getAuctionById(auctionId).thenCompose(optionalAuction -> {
            if (optionalAuction.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            Auction auction = optionalAuction.get();

            if (auction.getSellerUuid().equals(bidderUuid)) {
                return CompletableFuture.completedFuture(false); // Cannot bid on own auction
            }

            if (bidAmount <= auction.getCurrentBid()) {
                return CompletableFuture.completedFuture(false); // Bid too low
            }

            return escrowManager.withdrawBalance(bidderUuid, bidAmount, true).thenCompose(success -> {
                if (!success) {
                    return CompletableFuture.completedFuture(false); // Not enough money
                }
                // Refund previous highest bidder if any
                if (auction.getHighestBidderUuid() != null && auction.getCurrentBid() > auction.getStartPrice()) {
                     escrowManager.depositBalance(auction.getHighestBidderUuid(), auction.getCurrentBid(), false);
                }

                String sql = "UPDATE auctions SET current_bid = ?, highest_bidder_uuid = ?, highest_bidder_name = ? WHERE id = ? AND current_bid < ?";
                return databaseManager.executeUpdate(sql, bidAmount, bidderUuid.toString(), bidderName, auctionId, bidAmount)
                        .thenApply(v -> true);
            });
        });
    }

    public CompletableFuture<Void> finishAuction(Auction auction) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE auctions SET active = FALSE WHERE id = ?";
            databaseManager.executeUpdate(sql, auction.getId());

            if (auction.getHighestBidderUuid() != null && auction.getCurrentBid() > auction.getStartPrice()) {
                // Auction sold: Transfer item to winner, money to seller
                escrowManager.withdrawItem(auction.getSellerUuid(), auction.getItem(), true)
                        .thenAccept(itemSuccess -> {
                            if (itemSuccess) {
                                // Item transferred from escrow, now deposit for winner
                                escrowManager.depositItem(auction.getHighestBidderUuid(), auction.getItem(), false);
                                // Deposit money for seller
                                escrowManager.depositBalance(auction.getSellerUuid(), auction.getCurrentBid(), false);
                                plugin.getLogger().info("Auction " + auction.getId() + ": Sold to " + auction.getHighestBidderName() + " for " + auction.getCurrentBid());
                            } else {
                                plugin.getLogger().warning("Auction " + auction.getId() + ": Failed to withdraw item from seller's escrow.");
                                // Item stuck in escrow, refund bidder
                                escrowManager.depositBalance(auction.getHighestBidderUuid(), auction.getCurrentBid(), false);
                            }
                        });
            } else {
                // Auction expired unsold: Release item back to seller
                escrowManager.withdrawItem(auction.getSellerUuid(), auction.getItem(), true)
                        .thenAccept(itemSuccess -> {
                            if (itemSuccess) {
                                plugin.getLogger().info("Auction " + auction.getId() + ": Expired unsold. Item returned to seller.");
                            } else {
                                plugin.getLogger().warning("Auction " + auction.getId() + ": Expired unsold, but failed to withdraw item from seller's escrow.");
                            }
                        });
            }
        }, Bukkit.getScheduler().getAsyncScheduler());
    }

    private Auction createAuctionFromResultSet(ResultSet rs) throws SQLException {
        return new Auction(
                rs.getInt("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                NBTUtils.base64ToItemStack(rs.getString("item_nbt")),
                rs.getDouble("start_price"),
                rs.getDouble("current_bid"),
                rs.getString("highest_bidder_uuid") != null ? UUID.fromString(rs.getString("highest_bidder_uuid")) : null,
                rs.getString("highest_bidder_name"),
                rs.getLong("end_time"),
                rs.getBoolean("active"),
                rs.getLong("creation_time")
        );
    }

    private void startAuctionCleanupTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            plugin.getLogger().fine("Running async auction cleanup task.");
            getActiveAuctions().thenAccept(auctions -> {
                long currentTime = System.currentTimeMillis();
                for (Auction auction : auctions) {
                    if (auction.getEndTime() <= currentTime && auction.isActive()) {
                        finishAuction(auction);
                    }
                }
            });
        }, 20L * 60, 20L * 60); // Run every minute
    }
}