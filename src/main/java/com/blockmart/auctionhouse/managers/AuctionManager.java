package com.blockmart.auctionhouse.managers;

import com.blockmart.auctionhouse.AuctionHousePlugin;
import com.blockmart.auctionhouse.models.AuctionItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AuctionManager {

    private final AuctionHousePlugin plugin;
    private final DatabaseManager databaseManager;
    private Economy economy;

    public AuctionManager(AuctionHousePlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        if (!setupEconomy()) {
            plugin.getLogger().severe("Vault not found or no economy provider! AuctionHouse will not function.");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public CompletableFuture<Void> listItemForAuction(Player player, ItemStack item, double price, long durationMinutes) {
        return CompletableFuture.supplyAsync(() -> {
            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "You must hold an item to sell it at auction.");
                return false;
            }
            if (price <= 0) {
                player.sendMessage(ChatColor.RED + "Price must be greater than zero.");
                return false;
            }
            if (durationMinutes <= 0) {
                player.sendMessage(ChatColor.RED + "Auction duration must be greater than zero.");
                return false;
            }

            PlayerInventory inventory = player.getInventory();
            if (!inventory.containsAtLeast(item, item.getAmount())) {
                player.sendMessage(ChatColor.RED + "You don't have this item in your inventory.");
                return false;
            }

            long startTime = System.currentTimeMillis();
            long endTime = startTime + TimeUnit.MINUTES.toMillis(durationMinutes);

            AuctionItem auctionItem = new AuctionItem(
                    0, // ID will be set by DB
                    player.getName(),
                    player.getUniqueId(),
                    item.clone(), // Clone to prevent modifications after listing
                    price,
                    price,
                    null,
                    null,
                    startTime,
                    endTime,
                    AuctionItem.AuctionStatus.LISTED
            );

            // Remove item from player's inventory and put into escrow
            Bukkit.getScheduler().runTask(plugin, () -> {
                inventory.removeItem(item);
                player.sendMessage(ChatColor.YELLOW + "You have listed " + item.getAmount() + "x " + item.getType().name() + " for auction.");
            });
            return auctionItem;
        }).thenCompose(obj -> {
            if (obj instanceof AuctionItem) {
                AuctionItem auctionItem = (AuctionItem) obj;
                return databaseManager.saveAuctionItem(auctionItem).thenApply(v -> true);
            } else {
                return CompletableFuture.completedFuture(false);
            }
        }).thenAccept(success -> {
            if (!success) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                     player.sendMessage(ChatColor.RED + "Failed to list item for auction. Please try again.");
                     player.getInventory().addItem(item); // Return item if DB fails
                });
            }
        }).exceptionally(e -> {
            plugin.getLogger().log(Level.SEVERE, "Error listing item for auction: " + e.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + "An internal error occurred while listing your item.");
                player.getInventory().addItem(item); // Return item if exception occurs
            });
            return null;
        });
    }


    public CompletableFuture<List<AuctionItem>> getActiveAuctions() {
        return databaseManager.getListedAuctions();
    }

    public void closeExpiredAuctions() {
        databaseManager.getExpiredAuctions().thenAccept(expiredAuctions -> {
            for (AuctionItem auction : expiredAuctions) {
                if (auction.getCurrentBid() > auction.getStartingPrice() && auction.getHighestBidderUuid() != null) {
                    // Item sold
                    UUID winnerUuid = auction.getHighestBidderUuid();
                    String winnerName = auction.getHighestBidderName();
                    double finalPrice = auction.getCurrentBid();
                    UUID sellerUuid = auction.getSellerUuid();
                    String sellerName = auction.getSellerName();
                    ItemStack itemSold = auction.getItemStack();

                    databaseManager.depositEscrowItem(winnerUuid.toString(), itemSold).thenAccept(v -> {
                        plugin.getLogger().info("Auction " + auction.getId() + ": item for " + winnerName + " escrowed.");
                        databaseManager.withdrawEscrowMoney(winnerUuid.toString()).thenAccept(moneyCollected -> {
                            economy.depositPlayer(Bukkit.getOfflinePlayer(sellerUuid), finalPrice);
                            economy.depositPlayer(Bukkit.getOfflinePlayer(winnerUuid), moneyCollected - finalPrice); // Return excess bid
                            databaseManager.updateAuctionStatus(auction.getId(), AuctionItem.AuctionStatus.SOLD);

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player winnerPlayer = Bukkit.getPlayer(winnerUuid);
                                if (winnerPlayer != null) {
                                    winnerPlayer.sendMessage(ChatColor.GREEN + "You won auction #" + auction.getId() + " for " + economy.format(finalPrice) + "! Collect your item with /auction collect.");
                                    if (moneyCollected - finalPrice > 0) {
                                        winnerPlayer.sendMessage(ChatColor.GREEN + "" + economy.format(moneyCollected - finalPrice) + " has been returned to your balance.");
                                    }
                                }
                                Player sellerPlayer = Bukkit.getPlayer(sellerUuid);
                                if (sellerPlayer != null) {
                                    sellerPlayer.sendMessage(ChatColor.GREEN + "Your item in auction #" + auction.getId() + " was sold to " + winnerName + " for " + economy.format(finalPrice) + "! Your money has been deposited.");
                                }
                            });
                        }).exceptionally(ex -> {
                            plugin.getLogger().log(Level.SEVERE, "Error processing money for auction " + auction.getId() + ": " + ex.getMessage());
                            return null;
                        });
                    }).exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE, "Error escrowing item for auction " + auction.getId() + ": " + ex.getMessage());
                        return null;
                    });
                } else {
                    // No bids or highest bid not met, item returns to seller
                    databaseManager.depositEscrowItem(auction.getSellerUuid().toString(), auction.getItemStack()).thenAccept(v -> {
                        plugin.getLogger().info("Auction " + auction.getId() + ": item for seller " + auction.getSellerName() + " escrowed (no sale).");
                        // If there was a high bidder who paid, refund their money
                        if (auction.getHighestBidderUuid() != null && auction.getCurrentBid() > auction.getStartingPrice()) {
                            UUID highestBidder = auction.getHighestBidderUuid();
                            double bidAmount = auction.getCurrentBid();
                            economy.depositPlayer(Bukkit.getOfflinePlayer(highestBidder), bidAmount);
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player bidderPlayer = Bukkit.getPlayer(highestBidder);
                                if (bidderPlayer != null) {
                                    bidderPlayer.sendMessage(ChatColor.RED + "Your bid of " + economy.format(bidAmount) + " for auction #" + auction.getId() + " was refunded as the item didn't sell.");
                                }
                            });
                        }
                        databaseManager.updateAuctionStatus(auction.getId(), AuctionItem.AuctionStatus.EXPIRED);

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player sellerPlayer = Bukkit.getPlayer(auction.getSellerUuid());
                            if (sellerPlayer != null) {
                                sellerPlayer.sendMessage(ChatColor.YELLOW + "Your item in auction #" + auction.getId() + " did not sell and has been returned to your collection.");
                            }
                        });
                    }).exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE, "Error escrowing item for failed auction " + auction.getId() + ": " + ex.getMessage());
                        return null;
                    });
                }
            }
        }).exceptionally(e -> {
            plugin.getLogger().log(Level.SEVERE, "Error closing expired auctions: " + e.getMessage());
            return null;
        });
    }

    public CompletableFuture<Boolean> placeBid(Player player, int auctionId, double bidAmount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<AuctionItem> auctions = databaseManager.getListedAuctions().join(); // Blocking to get current auctions
                Optional<AuctionItem> optionalAuction = auctions.stream().filter(a -> a.getId() == auctionId).findFirst();

                if (optionalAuction.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Auction with ID " + auctionId + " not found or has ended.");
                    return false;
                }

                AuctionItem auction = optionalAuction.get();

                if (auction.getSellerUuid().equals(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "You cannot bid on your own auction.");
                    return false;
                }

                if (System.currentTimeMillis() > auction.getEndTime()) {
                    player.sendMessage(ChatColor.RED + "This auction has already ended.");
                    databaseManager.updateAuctionStatus(auction.getId(), AuctionItem.AuctionStatus.EXPIRED); // Ensure it's marked expired
                    return false;
                }

                // Minimum bid increment logic
                double minBid = auction.getCurrentBid() + plugin.getConfig().getDouble("auction.min-bid-increment", 1.0);
                if (bidAmount < minBid) {
                    player.sendMessage(ChatColor.RED + "Your bid must be at least " + economy.format(minBid) + ".");
                    return false;
                }

                if (!economy.has(player, bidAmount)) {
                    player.sendMessage(ChatColor.RED + "You do not have enough money to place this bid.");
                    return false;
                }

                // Process previous highest bidder refund
                if (auction.getHighestBidderUuid() != null) {
                    UUID previousBidder = auction.getHighestBidderUuid();
                    double refundedAmount = auction.getCurrentBid();
                    if (!previousBidder.equals(player.getUniqueId())) { // Don't refund yourself if you're outbidding yourself
                         economy.depositPlayer(Bukkit.getOfflinePlayer(previousBidder), refundedAmount);
                         Player previousBidderOnline = Bukkit.getPlayer(previousBidder);
                         if (previousBidderOnline != null) {
                             previousBidderOnline.sendMessage(ChatColor.YELLOW + "You have been outbid on auction #" + auction.getId() + "! " + economy.format(refundedAmount) + " has been refunded to your account.");
                         }
                    }
                }

                // Take money from current bidder
                economy.withdrawPlayer(player, bidAmount);

                auction.setCurrentBid(bidAmount);
                auction.setHighestBidderUuid(player.getUniqueId());
                auction.setHighestBidderName(player.getName());

                databaseManager.updateAuctionItem(auction);

                player.sendMessage(ChatColor.GREEN + "You have successfully bid " + economy.format(bidAmount) + " on auction #" + auction.getId() + ".");
                // Notify seller
                Player seller = Bukkit.getPlayer(auction.getSellerUuid());
                if (seller != null) {
                    seller.sendMessage(ChatColor.YELLOW + player.getName() + ChatColor.LIGHT_PURPLE + " just bid " + economy.format(bidAmount) + " on your auction #" + auction.getId() + "!");
                }
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error placing bid: " + e.getMessage());
                player.sendMessage(ChatColor.RED + "An internal error occurred while placing your bid.");
                return false;
            }
        });
    }

    public CompletableFuture<List<ItemStack>> collectItems(Player player) {
        return databaseManager.withdrawEscrowItems(player.getUniqueId().toString()).thenApply(items -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (items.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "You have no items to collect from the auction house.");
                    return;
                }
                for (ItemStack item : items) {
                    if (player.getInventory().firstEmpty() == -1) {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        player.sendMessage(ChatColor.YELLOW + "Your inventory was full, some items were dropped at your feet.");
                    } else {
                        player.getInventory().addItem(item);
                    }
                }
                player.sendMessage(ChatColor.GREEN + "You have collected your items from the auction house.");
            });
            return items;
        });
    }

    public CompletableFuture<Void> depositMoney(Player player, double amount) {
        return CompletableFuture.runAsync(() -> {
            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "You must deposit a positive amount of money.");
                return;
            }
            if (!economy.has(player, amount)) {
                player.sendMessage(ChatColor.RED + "You do not have " + economy.format(amount) + " to deposit.");
                return;
            }
            economy.withdrawPlayer(player, amount);
            databaseManager.depositEscrowMoney(player.getUniqueId().toString(), amount);
            player.sendMessage(ChatColor.GREEN + "You have deposited " + economy.format(amount) + " to your auction escrow.");
        });
    }

    public CompletableFuture<Double> withdrawMoney(Player player) {
        return databaseManager.withdrawEscrowMoney(player.getUniqueId().toString()).thenApply(amount -> {
            if (amount > 0) {
                economy.depositPlayer(player, amount);
                player.sendMessage(ChatColor.GREEN + "You have withdrawn " + economy.format(amount) + " from your auction escrow.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "You have no money in your auction escrow to withdraw.");
            }
            return amount;
        });
    }

    public CompletableFuture<Double> getPlayerEscrowBalance(Player player) {
        return databaseManager.getEscrowBalance(player.getUniqueId().toString()).thenApply(balance -> {
            player.sendMessage(ChatColor.YELLOW + "Your current escrow balance is: " + economy.format(balance));
            return balance;
        });
    }

}