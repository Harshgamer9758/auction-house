package com.blockmart.auctionhouse.commands;

import com.blockmart.auctionhouse.AuctionHouse;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.utils.ItemNBTUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AuctionCommand implements CommandExecutor {

    private final AuctionHouse plugin;
    private final AuctionManager auctionManager;

    public AuctionCommand(AuctionHouse plugin) {
        this.plugin = plugin;
        this.auctionManager = plugin.getAuctionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eUsage: /ah <list|sell|bid|collect>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                auctionManager.openAuctionGUI(player);
                break;
            case "sell":
                if (args.length < 3) {
                    player.sendMessage("§eUsage: /ah sell <price> <duration_minutes>");
                    return true;
                }
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item.getType().isAir()) {
                    player.sendMessage("§cYou must be holding an item to sell.");
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[1]);
                    long durationMinutes = Long.parseLong(args[2]);
                    if (price <= 0 || durationMinutes <= 0) {
                        player.sendMessage("§cPrice and duration must be positive numbers.");
                        return true;
                    }
                    ItemStack strippedItem = ItemNBTUtil.stripNBT(item);
                    auctionManager.createAuction(player, strippedItem, price, durationMinutes);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid price or duration. Must be a number.");
                }
                break;
            case "bid":
                if (args.length < 3) {
                    player.sendMessage("§eUsage: /ah bid <auction_id> <amount>");
                    return true;
                }
                try {
                    int auctionId = Integer.parseInt(args[1]);
                    double amount = Double.parseDouble(args[2]);
                    if (amount <= 0) {
                        player.sendMessage("§cBid amount must be positive.");
                        return true;
                    }
                    auctionManager.placeBid(player, auctionId, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid auction ID or bid amount. Must be a number.");
                }
                break;
            case "collect":
                auctionManager.collectItems(player);
                break;
            default:
                player.sendMessage("§eUsage: /ah <list|sell|bid|collect>");
                break;
        }
        return true;
    }
}