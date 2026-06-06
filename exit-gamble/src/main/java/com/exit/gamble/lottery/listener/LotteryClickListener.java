package com.exit.gamble.lottery.listener;

import com.exit.gamble.lottery.LotteryManager;
import com.exit.gamble.lottery.LotteryManager.PurchaseResult;
import com.exit.gamble.lottery.gui.LotteryGui;
import com.exit.gamble.lottery.gui.LotteryHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class LotteryClickListener implements Listener {

    private final LotteryGui gui;
    private final LotteryManager lottery;

    public LotteryClickListener(LotteryGui gui, LotteryManager lottery) {
        this.gui = gui;
        this.lottery = lottery;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LotteryHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(gui.actionKey(), PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case LotteryGui.ACTION_BUY_1 -> buy(player, 1);
            case LotteryGui.ACTION_BUY_5 -> buy(player, 5);
            case LotteryGui.ACTION_BUY_10 -> buy(player, 10);
            case LotteryGui.ACTION_MY_TICKETS -> gui.sendMyTicketsChat(player);
            case LotteryGui.ACTION_CLOSE -> player.closeInventory();
            default -> { /* unknown */ }
        }
    }

    private void buy(Player player, int count) {
        PurchaseResult res = lottery.buy(player, count);
        if (!res.success()) {
            player.sendMessage(Component.text("[복권] " + res.error(), NamedTextColor.RED));
            return;
        }
        int digits = String.valueOf(lottery.numberRange() - 1).length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < res.numbers().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%0" + digits + "d", res.numbers().get(i)));
        }
        player.sendMessage(Component.text("[복권] " + count + "장 구매 완료 (-" +
                String.format("%,d", res.totalCost()) + "원)", NamedTextColor.GREEN));
        player.sendMessage(Component.text("       번호: " + sb, NamedTextColor.AQUA));

        // GUI 의 상태 슬롯 새로고침 (잔액/티켓수 변경 반영)
        gui.renderStatus(player.getOpenInventory().getTopInventory(), player);
    }
}
