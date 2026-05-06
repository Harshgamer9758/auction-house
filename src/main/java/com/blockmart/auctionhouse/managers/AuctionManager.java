package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHouse;
import com.blockmart.auctionhouse.models.AuctionItem;
import com.blockmart.auctionhouse.utils.ItemSerializer;
import com.blockmart.auctionhouse.utils.NBTUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AuctionManager {

    private final AuctionHouse plugin;
    private final DatabaseManager databaseManager;
    private final Economy economy;
    private final Map<UUID, AuctionItem> activeAuctions;
    private final long AUCTION_DURATION_SECONDS = 24 * 60 * 60; // 24 hours

    public AuctionManager(AuctionHouse plugin, DatabaseManager databaseManager, Economy economy) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.economy = economy;
        this.activeAuctions = new ConcurrentHashMap<>();
    }

    public CompletableFuture<Void> loadActiveAuctions() {
        return CompletableFuture.runAsync(() -> {
            long currentTime = Instant.now().getEpochSecond();
            String query = "SELECT id, seller_uuid, item_nbt, start_price, current_bid, current_bidder_uuid, end_time, status FROM auctions WHERE status = 'ACTIVE' OR status = 'PENDING_CLAIM_SELLER_ITEM' OR status = 'PENDING_CLAIM_BIDDER_ITEM';";
            databaseManager.executeQuery(query, rs -> {
                try {
                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString("id"));
                        UUID sellerId = UUID.fromString(rs.getString("seller_uuid"));
                        ItemStack item = plugin.getNbtUtils().deserializeNBTItem(rs.getString("item_nbt"));
                        double startPrice = rs.getDouble("start_price");
                        double currentBid = rs.getDouble("current_bid");
                        UUID currentBidderId = null;
                        if (rs.getString("current_bidder_uuid") != null) {
                            currentBidderId = UUID.fromString(rs.getString("current_bidder_uuid"));
                        }
                        long endTime = rs.getLong("end_time");
                        AuctionItem.AuctionStatus status = AuctionItem.AuctionStatus.valueOf(rs.getString("status"));

                        AuctionItem auctionItem = new AuctionItem(id, sellerId, item, startPrice, currentBid, currentBidderId, endTime, status);
                        activeAuctions.put(id, auctionItem);
                        if (auctionItem.getStatus() == AuctionItem.AuctionStatus.ACTIVE && auctionItem.getEndTime() <= currentTime) {
                            // This auction should have expired async, but handle it if still active
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    expireAuction(auctionItem.getId());
                                }
                            }.runTask(plugin);
                        }
                    }
                    plugin.getLogger().info("Loaded " + activeAuctions.size() + " active auctions.");
                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to load auctions: " + e.getMessage());
                }
            });
        }, plugin.getServer().getScheduler().asyncScheduler());
    }

    public void createAuction(Player player, ItemStack item, double price) {
        UUID auctionId = UUID.randomUUID();
        long endTime = Instant.now().getEpochSecond() + AUCTION_DURATION_SECONDS;
        String itemNBT = plugin.getNbtUtils().serializeNBTItem(item);

        String insertSql = "INSERT INTO auctions (id, seller_uuid, item_nbt, start_price, current_bid, end_time, status) VALUES (?, ?, ?, ?, ?, ?, ?);";
        databaseManager.executeUpdate(insertSql,
                auctionId.toString(), player.getUniqueId().toString(), itemNBT, price, price, endTime, AuctionItem.AuctionStatus.ACTIVE.name()
        ).thenAccept(rows -> {
            if (rows > 0) {
                AuctionItem auctionItem = new AuctionItem(auctionId, player.getUniqueId(), item, price, price, null, endTime, AuctionItem.AuctionStatus.ACTIVE);
                activeAuctions.put(auctionId, auctionItem);
                player.getInventory().removeItem(item);
                player.sendMessage("§aYour item has been listed for auction! ID: §e" + auctionId + "§a. Starting bid: §e" + economy.format(price));
            } else {
                player.sendMessage("§cFailed to list item for auction.");
            }
        }).exceptionally(ex -> {
            player.sendMessage("§cAn error occurred while creating the auction.");
            plugin.getLogger().severe("Error creating auction: " + ex.getMessage());
            return null;
        });
    }

    public void placeBid(Player player, UUID auctionId, double bidAmount) {
        AuctionItem auction = activeAuctions.get(auctionId);

        if (auction == null || auction.getStatus() != AuctionItem.AuctionStatus.ACTIVE) {
            player.sendMessage("§cAuction not found or not active.");
            return;
        }
        if (auction.getSellerId().equals(player.getUniqueId())) {
            player.sendMessage("§cYou cannot bid on your own auction.");
            return;
        }
        if (bidAmount <= auction.getCurrentBid()) {
            player.sendMessage("§cYour bid must be higher than the current bid (which is " + economy.format(auction.getCurrentBid()) + ").");
            return;
        }
        if (!economy.has(player, bidAmount)) {
            player.sendMessage("§cYou do not have enough money to place that bid.");
            return;
        }

        // Asynchronously handle the bidding logic including escrow
        CompletableFuture.runAsync(() -> {
            // First, return previous bidder's money
            if (auction.getCurrentBidderId() != null) {
                OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(auction.getCurrentBidderId());
                economy.depositPlayer(previousBidder, auction.getCurrentBid());
                if (previousBidder.isOnline()) {
                    previousBidder.getPlayer().sendMessage("§aYour previous bid of " + economy.format(auction.getCurrentBid()) + " has been returned.");
                }
            }

            // Deduct new bid from current player and update auction
            economy.withdrawPlayer(player, bidAmount);

            String updateSql = "UPDATE auctions SET current_bid = ?, current_bidder_uuid = ? WHERE id = ? AND current_bid < ?;";
            databaseManager.executeUpdate(updateSql,
                    bidAmount, player.getUniqueId().toString(), auctionId.toString(), bidAmount // Use bidAmount again to ensure no race condition
            ).thenAccept(rows -> {
                if (rows > 0) {
                    auction.setCurrentBid(bidAmount);
                    auction.setCurrentBidderId(player.getUniqueId());
                    player.sendMessage("§aYou have successfully bid " + economy.format(bidAmount) + " on auction ID: §e" + auctionId);
                    Bukkit.broadcastMessage("§e" + player.getName() + " §ahas bid " + economy.format(bidAmount) + " on an item!");
                } else {
                    // This can happen if another player outbid in between the check and update.
                    // Return the money to the player and notify.
                    economy.depositPlayer(player, bidAmount);
                    player.sendMessage("§cYour bid was too low or another player outbid you. Please check the current bid again.");
                }
            }).exceptionally(ex -> {
                player.sendMessage("§cAn error occurred while placing your bid.");
                plugin.getLogger().severe("Error placing bid: " + ex.getMessage());
                economy.depositPlayer(player, bidAmount); // Refund if database error
                return null;
            });
        }, plugin.getServer().getScheduler().asyncScheduler());
    }

    public void claimItems(Player player) {
        long currentTime = Instant.now().getEpochSecond();
        String selectSql = "SELECT id, item_nbt, current_bid, seller_uuid, current_bidder_uuid FROM auctions WHERE (seller_uuid = ? AND (status IN ('PENDING_CLAIM_SELLER_ITEM', 'EXPIRED_NO_BIDS') OR (status = 'ACTIVE' AND end_time <= ?))) OR (current_bidder_uuid = ? AND status = 'PENDING_CLAIM_BIDDER_ITEM');";
        databaseManager.executeQuery(selectSql, rs -> {
            try {
                int claimedCount = 0;
                while (rs.next()) {
                    UUID auctionId = UUID.fromString(rs.getString("id"));
                    UUID sellerId = UUID.fromString(rs.getString("seller_uuid"));
                    UUID bidderId = null;
                    if (rs.getString("current_bidder_uuid") != null) {
                         bidderId = UUID.fromString(rs.getString("current_bidder_uuid"));
                    }
                    ItemStack item = plugin.getNbtUtils().deserializeNBTItem(rs.getString("item_nbt"));
                    double currentBid = rs.getDouble("current_bid");

                    if (player.getUniqueId().equals(sellerId)) {
                        // Seller may claim item or money
                        if (bidderId != null && currentBid > 0) { // Item was sold
                            economy.depositPlayer(player, currentBid);
                            player.sendMessage("§aClaimed §e" + economy.format(currentBid) + " §afrom auction §e" + auctionId + ".");
                            updateAuctionStatus(auctionId, AuctionItem.AuctionStatus.COMPLETED);
                        } else { // Item not sold, claim item back
                            player.getInventory().addItem(item);
                            player.sendMessage("§aClaimed item " + item.getType().name() + " from auction §e" + auctionId + ".");
                            updateAuctionStatus(auctionId, AuctionItem.AuctionStatus.COMPLETED);
                        }
                        claimedCount++;
                    } else if (player.getUniqueId().equals(bidderId)) {
                        // Bidder may claim item
                        player.getInventory().addItem(item);
                        player.sendMessage("§aClaimed item " + item.getType().name() + " from auction §e" + auctionId + ".");
                        updateAuctionStatus(auctionId, AuctionItem.AuctionStatus.COMPLETED);
                        claimedCount++;
                    }
                }
                if (claimedCount == 0) {
                    player.sendMessage("§cYou have no items or money to claim from auctions.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error claiming items for player " + player.getName() + ": " + e.getMessage());
                player.sendMessage("§cAn error occurred while claiming your items.");
            }
        }, player.getUniqueId().toString(), currentTime, player.getUniqueId().toString());
    }

    public void cancelAuction(Player player, UUID auctionId) {
        AuctionItem auction = activeAuctions.get(auctionId);
        if (auction == null) {
            player.sendMessage("§cAuction not found.");
            return;
        }
        if (!auction.getSellerId().equals(player.getUniqueId())) {
            player.sendMessage("§cYou can only cancel your own auctions.");
            return;
        }
        if (auction.getCurrentBidderId() != null) {
            player.sendMessage("§cYou cannot cancel an auction that already has bids.");
            return;
        }

        String updateSql = "UPDATE auctions SET status = 'CANCELLED' WHERE id = ?;";
        databaseManager.executeUpdate(updateSql, auctionId.toString()).thenAccept(rows -> {
            if (rows > 0) {
                activeAuctions.remove(auctionId);
                player.getInventory().addItem(auction.getItem());
                player.sendMessage("§aAuction §e" + auctionId + " §ahas been cancelled and your item returned.");
            } else {
                player.sendMessage("§cFailed to cancel auction.");
            }
        }).exceptionally(ex -> {
            player.sendMessage("§cAn error occurred while cancelling the auction.");
            plugin.getLogger().severe("Error cancelling auction: " + ex.getMessage());
            return null;
        });
    }

    public void sendAuctionList(Player player) {
        if (activeAuctions.isEmpty()) {
            player.sendMessage("§eThere are currently no active auctions.");
            return;
        }

        player.sendMessage("§b--- Active Auctions ---");
        activeAuctions.values().stream()
                .filter(a -> a.getStatus() == AuctionItem.AuctionStatus.ACTIVE)
                .forEach(auction -> {
                    OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());
                    String itemName = ItemSerializer.getItemName(auction.getItem());
                    String bidderInfo = auction.getCurrentBidderId() != null ? " / Bidder: " + Bukkit.getOfflinePlayer(auction.getCurrentBidderId()).getName() : "";
                    player.sendMessage("§fID: §e" + auction.getId().toString().substring(0, 8) + "... §f| Item: §a" + itemName + " §f| Seller: §b" + seller.getName() + " §f| Current Bid: §6" + economy.format(auction.getCurrentBid()) + bidderInfo);
                });
        player.sendMessage("§b-----------------------");
    }

    public void expireAuction(UUID auctionId) {
        AuctionItem auction = activeAuctions.get(auctionId);
        if (auction == null || auction.getStatus() != AuctionItem.AuctionStatus.ACTIVE) return;

        plugin.getLogger().info("Expiring auction: " + auctionId);

        if (auction.getCurrentBidderId() != null) {
            // Auction sold, transfer money to seller, item to buyer
            OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());
            OfflinePlayer bidder = Bukkit.getOfflinePlayer(auction.getCurrentBidderId());

            economy.depositPlayer(seller, auction.getCurrentBid());
            if (seller.isOnline()) {
                seller.getPlayer().sendMessage("§aYour auction §e" + auctionId + " §ahas sold for " + economy.format(auction.getCurrentBid()) + ". Money has been deposited.");
            }
            if (bidder.isOnline()) {
                bidder.getPlayer().sendMessage("§aYou won auction §e" + auctionId + "! Claim your item with /auction claim.");
            }
            updateAuctionStatus(auctionId, AuctionItem.AuctionStatus.PENDING_CLAIM_BIDDER_ITEM);
        } else {
            // No bids, return item to seller
            OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerId());
            if (seller.isOnline()) {
                seller.getPlayer().sendMessage("§cYour auction §e" + auctionId + " §ahad no bids. Claim your item with /auction claim.");
            }
            updateAuctionStatus(auctionId, AuctionItem.AuctionStatus.EXPIRED_NO_BIDS);
        }
        activeAuctions.remove(auctionId);
    }

    public void expireOldAuctionsForPlayer(Player player) {
        long currentTime = Instant.now().getEpochSecond();
        activeAuctions.values().stream()
                .filter(a -> a.getStatus() == AuctionItem.AuctionStatus.ACTIVE && a.getEndTime() <= currentTime)
                .filter(a -> a.getSellerId().equals(player.getUniqueId()) || (a.getCurrentBidderId() != null && a.getCurrentBidderId().equals(player.getUniqueId())))
                .collect(Collectors.toList()) // Collect to avoid ConcurrentModificationException
                .forEach(auction -> expireAuction(auction.getId()));
    }

    public void sendPendingClaimsNotification(Player player) {
        long currentTime = Instant.now().getEpochSecond();
        String checkSql = "SELECT COUNT(id) FROM auctions WHERE (seller_uuid = ? AND status IN ('PENDING_CLAIM_SELLER_ITEM', 'EXPIRED_NO_BIDS')) OR (current_bidder_uuid = ? AND status = 'PENDING_CLAIM_BIDDER_ITEM');";
        databaseManager.executeQuery(checkSql, rs -> {
            try {
                if (rs.next() && rs.getInt(1) > 0) {
                    player.sendMessage("§eYou have items or money to claim from the auction house! Use §a/auction claim§e.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error checking pending claims for " + player.getName() + ": " + e.getMessage());
            }
        }, player.getUniqueId().toString(), player.getUniqueId().toString());
    }

    private void updateAuctionStatus(UUID auctionId, AuctionItem.AuctionStatus newStatus) {
        String updateSql = "UPDATE auctions SET status = ? WHERE id = ?;";
        databaseManager.executeUpdate(updateSql, newStatus.name(), auctionId.toString()).exceptionally(ex -> {
            plugin.getLogger().severe("Error updating auction status for " + auctionId + " to " + newStatus.name() + ": " + ex.getMessage());
            return null;
        });
    }
}
