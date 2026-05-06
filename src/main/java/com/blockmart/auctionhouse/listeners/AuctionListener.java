package com.blockmart.auctionhouse.listeners;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.managers.AuctionManager;
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
        plugin.getEscrowManager().getPlayerEscrowItems(event.getPlayer().getUniqueId()).thenAccept(items -> {
            if (!items.isEmpty()) {
                event.getPlayer().sendMessage("§eYou have items waiting in the auction escrow! Use /auction collect to retrieve them.");
            }
        });
    }
}