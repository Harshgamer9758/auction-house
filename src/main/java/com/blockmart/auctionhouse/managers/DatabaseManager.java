package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final AuctionHousePlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(AuctionHousePlugin plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    private void initDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/auctionhouse.db");
        config.setMaximumPoolSize(10);
        config.setConnectionTestQuery("SELECT 1");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("journal_mode", "WAL");
        this.dataSource = new HikariDataSource(config);

        createTables();
    }

    private void createTables() {
        final String CREATE_AUCTIONS_TABLE = "CREATE TABLE IF NOT EXISTS auctions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "sellerUUID VARCHAR(36) NOT NULL," +
                "sellerName VARCHAR(16) NOT NULL," +
                "itemStack TEXT NOT NULL," +
                "startPrice DOUBLE NOT NULL," +
                "currentBid DOUBLE NOT NULL," +
                "highestBidderUUID VARCHAR(36)," +
                "highestBidderName VARCHAR(16)," +
                "endTime INTEGER NOT NULL," +
                "status VARCHAR(20) NOT NULL);";

        final String CREATE_ESCROWS_TABLE = "CREATE TABLE IF NOT EXISTS escrows (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ownerUUID VARCHAR(36) NOT NULL," +
                "itemStack TEXT,"
                + "amount DOUBLE NOT NULL DEFAULT 0.0);";

        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt1 = conn.prepareStatement(CREATE_AUCTIONS_TABLE);
                 PreparedStatement stmt2 = conn.prepareStatement(CREATE_ESCROWS_TABLE)) {
                stmt1.execute();
                stmt2.execute();
                plugin.getLogger().info("Database tables created or verified.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not create database tables: " + e.getMessage());
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
                plugin.getLogger().severe("Database update error: " + e.getMessage());
            }
        }, Bukkit.getScheduler().getCurrentWorkerThread() == null ? new BukkitRunnable() {
            @Override
            public void run() {}
        }.runTaskAsynchronously(plugin).getScheduler().getAsyncRunner() : Runnable::run);
    }

    public CompletableFuture<Connection> getConnectionAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get database connection: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    public void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}