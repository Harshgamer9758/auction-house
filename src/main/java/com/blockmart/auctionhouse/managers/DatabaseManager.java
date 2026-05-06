package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final AuctionHousePlugin plugin;
    private final String jdbcUrl;
    private HikariDataSource dataSource;

    public DatabaseManager(AuctionHousePlugin plugin, String jdbcUrl) {
        this.plugin = plugin;
        this.jdbcUrl = jdbcUrl;
    }

    public void connect() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        plugin.getLogger().info("Database connection pool initialized.");
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed.");
        }
    }

    public CompletableFuture<Void> createTables() {
        return CompletableFuture.runAsync(() -> {
            String createAuctionsTable = "CREATE TABLE IF NOT EXISTS auctions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "seller_uuid VARCHAR(36) NOT NULL," +
                    "seller_name VARCHAR(16) NOT NULL," +
                    "item_nbt TEXT NOT NULL," +
                    "start_price DOUBLE NOT NULL," +
                    "current_bid DOUBLE NOT NULL," +
                    "highest_bidder_uuid VARCHAR(36)," +
                    "highest_bidder_name VARCHAR(16)," +
                    "end_time BIGINT NOT NULL," +
                    "active BOOLEAN NOT NULL DEFAULT TRUE," +
                    "creation_time BIGINT NOT NULL" +
                    ");";

            String createEscrowTable = "CREATE TABLE IF NOT EXISTS escrow (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "item_nbt TEXT," +
                    "amount DOUBLE," +
                    "type VARCHAR(10) NOT NULL" +
                    ");";
            try (Connection conn = getConnection();
                 PreparedStatement ps1 = conn.prepareStatement(createAuctionsTable);
                 PreparedStatement ps2 = conn.prepareStatement(createEscrowTable)) {
                ps1.executeUpdate();
                ps2.executeUpdate();
                plugin.getLogger().info("Database tables checked/created.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
            }
        }, Bukkit.getScheduler().getAsyncScheduler());
    }

    public CompletableFuture<Void> executeUpdate(String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to execute update: " + sql + " - " + e.getMessage());
            }
        }, Bukkit.getScheduler().getAsyncScheduler());
    }

    public CompletableFuture<Connection> getConnectionAsync() {
        return CompletableFuture.supplyAsync(this::getConnection, Bukkit.getScheduler().getAsyncScheduler());
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}