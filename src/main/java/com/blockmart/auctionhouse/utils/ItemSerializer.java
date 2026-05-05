package com.blockmart.auctionhouse.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64converter.Base64; // Use Bukkit's internal Base64 if available, or shade one

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;

import com.blockmart.auctionhouse.AuctionHouse;

public class ItemSerializer {

    public static String itemStackToBase64(ItemStack item) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64.encodeBytes(outputStream.toByteArray());
        } catch (IOException e) {
            AuctionHouse.getInstance().getLogger().log(Level.SEVERE, "Unable to serialize item stack to Base64.", e);
            return null;
        }
    }

    public static ItemStack base64ToItemStack(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.decode(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (IOException | ClassNotFoundException e) {
            AuctionHouse.getInstance().getLogger().log(Level.SEVERE, "Unable to deserialize item stack from Base64.", e);
            return null;
        }
    }
}