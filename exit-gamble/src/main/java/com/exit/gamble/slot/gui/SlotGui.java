package com.exit.gamble.slot.gui;

import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.gamble.slot.config.SlotConfig;
import com.exit.gamble.slot.symbol.SlotSymbol;
import com.exit.gamble.slot.world.SlotMachine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class SlotGui {

    public static final int REEL_1_SLOT = 11;
    public static final int REEL_2_SLOT = 13;
    public static final int REEL_3_SLOT = 15;
    public static final int STATUS_SLOT = 4;
    public static final int INFO_SLOT = 5;
    public static final int CLOSE_SLOT = 8;
    public static final int SPIN_SLOT = 22;

    private static final int[] BET_SLOTS = { 0, 1, 2 };
    private static final String[] BET_ACTIONS = {
            SlotActionKeys.ACTION_BET_0,
            SlotActionKeys.ACTION_BET_1,
            SlotActionKeys.ACTION_BET_2
    };

    private final SlotConfig config;
    private final SlotActionKeys keys;

    public SlotGui(SlotConfig config, SlotActionKeys keys) {
        this.config = config;
        this.keys = keys;
    }

    public void open(Player player) {
        open(player, null);
    }

    public void open(Player player, SlotMachine machine) {
        long defaultBet = config.bets().get(0);
        SlotHolder holder = new SlotHolder(player, defaultBet, machine);
        String title = machine == null ? "슬롯머신" : "슬롯머신 (" + machine.id() + ")";
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text(title, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        fillBackground(inv);
        renderBetButtons(inv, holder);
        renderReelsIdle(inv);
        renderStatus(inv, holder, player, null);
        renderInfoButton(inv);
        renderSpinButton(inv, false);
        renderCloseButton(inv);

        player.openInventory(inv);
    }

    public void renderInfoButton(Inventory inv) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("배당표", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<SlotSymbol> symbols = config.symbols();
        int totalWeight = 0;
        for (SlotSymbol s : symbols) totalWeight += s.weight();

        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("3개 일치 시 베팅 × 배수 지급", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" ", NamedTextColor.GRAY));

        for (SlotSymbol sym : symbols) {
            boolean isJackpot = sym.id().equals(config.jackpotSymbolId());
            NamedTextColor color = isJackpot ? NamedTextColor.GOLD : NamedTextColor.AQUA;
            String tag = isJackpot ? "  ★ 잭팟" : "";
            // 3개 일치 확률 = (weight/total)^3
            double p = (double) sym.weight() / totalWeight;
            double p3 = p * p * p * 100.0;
            String probStr = p3 >= 0.01
                    ? String.format("%.2f%%", p3)
                    : String.format("%.4f%%", p3);
            lore.add(Component.text(
                    sym.displayName() + " × 3  →  ×" + sym.payout3() + "   (" + probStr + ")" + tag,
                    color).decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.text(" ", NamedTextColor.GRAY));
        lore.add(Component.text("잭팟 시 서버 전체 알림 + 사운드", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        inv.setItem(INFO_SLOT, item);
    }

    public void renderBetButtons(Inventory inv, SlotHolder holder) {
        List<Long> bets = config.bets();
        for (int i = 0; i < BET_SLOTS.length && i < bets.size(); i++) {
            long bet = bets.get(i);
            boolean selected = bet == holder.currentBet();
            Material mat = selected ? Material.LIME_DYE : Material.GRAY_DYE;
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            String label = (i == 0 ? "소액" : (i == 1 ? "중액" : "대액"));
            NamedTextColor color = selected ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            meta.displayName(Component.text(label + " 베팅: " + bet + "원", color)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(selected ? "선택됨" : "클릭하여 선택",
                                    selected ? NamedTextColor.YELLOW : NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(keys.actionKey(), PersistentDataType.STRING, BET_ACTIONS[i]);
            item.setItemMeta(meta);
            inv.setItem(BET_SLOTS[i], item);
        }
    }

    public void renderReelsIdle(Inventory inv) {
        SlotSymbol s = config.symbols().get(0);
        for (int slot : new int[]{REEL_1_SLOT, REEL_2_SLOT, REEL_3_SLOT}) {
            inv.setItem(slot, symbolItem(s));
        }
    }

    public void setReelSymbol(Inventory inv, int reelIndex, SlotSymbol symbol) {
        int slot = switch (reelIndex) {
            case 0 -> REEL_1_SLOT;
            case 1 -> REEL_2_SLOT;
            case 2 -> REEL_3_SLOT;
            default -> throw new IllegalArgumentException("reelIndex: " + reelIndex);
        };
        inv.setItem(slot, symbolItem(symbol));
    }

    public ItemStack symbolItem(SlotSymbol symbol) {
        ItemStack item = new ItemStack(symbol.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(symbol.displayName(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public void renderStatus(Inventory inv, SlotHolder holder, Player player, String message) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("상태", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        long balance = -1L;
        EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
        if (eco != null) balance = eco.getBalance(player.getUniqueId());
        meta.lore(List.of(
                Component.text("현재 잔액: " + (balance < 0 ? "-" : balance) + "원", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("현재 베팅: " + holder.currentBet() + "원", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(message == null ? "" : message,
                        message == null ? NamedTextColor.GRAY : NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        inv.setItem(STATUS_SLOT, item);
    }

    public void renderSpinButton(Inventory inv, boolean spinning) {
        Material mat = spinning ? Material.BARRIER : Material.LEVER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = spinning ? NamedTextColor.RED : NamedTextColor.GREEN;
        meta.displayName(Component.text(spinning ? "회전 중..." : "스핀!", color)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(spinning ? "기다려주세요" : "베팅 금액을 차감하고 회전 시작",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(keys.actionKey(), PersistentDataType.STRING, SlotActionKeys.ACTION_SPIN);
        item.setItemMeta(meta);
        inv.setItem(SPIN_SLOT, item);
    }

    public void renderCloseButton(Inventory inv) {
        ItemStack item = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("닫기", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(keys.actionKey(), PersistentDataType.STRING, SlotActionKeys.ACTION_CLOSE);
        item.setItemMeta(meta);
        inv.setItem(CLOSE_SLOT, item);
    }

    private void fillBackground(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == STATUS_SLOT || i == INFO_SLOT || i == CLOSE_SLOT || i == SPIN_SLOT
                    || i == REEL_1_SLOT || i == REEL_2_SLOT || i == REEL_3_SLOT
                    || i == BET_SLOTS[0] || i == BET_SLOTS[1] || i == BET_SLOTS[2]) continue;
            inv.setItem(i, filler);
        }
    }

    public long betForAction(String action) {
        List<Long> bets = config.bets();
        return switch (action) {
            case SlotActionKeys.ACTION_BET_0 -> bets.get(0);
            case SlotActionKeys.ACTION_BET_1 -> bets.size() > 1 ? bets.get(1) : bets.get(0);
            case SlotActionKeys.ACTION_BET_2 -> bets.size() > 2 ? bets.get(2) : bets.get(bets.size() - 1);
            default -> throw new IllegalArgumentException("not a bet action: " + action);
        };
    }
}
