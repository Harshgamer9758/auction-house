package com.blockmart.auctionhouse.listeners;

import com.blockmart.auctionhouse.managers.AuctionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.util.concurrent.CompletableFuture;

public class InventoryListener implements Listener {

    private final AuctionManager auctionManager;

    public InventoryListener(AuctionManager auctionManager) {
        this.auctionManager = auctionManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null || !auctionManager.isAuctionGUI(clickedInventory)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        CompletableFuture.runAsync(() -> {
            if (auctionManager.handleAuctionGUIClick(player, slot, event.getClick())) {
                // Logic handled by auction manager, potentially requiring GUI refresh
            } else {
                player.sendMessage(ChatColor.RED + "Failed to interact with auction item.");
            }
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (auctionManager.isAuctionGUI(event.getInventory())) {
            auctionManager.removeOpenGUI((Player) event.getPlayer());
        }
    }
}