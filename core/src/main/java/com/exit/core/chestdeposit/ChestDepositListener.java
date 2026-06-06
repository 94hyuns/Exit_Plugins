package com.exit.core.chestdeposit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ChestDepositListener implements Listener {

    private final JavaPlugin plugin;

    public ChestDepositListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Inventory original = event.getInventory();
        InventoryHolder holder = original.getHolder();
        if (holder instanceof ChestDepositHolder) return;
        if (!(holder instanceof Chest) && !(holder instanceof DoubleChest)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        ChestDepositHolder depositHolder = ChestDepositHolder.of(original);
        if (depositHolder == null) return;

        ItemStack[] snapshot = new ItemStack[depositHolder.getChestArea()];
        ItemStack[] originalContents = original.getContents();
        for (int i = 0; i < snapshot.length && i < originalContents.length; i++) {
            ItemStack it = originalContents[i];
            snapshot[i] = (it == null) ? null : it.clone();
        }

        Component title = resolveTitle(holder, depositHolder.isDouble());
        event.setCancelled(true);

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Hijack 슬롯에 기존 아이템이 있으면 플레이어에게 이전 (꽉 차면 발 밑 드롭) → 슬롯 영구 비움
            if (depositHolder.isHijack()) {
                int hs = depositHolder.getButtonSlot();
                ItemStack existing = snapshot[hs];
                if (existing != null && existing.getType() != Material.AIR) {
                    HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(existing.clone());
                    for (ItemStack drop : overflow.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    snapshot[hs] = null;
                    Inventory chestInv = depositHolder.getOriginalChestInv();
                    if (chestInv != null && hs < chestInv.getSize()) chestInv.setItem(hs, null);
                }
            }
            Inventory gui = Bukkit.createInventory(depositHolder, depositHolder.getGuiSize(), title);
            depositHolder.setInventory(gui);
            for (int i = 0; i < snapshot.length; i++) {
                if (snapshot[i] != null) gui.setItem(i, snapshot[i]);
            }
            decorateBottomRow(gui, depositHolder);
            player.openInventory(gui);
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ChestDepositHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int chestArea = holder.getChestArea();
        int buttonSlot = holder.getButtonSlot();
        Inventory clicked = event.getClickedInventory();
        int rawSlot = event.getRawSlot();

        boolean isTopClick = clicked == top;
        boolean inDecoration = !holder.isHijack() && isTopClick && rawSlot >= chestArea;
        boolean isButton = isTopClick && rawSlot == buttonSlot;

        if (inDecoration || isButton) {
            event.setCancelled(true);
            if (isButton) {
                int moved = depositMatching(player, top, holder);
                if (moved > 0) {
                    player.sendActionBar(Component.text(moved + "개 아이템 입금", NamedTextColor.GREEN));
                } else {
                    player.sendActionBar(Component.text("옮길 아이템 없음", NamedTextColor.GRAY));
                }
                player.updateInventory();
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof ChestDepositHolder holder)) return;
        writeBack(holder, top);
    }

    private int depositMatching(Player player, Inventory top, ChestDepositHolder holder) {
        int chestArea = holder.getChestArea();
        int buttonSlot = holder.getButtonSlot();
        boolean hijack = holder.isHijack();
        List<ItemStack> templates = new ArrayList<>();
        for (int i = 0; i < chestArea; i++) {
            if (hijack && i == buttonSlot) continue;
            ItemStack it = top.getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;
            boolean already = false;
            for (ItemStack t : templates) {
                if (t.isSimilar(it)) { already = true; break; }
            }
            if (!already) {
                ItemStack template = it.clone();
                template.setAmount(1);
                templates.add(template);
            }
        }
        if (templates.isEmpty()) return 0;

        PlayerInventory inv = player.getInventory();
        int totalMoved = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) continue;
            boolean match = false;
            for (ItemStack t : templates) {
                if (t.isSimilar(stack)) { match = true; break; }
            }
            if (!match) continue;

            int before = stack.getAmount();
            HashMap<Integer, ItemStack> leftover = top.addItem(stack.clone());
            int remaining = 0;
            for (ItemStack l : leftover.values()) remaining += l.getAmount();
            int moved = before - remaining;
            if (moved <= 0) continue;
            totalMoved += moved;
            if (remaining == 0) {
                inv.setItem(slot, null);
            } else {
                stack.setAmount(remaining);
                inv.setItem(slot, stack);
            }
        }
        return totalMoved;
    }

    private void writeBack(ChestDepositHolder holder, Inventory top) {
        Inventory chestInv = holder.getOriginalChestInv();
        if (chestInv == null) return;

        int chestArea = holder.getChestArea();
        int chestSize = chestInv.getSize();
        int limit = Math.min(chestArea, chestSize);

        for (int i = 0; i < limit; i++) {
            ItemStack item;
            if (holder.isHijack() && i == holder.getButtonSlot()) {
                item = holder.getHijackedOriginal();
            } else {
                item = top.getItem(i);
            }
            chestInv.setItem(i, item);
        }
    }

    private void decorateBottomRow(Inventory gui, ChestDepositHolder holder) {
        int chestArea = holder.getChestArea();
        int buttonSlot = holder.getButtonSlot();

        if (!holder.isHijack()) {
            ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta fillerMeta = filler.getItemMeta();
            if (fillerMeta != null) {
                fillerMeta.displayName(Component.text(" "));
                filler.setItemMeta(fillerMeta);
            }
            for (int i = chestArea; i < chestArea + 9; i++) {
                gui.setItem(i, filler);
            }
        }

        ItemStack button = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("동일 아이템 입금", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("인벤토리의 같은 종류 아이템을", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("이 상자로 한 번에 옮깁니다", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            button.setItemMeta(meta);
        }
        gui.setItem(buttonSlot, button);
    }

    private Component resolveTitle(InventoryHolder holder, boolean isDouble) {
        Component custom = null;
        if (holder instanceof Chest chest) {
            custom = chest.customName();
        } else if (holder instanceof DoubleChest dc) {
            InventoryHolder left = dc.getLeftSide();
            if (left instanceof Chest lc) custom = lc.customName();
        }
        if (custom != null) return custom;
        return Component.text(isDouble ? "큰 상자" : "상자");
    }
}
