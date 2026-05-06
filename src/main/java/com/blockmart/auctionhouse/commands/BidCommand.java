package com.blockmart.auctionhouse.commands;

import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.models.Auction;
import com.blockmart.auctionhouse.utils.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public class BidCommand implements CommandExecutor {

    private final AuctionManager auctionManager;
    private final VaultHook vaultHook;

    public BidCommand(AuctionManager auctionManager, VaultHook vaultHook) {
        this.auctionManager = auctionManager;
        this.vaultHook = vaultHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("auctionhouse.use")) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.no-permission"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.bid-usage"));
            return true;
        }

        int auctionId;
        double bidAmount;
        try {
            auctionId = Integer.parseInt(args[0]);
            bidAmount = Double.parseDouble(args[1]);
            if (bidAmount <= 0) {
                player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.bid-must-be-positive"));
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.invalid-id-or-amount"));
            return true;
        }

        Optional<Auction> auctionOptional = auctionManager.getAuctionById(auctionId);
        if (auctionOptional.isEmpty()) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.auction-not-found"));
            return true;
        }

        Auction auction = auctionOptional.get();

        if (auction.getSellerUUID().equals(player.getUniqueId())) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.cannot-bid-on-own-auction"));
            return true;
        }

        if (bidAmount <= auction.getCurrentBid()) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.bid-too-low").replace("%current_bid%", String.format("%.2f", auction.getCurrentBid())));
            return true;
        }

        if (!vaultHook.hasEnough(player.getUniqueId(), bidAmount)) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.not-enough-money"));
            return true;
        }

        // Refund previous bidder if any
        if (auction.getHighestBidderUUID() != null) {
            UUID previousBidder = auction.getHighestBidderUUID();
            double previousBid = auction.getCurrentBid();
            auctionManager.addEscrowMoney(previousBidder, previousBid);
            Player prevBidderOnline = Bukkit.getPlayer(previousBidder);
            if (prevBidderOnline != null) {
                prevBidderOnline.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.outbid").replace("%item%", auction.getItemStack().getItemMeta().getDisplayName()).replace("%amount%", String.valueOf(previousBid)));
            }
        }

        if (vaultHook.withdraw(player.getUniqueId(), bidAmount)) {
            auctionManager.bidOnAuction(auction, player.getUniqueId(), player.getName(), bidAmount);
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.bid-placed")
                    .replace("%id%", String.valueOf(auctionId))
                    .replace("%amount%", String.format("%.2f", bidAmount)));
            if (auction.getSellerUUID() != null) {
                Player sellerOnline = Bukkit.getPlayer(auction.getSellerUUID());
                if (sellerOnline != null) {
                    sellerOnline.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.new-bid").replace("%item%", auction.getItemStack().getItemMeta().getDisplayName()).replace("%bidder%", player.getName()).replace("%amount%", String.valueOf(bidAmount)));
                }
            }
        } else {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.bid-failed"));
        }

        return true;
    }
}