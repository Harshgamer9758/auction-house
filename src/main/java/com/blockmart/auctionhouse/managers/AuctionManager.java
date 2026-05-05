package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHouse;
import com.blockmart.auctionhouse.database.DatabaseManager;
import com.blockmart.auctionhouse.models.AuctionItem;
import com.blockmart.auctionhouse.utils.ItemNBTUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AuctionManager {

    private final AuctionHouse plugin;
    private final DatabaseManager databaseManager;
    private final Map<Integer, AuctionItem> activeAuctions;
    private final Map<UUID, List<AuctionItem>> playerEscrow;
    private ScheduledTask expiredAuctionChecker;

    public AuctionManager(AuctionHouse plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.activeAuctions = new ConcurrentHashMap<>();
        this.playerEscrow = new ConcurrentHashMap<>();
    }

    public void loadActiveAuctions() {
        databaseManager.loadActiveAuctions().thenAcceptAsync(auctions -> {
            auctions.forEach(auction -> {
                activeAuctions.put(auction.getId(), auction);
                startAuctionCountdown(auction);
            });
            plugin.getLogger().info("Loaded " + activeAuctions.size() + " active auctions.");
        }, Bukkit.getScheduler(). getCurrent().sync());
        scheduleExpiredAuctionCheck();
    }

    private void scheduleExpiredAuctionCheck() {
        if (expiredAuctionChecker != null) {
            expiredAuctionChecker.cancel();
        }
        expiredAuctionChecker = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndHandleExpiredAuctions, 20 * 60, 20 * 60); // Every minute
    }

    private class AuctionGUIHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void openAuctionGUI(Player player) {
        int guiSize = Math.min(54, ((activeAuctions.size() / 9) + 1) * 9);
        Inventory gui = Bukkit.createInventory(new AuctionGUIHolder(), guiSize, "§8Auction House (§e" + activeAuctions.size() + "§8)");

        List<AuctionItem> sortedAuctions = new ArrayList<>(activeAuctions.values());
        sortedAuctions.sort(Comparator.comparingLong(AuctionItem::getEndTime)); // Sort by end time

        for (int i = 0; i < sortedAuctions.size() && i < guiSize; i++) {
            AuctionItem auction = sortedAuctions.get(i);
            ItemStack displayItem = createDisplayItem(auction);
            ItemNBTUtil.setNBTTag(displayItem, "auction_id", String.valueOf(auction.getId()));
            gui.setItem(i, displayItem);
        }

        player.openInventory(gui);
    }

    public void handleGUIClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        String auctionIdStr = ItemNBTUtil.getNBTTag(clickedItem, "auction_id");
        if (auctionIdStr == null) {
            return;
        }

        int auctionId = Integer.parseInt(auctionIdStr);
        AuctionItem auction = activeAuctions.get(auctionId);

        if (auction == null) {
            player.sendMessage("§cThis auction is no longer active.");
            player.closeInventory();
            openAuctionGUI(player); // Refresh GUI
            return;
        }

        if (event.isRightClick()) { // Right-click to view details / place initial bid
            player.sendMessage("§a--- Auction Details (§bID: " + auction.getId() + "§a) ---");
            player.sendMessage("§7Item: §f" + auction.getItemStack().getItemMeta().getDisplayName());
            player.sendMessage("§7Seller: §f" + Bukkit.getOfflinePlayer(auction.getSellerUUID()).getName());
            player.sendMessage("§7Current Bid: §e$" + String.format("%.2f", auction.getCurrentBid()));
            if (auction.getHighestBidderUUID() != null) {
                player.sendMessage("§7Highest Bidder: §f" + Bukkit.getOfflinePlayer(auction.getHighestBidderUUID()).getName());
            }
            player.sendMessage("§7Time Left: §a" + formatTimeLeft(auction.getEndTime()));
            player.sendMessage("§eUse /ah bid " + auction.getId() + " <amount> to place a bid.");
        } else if (event.isLeftClick()) { // Left-click to quick bid minimum
            double currentBid = auction.getCurrentBid();
            double minimumBid = currentBid + 1.0; // Example: Minimum bid increase
            player.sendMessage("§eAttempting quick bid of §a$" + String.format("%.2f", minimumBid));
            placeBid(player, auctionId, minimumBid);
            player.closeInventory();
        }
    }

    public void createAuction(Player player, ItemStack itemStack, double price, long durationMinutes) {
        if (!player.getInventory().containsAtLeast(itemStack, itemStack.getAmount())) {
            player.sendMessage("§cYou don't have enough of that item to sell.");
            return;
        }

        player.getInventory().removeItem(itemStack);

        long endTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes);
        AuctionItem auction = new AuctionItem(0, player.getUniqueId(), itemStack, price, price, null, endTime, true);

        databaseManager.saveAuction(auction).thenAcceptAsync(aVoid -> {
            activeAuctions.put(auction.getId(), auction);
            startAuctionCountdown(auction);
            player.sendMessage("§aYour item " + itemStack.getItemMeta().getDisplayName() + " has been listed for auction!");
            plugin.getLogger().info(player.getName() + " created auction for item: " + itemStack.getType().name() + " at $" + price);
        }, Bukkit.getScheduler().getCurrent().sync());
    }

    public void placeBid(Player player, int auctionId, double amount) {
        AuctionItem auction = activeAuctions.get(auctionId);
        if (auction == null || !auction.isActive() || auction.getEndTime() < System.currentTimeMillis()) {
            player.sendMessage("§cThis auction is no longer active or has expired.");
            return;
        }

        if (player.getUniqueId().equals(auction.getSellerUUID())) {
            player.sendMessage("§cYou cannot bid on your own auction.");
            return;
        }

        if (amount <= auction.getCurrentBid()) {
            player.sendMessage("§cYour bid must be higher than the current bid of §e$" + String.format("%.2f", auction.getCurrentBid()) + "§c.");
            return;
        }

        // Simulate economy check and deduction. Replace with actual economy plugin integration (e.g., Vault)
        boolean hasMoney = true; // Placeholder
        if (hasMoney) { // Example: If player has enough money
            if (auction.getHighestBidderUUID() != null) {
                // Refund previous highest bidder
                UUID previousBidder = auction.getHighestBidderUUID();
                double previousBidAmount = auction.getCurrentBid();
                depositToPlayerEscrow(previousBidder, previousBidAmount);
                OfflinePlayer offlinePrevBidder = Bukkit.getOfflinePlayer(previousBidder);
                if (offlinePrevBidder.isOnline()) {
                    ((Player) offlinePrevBidder).sendMessage("§aYou have been outbid on auction ID " + auction.getId() + ". Your §e$" + String.format("%.2f", previousBidAmount) + "§a has been returned to your escrow.");
                }
            }

            // Deduct new bid amount from current bidder (escrow)
            // This would interact with an economy plugin
            // plugin.getEconomy().withdrawPlayer(player, amount);

            auction.setCurrentBid(amount);
            auction.setHighestBidderUUID(player.getUniqueId());

            databaseManager.saveAuction(auction).thenAcceptAsync(aVoid -> {
                Player seller = Bukkit.getPlayer(auction.getSellerUUID());
                if (seller != null) {
                    seller.sendMessage("§b" + player.getName() + " has bid §e$" + String.format("%.2f", amount) + "§b on your item (ID: " + auction.getId() + ").");
                }
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (onlinePlayer.getOpenInventory().getHolder() instanceof AuctionGUIHolder) {
                        openAuctionGUI(onlinePlayer);
                    }
                }
                player.sendMessage("§aYou have successfully bid §e$" + String.format("%.2f", amount) + "§a on item ID " + auction.getId() + ".");
                plugin.getLogger().info(player.getName() + " bid $" + amount + " on auction ID " + auction.getId());
            }, Bukkit.getScheduler().getCurrent().sync());

        } else {
            player.sendMessage("§cYou do not have enough money to place this bid."); // Replace with actual economy message
        }
    }

    public void collectItems(Player player) {
        List<AuctionItem> collectedItems = new ArrayList<>();
        Set<Integer> itemIdsToRemove = new HashSet<>();

        databaseManager.loadPlayerAuctions(player.getUniqueId()).thenAcceptAsync(playerAuctions -> {
            for (AuctionItem auction : playerAuctions) {
                if (auction.isActive()) continue;
                // Seller side - item sold, collect money
                if (auction.getSellerUUID().equals(player.getUniqueId()) && auction.getHighestBidderUUID() != null) {
                    // Transfer money to seller (replace with economy plugin)
                    // plugin.getEconomy().depositPlayer(player, auction.getCurrentBid());
                    player.sendMessage("§aCollected §e$" + String.format("%.2f", auction.getCurrentBid()) + "§a for item ID " + auction.getId() + ".");
                    itemIdsToRemove.add(auction.getId());
                }
                // Bidder side - won item, collect item
                else if (auction.getHighestBidderUUID() != null && auction.getHighestBidderUUID().equals(player.getUniqueId()) && !auction.isActive()) {
                    if (player.getInventory().addItem(auction.getItemStack()).isEmpty()) {
                        player.sendMessage("§aCollected your won item (ID: " + auction.getId() + ")");
                        itemIdsToRemove.add(auction.getId());
                    } else {
                        player.sendMessage("§cYour inventory is full! Make space to collect item ID " + auction.getId() + ".");
                    }
                }
                // Seller side - item expired, collect item
                else if (auction.getSellerUUID().equals(player.getUniqueId()) && auction.getHighestBidderUUID() == null && !auction.isActive()) {
                    if (player.getInventory().addItem(auction.getItemStack()).isEmpty()) {
                        player.sendMessage("§aCollected your unsold item (ID: " + auction.getId() + ")");
                        itemIdsToRemove.add(auction.getId());
                    } else {
                        player.sendMessage("§cYour inventory is full! Make space to collect item ID " + auction.getId() + ".");
                    }
                }
            }

            // Handle escrowed funds
            if (playerEscrow.containsKey(player.getUniqueId())) {
                for (AuctionItem escrowedFund : playerEscrow.get(player.getUniqueId())) {
                    // This would be a refund, so the 'item' is money
                    // plugin.getEconomy().depositPlayer(player, escrowedFund.getCurrentBid());
                    player.sendMessage("§aCollected §e$" + String.format("%.2f", escrowedFund.getCurrentBid()) + "§a from escrow.");
                }
                playerEscrow.remove(player.getUniqueId());
            }

            // Mark collected items as inactive in the database or remove them (depends on persistence strategy)
            itemIdsToRemove.forEach(id -> { 
                AuctionItem item = activeAuctions.get(id);
                if(item != null) {
                    item.setActive(false);
                    databaseManager.saveAuction(item);
                    activeAuctions.remove(id);
                }
            });
            player.sendMessage("§aCollection complete.");
        }, Bukkit.getScheduler().getCurrent().sync());
    }

    // Method to add bid money back to a player's escrow
    private void depositToPlayerEscrow(UUID playerUUID, double amount) {
        // This is a simplified escrow. In a real scenario, you'd store pending funds.
        // For now, we simulate by adding a 'money item' to playerEscrow.
        // A more robust system would involve a separate 'escrowed_funds' table in DB.
        ItemStack moneyItem = new ItemStack(Material.GOLD_INGOT); // Represents money
        ItemMeta meta = moneyItem.getItemMeta();
        meta.setDisplayName("§eEscrowed Funds: §a$" + String.format("%.2f", amount));
        moneyItem.setItemMeta(meta);

        AuctionItem escrowFund = new AuctionItem(
                0, // No ID for temporary escrow item
                playerUUID,
                moneyItem, // The itemStack is just a representation
                amount, // The 'start price' here means the amount escrowed
                amount, // The 'current bid' here means the amount escrowed
                playerUUID, // Highest bidder is the one whose money is escrowed
                0,
                false // Not an active auction, just escrowed funds
        );
        playerEscrow.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(escrowFund);
    }

    private void startAuctionCountdown(AuctionItem auction) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            AuctionItem expiredAuction = activeAuctions.get(auction.getId());
            if (expiredAuction != null && expiredAuction.isActive() && expiredAuction.getEndTime() <= System.currentTimeMillis()) {
                handleAuctionEnd(expiredAuction);
            }
        }, (auction.getEndTime() - System.currentTimeMillis()) / 50 + 1); // Delay in ticks
    }

    private void checkAndHandleExpiredAuctions() {
        List<AuctionItem> expired = new ArrayList<>();
        activeAuctions.values().forEach(auction -> {
            if (auction.isActive() && auction.getEndTime() <= System.currentTimeMillis()) {
                expired.add(auction);
            }
        });
        expired.forEach(this::handleAuctionEnd);
    }

    private void handleAuctionEnd(AuctionItem auction) {
        if (!auction.isActive()) return; // Already handled

        auction.setActive(false);
        activeAuctions.remove(auction.getId());
        databaseManager.saveAuction(auction).thenRunAsync(() -> {
            if (auction.getHighestBidderUUID() != null) {
                // Auction sold to highest bidder
                OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getHighestBidderUUID());
                OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUUID());

                Bukkit.getLogger().info("Auction ID " + auction.getId() + " sold to " + winner.getName() + " for $" + auction.getCurrentBid());
                if (winner.isOnline()) {
                    ((Player) winner).sendMessage("§aCongratulations! You won auction ID " + auction.getId() + " for §e$" + String.format("%.2f", auction.getCurrentBid()) + "§a. Use /ah collect to get your item.");
                }
                if (seller.isOnline()) {
                    ((Player) seller).sendMessage("§aYour item (ID: " + auction.getId() + ") was sold for §e$" + String.format("%.2f", auction.getCurrentBid()) + "§a. Use /ah collect to get your earnings.");
                }
            } else {
                // Auction expired without bids or no winner
                OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUUID());
                Bukkit.getLogger().info("Auction ID " + auction.getId() + " expired without bids.");
                if (seller.isOnline()) {
                    ((Player) seller).sendMessage("§cYour item (ID: " + auction.getId() + ") expired without bids. Use /ah collect to retrieve it.");
                }
            }
        }, Bukkit.getScheduler().getCurrent().sync());
    }

    private String formatTimeLeft(long endTime) {
        long timeLeftMillis = endTime - System.currentTimeMillis();
        if (timeLeftMillis <= 0) {
            return "Expired";
        }

        long days = TimeUnit.MILLISECONDS.toDays(timeLeftMillis);
        timeLeftMillis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(timeLeftMillis);
        timeLeftMillis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeftMillis);
        timeLeftMillis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeftMillis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    private ItemStack createDisplayItem(AuctionItem auction) {
        ItemStack item = auction.getItemStack().clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

        lore.add("§7---------------------");
        lore.add("§7ID: §b" + auction.getId());
        lore.add("§7Seller: §f" + Bukkit.getOfflinePlayer(auction.getSellerUUID()).getName());
        lore.add("§7Current Bid: §e$" + String.format("%.2f", auction.getCurrentBid()));
        if (auction.getHighestBidderUUID() != null) {
            lore.add("§7Highest Bidder: §f" + Bukkit.getOfflinePlayer(auction.getHighestBidderUUID()).getName());
        } else {
            lore.add("§7No bids yet.");
        }
        lore.add("§7Time Left: §a" + formatTimeLeft(auction.getEndTime()));
        lore.add("§7---------------------");
        lore.add("§aLeft-Click to quick bid");
        lore.add("§aRight-Click for details");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}