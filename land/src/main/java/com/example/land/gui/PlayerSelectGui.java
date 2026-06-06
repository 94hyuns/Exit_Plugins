package com.example.land.gui;

import com.example.land.LandPlugin;
import com.example.land.data.ChunkPos;
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

public class PlayerSelectGui implements Listener {

    private final LandPlugin plugin;
    private final Player player;
    // null이면 모든 청크에 일괄 적용
    private final ChunkPos targetChunk;

    private Inventory inventory;

    // 플레이어 목록 (자기 자신 + 이미 멤버 제외)
    private List<OfflinePlayer> playerList = new ArrayList<>();

    private int page = 0;
    private static final int PAGE_SIZE = 45; // 5행 표시
    private static final int SIZE = 54;

    // 하단 버튼 슬롯
    private static final int BTN_PREV = 45;
    private static final int BTN_BACK = 49;
    private static final int BTN_NEXT = 53;

    // 슬롯 → OfflinePlayer 맵
    private final Map<Integer, OfflinePlayer> slotMap = new HashMap<>();

    public PlayerSelectGui(LandPlugin plugin, Player player, ChunkPos targetChunk) {
        this.plugin = plugin;
        this.player = player;
        this.targetChunk = targetChunk;
    }

    public void open() {
        // 이미 멤버인 플레이어 수집 (모든 청크의 멤버 합집합)
        Set<UUID> alreadyMembers = new HashSet<>();
        if (targetChunk != null) {
            ClaimedChunk chunk = plugin.getLandManager().getChunk(targetChunk);
            if (chunk != null) alreadyMembers.addAll(chunk.getMembers());
        } else {
            for (ClaimedChunk c : plugin.getLandManager().getClaimsOf(player.getUniqueId())) {
                alreadyMembers.addAll(c.getMembers());
            }
        }

        // 오프라인(playerdata 기반) + 현재 온라인 합집합으로 수집.
        // getOfflinePlayers() 단독 사용 시 첫 접속 직후 / 캐시 stale 상태 플레이어 누락 가능.
        // 온라인 목록을 별도로 합쳐 갓 접속한 플레이어도 즉시 보이도록 함.
        Map<UUID, OfflinePlayer> merged = new LinkedHashMap<>();
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.hasPlayedBefore()) merged.put(op.getUniqueId(), op);
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            merged.put(online.getUniqueId(), online);  // Player ⊂ OfflinePlayer
        }
        playerList = merged.values().stream()
                .filter(op -> !op.getUniqueId().equals(player.getUniqueId()))
                .filter(op -> !alreadyMembers.contains(op.getUniqueId()))
                .sorted(Comparator.comparing(op -> op.getName() != null ? op.getName() : ""))
                .collect(java.util.stream.Collectors.toList());

        inventory = Bukkit.createInventory(null, SIZE, Component.text("§b멤버 추가 - 플레이어 선택"));
        render();
        player.openInventory(inventory);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void render() {
        // 초기화
        for (int i = 0; i < SIZE; i++) inventory.setItem(i, null);
        slotMap.clear();

        // 하단 필러
        for (int i = 45; i < 54; i++) inventory.setItem(i, filler(Material.GRAY_STAINED_GLASS_PANE));

        // 플레이어 목록 표시
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, playerList.size());

        for (int i = start; i < end; i++) {
            int slot = i - start;
            OfflinePlayer op = playerList.get(i);

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) skull.getItemMeta();
            sm.setOwningPlayer(op);

            String name = op.getName() != null ? op.getName() : "알 수 없음";
            boolean isOnline = op.isOnline();

            sm.displayName(Component.text(name).color(isOnline ? NamedTextColor.GREEN : NamedTextColor.WHITE));
            sm.lore(List.of(
                    Component.text(isOnline ? "§a온라인" : "§7오프라인"),
                    Component.text("§e클릭: 멤버로 추가")
            ));
            skull.setItemMeta(sm);
            inventory.setItem(slot, skull);
            slotMap.put(slot, op);
        }

        // 이전/다음 버튼
        if (page > 0) {
            inventory.setItem(BTN_PREV, label(Material.ARROW, "§f◀ 이전 페이지", ""));
        }
        inventory.setItem(BTN_BACK, label(Material.BARRIER, "§c돌아가기", ""));
        if (end < playerList.size()) {
            inventory.setItem(BTN_NEXT, label(Material.ARROW, "§f다음 페이지 ▶", ""));
        }

        // 페이지 표시
        int totalPages = (int) Math.ceil((double) playerList.size() / PAGE_SIZE);
        if (totalPages > 1) {
            inventory.setItem(49, label(Material.PAPER,
                    "§f" + (page + 1) + " / " + totalPages + " 페이지",
                    "§7전체 " + playerList.size() + "명"));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!p.equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot >= SIZE) return;

        // 이전 페이지
        if (slot == BTN_PREV && page > 0) {
            page--;
            render();
            return;
        }

        // 다음 페이지
        if (slot == BTN_NEXT && (page + 1) * PAGE_SIZE < playerList.size()) {
            page++;
            render();
            return;
        }

        // 돌아가기
        if (slot == BTN_BACK) {
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> new ControllerGui(plugin, player).open());
            return;
        }

        // 플레이어 선택 → 일괄 추가
        if (slotMap.containsKey(slot)) {
            OfflinePlayer target = slotMap.get(slot);
            String name = target.getName() != null ? target.getName() : "알 수 없음";
            boolean added;

            if (targetChunk == null) {
                // 모든 청크에 일괄 추가
                List<ClaimedChunk> claims = plugin.getLandManager().getClaimsOf(player.getUniqueId());
                added = false;
                for (ClaimedChunk c : claims) {
                    if (plugin.getLandManager().addMember(player.getUniqueId(), c.getPos(), target.getUniqueId())) {
                        added = true;
                    }
                }
            } else {
                added = plugin.getLandManager().addMember(player.getUniqueId(), targetChunk, target.getUniqueId());
            }

            if (added) {
                player.sendMessage(Component.text(name + " 님을 모든 청크의 멤버로 추가했습니다.").color(NamedTextColor.GREEN));
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> new ControllerGui(plugin, player).open());
            } else {
                player.sendMessage(Component.text("멤버 추가에 실패했습니다.").color(NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!event.getInventory().equals(inventory)) return;
        HandlerList.unregisterAll(this);
    }

    // ── 헬퍼 ──
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
