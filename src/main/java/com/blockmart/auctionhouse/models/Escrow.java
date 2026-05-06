package com.blockmart.auctionhouse.models;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Escrow {
    private int id;
    private UUID ownerUUID;
    private ItemStack itemStack;
    private double amount;

    public Escrow(int id, UUID ownerUUID, ItemStack itemStack, double amount) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.itemStack = itemStack;
        this.amount = amount;
    }

    public int getId() {
        return id;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public double getAmount() {
        return amount;
    }
}