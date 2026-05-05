package com.blockmart.auctionhouse.database;

import com.blockmart.auctionhouse.AuctionHouse;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {

    private final AuctionHouse plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(AuctionHouse plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    private void initDatabase() {
        HikariConfig config = new HikariConfig();
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        String path = new File(dataFolder, "auctionhouse.db").getAbsolutePath();
        config.setJdbcUrl("jdbc:sqlite:" + path);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(5000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() {
        String auctionTable = "CREATE TABLE IF NOT EXISTS auctions (\n" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "    seller_uuid VARCHAR(36) NOT NULL,\n" +
                "    item_data TEXT NOT NULL,\n" +
                "    start_price DOUBLE NOT NULL,\n" +
                "    current_bid DOUBLE DEFAULT 0.0,\n" +
                "    highest_bidder_uuid VARCHAR(36) DEFAULT NULL,\n" +
                "    list_time DATETIME DEFAULT CURRENT_TIMESTAMP,\n" +
                "    end_time DATETIME,\n" +
                "    status VARCHAR(10) NOT NULL DEFAULT 'LISTED'\n" +
                ");";

        String escrowTable = "CREATE TABLE IF NOT EXISTS escrow (\n" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "    player_uuid VARCHAR(36) NOT NULL,\n" +
                "    item_data TEXT DEFAULT NULL,\n" +
                "    amount DOUBLE DEFAULT 0.0,\n" +
                "    type VARCHAR(10) NOT NULL\n" +
                ");";
        
        String configTable = "CREATE TABLE IF NOT EXISTS config (\n" +
                              "    key TEXT PRIMARY KEY,\n" +
                              "    value TEXT NOT NULL\n" +
                              ");";

        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt1 = conn.prepareStatement(auctionTable);
                 PreparedStatement stmt2 = conn.prepareStatement(escrowTable);
                 PreparedStatement stmt3 = conn.prepareStatement(configTable)) {
                stmt1.execute();
                stmt2.execute();
                stmt3.execute();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error creating database tables: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> executeUpdate(String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database update failed: " + sql, e);
            }
        }, Bukkit.getScheduler().parseChunkRegistrationContext('async'));
    }

    public <T> CompletableFuture<T> executeQuery(String sql, ResultSetProcessor<T> processor, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    return processor.process(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database query failed: " + sql, e);
                return null;
            }
        }, Bukkit.getScheduler().parseChunkRegistrationContext('async'));
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @FunctionalInterface
    public interface ResultSetProcessor<T> {
        T process(ResultSet resultSet) throws SQLException;
    }
}