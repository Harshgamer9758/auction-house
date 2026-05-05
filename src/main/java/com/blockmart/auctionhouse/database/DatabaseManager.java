package com.blockmart.auctionhouse.database;

import com.blockmart.auctionhouse.AuctionHouse;
import com.blockmart.auctionhouse.models.AuctionItem;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final AuctionHouse plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/auctionhouse.db");
        config.setPoolName("AuctionHouseCP");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setMaxLifetime(30000);
        config.setConnectionTimeout(5000);

        dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS auctions (" +
                                 "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                 "seller_uuid VARCHAR(36) NOT NULL," +
                                 "item_base64 TEXT NOT NULL," +
                                 "start_price DOUBLE NOT NULL," +
                                 "current_bid DOUBLE NOT NULL," +
                                 "highest_bidder_uuid VARCHAR(36)," +
                                 "end_time BIGINT NOT NULL," +
                                 "active BOOLEAN NOT NULL"
                                 ");")) {
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create auction table: " + e.getMessage());
            }
        }, Bukkit.getScheduler(). বর্তমান().asyncPool());
    }

    public CompletableFuture<Void> saveAuction(AuctionItem auction) {
        return CompletableFuture.runAsync(() -> {
            String sql;
            if (auction.getId() == 0) {
                sql = "INSERT INTO auctions (seller_uuid, item_base64, start_price, current_bid, highest_bidder_uuid, end_time, active) VALUES (?, ?, ?, ?, ?, ?, ?)";
            } else {
                sql = "UPDATE auctions SET seller_uuid=?, item_base64=?, start_price=?, current_bid=?, highest_bidder_uuid=?, end_time=?, active=? WHERE id=?";
            }

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, auction.getSellerUUID().toString());
                ps.setString(2, itemStackToBase64(auction.getItemStack()));
                ps.setDouble(3, auction.getStartPrice());
                ps.setDouble(4, auction.getCurrentBid());
                ps.setString(5, auction.getHighestBidderUUID() != null ? auction.getHighestBidderUUID().toString() : null);
                ps.setLong(6, auction.getEndTime());
                ps.setBoolean(7, auction.isActive());

                if (auction.getId() != 0) {
                    ps.setInt(8, auction.getId());
                }

                ps.executeUpdate();

                if (auction.getId() == 0) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            auction.setId(rs.getInt(1));
                        }
                    }
                }

            } catch (SQLException | IOException e) {
                plugin.getLogger().severe("Failed to save auction: " + e.getMessage());
            }
        }, Bukkit.getScheduler(). বর্তমান().asyncPool());
    }

    public CompletableFuture<List<AuctionItem>> loadActiveAuctions() {
        return CompletableFuture.supplyAsync(() -> {
            List<AuctionItem> auctions = new ArrayList<>();
            String sql = "SELECT * FROM auctions WHERE active = TRUE AND end_time > ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        auctions.add(mapResultSetToAuctionItem(rs));
                    }
                }
            } catch (SQLException | IOException | ClassNotFoundException e) {
                plugin.getLogger().severe("Failed to load active auctions: " + e.getMessage());
            }
            return auctions;
        }, Bukkit.getScheduler(). वर्तमान().asyncPool());
    }

    public CompletableFuture<List<AuctionItem>> loadPlayerAuctions(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            List<AuctionItem> auctions = new ArrayList<>();
            String sql = "SELECT * FROM auctions WHERE seller_uuid = ? OR highest_bidder_uuid = ? AND active = FALSE";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        auctions.add(mapResultSetToAuctionItem(rs));
                    }
                }
            } catch (SQLException | IOException | ClassNotFoundException e) {
                plugin.getLogger().severe("Failed to load player auctions: " + e.getMessage());
            }
            return auctions;
        }, Bukkit.getScheduler(). वर्तमान().asyncPool());
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            plugins.getLogger().info("Shutting down database connection pool...");
            dataSource.close();
        }
    }

    private AuctionItem mapResultSetToAuctionItem(ResultSet rs) throws SQLException, IOException, ClassNotFoundException {
        int id = rs.getInt("id");
        UUID sellerUUID = UUID.fromString(rs.getString("seller_uuid"));
        ItemStack itemStack = itemStackFromBase64(rs.getString("item_base64"));
        double startPrice = rs.getDouble("start_price");
        double currentBid = rs.getDouble("current_bid");
        String highestBidderUUIDStr = rs.getString("highest_bidder_uuid");
        UUID highestBidderUUID = highestBidderUUIDStr != null ? UUID.fromString(highestBidderUUIDStr) : null;
        long endTime = rs.getLong("end_time");
        boolean active = rs.getBoolean("active");
        return new AuctionItem(id, sellerUUID, itemStack, startPrice, currentBid, highestBidderUUID, endTime, active);
    }

    private String itemStackToBase64(ItemStack item) throws IllegalStateException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        dataOutput.writeObject(item);
        dataOutput.close();
        return Base64Coder.encodeLines(outputStream.toByteArray());
    }

    private ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }
}