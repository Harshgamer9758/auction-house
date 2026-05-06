package com.blockmart.auctionhouse.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public class NBTUtil {

    /**
     * Removes all PersistentDataContainer (NBT) tags from an ItemStack.
     * This is crucial for preventing other plugin's NBT data from breaking serialization
     * and to ensure only vanilla or specified data persists in storage.
     *
     * @param itemStack The ItemStack to clean.
     * @return A new ItemStack with NBT data removed, or the original if it has no item meta.
     */
    public static ItemStack removeNBT(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return itemStack;
        }
        ItemStack cleanItem = itemStack.clone();
        ItemMeta meta = cleanItem.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            for (org.bukkit.NamespacedKey key : container.getKeys()) {
                container.remove(key);
            }
            cleanItem.setItemMeta(meta);
        }
        return cleanItem;
    }
}