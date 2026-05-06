package com.blockmart.auctionhouse;

import com.blockmart.auctionhouse.commands.AuctionCommand;
import com.blockmart.auctionhouse.listeners.AuctionListener;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.managers.DatabaseManager;
import com.blockmart.auctionhouse.utils.NBTUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Logger;

public final class AuctionHouse extends JavaPlugin {

    private static AuctionHouse instance;
    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;
    private Economy economy;
    private NBTUtils nbtUtils;

    @Override
    public void onEnable() {
        instance = this;
        Logger log = getLogger();

        if (!setupEconomy()) {
            log.severe("Disabled due to no Vault dependency found or economy provider!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.nbtUtils = new NBTUtils();

        File dataFolder = new File(getDataFolder(), "auction_house.db");
        this.databaseManager = new DatabaseManager(this, dataFolder.getAbsolutePath());
        this.databaseManager.loadDatabase();

        this.auctionManager = new AuctionManager(this, databaseManager, economy);
        this.auctionManager.loadActiveAuctions().join(); // Wait for auctions to load before plugin is fully enabled

        getCommand("auction").setExecutor(new AuctionCommand(this, auctionManager, nbtUtils));
        getServer().getPluginManager().registerEvents(new AuctionListener(auctionManager), this);

        log.info("AuctionHouse has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        getLogger().info("AuctionHouse has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
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

    public NBTUtils getNbtUtils() {
        return nbtUtils;
    }
}
