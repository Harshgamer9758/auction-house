package com.blockmart.auctionhouse.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import com.blockmart.auctionhouse.AuctionHouse;

public class ItemNBTUtil {

    private static final NamespacedKey AUCTION_KEY = new NamespacedKey(AuctionHouse.getInstance(), "auction_data");

    public static ItemStack setNBTTag(ItemStack item, String key, String value) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(AuctionHouse.getInstance(), key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    public static String getNBTTag(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(new NamespacedKey(AuctionHouse.getInstance(), key), PersistentDataType.STRING);
    }

    public static ItemStack stripNBT(ItemStack item) {
        // This method can be expanded to remove specific NBT data if necessary.
        // For now, it simply clones the item to ensure no external references
        // and serves as a placeholder for more complex NBT filtering if required.
        return item.clone();
    }
}