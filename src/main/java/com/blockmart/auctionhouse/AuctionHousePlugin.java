package com.blockmart.auctionhouse;

import com.blockmart.auctionhouse.commands.AuctionCommand;
import com.blockmart.auctionhouse.listeners.AuctionListener;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.managers.DatabaseManager;
import com.blockmart.auctionhouse.managers.EscrowManager;
import com.blockmart.auctionhouse.utils.NBTUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionHousePlugin extends JavaPlugin {

    private static AuctionHousePlugin instance;
    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;
    private EscrowManager escrowManager;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Disabled due to no Vault dependency found or economy provider not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = new DatabaseManager(this, "jdbc:sqlite:" + getDataFolder().getAbsolutePath() + "/auctionhouse.db");
        databaseManager.connect();
        databaseManager.createTables();

        this.escrowManager = new EscrowManager(this, databaseManager, economy);
        this.auctionManager = new AuctionManager(this, databaseManager, escrowManager);

        getCommand("auction").setExecutor(new AuctionCommand(this, auctionManager));
        getServer().getPluginManager().registerEvents(new AuctionListener(this, auctionManager), this);

        getLogger().info("AuctionHouse has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.disconnect();
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

    public static AuctionHousePlugin getInstance() {
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