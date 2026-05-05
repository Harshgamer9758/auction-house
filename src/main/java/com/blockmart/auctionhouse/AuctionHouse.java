package com.blockmart.auctionhouse;

import com.blockmart.auctionhouse.commands.AuctionCommand;
import com.blockmart.auctionhouse.database.DatabaseManager;
import com.blockmart.auctionhouse.listeners.AuctionListener;
import com.blockmart.auctionhouse.managers.AuctionManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionHouse extends JavaPlugin {

    private static AuctionHouse instance;
    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        this.auctionManager = new AuctionManager(this, databaseManager);
        this.auctionManager.loadActiveAuctions();

        getCommand("ah").setExecutor(new AuctionCommand(this));
        getServer().getPluginManager().registerEvents(new AuctionListener(this), this);

        getLogger().info("AuctionHouse has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("AuctionHouse has been disabled!");
    }

    public static AuctionHouse getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }
}