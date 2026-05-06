package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.models.AuctionItem;
import com.blockmart.auctionhouse.utils.NBTUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64converted.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {

    private final AuctionHousePlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(AuctionHousePlugin plugin) {
        this.plugin = plugin;
        setupDatabase();
    }

    private void setupDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/auction_house.db");
        config.setPoolName("AuctionHouse-Pool");
        config.setMaxLifetime(60000);
        config.setIdleTimeout(30000);
        config.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                String createAuctionsTable = "CREATE TABLE IF NOT EXISTS auctions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "seller TEXT NOT NULL," +
                        "seller_uuid TEXT NOT NULL," +
                        "item TEXT NOT NULL," +
                        "price REAL NOT NULL," +
                        "current_bid REAL DEFAULT 0.0,"
                        "highest_bidder_uuid TEXT,"
                        "highest_bidder_name TEXT,"
                        "start_time INTEGER NOT NULL," +
                        "end_time INTEGER NOT NULL," +
                        "status TEXT NOT NULL DEFAULT 'LISTED'" + // LISTED, SOLD, EXPIRED, CANCELLED
                        ");";
                stmt.execute(createAuctionsTable);

                String createEscrowTable = "CREATE TABLE IF NOT EXISTS escrow (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "player_uuid TEXT NOT NULL," +
                        "item TEXT," +
                        "money REAL DEFAULT 0.0" +
                        ");";
                stmt.execute(createEscrowTable);

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create database tables: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> saveAuctionItem(AuctionItem auctionItem) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO auctions (seller, seller_uuid, item, price, current_bid, highest_bidder_uuid, highest_bidder_name, start_time, end_time, status) VALUES (?,?,?,?,?,?,?,?,?,?);";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, auctionItem.getSellerName());
                ps.setString(2, auctionItem.getSellerUuid().toString());
                ps.setString(3, itemStackToBase64(auctionItem.getItemStack()));
                ps.setDouble(4, auctionItem.getStartingPrice());
                ps.setDouble(5, auctionItem.getCurrentBid());
                ps.setString(6, auctionItem.getHighestBidderUuid() != null ? auctionItem.getHighestBidderUuid().toString() : null);
                ps.setString(7, auctionItem.getHighestBidderName());
                ps.setLong(8, auctionItem.getStartTime());
                ps.setLong(9, auctionItem.getEndTime());
                ps.setString(10, auctionItem.getStatus().name());
                ps.executeUpdate();
            } catch (SQLException | IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save auction item: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<AuctionItem>> getListedAuctions() {
        return CompletableFuture.supplyAsync(() -> {
            List<AuctionItem> auctions = new ArrayList<>();
            String sql = "SELECT * FROM auctions WHERE status = 'LISTED' AND end_time > ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    auctions.add(createAuctionItemFromResultSet(rs));
                }
            } catch (SQLException | IOException | ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load listed auctions: " + e.getMessage());
            }
            return auctions;
        });
    }

    public CompletableFuture<List<AuctionItem>> getExpiredAuctions() {
        return CompletableFuture.supplyAsync(() -> {
            List<AuctionItem> auctions = new ArrayList<>();
            String sql = "SELECT * FROM auctions WHERE status = 'LISTED' AND end_time <= ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, System.currentTimeMillis());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    auctions.add(createAuctionItemFromResultSet(rs));
                }
            } catch (SQLException | IOException | ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load expired auctions: " + e.getMessage());
            }
            return auctions;
        });
    }

    public CompletableFuture<Void> updateAuctionItem(AuctionItem auctionItem) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE auctions SET current_bid = ?, highest_bidder_uuid = ?, highest_bidder_name = ?, status = ? WHERE id = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, auctionItem.getCurrentBid());
                ps.setString(2, auctionItem.getHighestBidderUuid() != null ? auctionItem.getHighestBidderUuid().toString() : null);
                ps.setString(3, auctionItem.getHighestBidderName());
                ps.setString(4, auctionItem.getStatus().name());
                ps.setInt(5, auctionItem.getId());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update auction item: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> updateAuctionStatus(int auctionId, AuctionItem.AuctionStatus status) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE auctions SET status = ? WHERE id = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status.name());
                ps.setInt(2, auctionId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update auction status: " + e.getMessage());
            }
        });
    }

    // Escrow methods

    public CompletableFuture<Void> depositEscrowItem(String playerUuid, ItemStack itemStack) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO escrow (player_uuid, item, money) VALUES (?,?,0.0);";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid);
                ps.setString(2, itemStackToBase64(itemStack));
                ps.executeUpdate();
            } catch (SQLException | IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to deposit item to escrow: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> depositEscrowMoney(String playerUuid, double amount) {
        return CompletableFuture.runAsync(() -> {
            String selectSql = "SELECT money FROM escrow WHERE player_uuid = ?;";
            String updateSql = "UPDATE escrow SET money = money + ? WHERE player_uuid = ?;";
            String insertSql = "INSERT INTO escrow (player_uuid, money) VALUES (?,?);";

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                    selectPs.setString(1, playerUuid);
                    ResultSet rs = selectPs.executeQuery();

                    if (rs.next()) {
                        try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                            updatePs.setDouble(1, amount);
                            updatePs.setString(2, playerUuid);
                            updatePs.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                            insertPs.setString(1, playerUuid);
                            insertPs.setDouble(2, amount);
                            insertPs.executeUpdate();
                        }
                    }
                    conn.commit();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to deposit money to escrow: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<ItemStack>> withdrawEscrowItems(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<ItemStack> items = new ArrayList<>();
            String selectSql = "SELECT item FROM escrow WHERE player_uuid = ? AND item IS NOT NULL;";
            String deleteSql = "DELETE FROM escrow WHERE player_uuid = ? AND item IS NOT NULL;";

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                    selectPs.setString(1, playerUuid);
                    ResultSet rs = selectPs.executeQuery();
                    while (rs.next()) {
                        String itemBase64 = rs.getString("item");
                        if (itemBase64 != null) {
                            items.add(itemStackFromBase64(itemBase64));
                        }
                    }
                }

                if (!items.isEmpty()) {
                    try (PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {
                        deletePs.setString(1, playerUuid);
                        deletePs.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException | IOException | ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to withdraw items from escrow: " + e.getMessage());
            }
            return items;
        });
    }

    public CompletableFuture<Double> withdrawEscrowMoney(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            double amount = 0.0;
            String selectSql = "SELECT money FROM escrow WHERE player_uuid = ?;";
            String updateSql = "UPDATE escrow SET money = 0.0 WHERE player_uuid = ?;";

            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
                    selectPs.setString(1, playerUuid);
                    ResultSet rs = selectPs.executeQuery();
                    if (rs.next()) {
                        amount = rs.getDouble("money");
                    }
                }

                if (amount > 0) {
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        updatePs.setString(1, playerUuid);
                        updatePs.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to withdraw money from escrow: " + e.getMessage());
            }
            return amount;
        });
    }

     public CompletableFuture<Double> getEscrowBalance(String playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            double balance = 0.0;
            String sql = "SELECT money FROM escrow WHERE player_uuid = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    balance = rs.getDouble("money");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get escrow balance for " + playerUuid + ": " + e.getMessage());
            }
            return balance;
        });
    }

    private AuctionItem createAuctionItemFromResultSet(ResultSet rs) throws SQLException, IOException, ClassNotFoundException {
        return new AuctionItem(
                rs.getInt("id"),
                rs.getString("seller"),
                java.util.UUID.fromString(rs.getString("seller_uuid")),
                itemStackFromBase64(rs.getString("item")),
                rs.getDouble("price"),
                rs.getDouble("current_bid"),
                rs.getString("highest_bidder_uuid") != null ? java.util.UUID.fromString(rs.getString("highest_bidder_uuid")) : null,
                rs.getString("highest_bidder_name"),
                rs.getLong("start_time"),
                rs.getLong("end_time"),
                AuctionItem.AuctionStatus.valueOf(rs.getString("status"))
        );
    }

    private String itemStackToBase64(ItemStack item) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(NBTUtil.removeNBT(item)); // Ensure no NBT from other plugins interferes
            return Base64.encodeBytes(outputStream.toByteArray());
        }
    }

    private ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        }
    }

    public void closeConnection() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}