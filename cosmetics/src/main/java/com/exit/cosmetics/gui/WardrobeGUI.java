package com.exit.cosmetics.gui;

import com.exit.core.api.CosmeticProvider;
import com.exit.core.data.PlayerDataManager;
import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticType;
import com.exit.cosmetics.registry.CosmeticRegistry;
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
 * 옷장 GUI. 54칸 인벤토리. 7종 탭 (HAT/CHEST/LEGS/FEET/WEAPON/WING/TRAIL).
 *
 * <p>레이아웃:
 * - 1행(0~8): 타입 탭 7개 (슬롯 1~7)
 * - 2~5행(9~44): 해당 타입의 보유 치장 (최대 36칸)
 * - 6행(45~53): 뒤로가기, 해제 버튼
 */
public class WardrobeGUI {

    public static final NamespacedKey ACTION_KEY = new NamespacedKey("cosmetics", "wardrobe_action");
    public static final NamespacedKey PAYLOAD_KEY = new NamespacedKey("cosmetics", "wardrobe_payload");
    private static final Component TITLE = Component.text("내 옷장", NamedTextColor.LIGHT_PURPLE);
    private static final int ITEMS_START = 9;
    private static final int ITEMS_END = 45;

    private final CosmeticRegistry registry;
    private final PlayerDataManager dataManager;
    private final CosmeticProvider provider;

    private final Map<UUID, CosmeticType> currentTab = new HashMap<>();
    private final Map<UUID, Integer> currentPage = new HashMap<>();
    private final Set<UUID> viewers = new HashSet<>();
    private static final int ITEMS_PER_PAGE = ITEMS_END - ITEMS_START;

    public WardrobeGUI(CosmeticRegistry registry, PlayerDataManager dataManager, CosmeticProvider provider) {
        this.registry = registry;
        this.dataManager = dataManager;
        this.provider = provider;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        open(player, currentTab.getOrDefault(uuid, CosmeticType.WEAPON),
             currentPage.getOrDefault(uuid, 0));
    }

    public void open(Player player, CosmeticType tab) {
        // 탭 전환 시엔 첫 페이지로
        open(player, tab, 0);
    }

    public void open(Player player, CosmeticType tab, int page) {
        UUID uuid = player.getUniqueId();
        currentTab.put(uuid, tab);

        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // 탭 (1차 출시: WEAPON 만)
        CosmeticType[] order = { CosmeticType.WEAPON };
        for (int i = 0; i < order.length; i++) {
            inv.setItem(1 + i, tabButton(order[i], tab));
        }

        // 보유 치장
        Set<String> owned = provider.listOwned(uuid);
        List<CosmeticDefinition> inTab = registry.getByType(tab).stream()
                .filter(d -> owned.contains(d.getId()))
                .toList();

        // 현재 장착된 모든 cosmetic id 수집. WEAPON 은 카테고리별로 여러 개 동시 장착 가능하므로 Set 으로 처리.
        Map<String, String> allEquipped = dataManager.getAllEquippedCosmetics(uuid);
        Set<String> equippedIdsInTab = new HashSet<>();
        for (Map.Entry<String, String> e : allEquipped.entrySet()) {
            CosmeticDefinition d = registry.get(e.getValue());
            if (d != null && d.getType() == tab) equippedIdsInTab.add(d.getId());
        }

        int totalPages = Math.max(1, (inTab.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        currentPage.put(uuid, clampedPage);
        int from = clampedPage * ITEMS_PER_PAGE;
        int to = Math.min(from + ITEMS_PER_PAGE, inTab.size());

        int slot = ITEMS_START;
        for (CosmeticDefinition def : inTab.subList(from, to)) {
            boolean isEquipped = equippedIdsInTab.contains(def.getId());
            inv.setItem(slot++, cosmeticIcon(def, isEquipped));
        }

        if (inTab.isEmpty()) {
            inv.setItem(22, emptyNotice(tab));
        }

        // 하단
        inv.setItem(45, actionButton(Material.ARROW, "§e◀ 뒤로", "BACK", null, List.of()));
        if (totalPages > 1) {
            if (clampedPage > 0) {
                inv.setItem(46, actionButton(Material.SPECTRAL_ARROW,
                        "§b◀ 이전 페이지", "PAGE_PREV", null, List.of()));
            }
            inv.setItem(47, info(Material.PAPER,
                    "§7페이지 §f" + (clampedPage + 1) + "§7 / §f" + totalPages,
                    List.of(Component.text("§8총 " + inTab.size() + "개 보유")
                            .decoration(TextDecoration.ITALIC, false))));
            if (clampedPage < totalPages - 1) {
                inv.setItem(53, actionButton(Material.SPECTRAL_ARROW,
                        "§b다음 페이지 ▶", "PAGE_NEXT", null, List.of()));
            }
        }
        if (!equippedIdsInTab.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String eqId : equippedIdsInTab) {
                lore.add(Component.text("§7장착: §f" + displayNameOf(eqId))
                        .decoration(TextDecoration.ITALIC, false));
            }
            String btnLabel = equippedIdsInTab.size() > 1
                    ? "§c" + typeKor(tab) + " 전체 해제 (" + equippedIdsInTab.size() + "개)"
                    : "§c현재 " + typeKor(tab) + " 해제";
            inv.setItem(49, actionButton(Material.BARRIER, btnLabel,
                    "UNEQUIP", tab.name(), lore));
        } else {
            inv.setItem(49, info(Material.GRAY_STAINED_GLASS_PANE, "§7장착된 치장 없음", List.of()));
        }

        player.openInventory(inv);
        viewers.add(uuid);
    }

    public void openPage(Player player, int delta) {
        UUID uuid = player.getUniqueId();
        CosmeticType tab = currentTab.getOrDefault(uuid, CosmeticType.WEAPON);
        int next = currentPage.getOrDefault(uuid, 0) + delta;
        open(player, tab, next);
    }

    public boolean isViewing(UUID uuid) { return viewers.contains(uuid); }
    public void close(UUID uuid) { viewers.remove(uuid); }

    public Optional<String> getAction(ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return Optional.empty();
        var pdc = clicked.getItemMeta().getPersistentDataContainer();
        if (pdc.has(ACTION_KEY, PersistentDataType.STRING)) {
            return Optional.ofNullable(pdc.get(ACTION_KEY, PersistentDataType.STRING));
        }
        return Optional.empty();
    }

    public Optional<String> getPayload(ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return Optional.empty();
        var pdc = clicked.getItemMeta().getPersistentDataContainer();
        if (pdc.has(PAYLOAD_KEY, PersistentDataType.STRING)) {
            return Optional.ofNullable(pdc.get(PAYLOAD_KEY, PersistentDataType.STRING));
        }
        return Optional.empty();
    }

    // ─── 빌더 ───

    private ItemStack tabButton(CosmeticType type, CosmeticType active) {
        boolean selected = type == active;
        Material mat = selected ? Material.GLOWSTONE_DUST : iconFor(type);
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text((selected ? "§a▶ " : "§f") + typeKor(type))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(selected ? "§8현재 탭" : "§8클릭하여 탭 전환")
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "TAB");
        meta.getPersistentDataContainer().set(PAYLOAD_KEY, PersistentDataType.STRING, type.name());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack cosmeticIcon(CosmeticDefinition def, boolean equipped) {
        ItemStack stack = provider.createShowcaseItem(def.getId());
        if (stack == null) stack = new ItemStack(def.getBaseItem());

        ItemMeta meta = stack.getItemMeta();
        String prefix = equipped ? "§a[장착중] " : "";
        meta.displayName(Component.text(prefix + def.getDisplayName())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (!def.getDescription().isEmpty()) {
            lore.add(Component.text(def.getDescription()).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }
        lore.add(Component.text("§7등급: " + def.getRarity().getDisplayName())
                .decoration(TextDecoration.ITALIC, false));
        String applies = applicabilityLabel(def);
        if (!applies.isEmpty()) {
            lore.add(Component.text("§7적용: §f" + applies)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text(equipped ? "§8▶ 클릭하여 해제" : "§8▶ 클릭하여 장착")
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                equipped ? "UNEQUIP" : "EQUIP");
        meta.getPersistentDataContainer().set(PAYLOAD_KEY, PersistentDataType.STRING, def.getId());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack actionButton(Material mat, String name, String action, String payload, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        if (payload != null) {
            meta.getPersistentDataContainer().set(PAYLOAD_KEY, PersistentDataType.STRING, payload);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack info(Material mat, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack emptyNotice(CosmeticType type) {
        return info(Material.PAPER, "§7보유한 " + typeKor(type) + " 없음",
                List.of(
                        Component.text("§8뽑기로 획득하거나").decoration(TextDecoration.ITALIC, false),
                        Component.text("§8가루로 교환하세요.").decoration(TextDecoration.ITALIC, false)
                ));
    }

    private String applicabilityLabel(CosmeticDefinition def) {
        Material base = def.getBaseItem();
        if (base == null) return "";
        String n = base.name();
        if (n.endsWith("_SWORD")) return "검";
        if (n.endsWith("_AXE")) return "도끼";
        if (n.endsWith("_PICKAXE")) return "곡괭이";
        if (n.endsWith("_HOE")) return "괭이";
        if (n.endsWith("_SPEAR")) return "창";
        return switch (base) {
            case TRIDENT -> "삼지창";
            case MACE -> "철퇴";
            case BOW -> "활";
            case CROSSBOW -> "석궁";
            default -> "";
        };
    }

    private Material iconFor(CosmeticType type) {
        return switch (type) {
            case HAT -> Material.LEATHER_HELMET;
            case CHEST -> Material.LEATHER_CHESTPLATE;
            case LEGS -> Material.LEATHER_LEGGINGS;
            case FEET -> Material.LEATHER_BOOTS;
            case WEAPON -> Material.IRON_SWORD;
            case WING -> Material.ELYTRA;
            case TRAIL -> Material.FIREWORK_ROCKET;
            case MOUNT -> Material.SADDLE;
        };
    }

    private String typeKor(CosmeticType type) {
        return switch (type) {
            case HAT -> "모자";
            case CHEST -> "상의";
            case LEGS -> "하의";
            case FEET -> "신발";
            case WEAPON -> "무기";
            case WING -> "날개";
            case TRAIL -> "트레일";
            case MOUNT -> "탈것";
        };
    }

    private String displayNameOf(String cosmeticId) {
        CosmeticDefinition def = registry.get(cosmeticId);
        return def != null ? def.getDisplayName() : cosmeticId;
    }
}
