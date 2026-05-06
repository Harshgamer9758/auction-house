package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.models.Auction;
import com.blockmart.auctionhouse.models.AuctionStatus;
import com.blockmart.auctionhouse.models.Escrow;
import com.blockmart.auctionhouse.utils.NBTUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AuctionManager {

    private final AuctionHousePlugin plugin;
    private final DatabaseManager databaseManager;
    private final ConcurrentHashMap<Integer, Auction> activeAuctions;

    public AuctionManager(AuctionHousePlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.activeAuctions = new ConcurrentHashMap<>();
        startAuctionCleanupTask();
    }

    public Optional<Auction> getAuctionById(int id) {
        return Optional.ofNullable(activeAuctions.get(id));
    }

    public List<Auction> getActiveAuctions() {
        return new ArrayList<>(activeAuctions.values());
    }

    public void loadAllAuctions() {
        databaseManager.getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, sellerUUID, sellerName, itemStack, startPrice, currentBid, highestBidderUUID, highestBidderName, endTime, status FROM auctions WHERE status = ? OR status = ?")) {
                ps.setString(1, AuctionStatus.ACTIVE.name());
                ps.setString(2, AuctionStatus.ENDING.name());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    try {
                        ItemStack itemStack = NBTUtil.itemStackFromBase64(rs.getString("itemStack"));
                        Auction auction = new Auction(
                                rs.getInt("id"),
                                UUID.fromString(rs.getString("sellerUUID")),
                                rs.getString("sellerName"),
                                itemStack,
                                rs.getDouble("startPrice"),
                                rs.getDouble("currentBid"),
                                rs.getString("highestBidderUUID") != null ? UUID.fromString(rs.getString("highestBidderUUID")) : null,
                                rs.getString("highestBidderName"),
                                rs.getLong("endTime"),
                                AuctionStatus.valueOf(rs.getString("status"))
                        );
                        activeAuctions.put(auction.getId(), auction);
                    } catch (IOException | InvalidConfigurationException e) {
                        plugin.getLogger().warning("Failed to deserialize item for auction ID " + rs.getInt("id") + ": " + e.getMessage());
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load auctions: " + e.getMessage());
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().severe("Error closing connection: " + e.getMessage()); }
            }
        });
    }

    public void createAuction(UUID sellerUUID, String sellerName, ItemStack itemStack, double price, long durationMinutes) {
        long endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes);
        String itemStackBase64 = NBTUtil.itemStackToBase64(itemStack);

        databaseManager.getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO auctions (sellerUUID, sellerName, itemStack, startPrice, currentBid, endTime, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, sellerUUID.toString());
                ps.setString(2, sellerName);
                ps.setString(3, itemStackBase64);
                ps.setDouble(4, price);
                ps.setDouble(5, price);
                ps.setLong(6, endTime);
                ps.setString(7, AuctionStatus.ACTIVE.name());
                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    int auctionId = rs.getInt(1);
                    Auction auction = new Auction(auctionId, sellerUUID, sellerName, itemStack, price, price, null, null, endTime, AuctionStatus.ACTIVE);
                    activeAuctions.put(auctionId, auction);
                    plugin.getLogger().info("Created new auction: " + auctionId);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create auction: " + e.getMessage());
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().severe("Error closing connection: " + e.getMessage()); }
            }
        });
    }

    public void bidOnAuction(Auction auction, UUID bidderUUID, String bidderName, double bidAmount) {
        if (!activeAuctions.containsKey(auction.getId())) {
            // Auction might have just ended or been removed
            return;
        }

        databaseManager.getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE auctions SET currentBid = ?, highestBidderUUID = ?, highestBidderName = ? WHERE id = ?")) {
                ps.setDouble(1, bidAmount);
                ps.setString(2, bidderUUID.toString());
                ps.setString(3, bidderName);
                ps.setInt(4, auction.getId());
                ps.executeUpdate();

                // Update in-memory object
                Auction updatedAuction = activeAuctions.get(auction.getId());
                if (updatedAuction != null) {
                    updatedAuction.setCurrentBid(bidAmount);
                    updatedAuction.setHighestBidderUUID(bidderUUID);
                    updatedAuction.setHighestBidderName(bidderName);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update bid for auction " + auction.getId() + ": " + e.getMessage());
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().severe("Error closing connection: " + e.getMessage()); }
            }
        });
    }

    public void endAuction(Auction auction) {
        databaseManager.getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE auctions SET status = ? WHERE id = ?")) {
                ps.setString(1, AuctionStatus.ENDED.name());
                ps.setInt(2, auction.getId());
                ps.executeUpdate();

                activeAuctions.remove(auction.getId());

                if (auction.getHighestBidderUUID() != null && auction.getCurrentBid() > auction.getStartPrice()) {
                    // Auction sold, transfer item to bidder's escrow and money to seller's escrow
                    addEscrowItem(auction.getHighestBidderUUID(), auction.getItemStack());
                    addEscrowMoney(auction.getSellerUUID(), auction.getCurrentBid());
                    Bukkit.getLogger().info("Auction " + auction.getId() + " ended. Item to bidder, money to seller.");
                    OfflinePlayer bidder = Bukkit.getOfflinePlayer(auction.getHighestBidderUUID());
                    OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUUID());

                    if (bidder.isOnline()) {
                        bidder.getPlayer().sendMessage(plugin.getConfig().getString("messages.auction-won").replace("%item%", auction.getItemStack().getItemMeta().getDisplayName()));
                    }
                    if (seller.isOnline()) {
                        seller.getPlayer().sendMessage(plugin.getConfig().getString("messages.auction-sold").replace("%item%", auction.getItemStack().getItemMeta().getDisplayName()).replace("%amount%", String.valueOf(auction.getCurrentBid())));
                    }
                } else {
                    // No bids or not sold, return item to seller's escrow
                    addEscrowItem(auction.getSellerUUID(), auction.getItemStack());
                    plugin.getLogger().info("Auction " + auction.getId() + " ended. Item returned to seller.");
                    OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUUID());
                    if (seller.isOnline()) {
                        seller.getPlayer().sendMessage(plugin.getConfig().getString("messages.auction-expired").replace("%item%", auction.getItemStack().getItemMeta().getDisplayName()));
                    }
                }
                // Refund previous highest bidder if a new bid overwrites theirs (escrow logic not fully implemented here but part of the design)

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to end auction " + auction.getId() + ": " + e.getMessage());
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().severe("Error closing connection: " + e.getMessage()); }
            }
        });
    }

    public void addEscrowItem(UUID playerUUID, ItemStack item) {
        String itemBase64 = NBTUtil.itemStackToBase64(item);
        databaseManager.getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO escrows (ownerUUID, itemStack, amount) VALUES (?, ?, ?)")) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, itemBase64);
                ps.setDouble(3, 0.0); // Amount is 0 for items
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to add item to escrow for " + playerUUID + ": " + e.getMessage());
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().severe("Error closing connection: " + e.getMessage()); }
            }
        });
    }

    public void addEscrowMoney(UUID playerUUID, double amount) {
        databaseManager.getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO escrows (ownerUUID, itemStack, amount) VALUES (?, ?, ?) ON CONFLICT(ownerUUID) DO UPDATE SET amount = amount + EXCLUDED.amount WHERE ownerUUID = EXCLUDED.ownerUUID")) {
                ps.setString(1, playerUUID.toString());
                ps.setObject(2, null); // itemStack is null for money
                ps.setDouble(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to add money to escrow for " + playerUUID + ": " + e.getMessage());
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().severe("Error closing connection: " + e.getMessage()); }
            }
        });
    }

    public List<Escrow> getPlayerEscrow(UUID playerUUID) {
        List<Escrow> escrows = new ArrayList<>();
        databaseManager.getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, ownerUUID, itemStack, amount FROM escrows WHERE ownerUUID = ?")) {
                ps.setString(1, playerUUID.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    ItemStack item = null;
                    String itemBase64 = rs.getString("itemStack");
                    if (itemBase64 != null) {
                        item = NBTUtil.itemStackFromBase64(itemBase64);
                    }
                    escrows.add(new Escrow(
                            rs.getInt("id"),
                            UUID.fromString(rs.getString("ownerUUID")),
                            item,
                            rs.getDouble("amount")
                    ));
                }
            } catch (SQLException | IOException | InvalidConfigurationException e) {
                plugin.getLogger().severe("Failed to retrieve escrow for " + playerUUID + ": " + e.getMessage());
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().severe("Error closing connection: " + e.getMessage()); }
            }
        });
        return escrows;
    }

    public void claimFromEscrow(int escrowId) {
        databaseManager.getConnectionAsync().thenAccept(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM escrows WHERE id = ?")) {
                ps.setInt(1, escrowId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to claim from escrow ID " + escrowId + ": " + e.getMessage());
            } finally {
                try { conn.close(); } catch (SQLException e) { plugin.getLogger().severe("Error closing connection: " + e.getMessage()); }
            }
        });
    }


    private void startAuctionCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                List<Auction> endedAuctions = activeAuctions.values().stream()
                        .filter(auction -> auction.getEndTime() <= currentTime && auction.getStatus() == AuctionStatus.ACTIVE)
                        .collect(Collectors.toList());

                if (!endedAuctions.isEmpty()) {
                    plugin.getLogger().info("Processing " + endedAuctions.size() + " ended auctions.");
                }

                for (Auction auction : endedAuctions) {
                    auction.setStatus(AuctionStatus.ENDING); // Mark as ending to prevent double processing
                    endAuction(auction);
                }

                // Clean up any auctions that might still be marked as ENDING but are already processed.
                // This part might need more robust handling if `endAuction` can fail repeatedly.
                activeAuctions.entrySet().removeIf(entry -> entry.getValue().getStatus() == AuctionStatus.ENDED);
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 5, 20L * 60); // Every minute
    }
}
