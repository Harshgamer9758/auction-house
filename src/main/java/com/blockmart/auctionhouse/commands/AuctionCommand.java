package com.blockmart.auctionhouse.commands;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.models.Auction;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AuctionCommand implements CommandExecutor {

    private final AuctionHousePlugin plugin;
    private final AuctionManager auctionManager;

    public AuctionCommand(AuctionHousePlugin plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("auctionhouse.use")) {
            player.sendMessage(plugin.getConfig().getString("messages.no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            List<Auction> auctions = auctionManager.getActiveAuctions();
            if (auctions.isEmpty()) {
                player.sendMessage(plugin.getConfig().getString("messages.no-active-auctions"));
                return true;
            }

            player.sendMessage(plugin.getConfig().getString("messages.auction-list-header"));
            for (Auction auction : auctions) {
                long timeLeft = auction.getEndTime() - System.currentTimeMillis();
                String timeLeftFormatted;
                if (timeLeft <= 0) {
                    timeLeftFormatted = "Ending soon";
                } else {
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft);
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) - TimeUnit.MINUTES.toSeconds(minutes);
                    timeLeftFormatted = String.format("%d min %d sec", minutes, seconds);
                }

                player.sendMessage(plugin.getConfig().getString("messages.auction-list-entry")
                        .replace("%id%", String.valueOf(auction.getId()))
                        .replace("%item%", auction.getItemStack().getItemMeta().hasDisplayName() ? auction.getItemStack().getItemMeta().getDisplayName() : auction.getItemStack().getType().name())
                        .replace("%seller%", auction.getSellerName())
                        .replace("%bid%", String.format("%.2f", auction.getCurrentBid()))
                        .replace("%time_left%", timeLeftFormatted)
                );
            }
            return true;
        } else if (args[0].equalsIgnoreCase("collect")) {
            // Implement escrow collection logic
            auctionManager.getPlayerEscrow(player.getUniqueId()).thenAccept(escrows -> {
                if (escrows.isEmpty()) {
                    player.sendMessage(plugin.getConfig().getString("messages.no-escrow-items"));
                    return;
                }
                player.sendMessage(plugin.getConfig().getString("messages.escrow-collect-header"));
                for (Escrow escrow : escrows) {
                    if (escrow.getItemStack() != null) {
                        if (player.getInventory().addItem(escrow.getItemStack()).isEmpty()) {
                            auctionManager.claimFromEscrow(escrow.getId());
                            player.sendMessage(plugin.getConfig().getString("messages.escrow-item-collected")
                                    .replace("%item%", escrow.getItemStack().getItemMeta().hasDisplayName() ? escrow.getItemStack().getItemMeta().getDisplayName() : escrow.getItemStack().getType().name()));
                        } else {
                            player.sendMessage(plugin.getConfig().getString("messages.escrow-inventory-full"));
                        }
                    } else if (escrow.getAmount() > 0) {
                        if (plugin.getVaultHook().deposit(player.getUniqueId(), escrow.getAmount())) {
                            auctionManager.claimFromEscrow(escrow.getId());
                            player.sendMessage(plugin.getConfig().getString("messages.escrow-money-collected")
                                    .replace("%amount%", String.format("%.2f", escrow.getAmount())));
                        } else {
                            player.sendMessage(plugin.getConfig().getString("messages.escrow-money-failed"));
                        }
                    }
                }
            });
            return true;
        }

        player.sendMessage(plugin.getConfig().getString("messages.invalid-command-usage").replace("%command%", label));
        return true;
    }
}