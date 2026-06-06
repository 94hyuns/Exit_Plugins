package com.exit.core.chestdeposit;

import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ChestDepositHolder implements InventoryHolder {

    private final Inventory originalChestInv;
    private final int chestArea;
    private final int guiSize;
    private final int buttonSlot;
    private final boolean hijack;
    private final boolean isDouble;
    private ItemStack hijackedOriginal;
    private Inventory inventory;

    private ChestDepositHolder(@NotNull Inventory original, int chestArea, int guiSize,
                               int buttonSlot, boolean hijack, boolean isDouble) {
        this.originalChestInv = original;
        this.chestArea = chestArea;
        this.guiSize = guiSize;
        this.buttonSlot = buttonSlot;
        this.hijack = hijack;
        this.isDouble = isDouble;
    }

    public static ChestDepositHolder of(@NotNull Inventory original) {
        InventoryHolder holder = original.getHolder();
        if (holder instanceof DoubleChest) {
            // 54칸 한계라 더블체스트는 마지막 슬롯을 hijack
            return new ChestDepositHolder(original, 54, 54, 53, true, true);
        } else if (holder instanceof Chest) {
            // 27칸 → 36칸으로 한 줄 추가, 버튼은 추가된 줄 가운데
            return new ChestDepositHolder(original, 27, 36, 31, false, false);
        }
        return null;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Inventory getOriginalChestInv() {
        return originalChestInv;
    }

    public int getChestArea() {
        return chestArea;
    }

    public int getGuiSize() {
        return guiSize;
    }

    public int getButtonSlot() {
        return buttonSlot;
    }

    public boolean isHijack() {
        return hijack;
    }

    public boolean isDouble() {
        return isDouble;
    }

    public ItemStack getHijackedOriginal() {
        return hijackedOriginal;
    }

    public void setHijackedOriginal(ItemStack item) {
        this.hijackedOriginal = item;
    }
}
