package com.exit.customitems.consumable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * 사망 이벤트 처리.
 *
 * 인벤토리에 인벤세이브가 있으면 1개 소모하고 keepInventory + keepLevel을 설정한다.
 * 엔더체스트는 바닐라가 이미 사망과 무관하게 보존하므로 별도 처리 불필요.
 */
public final class InvSaveListener implements Listener {

    private final Plugin plugin;
    private final InvSaveItem invSaveItem;

    public InvSaveListener(Plugin plugin, InvSaveItem invSaveItem) {
        this.plugin = plugin;
        this.invSaveItem = invSaveItem;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerInventory inv = player.getInventory();

        int slot = findInvSaveSlot(inv);
        if (slot < 0) return;  // 보관석 없음 — 일반 사망

        ItemStack stack = inv.getItem(slot);
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else {
            inv.setItem(slot, null);
        }

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        event.getDrops().clear();

        // 사망 직후엔 메시지가 표시 안 될 수 있어 다음 틱에 전송
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(Component.text("[보관석] 인벤토리가 보호되었습니다.")
                        .color(NamedTextColor.AQUA));
            }
        });
    }

    private int findInvSaveSlot(PlayerInventory inv) {
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (invSaveItem.isInvSave(contents[i])) return i;
        }
        return -1;
    }
}
