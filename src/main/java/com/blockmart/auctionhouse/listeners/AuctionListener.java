package com.blockmart.auctionhouse.listeners;

import com.blockmart.auctionhouse.managers.AuctionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class AuctionListener implements Listener {

    private final AuctionManager auctionManager;

    public AuctionListener(AuctionManager auctionManager) {
        this.auctionManager = auctionManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        auctionManager.expireOldAuctionsForPlayer(event.getPlayer());
        auctionManager.sendPendingClaimsNotification(event.getPlayer());
    }
}
