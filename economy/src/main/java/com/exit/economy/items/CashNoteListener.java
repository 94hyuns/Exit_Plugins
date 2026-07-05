package com.exit.economy.items;

import com.exit.core.CorePlugin;
import com.exit.economy.EconomyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class CashNoteListener implements Listener {

    private final EconomyPlugin plugin;

    public CashNoteListener(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isNew = CorePlugin.getInstance().getPlayerDataManager().registerIfAbsent(player);
        if (isNew) {
            player.sendMessage(
                Component.text("✦ 서버에 오신 걸 환영합니다! 초기 지원금 ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text("1,000w").color(NamedTextColor.GOLD))
                    .append(Component.text("가 지급되었습니다.").color(NamedTextColor.GREEN))
            );
        }
        plugin.getBalanceScoreboard().update(player);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!CashNoteFactory.isCashNote(item)) return;

        event.setCancelled(true);

        long amount = CashNoteFactory.getAmount(item);
        if (amount <= 0) return;

        item.setAmount(item.getAmount() - 1);
        plugin.getEconomy().addBalance(player.getUniqueId(), amount);

        long newBal = plugin.getEconomy().getBalance(player.getUniqueId());
        player.sendMessage(
            Component.text("✦ ").color(NamedTextColor.GOLD)
                .append(Component.text(String.format("%,d", amount) + "w").color(NamedTextColor.YELLOW))
                .append(Component.text(" 입금 완료 → 잔액: ").color(NamedTextColor.GREEN))
                .append(Component.text(String.format("%,d", newBal) + "w").color(NamedTextColor.GOLD))
        );
        plugin.getBalanceScoreboard().update(player);
    }
}
