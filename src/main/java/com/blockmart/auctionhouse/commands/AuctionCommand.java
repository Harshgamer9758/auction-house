package com.blockmart.auctionhouse.commands;

import com.blockmart.auctionhouse.AuctionHouse;
import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.utils.NBTUtils;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionCommand implements CommandExecutor {

    private final AuctionHouse plugin;
    private final AuctionManager auctionManager;
    private final NBTUtils nbtUtils;

    public AuctionCommand(AuctionHouse plugin, AuctionManager auctionManager, NBTUtils nbtUtils) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
        this.nbtUtils = nbtUtils;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eUsage: /auction [list|sell <price>|bid <id> <amount>|claim|cancel <id>]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                auctionManager.sendAuctionList(player);
                break;
            case "sell":
                if (args.length < 2) {
                    player.sendMessage("§eUsage: /auction sell <price>");
                    return true;
                }
                try {
                    double price = Double.parseDouble(args[1]);
                    if (price <= 0) {
                        player.sendMessage("§cPrice must be positive.");
                        return true;
                    }
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    if (handItem.getType() == Material.AIR) {
                        player.sendMessage("§cYou must hold an item to sell.");
                        return true;
                    }
                    auctionManager.createAuction(player, handItem, price);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cInvalid price.");
                }
                break;
            case "bid":
                if (args.length < 3) {
                    player.sendMessage("§eUsage: /auction bid <id> <amount>");
                    return true;
                }
                try {
                    UUID auctionId = UUID.fromString(args[1]);
                    double bidAmount = Double.parseDouble(args[2]);
                    if (bidAmount <= 0) {
                        player.sendMessage("§cBid amount must be positive.");
                        return true;
                    }
                    auctionManager.placeBid(player, auctionId, bidAmount);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cInvalid auction ID or bid amount.");
                }
                break;
            case "claim":
                auctionManager.claimItems(player);
                break;
            case "cancel":
                if (args.length < 2) {
                    player.sendMessage("§eUsage: /auction cancel <id>");
                    return true;
                }
                try {
                    UUID auctionId = UUID.fromString(args[1]);
                    auctionManager.cancelAuction(player, auctionId);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cInvalid auction ID.");
                }
                break;
            default:
                player.sendMessage("§eUsage: /auction [list|sell <price>|bid <id> <amount>|claim|cancel <id>]");
                break;
        }
        return true;
    }
}
