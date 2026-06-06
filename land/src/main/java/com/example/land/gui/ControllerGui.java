package com.example.land.gui;

import com.example.land.LandPlugin;
import com.example.land.data.ClaimedChunk;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * 청크 컨트롤러 GUI — 멤버 일괄 관리
 *
 * 레이아웃 (6행 54칸):
 *  0      : 멤버 목록 라벨
 *  1~44   : 멤버 스컬 (최대 44명)
 *  49     : 멤버 추가 버튼
 *  53     : 닫기 버튼
 *
 * 추가/회수 모두 내 모든 청크에 일괄 적용.
 */
public class ControllerGui implements Listener {

    private final LandPlugin plugin;
    private final Player player;
    private Inventory inventory;

    private static final int SIZE = 54;
    private static final int BTN_ADD_MEMBER = 49;
    private static final int BTN_CLOSE = 53;

    private final Map<Integer, UUID> memberSlotMap = new HashMap<>();

    public ControllerGui(LandPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(null, SIZE, Component.text("§6청크 컨트롤러"));
        render();
        player.openInventory(inventory);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void render() {
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, filler(Material.GRAY_STAINED_GLASS_PANE));
        memberSlotMap.clear();

        // 내 모든 청크의 멤버 합집합
        Set<UUID> memberSet = new LinkedHashSet<>();
        for (ClaimedChunk c : plugin.getLandManager().getClaimsOf(player.getUniqueId())) {
            memberSet.addAll(c.getMembers());
        }

        // 라벨
        inventory.setItem(0, label(Material.PLAYER_HEAD,
                "§b멤버 목록 §7(" + memberSet.size() + "명)",
                "§7클릭: 모든 청크에서 권한 회수"));

        // 멤버 스컬 슬롯 1~44
        int slot = 1;
        for (UUID memberUuid : memberSet) {
            if (slot > 44) break;
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            String name = member.getName() != null ? member.getName() : "알 수 없음";
            boolean isOnline = member.isOnline();

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            sm.setOwningPlayer(member);
            sm.displayName(Component.text(name).color(isOnline ? NamedTextColor.GREEN : NamedTextColor.WHITE));
            sm.lore(List.of(
                    Component.text(isOnline ? "§a온라인" : "§7오프라인"),
                    Component.text("§c클릭: 모든 청크에서 권한 회수")
            ));
            skull.setItemMeta(sm);
            inventory.setItem(slot, skull);
            memberSlotMap.put(slot, memberUuid);
            slot++;
        }

        // 하단 버튼
        inventory.setItem(BTN_ADD_MEMBER, label(Material.EMERALD,
                "§a멤버 추가",
                "§7클릭하여 플레이어 선택 (모든 청크에 일괄 적용)"));
        inventory.setItem(BTN_CLOSE, label(Material.BARRIER, "§c닫기", ""));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!p.equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot >= SIZE) return;

        // 멤버 일괄 회수
        if (memberSlotMap.containsKey(slot)) {
            UUID memberUuid = memberSlotMap.get(slot);
            plugin.getLandManager().removeMemberFromAll(player.getUniqueId(), memberUuid);
            String name = Bukkit.getOfflinePlayer(memberUuid).getName();
            player.sendMessage(Component.text(name + " 님의 권한을 모든 청크에서 회수했습니다.").color(NamedTextColor.RED));
            render();
            return;
        }

        // 멤버 추가 → PlayerSelectGui (targetChunk=null → 일괄 적용)
        if (slot == BTN_ADD_MEMBER) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () ->
                    new PlayerSelectGui(plugin, player, null).open());
            return;
        }

        if (slot == BTN_CLOSE) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;
        HandlerList.unregisterAll(this);
    }

    private ItemStack filler(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack label(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        if (!lore.isEmpty()) meta.lore(List.of(Component.text(lore)));
        item.setItemMeta(meta);
        return item;
    }
}
