package com.blockmart.auctionhouse.commands;

import com.blockmart.auctionhouse.AuctionHouse;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.models.AuctionItem;
import com.blockmart.auctionhouse.utils.NBTUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;

import java.util.concurrent.CompletableFuture;

public class AuctionHouseCommand implements CommandExecutor {

    private final AuctionHouse plugin;
    private final AuctionManager auctionManager;

    public AuctionHouseCommand(AuctionHouse plugin, AuctionManager auctionManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            CompletableFuture.runAsync(() -> auctionManager.openAuctionGUI(player));
            return true;
        }

        if (args[0].equalsIgnoreCase("sell")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /ah sell <price>");
                return true;
            }
            double price;
            try {
                price = Double.parseDouble(args[1]);
                if (price <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Price must be a positive number.");
                return true;
            }
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                player.sendMessage(ChatColor.RED + "You must be holding an item to sell.");
                return true;
            }

            CompletableFuture.runAsync(() -> {
                if (auctionManager.sellItem(player, item, price)) {
                    player.getInventory().setItemInMainHand(null);
                    player.sendMessage(ChatColor.GREEN + "Item listed for auction!");
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to list item for auction. Perhaps you don't have enough balance or the item is blacklisted?");
                }
            });
            return true;
        }

        if (args[0].equalsIgnoreCase("bid")) {
            if (args.length < 3) {
                player.sendMessage(ChatColor.RED + "Usage: /ah bid <auction_id> <amount>");
                return true;
            }
            long auctionId;
            double amount;
            try {
                auctionId = Long.parseLong(args[1]);
                amount = Double.parseDouble(args[2]);
                if (amount <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid auction ID or amount.");
                return true;
            }

            CompletableFuture.runAsync(() -> {
                if (auctionManager.placeBid(player, auctionId, amount)) {
                    player.sendMessage(ChatColor.GREEN + "Bid placed successfully for auction ID " + auctionId + "!");
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to place bid. Check your balance or if the bid is high enough.");
                }
            });
            return true;
        }

        if (args[0].equalsIgnoreCase("collect")) {
            CompletableFuture.runAsync(() -> {
                int collectedItems = auctionManager.collectPlayerItems(player);
                double collectedMoney = auctionManager.collectPlayerMoney(player);

                if (collectedItems > 0) {
                    player.sendMessage(ChatColor.GREEN + "Collected " + collectedItems + " items from the auction house.");
                }
                if (collectedMoney > 0) {
                    player.sendMessage(ChatColor.GREEN + "Collected " + plugin.getEconomy().format(collectedMoney) + " from the auction house.");
                }
                if (collectedItems == 0 && collectedMoney == 0) {
                    player.sendMessage(ChatColor.YELLOW + "Nothing to collect from the auction house.");
                }
            });
            return true;
        }

        player.sendMessage(ChatColor.RED + "Unknown command. Usage: /ah [list|sell <price>|bid <id> <amount>|collect]");
        return true;
    }
}