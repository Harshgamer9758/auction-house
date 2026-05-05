package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHouse;
import com.blockmart.auctionhouse.database.DatabaseManager;
import com.blockmart.auctionhouse.models.AuctionItem;
import com.blockmart.auctionhouse.utils.ItemNBTUtil;
import com.blockmart.auctionhouse.utils.ItemSerializer;
import com.blockmart.auctionhouse.utils.NBTUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class AuctionManager {

    private final AuctionHouse plugin;
    private final DatabaseManager databaseManager;
    private final EscrowManager escrowManager;
    private final Economy economy;
    private final Map<UUID, Inventory> openAuctionGUIs = new ConcurrentHashMap<>();
    private static final int ITEMS_PER_PAGE = 45; // 5 rows for items
    private final Set<String> blacklistedNBTKeys = new HashSet<>(Arrays.asList("auction_data", "some_other_plugin_data")); // Example blacklist

    public AuctionManager(AuctionHouse plugin, DatabaseManager databaseManager, EscrowManager escrowManager, Economy economy) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.escrowManager = escrowManager;
        this.economy = economy;
        startAuctionEndTask();
    }

    public boolean isAuctionGUI(Inventory inventory) {
        return openAuctionGUIs.containsValue(inventory);
    }

    public void removeOpenGUI(Player player) {
        openAuctionGUIs.remove(player.getUniqueId());
    }

    public boolean sellItem(Player seller, ItemStack item, double price) {
        // Basic validation for NBT tags (e.g., preventing nested auction data or blacklisted NBT)
        if (NBTUtils.containsBlacklistedNBT(item, blacklistedNBTKeys)) {
            seller.sendMessage(ChatColor.RED + "This item contains blacklisted NBT data and cannot be sold.");
            return false;
        }

        String itemData = ItemSerializer.itemStackToBase64(item);
        long endTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 hours

        return databaseManager.executeUpdate(
                "INSERT INTO auctions (seller_uuid, item_data, start_price, current_bid, end_time) VALUES (?, ?, ?, ?, ?)",
                seller.getUniqueId().toString(), itemData, price, price, new Date(endTime)
        ).join() != null; // join() will block until the future is done, returning null on failure
    }

    public boolean placeBid(Player bidder, long auctionId, double amount) {
        plugin.getLogger().log(Level.INFO, "Attempting to place bid for auction " + auctionId + " by " + bidder.getName() + " with amount " + amount);

        return databaseManager.executeQuery("SELECT seller_uuid, current_bid, highest_bidder_uuid FROM auctions WHERE id = ? AND status = 'LISTED'", rs -> {
            if (!rs.next()) {
                bidder.sendMessage(ChatColor.RED + "Auction not found or no longer listed.");
                return false;
            }
            UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
            double currentBid = rs.getDouble("current_bid");
            String highestBidderUuidStr = rs.getString("highest_bidder_uuid");
            UUID highestBidderUuid = highestBidderUuidStr != null ? UUID.fromString(highestBidderUuidStr) : null;

            if (bidder.getUniqueId().equals(sellerUuid)) {
                bidder.sendMessage(ChatColor.RED + "You cannot bid on your own item.");
                return false;
            }

            if (amount <= currentBid) {
                bidder.sendMessage(ChatColor.RED + "Your bid must be higher than the current bid (" + economy.format(currentBid) + ").");
                return false;
            }

            if (!economy.has(bidder, amount)) {
                bidder.sendMessage(ChatColor.RED + "You don't have enough money to place this bid.");
                return false;
            }

            economy.withdrawPlayer(bidder, amount);
            escrowManager.addMoneyToEscrow(bidder.getUniqueId(), amount, "BID");
            plugin.getLogger().log(Level.INFO, "Withdrew " + amount + " from " + bidder.getName() + " and placed in escrow.");

            if (highestBidderUuid != null) {
                escrowManager.releaseMoneyFromEscrow(highestBidderUuid, currentBid, "BID_REFUND");
                OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(highestBidderUuid);
                if (previousBidder.isOnline()) {
                    previousBidder.getPlayer().sendMessage(ChatColor.YELLOW + "You have been outbid on auction ID: " + auctionId + ". Your bid has been refunded.");
                }
                plugin.getLogger().log(Level.INFO, "Refunded " + currentBid + " to previous bidder " + highestBidderUuid + ".");
            }

            databaseManager.executeUpdate(
                    "UPDATE auctions SET current_bid = ?, highest_bidder_uuid = ? WHERE id = ?",
                    amount, bidder.getUniqueId().toString(), auctionId
            ).join();

            Bukkit.broadcastMessage(ChatColor.YELLOW + bidder.getName() + ChatColor.GOLD + " has bid " +
                    ChatColor.YELLOW + economy.format(amount) + ChatColor.GOLD + " on auction ID " + auctionId + "!");
            return true;
        }, auctionId).join();
    }


    public CompletableFuture<Void> openAuctionGUI(Player player) {
        return getAllActiveAuctions().thenAccept(auctionItems -> {
            int numPages = (int) Math.ceil((double) auctionItems.size() / ITEMS_PER_PAGE);
            // For simplicity, implement page 0 directly, more complex paginations would need more methods/classes
            Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Auction House"); // 45 item slots + 9 control slots

            for (int i = 0; i < Math.min(ITEMS_PER_PAGE, auctionItems.size()); i++) {
                AuctionItem auctionItem = auctionItems.get(i);
                ItemStack displayItem = ItemSerializer.base64ToItemStack(auctionItem.getItemData());
                if (displayItem != null) {
                    ItemMeta meta = displayItem.getItemMeta();
                    List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                    lore.add("");
                    lore.add(ChatColor.GRAY + "ID: " + ChatColor.WHITE + auctionItem.getId());
                    lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + Bukkit.getOfflinePlayer(auctionItem.getSellerUuid()).getName());
                    lore.add(ChatColor.GRAY + "Current Bid: " + ChatColor.GREEN + economy.format(auctionItem.getCurrentBid()));
                    if (auctionItem.getHighestBidderUuid() != null) {
                        lore.add(ChatColor.GRAY + "Highest Bidder: " + ChatColor.YELLOW + Bukkit.getOfflinePlayer(auctionItem.getHighestBidderUuid()).getName());
                    }
                    lore.add(ChatColor.GRAY + "Ends: " + ChatColor.WHITE + auctionItem.getEndTime().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")));
                    lore.add("");
                    lore.add(ChatColor.YELLOW + "Click to bid!");
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                    gui.setItem(i, ItemNBTUtil.setNBTTag(displayItem, "auctionId", String.valueOf(auctionItem.getId())));
                }
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.openInventory(gui);
                openAuctionGUIs.put(player.getUniqueId(), gui);
            });
        });
    }

    public boolean handleAuctionGUIClick(Player player, int slot, org.bukkit.event.inventory.ClickType clickType) {
        ItemStack clickedItem = player.getOpenInventory().getItem(slot);
        if (clickedItem == null || clickedItem.getType().isAir()) return false;

        String auctionIdStr = ItemNBTUtil.getNBTTag(clickedItem, "auctionId");
        if (auctionIdStr == null) return false;

        long auctionId = Long.parseLong(auctionIdStr);

        // Example: left click to bid, right click to view details (which might open another sub-GUI or chat message)
        if (clickType.isLeftClick()) {
            player.sendMessage(ChatColor.YELLOW + "You clicked auction ID: " + auctionId + ". Use /ah bid " + auctionId + " <amount> to place a bid.");
            player.closeInventory();
        } else if (clickType.isRightClick()) {
            // Optionally show more details or a confirmation GUI
            player.sendMessage(ChatColor.YELLOW + "Right-clicked auction ID: " + auctionId + ". More details could be shown here.");
        }
        return true;
    }

    private CompletableFuture<List<AuctionItem>> getAllActiveAuctions() {
        return databaseManager.executeQuery("SELECT * FROM auctions WHERE status = 'LISTED' ORDER BY end_time ASC", rs -> {
            List<AuctionItem> auctionItems = new ArrayList<>();
            while (rs.next()) {
                auctionItems.add(createAuctionItemFromResultSet(rs));
            }
            return auctionItems;
        });
    }

    public int collectPlayerItems(Player player) {
        final int[] collected = {0};
        databaseManager.executeQuery("SELECT id, item_data FROM escrow WHERE player_uuid = ? AND type = 'ITEM'", rs -> {
            while (rs.next()) {
                long escrowId = rs.getLong("id");
                ItemStack item = ItemSerializer.base64ToItemStack(rs.getString("item_data"));
                if (item != null) {
                    CompletableFuture.runAsync(() -> {
                        HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(item);
                        if (remaining.isEmpty()) {
                            databaseManager.executeUpdate("DELETE FROM escrow WHERE id = ?", escrowId).join();
                            collected[0]++;
                        } else {
                            // If inventory is full, item remains in escrow
                            player.sendMessage(ChatColor.RED + "Your inventory is full. Item (ID: " + escrowId + ") remains in escrow.");
                        }
                    });
                }
            }
            return collected[0];
        }, player.getUniqueId().toString()).join();
        return collected[0];
    }

    public double collectPlayerMoney(Player player) {
        final double[] collectedAmount = {0.0};
        databaseManager.executeQuery("SELECT id, amount FROM escrow WHERE player_uuid = ? AND type = 'MONEY'", rs -> {
            while (rs.next()) {
                long escrowId = rs.getLong("id");
                double amount = rs.getDouble("amount");
                if (economy.depositPlayer(player, amount).transactionSuccess()) {
                    databaseManager.executeUpdate("DELETE FROM escrow WHERE id = ?", escrowId).join();
                    collectedAmount[0] += amount;
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to deposit money to your account. Please contact an admin.");
                }
            }
            return collectedAmount[0];
        }, player.getUniqueId().toString()).join();
        return collectedAmount[0];
    }

    private void startAuctionEndTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            databaseManager.executeQuery("SELECT * FROM auctions WHERE end_time <= CURRENT_TIMESTAMP AND status = 'LISTED'", rs -> {
                List<AuctionItem> endedAuctions = new ArrayList<>();
                while (rs.next()) {
                    endedAuctions.add(createAuctionItemFromResultSet(rs));
                }
                return endedAuctions;
            }).thenAccept(endedAuctions -> {
                for (AuctionItem auction : endedAuctions) {
                    handleAuctionEnd(auction);
                }
            });
        }, 20L * 60, 20L * 60); // Run every minute
    }

    private void handleAuctionEnd(AuctionItem auction) {
        plugin.getLogger().log(Level.INFO, "Handling end for auction ID: " + auction.getId());
        // Mark as ended first to prevent re-processing
        databaseManager.executeUpdate("UPDATE auctions SET status = 'ENDED' WHERE id = ?", auction.getId()).join();

        if (auction.getHighestBidderUuid() != null && auction.getCurrentBid() > auction.getStartPrice()) {
            // Item sold
            // Release item to highest bidder's escrow
            escrowManager.addItemToEscrow(auction.getHighestBidderUuid(), ItemSerializer.base64ToItemStack(auction.getItemData()));
            // Release money to seller's escrow
            escrowManager.addMoneyToEscrow(auction.getSellerUuid(), auction.getCurrentBid(), "SALE");

            OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());
            OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getHighestBidderUuid());

            if (winner.isOnline()) {
                winner.getPlayer().sendMessage(ChatColor.GREEN + "You won auction ID " + auction.getId() + "! Collect your item with /ah collect.");
            }
            if (seller.isOnline()) {
                seller.getPlayer().sendMessage(ChatColor.GREEN + "Your item (ID: " + auction.getId() + ") was sold for " + economy.format(auction.getCurrentBid()) + ". Collect your money with /ah collect.");
            }
            Bukkit.broadcastMessage(ChatColor.GOLD + "Auction ID " + auction.getId() + " has ended! " + ChatColor.YELLOW + winner.getName() + ChatColor.GOLD + " won the " +
                    (ItemSerializer.base64ToItemStack(auction.getItemData()) != null ? ItemSerializer.base64ToItemStack(auction.getItemData()).getType().name() : "item") + ChatColor.GOLD + " for " + economy.format(auction.getCurrentBid()) + "!");
        } else {
            // No bids or bids not high enough, return item to seller
            escrowManager.addItemToEscrow(auction.getSellerUuid(), ItemSerializer.base64ToItemStack(auction.getItemData()));
            
            // Refund highest bidder if any and bid was valid but didn't meet reserve/initial price
            if (auction.getHighestBidderUuid() != null && auction.getCurrentBid() > 0) {
                escrowManager.releaseMoneyFromEscrow(auction.getHighestBidderUuid(), auction.getCurrentBid(), "BID_REFUND");
                OfflinePlayer previousBidder = Bukkit.getOfflinePlayer(auction.getHighestBidderUuid());
                if (previousBidder.isOnline()) {
                    previousBidder.getPlayer().sendMessage(ChatColor.YELLOW + "Your bid on auction ID: " + auction.getId() + " was refunded as the item didn't sell.");
                }
            }

            OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());
            if (seller.isOnline()) {
                seller.getPlayer().sendMessage(ChatColor.YELLOW + "Your item (ID: " + auction.getId() + ") did not sell. Collect it with /ah collect.");
            }
            Bukkit.broadcastMessage(ChatColor.YELLOW + "Auction ID " + auction.getId() + " has ended without a sale. Item returned to seller.");
        }
    }

    private AuctionItem createAuctionItemFromResultSet(ResultSet rs) throws SQLException {
        return new AuctionItem(
                rs.getLong("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("item_data"),
                rs.getDouble("start_price"),
                rs.getDouble("current_bid"),
                rs.getString("highest_bidder_uuid") != null ? UUID.fromString(rs.getString("highest_bidder_uuid")) : null,
                rs.getTimestamp("list_time").toLocalDateTime(),
                rs.getTimestamp("end_time").toLocalDateTime(),
                rs.getString("status")
        );
    }
}