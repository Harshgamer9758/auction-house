package com.blockmart.auctionhouse.commands;

import com.blockmart.auctionhouse.managers.AuctionManager;
import com.blockmart.auctionhouse.utils.VaultHook;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SellItemCommand implements CommandExecutor {

    private final AuctionManager auctionManager;
    private final VaultHook vaultHook;

    public SellItemCommand(AuctionManager auctionManager, VaultHook vaultHook) {
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

        if (args.length < 1) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.sellitem-usage"));
            return true;
        }

        double price;
        try {
            price = Double.parseDouble(args[0]);
            if (price <= 0) {
                player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.price-must-be-positive"));
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.invalid-price"));
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType().isAir()) {
            player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.no-item-in-hand"));
            return true;
        }

        // Remove item from inventory before creating auction to escrow it mentally
        player.getInventory().setItemInMainHand(null);
        player.updateInventory();

        long auctionDuration = vaultHook.getPlugin().getConfig().getLong("auction-settings.default-duration-minutes", 60);
        auctionManager.createAuction(player.getUniqueId(), player.getName(), itemInHand, price, auctionDuration);
        player.sendMessage(vaultHook.getPlugin().getConfig().getString("messages.item-listed-for-auction")
                .replace("%item%", itemInHand.getItemMeta().hasDisplayName() ? itemInHand.getItemMeta().getDisplayName() : itemInHand.getType().name())
                .replace("%price%", String.format("%.2f", price))
                .replace("%duration%", String.valueOf(auctionDuration))
        );
        return true;
    }
}