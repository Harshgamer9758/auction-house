package com.blockmart.auctionhouse.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import com.blockmart.auctionhouse.AuctionHouse;

public class ItemNBTUtil {

    private static NamespacedKey getKey(String tag) {
        return new NamespacedKey(AuctionHouse.getInstance(), tag);
    }

    public static ItemStack setNBTTag(ItemStack item, String key, String value) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(getKey(key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    public static String getNBTTag(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(getKey(key), PersistentDataType.STRING);
    }

    public static boolean containsNBTTag(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(getKey(key), PersistentDataType.STRING);
    }

    public static ItemStack removeNBTTag(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(getKey(key));
        item.setItemMeta(meta);
        return item;
    }
}