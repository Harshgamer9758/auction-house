package com.blockmart.auctionhouse.commands;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.models.Auction;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> listAuctions(player);
            case "sell" -> sellItem(player, args);
            case "bid" -> placeBid(player, args);
            case "collect" -> collectItems(player);
            default -> sendHelpMessage(player);
        }

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§6--- Auction House Help ---");
        player.sendMessage("§e/auction list §7- View active auctions.");
        player.sendMessage("§e/auction sell <price> <duration_minutes> §7- Sell the item in your hand.");
        player.sendMessage("§e/auction bid <auction_id> <amount> §7- Place a bid on an auction.");
        player.sendMessage("§e/auction collect §7- Collect expired/won items from escrow.");
    }

    private void listAuctions(Player player) {
        auctionManager.getActiveAuctions().thenAccept(auctions -> {
            if (auctions.isEmpty()) {
                player.sendMessage("§cThere are no active auctions right now.");
                return;
            }
            player.sendMessage("§6--- Active Auctions ---");
            for (Auction auction : auctions) {
                long timeLeft = TimeUnit.MILLISECONDS.toMinutes(auction.getEndTime() - System.currentTimeMillis());
                String bidderInfo = auction.getHighestBidderName() != null ? "(Bid by: " + auction.getHighestBidderName() + ")" : "(No bids yet)";
                player.sendMessage(String.format("§eID: %d | Item: %s | Seller: %s | Current Bid: %.2f %s | Time Left: %s mins",
                        auction.getId(),
                        auction.getItem().hasItemMeta() ? auction.getItem().getItemMeta().getDisplayName() : auction.getItem().getType().name(),
                        auction.getSellerName(),
                        auction.getCurrentBid(),
                        bidderInfo,
                        timeLeft));
            }
        }).exceptionally(ex -> {
            player.sendMessage("§cAn error occurred while listing auctions.");
            plugin.getLogger().severe("Error listing auctions: " + ex.getMessage());
            return null;
        });
    }

    private void sellItem(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /auction sell <price> <duration_minutes>");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            player.sendMessage("§cYou must be holding an item to sell.");
            return;
        }

        double price;
        long duration;
        try {
            price = Double.parseDouble(args[1]);
            duration = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid price or duration. Please use numbers.");
            return;
        }

        if (price <= 0 || duration <= 0) {
            player.sendMessage("§cPrice and duration must be greater than zero.");
            return;
        }
        if (duration > 1440) { // Max 24 hours
            player.sendMessage("§cMaximum auction duration is 1440 minutes (24 hours).");
            return;
        }

        auctionManager.createAuction(player.getUniqueId(), player.getName(), item, price, duration)
                .thenAccept(v -> player.sendMessage("§aYour item has been listed for auction!"))
                .exceptionally(ex -> {
                    player.sendMessage("§cFailed to list your item for auction: " + ex.getMessage());
                    plugin.getLogger().severe("Error creating auction: " + ex.getMessage());
                    return null;
                });
    }

    private void placeBid(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /auction bid <auction_id> <amount>");
            return;
        }

        int auctionId;
        double bidAmount;
        try {
            auctionId = Integer.parseInt(args[1]);
            bidAmount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid auction ID or bid amount. Please use numbers.");
            return;
        }

        if (bidAmount <= 0) {
            player.sendMessage("§cBid amount must be greater than zero.");
            return;
        }

        auctionManager.placeBid(auctionId, player.getUniqueId(), player.getName(), bidAmount)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§aBid placed successfully on auction ID " + auctionId + " for " + bidAmount + ".");
                    } else {
                        // Detailed error messages should come from AuctionManager
                        player.sendMessage("§cFailed to place bid. Check if auction exists, is active, or if your bid is high enough and you have enough money.");
                    }
                })
                .exceptionally(ex -> {
                    player.sendMessage("§cAn error occurred while placing your bid.");
                    plugin.getLogger().severe("Error placing bid for " + player.getName() + ": " + ex.getMessage());
                    return null;
                });
    }

    private void collectItems(Player player) {
        plugin.getEscrowManager().getPlayerEscrowItems(player.getUniqueId()).thenAccept(items -> {
            if (items.isEmpty()) {
                player.sendMessage("§cYou have no items to collect from the escrow.");
                return;
            }

            player.sendMessage("§aCollecting items from your escrow...");
            for (ItemStack item : items) {
                plugin.getEscrowManager().withdrawItem(player.getUniqueId(), item, true).thenAccept(success -> {
                    if (success) {
                        player.sendMessage("§fCollected an item: " + (item.hasItemMeta() ? item.getItemMeta().getDisplayName() : item.getType().name()));
                    } else {
                        player.sendMessage("§cFailed to collect an item from escrow.");
                    }
                });
            }
        }).exceptionally(ex -> {
            player.sendMessage("§cAn error occurred while trying to collect your items.");
            plugin.getLogger().severe("Error collecting items for " + player.getName() + ": " + ex.getMessage());
            return null;
        });
    }
}