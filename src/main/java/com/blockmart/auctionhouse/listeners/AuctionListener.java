package com.blockmart.auctionhouse.listeners;

import com.blockmart.auctionhouse.AuctionHouse;
import com.blockmart.auctionhouse.managers.AuctionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public class AuctionListener implements Listener {

    private final AuctionHouse plugin;
    private final AuctionManager auctionManager;

    public AuctionListener(AuctionHouse plugin) {
        this.plugin = plugin;
        this.auctionManager = plugin.getAuctionManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof AuctionManager.AuctionGUIHolder) {
            event.setCancelled(true);
            auctionManager.handleGUIClick(event);
        }
    }
}