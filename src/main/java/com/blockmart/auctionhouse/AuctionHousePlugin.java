package com.blockmart.auctionhouse;

import com.blockmart.auctionhouse.commands.AuctionCommand;
import com.blockmart.auctionhouse.listeners.AuctionListener;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.managers.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

public final class AuctionHousePlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.databaseManager = new DatabaseManager(this);
        this.auctionManager = new AuctionManager(this, databaseManager);

        getCommand("auction").setExecutor(new AuctionCommand(this, auctionManager));
        getServer().getPluginManager().registerEvents(new AuctionListener(this, auctionManager), this);

        getLogger().info("AuctionHouse has been enabled!");

        // Schedule a task to check and close expired auctions periodically
        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.runTaskTimerAsynchronously(this, () -> auctionManager.closeExpiredAuctions(), 0L, 20L * 60 * 5); // Every 5 minutes
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        getLogger().info("AuctionHouse has been disabled!");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }
}