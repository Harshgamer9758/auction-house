package com.blockmart.auctionhouse.listeners;

import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.models.Auction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class AuctionListener implements Listener {

    private final AuctionManager auctionManager;

    public AuctionListener(AuctionManager auctionManager) {
        this.auctionManager = auctionManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        // Check for items/money in escrow and notify player
        auctionManager.getPlayerEscrow(playerUUID).thenAccept(escrows -> {
            long itemCount = escrows.stream().filter(e -> e.getItemStack() != null).count();
            double totalMoney = escrows.stream().filter(e -> e.getItemStack() == null).mapToDouble(e -> e.getAmount()).sum();

            if (itemCount > 0 || totalMoney > 0) {
                if (itemCount > 0) {
                    event.getPlayer().sendMessage(auctionManager.getPlugin().getConfig().getString("messages.escrow-notification-items").replace("%count%", String.valueOf(itemCount)));
                }
                if (totalMoney > 0) {
                    event.getPlayer().sendMessage(auctionManager.getPlugin().getConfig().getString("messages.escrow-notification-money").replace("%amount%", String.format("%.2f", totalMoney)));
                }
                event.getPlayer().sendMessage(auctionManager.getPlugin().getConfig().getString("messages.escrow-collect-prompt"));
            }
        });
    }
}