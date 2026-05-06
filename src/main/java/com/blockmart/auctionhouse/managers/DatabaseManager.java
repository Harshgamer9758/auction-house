package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHouse;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DatabaseManager {

    private final AuctionHouse plugin;
    private final String databasePath;
    private HikariDataSource dataSource;

    public DatabaseManager(AuctionHouse plugin, String databasePath) {
        this.plugin = plugin;
        this.databasePath = databasePath;
    }

    public void loadDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databasePath);
        config.setPoolName("AuctionHouseHikari");
        config.setMaxLifetime(60000);
        config.setIdleTimeout(30000);
        config.setMaximumPoolSize(10);

        File dbFile = new File(databasePath);
        if (!dbFile.exists()) {
            dbFile.getParentFile().mkdirs();
        }

        try {
            dataSource = new HikariDataSource(config);
            createTables();
            plugin.getLogger().info("SQLite database connected and tables checked/created.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to SQLite database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS auctions (" +
                     "id VARCHAR(36) PRIMARY KEY," +
                     "seller_uuid VARCHAR(36) NOT NULL," +
                     "item_nbt TEXT NOT NULL," +
                     "start_price DOUBLE NOT NULL," +
                     "current_bid DOUBLE NOT NULL," +
                     "current_bidder_uuid VARCHAR(36)," +
                     "end_time BIGINT NOT NULL," +
                     "status VARCHAR(50) NOT NULL" +
                     ");";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public CompletableFuture<Integer> executeUpdate(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                return stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Database update error for query: " + sql + ", Error: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, plugin.getServer().getScheduler().asyncScheduler());
    }

    public CompletableFuture<Void> executeQuery(String sql, Consumer<ResultSet> consumer, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    consumer.accept(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Database query error for query: " + sql + ", Error: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, plugin.getServer().getScheduler().asyncScheduler());
    }

    public void closeConnection() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("SQLite database connection closed.");
        }
    }
}
