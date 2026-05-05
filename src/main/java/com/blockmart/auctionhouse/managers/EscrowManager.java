package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHouse;
import com.blockmart.auctionhouse.database.DatabaseManager;
import com.blockmart.auctionhouse.utils.ItemSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class EscrowManager {

    private final AuctionHouse plugin;
    private final DatabaseManager databaseManager;
    private final Economy economy;

    public EscrowManager(AuctionHouse plugin, DatabaseManager databaseManager, Economy economy) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.economy = economy;
    }

    public CompletableFuture<Void> addItemToEscrow(UUID playerUuid, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return CompletableFuture.completedFuture(null);
        }
        String itemData = ItemSerializer.itemStackToBase64(item);
        return databaseManager.executeUpdate(
                "INSERT INTO escrow (player_uuid, item_data, type) VALUES (?, ?, 'ITEM')",
                playerUuid.toString(), itemData
        );
    }

    public CompletableFuture<Void> addMoneyToEscrow(UUID playerUuid, double amount, String type) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return databaseManager.executeUpdate(
                "INSERT INTO escrow (player_uuid, amount, type) VALUES (?, ?, 'MONEY')",
                playerUuid.toString(), amount
        );
    }

    public CompletableFuture<Boolean> releaseMoneyFromEscrow(UUID playerUuid, double amount, String type) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(true);
        }
        // Attempt to remove specific amount from escrow and add to player's balance
        return databaseManager.executeQuery("SELECT id, amount FROM escrow WHERE player_uuid = ? AND type = '" + type + "' LIMIT 1", rs -> {
            if (rs.next()) {
                long id = rs.getLong("id");
                double escrowAmount = rs.getDouble("amount");

                if (escrowAmount == amount) {
                    // If the exact amount is found, remove it completely
                    databaseManager.executeUpdate("DELETE FROM escrow WHERE id = ?", id).join();
                } else if (escrowAmount > amount) {
                    // If more is in escrow, update the existing entry
                    databaseManager.executeUpdate("UPDATE escrow SET amount = amount - ? WHERE id = ?", amount, id).join();
                } else {
                    // Not enough specific escrow for this type or amount, handle with caution or error
                    plugin.getLogger().log(Level.WARNING, "Attempted to release " + amount + " but found " + escrowAmount + " for player " + playerUuid + " of type " + type);
                    return false;
                }
                return true;
            }
            return false;
        }, playerUuid.toString());
    }
}