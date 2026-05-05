package com.blockmart.auctionhouse;

import com.blockmart.auctionhouse.commands.AuctionHouseCommand;
import com.blockmart.auctionhouse.database.DatabaseManager;
import com.blockmart.auctionhouse.listeners.InventoryListener;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.managers.EscrowManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class AuctionHouse extends JavaPlugin {

    private static AuctionHouse instance;
    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;
    private EscrowManager escrowManager;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().log(Level.SEVERE, "Vault not found or economy provider not registered! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = new DatabaseManager(this);
        this.escrowManager = new EscrowManager(this, databaseManager, economy);
        this.auctionManager = new AuctionManager(this, databaseManager, escrowManager, economy);

        getCommand("ah").setExecutor(new AuctionHouseCommand(this, auctionManager));
        getServer().getPluginManager().registerEvents(new InventoryListener(auctionManager), this);

        getLogger().log(Level.INFO, "AuctionHouse has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().log(Level.INFO, "AuctionHouse has been disabled!");
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

    public EscrowManager getEscrowManager() {
        return escrowManager;
    }

    public Economy getEconomy() {
        return economy;
    }
}