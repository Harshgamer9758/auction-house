package com.blockmart.auctionhouse.commands;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.models.AuctionItem;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class AuctionCommand implements CommandExecutor {

    private final AuctionHousePlugin plugin;
    private final AuctionManager auctionManager;

    public AuctionCommand(AuctionHousePlugin plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use auction commands.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                handleListCommand(player);
                break;
            case "sell":
                handleSellCommand(player, args);
                break;
            case "bid":
                handleBidCommand(player, args);
                break;
            case "collect":
                auctionManager.collectItems(player);
                auctionManager.withdrawMoney(player);
                break;
            case "deposit":
                handleDepositCommand(player, args);
                break;
            case "balance":
                auctionManager.getPlayerEscrowBalance(player);
                break;
            default:
                sendHelpMessage(player);
                break;
        }

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- Auction House Help ---");
        player.sendMessage(ChatColor.YELLOW + "/auction list" + ChatColor.WHITE + " - View active auctions.");
        player.sendMessage(ChatColor.YELLOW + "/auction sell <price> <duration_minutes>" + ChatColor.WHITE + " - Sell the item in your hand.");
        player.sendMessage(ChatColor.YELLOW + "/auction bid <auction_id> <amount>" + ChatColor.WHITE + " - Place a bid on an auction.");
        player.sendMessage(ChatColor.YELLOW + "/auction collect" + ChatColor.WHITE + " - Collect your items/money from escrow.");
        player.sendMessage(ChatColor.YELLOW + "/auction deposit <amount>" + ChatColor.WHITE + " - Deposit money to your escrow.");
        player.sendMessage(ChatColor.YELLOW + "/auction balance" + ChatColor.WHITE + " - Check your escrow balance.");
    }

    private void handleListCommand(Player player) {
        auctionManager.getActiveAuctions().thenAccept(auctions -> {
            if (auctions.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "There are no active auctions.");
                return;
            }
            player.sendMessage(ChatColor.GOLD + "--- Active Auctions ---");
            for (AuctionItem auction : auctions) {
                long timeLeftMillis = auction.getEndTime() - System.currentTimeMillis();
                long minutes = timeLeftMillis / (1000 * 60);
                long seconds = (timeLeftMillis / 1000) % 60;
                String timeLeft = String.format("%d min %d sec", minutes, seconds);

                player.sendMessage(ChatColor.YELLOW + "ID: " + auction.getId() +
                        ChatColor.WHITE + " | Item: " + auction.getItemStack().getItemMeta().getDisplayName() + " x" + auction.getItemStack().getAmount() +
                        ChatColor.WHITE + " | Seller: " + auction.getSellerName() +
                        ChatColor.WHITE + " | Current Bid: " + plugin.getDatabaseManager().getEconomy().format(auction.getCurrentBid()) +
                        (auction.getHighestBidderName() != null ? " (" + auction.getHighestBidderName() + ")" : "") +
                        ChatColor.WHITE + " | Ends in: " + timeLeft);
            }
        }).exceptionally(e -> {
            player.sendMessage(ChatColor.RED + "Error fetching auctions: " + e.getMessage());
            return null;
        });
    }

    private void handleSellCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /auction sell <price> <duration_minutes>");
            return;
        }
        double price;
        long duration;
        try {
            price = Double.parseDouble(args[1]);
            duration = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid price or duration. Must be a number.");
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        auctionManager.listItemForAuction(player, itemInHand, price, duration);
    }

    private void handleBidCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /auction bid <auction_id> <amount>");
            return;
        }
        int auctionId;
        double amount;
        try {
            auctionId = Integer.parseInt(args[1]);
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid auction ID or bid amount. Must be a number.");
            return;
        }

        auctionManager.placeBid(player, auctionId, amount);
    }

    private void handleDepositCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /auction deposit <amount>");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount. Must be a number.");
            return;
        }
        auctionManager.depositMoney(player, amount);
    }
}