package com.exit.gamble.lottery.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class LotteryHolder implements InventoryHolder {

    private final UUID playerId;
    private Inventory inventory;

    public LotteryHolder(Player player) {
        this.playerId = player.getUniqueId();
    }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public UUID playerId() { return playerId; }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("LotteryHolder.inventory not initialized");
        return inventory;
    }
}
