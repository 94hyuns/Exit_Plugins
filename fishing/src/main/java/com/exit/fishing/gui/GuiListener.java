package com.exit.fishing.gui;

import com.exit.fishing.FishingPlugin;
import com.exit.fishing.fish.FishRegistry;
import com.exit.fishing.fish.FishSpecies;
import com.exit.fishing.item.FishItem;
import com.exit.fishing.season.SeasonManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 낚시 GUI 관련 이벤트를 한 곳에서 처리.
 * - Shop 배럴 우클릭 → 상점 오픈
 * - 상점 클릭: 판매 슬롯은 드나들기 가능, 바구니/도감은 액션
 * - 상점 닫기: 판매 슬롯 내용 돌려주기
 * - 도감 클릭: 탭 전환, 어종 상세, 닫기
 */
public class GuiListener implements Listener {

    private final FishingPlugin plugin;
    private final SeasonManager seasons;

    public GuiListener(FishingPlugin plugin, SeasonManager seasons) {
        this.plugin = plugin;
        this.seasons = seasons;
    }

    // ===== Shop 플러그인의 낚시 NPC가 진입점. 배럴 진입은 제거됨. =====

    // ===== 상점 & 도감 클릭 =====
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 상점
        if (holder instanceof FishShopGUI.FishShopHolder shopHolder) {
            handleShopClick(event, player, top, shopHolder);
            return;
        }
        // 도감
        if (holder instanceof FishCodexGUI.FishCodexHolder codexHolder) {
            handleCodexClick(event, player, top, codexHolder);
        }
    }

    private void handleShopClick(InventoryClickEvent event, Player player, Inventory top, FishShopGUI.FishShopHolder holder) {
        int rawSlot = event.getRawSlot();
        boolean topClick = event.getClickedInventory() == top;

        // 클릭 처리 후 다음 틱에 바구니 미리보기 갱신 (이벤트 처리 순서 안전망)
        Bukkit.getScheduler().runTask(plugin,
                () -> FishShopGUI.updateBasketPreview(top, holder.season(), plugin));

        // === 플레이어 본인 인벤토리 클릭 ===
        if (!topClick) {
            // shift-click: 물고기만 판매슬롯으로 이동 허용
            if (event.isShiftClick() || event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() == Material.AIR) return;
                if (!FishItem.isFish(clicked)) {
                    event.setCancelled(true);
                    return;
                }
                event.setCancelled(true);
                // 빈 판매 슬롯 찾아서 이동
                ItemStack moving = clicked.clone();
                for (int s : new int[]{
                        0, 1, 2, 3, 4, 5, 6,
                        9, 10, 11, 12, 13, 14, 15,
                        18, 19, 20, 21, 22, 23, 24,
                        27, 28, 29, 30, 31, 32, 33}) {
                    if (top.getItem(s) == null || top.getItem(s).getType() == Material.AIR) {
                        top.setItem(s, moving);
                        event.setCurrentItem(null);
                        player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL_FISH, 0.5f, 1.2f);
                        return;
                    }
                }
                player.sendMessage(Component.text("판매 슬롯이 가득 찼습니다.", NamedTextColor.YELLOW));
            }
            // 일반 클릭은 자유 (본인 인벤)
            return;
        }

        // === 상단 인벤토리 클릭 ===
        if (rawSlot == FishShopGUI.basketSlot()) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 4.0f, 2.0f);
            var result = FishShopGUI.sellAll(player, top, holder.season(), plugin);
            if (result.count() == 0) {
                player.sendMessage(Component.text("판매할 물고기가 없습니다.", NamedTextColor.RED));
                return;
            }
            String prefix = plugin.getConfig().getString("prefix", "&6[ &fserver &6]");
            Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    prefix + " &7" + result.firstFishName() + "&7,등 총&f" + result.count()
                            + "&7개를 판매하였습니다 &7(&6" + result.amount() + "w&7를 벌었습니다)"
            );
            player.sendMessage(msg);
            return;
        }

        if (rawSlot == FishShopGUI.codexSlot()) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 4.0f, 2.0f);
            FishCodexGUI.open(player, holder.season());
            return;
        }

        if (rawSlot == FishShopGUI.bulkStorageSlot()) {
            event.setCancelled(true);
            var storage = plugin.getFishStorageManager();
            if (storage == null) {
                player.sendMessage(Component.text("보관함 시스템이 비활성 상태입니다.", NamedTextColor.RED));
                return;
            }
            org.bukkit.inventory.ItemStack[] items = storage.takeAll(player.getUniqueId());
            var result = FishShopGUI.sellAllFromArray(player, items, holder.season(), plugin);
            if (result.count() == 0) {
                player.sendMessage(Component.text("보관함이 비어 있습니다.", NamedTextColor.YELLOW));
                return;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            String prefix = plugin.getConfig().getString("prefix", "&6[ &fserver &6]");
            Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    prefix + " &b[보관함 일괄 판매] &7" + result.firstFishName() + "&7 등 총 &f"
                            + result.count() + "&7개 → &6" + result.amount() + "w &7입금"
            );
            player.sendMessage(msg);
            return;
        }

        if (rawSlot == FishShopGUI.bulkSeasonalSlot()) {
            event.setCancelled(true);
            var storage = plugin.getFishStorageManager();
            if (storage == null) {
                player.sendMessage(Component.text("보관함 시스템이 비활성 상태입니다.", NamedTextColor.RED));
                return;
            }
            var season = holder.season();
            org.bukkit.inventory.ItemStack[] items = storage.extractInSeason(player.getUniqueId(), season);
            var result = FishShopGUI.sellAllFromArray(player, items, season, plugin);
            if (result.count() == 0) {
                player.sendMessage(Component.text("보관함에 " + season.korean() + " 제철 물고기가 없습니다.", NamedTextColor.YELLOW));
                return;
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            String prefix = plugin.getConfig().getString("prefix", "&6[ &fserver &6]");
            Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    prefix + " &6[계절 일괄 판매] &7" + result.firstFishName() + "&7 등 &f"
                            + result.count() + "&7마리(" + season.korean() + ") → &6" + result.amount() + "w &7입금"
            );
            player.sendMessage(msg);
            return;
        }

        // 판매 슬롯 아니면 취소
        if (!FishShopGUI.isSellSlot(rawSlot)) {
            event.setCancelled(true);
            return;
        }

        // 판매 슬롯: 물고기만 허용
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR && !FishItem.isFish(cursor)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("물고기만 넣을 수 있습니다.", NamedTextColor.YELLOW));
        }
    }

    private void handleCodexClick(InventoryClickEvent event, Player player, Inventory top, FishCodexGUI.FishCodexHolder holder) {
        if (event.getClickedInventory() != top) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == FishCodexGUI.SLOT_CLOSE) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 4.0f, 2.0f);
            player.closeInventory();
            return;
        }

        var tab = FishCodexGUI.tabOf(slot);
        if (tab != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 4.0f, 2.0f);
            FishCodexGUI.open(player, tab);
            return;
        }

        // 어종 슬롯 (0~5)
        if (slot >= 0 && slot <= 5) {
            ItemStack it = top.getItem(slot);
            if (it == null || !it.hasItemMeta()) return;
            ItemMeta meta = it.getItemMeta();
            // 디스플레이 아이템은 koreanName을 display name으로 가지므로 plain text로 식별
            String koName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName() == null ? Component.empty() : meta.displayName());
            FishSpecies sp = FishRegistry.byKorean(koName);
            if (sp == null) return;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 4.0f, 2.0f);
            FishCodexGUI.showDetail(top, sp);
        }
    }

    // ===== 상점 닫을 때 판매 슬롯 내용 반환 =====
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof FishShopGUI.FishShopHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1.0f, 1.0f);
        FishShopGUI.returnItems(player, top);
    }

    // ===== 드래그 방지 (비-판매 슬롯으로) =====
    @EventHandler
    public void onDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof FishShopGUI.FishShopHolder shopHolder)) return;

        int topSize = top.getSize();
        // 드래그된 상단 슬롯 중 판매슬롯이 아닌 곳이 있으면 취소
        for (int raw : event.getRawSlots()) {
            if (raw < topSize && !FishShopGUI.isSellSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
        // 드래그 아이템이 물고기가 아니면 취소
        ItemStack cursor = event.getOldCursor();
        if (cursor != null && cursor.getType() != Material.AIR && !FishItem.isFish(cursor)) {
            event.setCancelled(true);
            return;
        }
        // 드래그 후 다음 틱에 바구니 미리보기 갱신
        Bukkit.getScheduler().runTask(plugin,
                () -> FishShopGUI.updateBasketPreview(top, shopHolder.season(), plugin));
    }
}
