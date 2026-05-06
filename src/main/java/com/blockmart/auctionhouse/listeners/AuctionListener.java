package com.blockmart.auctionhouse.listeners;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.managers.AuctionManager;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AuctionListener implements Listener {

    private final AuctionHousePlugin plugin;
    private final AuctionManager auctionManager;

    public AuctionListener(AuctionHousePlugin plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Check for items/money in escrow upon login
        auctionManager.collectItems(event.getPlayer()).thenAccept(items -> {
            if (!items.isEmpty()) {
                event.getPlayer().sendMessage(ChatColor.YELLOW + "You have items waiting in your auction escrow! Use /auction collect to retrieve them.");
            }
        });
        auctionManager.getPlayerEscrowBalance(event.getPlayer()).thenAccept(balance -> {
            if (balance > 0) {
                event.getPlayer().sendMessage(ChatColor.YELLOW + "You have an escrow balance of " + plugin.getDatabaseManager().getEconomy().format(balance) + "! Use /auction collect to withdraw.");
            }
        });
    }
}