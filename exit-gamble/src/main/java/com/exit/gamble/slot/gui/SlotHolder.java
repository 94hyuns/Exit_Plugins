package com.exit.gamble.slot.gui;

import com.exit.gamble.slot.symbol.SlotSymbol;
import com.exit.gamble.slot.world.SlotMachine;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SlotHolder implements InventoryHolder {

    private final UUID playerId;
    private final SlotMachine machine;
    private Inventory inventory;
    private long currentBet;
    private boolean spinning;
    private SlotSymbol[] finalReels;
    private boolean testMode;

    public SlotHolder(Player player, long defaultBet, SlotMachine machine) {
        this.playerId = player.getUniqueId();
        this.machine = machine;
        this.currentBet = defaultBet;
        this.spinning = false;
        this.testMode = false;
    }

    public SlotMachine machine() { return machine; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public UUID playerId() { return playerId; }
    public long currentBet() { return currentBet; }
    public void setCurrentBet(long bet) { this.currentBet = bet; }
    public boolean isSpinning() { return spinning; }
    public void setSpinning(boolean spinning) { this.spinning = spinning; }
    public SlotSymbol[] finalReels() { return finalReels; }
    public void setFinalReels(SlotSymbol[] reels) { this.finalReels = reels; }
    public boolean isTestMode() { return testMode; }
    public void setTestMode(boolean testMode) { this.testMode = testMode; }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("SlotHolder.inventory not initialized");
        }
        return inventory;
    }

    @SuppressWarnings("unused")
    public static InventoryType type() { return InventoryType.CHEST; }
}
