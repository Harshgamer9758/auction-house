package com.blockmart.auctionhouse.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/**
 * Utility for filtering items based on NBT data.
 */
public class NBTUtils {

    /**
     * Checks if an ItemStack contains any NBT tags from a given set of blacklisted keys.
     * This helps prevent players from auctioning items with problematic or plugin-specific NBT.
     *
     * @param itemStack The item to check.
     * @param blacklistedKeys A set of NBT keys that are not allowed.
     * @return true if the itemStack contains any blacklisted NBT key, false otherwise.
     */
    public static boolean containsBlacklistedNBT(ItemStack itemStack, Set<String> blacklistedKeys) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        for (String key : blacklistedKeys) {
            // Check for existence without specific type, as type might vary or be unknown for blacklisted tags.
            // Using ItemNBTUtil here would require knowing the exact type, which is not ideal for general blacklisting.
            // Instead, we check the underlying keys.
            if (container.has(new NamespacedKey(ItemNBTUtil.class.getPackageName().replace("utils", ""), key), PersistentDataType.STRING)) { // Example, dynamically adjust package for NamespacedKey
                return true;
            }
        }
        // More generic check for keys from any namespace (might be too broad)
        for (NamespacedKey key : container.getKeys()) {
             if (blacklistedKeys.contains(key.getKey())) {
                 return true;
             }
        }
        return false;
    }
}