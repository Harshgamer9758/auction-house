package com.blockmart.auctionhouse;

import com.blockmart.auctionhouse.commands.AuctionCommand;
import com.blockmart.auctionhouse.commands.BidCommand;
import com.blockmart.auctionhouse.commands.SellItemCommand;
import com.blockmart.auctionhouse.listeners.AuctionListener;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.managers.DatabaseManager;
import com.blockmart.auctionhouse.utils.VaultHook;
import com.blockmart.auctionhouse.utils.NBTUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class AuctionHousePlugin extends JavaPlugin {

    private static AuctionHousePlugin instance;
    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;
    private VaultHook vaultHook;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.databaseManager = new DatabaseManager(this);
        this.auctionManager = new AuctionManager(this, databaseManager);

        // Initialize Vault
        this.vaultHook = new VaultHook(this);
        if (!vaultHook.setupEconomy()) {
            getLogger().severe("Disabling due to no Vault dependency found or economy provider not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register Commands
        getCommand("ah").setExecutor(new AuctionCommand(this, auctionManager));
        getCommand("sellitem").setExecutor(new SellItemCommand(auctionManager, vaultHook));
        getCommand("bid").setExecutor(new BidCommand(auctionManager, vaultHook));

        // Register Listeners
        getServer().getPluginManager().registerEvents(new AuctionListener(auctionManager), this);

        // Load auctions from database asynchronously
        new BukkitRunnable() {
            @Override
            public void run() {
                auctionManager.loadAllAuctions();
            }
        }.runTaskAsynchronously(this);

        getLogger().info("AuctionHouse has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.closePool();
        }
        getLogger().info("AuctionHouse has been disabled!");
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

    public VaultHook getVaultHook() {
        return vaultHook;
    }
}