package com.exit.cosmetics.gui;

import com.exit.core.data.PlayerDataManager;
import com.exit.cosmetics.gacha.GachaConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 치장 상인 메인 GUI. 27칸 3행 인벤토리.
 * [10] 뽑기권 1장  [12] 뽑기권 10장  [14] 옷장  [16] 가루 교환소
 */
public class GachaMainGUI {

    public static final NamespacedKey ACTION_KEY = new NamespacedKey("cosmetics", "main_action");
    private static final Component TITLE = Component.text("치장 상인", NamedTextColor.AQUA);

    private final PlayerDataManager dataManager;
    private final GachaConfig gachaConfig;
    private final Set<UUID> viewers = new HashSet<>();

    public GachaMainGUI(PlayerDataManager dataManager, GachaConfig gachaConfig) {
        this.dataManager = dataManager;
        this.gachaConfig = gachaConfig;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, TITLE);

        fillBorder(inv);

        // 치장 뽑기권 1장
        inv.setItem(10, actionButton(
                Material.PAPER, "§e치장 뽑기권 1장",
                "BUY_TICKET_1",
                List.of(
                        Component.text("§7가격: §e" + format(gachaConfig.getSinglePrice()) + "w"),
                        Component.empty(),
                        Component.text("§7우클릭으로 사용 — 치장 무기 획득"),
                        Component.text("§7※ 현재는 무기만 출시"),
                        Component.empty(),
                        Component.text("§8▶ 클릭하여 구매")
                )
        ));

        // 치장 뽑기권 10장
        inv.setItem(11, actionButton(
                Material.PAPER, "§6치장 뽑기권 10장 §8(할인)",
                "BUY_TICKET_10",
                List.of(
                        Component.text("§7가격: §e" + format(gachaConfig.getTenPrice()) + "w"),
                        Component.text("§8§o(원가 " + format(gachaConfig.getSinglePrice() * 10) + "w 대비 할인)"),
                        Component.text("§7※ 현재는 무기만 출시"),
                        Component.empty(),
                        Component.text("§8▶ 클릭하여 구매")
                )
        ));

        // 탈것 뽑기권 1장
        inv.setItem(13, actionButton(
                Material.SADDLE, "§b탈것 뽑기권 1장",
                "BUY_MOUNT_TICKET_1",
                List.of(
                        Component.text("§7가격: §e" + format(gachaConfig.getMountSinglePrice()) + "w"),
                        Component.empty(),
                        Component.text("§7우클릭으로 사용 — 말/팬텀 등 탈것 획득"),
                        Component.text("§7/ride 명령으로 소환"),
                        Component.empty(),
                        Component.text("§8▶ 클릭하여 구매")
                )
        ));

        // 탈것 뽑기권 10장
        inv.setItem(14, actionButton(
                Material.SADDLE, "§3탈것 뽑기권 10장 §8(할인)",
                "BUY_MOUNT_TICKET_10",
                List.of(
                        Component.text("§7가격: §e" + format(gachaConfig.getMountTenPrice()) + "w"),
                        Component.text("§8§o(원가 " + format(gachaConfig.getMountSinglePrice() * 10) + "w 대비 할인)"),
                        Component.empty(),
                        Component.text("§8▶ 클릭하여 구매")
                )
        ));

        // 옷장
        inv.setItem(16, actionButton(
                Material.ARMOR_STAND, "§d내 옷장",
                "OPEN_WARDROBE",
                List.of(
                        Component.text("§7보유한 치장을 장착/해제."),
                        Component.text("§7※ 현재는 무기만 보유/장착 가능"),
                        Component.empty(),
                        Component.text("§8▶ 클릭하여 열기")
                )
        ));

        // 가루 교환소
        inv.setItem(28, actionButton(
                Material.AMETHYST_SHARD, "§b가루 교환소",
                "OPEN_EXCHANGE",
                List.of(
                        Component.text("§7가루로 원하는 치장을 교환."),
                        Component.text("§7※ 현재는 무기만 교환 가능"),
                        Component.empty(),
                        Component.text("§8▶ 클릭하여 열기")
                )
        ));

        // 잔액
        long balance = Math.max(0, dataManager.getBalance(player.getUniqueId()));
        long shards = Math.max(0, dataManager.getShards(player.getUniqueId()));
        inv.setItem(31, infoItem(Material.GOLD_INGOT, "§6내 잔액",
                List.of(
                        Component.text("§f울캐쉬: §e" + format(balance) + "w"),
                        Component.text("§f가루: §b" + format(shards))
                )));

        player.openInventory(inv);
        viewers.add(player.getUniqueId());
    }

    public boolean isViewing(UUID uuid) {
        return viewers.contains(uuid);
    }

    public void close(UUID uuid) {
        viewers.remove(uuid);
    }

    public Optional<String> getAction(ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return Optional.empty();
        var pdc = clicked.getItemMeta().getPersistentDataContainer();
        if (pdc.has(ACTION_KEY, PersistentDataType.STRING)) {
            return Optional.ofNullable(pdc.get(ACTION_KEY, PersistentDataType.STRING));
        }
        return Optional.empty();
    }

    private ItemStack actionButton(Material mat, String name, String action, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack infoItem(Material mat, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private void fillBorder(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            // 1행 전체 + 마지막 행 전체 + 양 끝 컬럼을 테두리로
            int row = i / 9, col = i % 9;
            if (row == 0 || row == size / 9 - 1 || col == 0 || col == 8) {
                inv.setItem(i, filler);
            }
        }
    }

    private String format(long value) {
        return String.format("%,d", value);
    }
}
