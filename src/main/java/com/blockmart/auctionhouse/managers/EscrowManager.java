package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.models.EscrowType;
import com.blockmart.auctionhouse.utils.NBTUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EscrowManager {

    private final AuctionHousePlugin plugin;
    private final DatabaseManager databaseManager;
    private final Economy economy;

    public EscrowManager(AuctionHousePlugin plugin, DatabaseManager databaseManager, Economy economy) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.economy = economy;
    }

    public CompletableFuture<Boolean> depositItem(UUID playerUuid, ItemStack item, boolean intoEscrow) {
        if (!intoEscrow) {

            return CompletableFuture.completedFuture( выдаёт предмет );
        }

        String itemNbt = NBTUtils.itemStackToBase64(item);
        String sql = "INSERT INTO escrow (player_uuid, item_nbt, type) VALUES (?, ?, ?)";
        return databaseManager.executeUpdate(sql, playerUuid.toString(), itemNbt, EscrowType.ITEM.name())
                .thenApply(v -> {
                    Bukkit.getPlayer(playerUuid).getInventory().removeItem(item);
                    return true;
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to deposit item into escrow for " + playerUuid + ": " + ex.getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> withdrawItem(UUID playerUuid, ItemStack item, boolean fromEscrow) {
        if (!fromEscrow) {
            // Directly give item to player (not applicable for basic escrow, but useful for clarity)
            return CompletableFuture.completedFuture(Bukkit.getPlayer(playerUuid).getInventory().addItem(item).isEmpty());
        }

        String itemNbt = NBTUtils.itemStackToBase64(item);
        String sql = "DELETE FROM escrow WHERE player_uuid = ? AND item_nbt = ? AND type = ? LIMIT 1";
        return databaseManager.executeUpdate(sql, playerUuid.toString(), itemNbt, EscrowType.ITEM.name())
                .thenApply(v -> {
                    // After successful removal from DB, give item to player.
                    // This assumes item in escrow is unique per entry, or we need to manage quantities.
                    Bukkit.getPlayer(playerUuid).getInventory().addItem(item);
                    return true;
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to withdraw item from escrow for " + playerUuid + ": " + ex.getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> depositBalance(UUID playerUuid, double amount, boolean intoEscrow) {
        if (intoEscrow) {
            // This isn't usually how money escrow works in bids; bids are held from player's balance directly by an economy plugin.
            // For simplicity, we assume Vault handles player's main balance.
            // If we needed internal money escrow, we'd add it here.
            return CompletableFuture.completedFuture(true);
        } else {
            return CompletableFuture.supplyAsync(() -> economy.depositPlayer(Bukkit.getOfflinePlayer(playerUuid), amount).transactionSuccess(), Bukkit.getScheduler().getAsyncScheduler());
        }
    }

    public CompletableFuture<Boolean> withdrawBalance(UUID playerUuid, double amount, boolean fromEscrow) {
        if (fromEscrow) {
            // For bidding, this implicitly means checking and withdrawing from the player's main balance using Vault
            return CompletableFuture.supplyAsync(() -> economy.withdrawPlayer(Bukkit.getOfflinePlayer(playerUuid), amount).transactionSuccess(), Bukkit.getScheduler().getAsyncScheduler());
        } else {
            return CompletableFuture.completedFuture(true);
        }
    }

    public CompletableFuture<List<ItemStack>> getPlayerEscrowItems(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<ItemStack> items = new ArrayList<>();
            String sql = "SELECT item_nbt FROM escrow WHERE player_uuid = ? AND type = ?";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, EscrowType.ITEM.name());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    items.add(NBTUtils.base64ToItemStack(rs.getString("item_nbt")));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting escrow items for player " + playerUuid + ": " + e.getMessage());
            }
            return items;
        }, Bukkit.getScheduler().getAsyncScheduler());
    }
}