package com.blockmart.auctionhouse.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemSerializer {

    public static String getItemName(ItemStack item) {
        if (item == null) return "§cInvalid Item";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        } else {
            return item.getType().name().replace("_", " ").toLowerCase();
        }
    }
}
