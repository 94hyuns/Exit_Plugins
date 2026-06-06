package com.exit.cosmetics.gui;

import com.exit.core.api.CosmeticProvider;
import com.exit.core.data.PlayerDataManager;
import com.exit.cosmetics.gacha.GachaConfig;
import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticRarity;
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
 * 가루 교환소 GUI. 54칸. 미보유 치장을 등급별 탭으로 표시, 클릭하여 가루로 교환.
 */
public class ExchangeGUI {

    public static final NamespacedKey ACTION_KEY = new NamespacedKey("cosmetics", "exchange_action");
    public static final NamespacedKey PAYLOAD_KEY = new NamespacedKey("cosmetics", "exchange_payload");
    private static final Component TITLE = Component.text("가루 교환소", NamedTextColor.AQUA);
    private static final int ITEMS_START = 9;
    private static final int ITEMS_END = 45;

    private final CosmeticRegistry registry;
    private final PlayerDataManager dataManager;
    private final CosmeticProvider provider;
    private final GachaConfig gachaConfig;

    private final Map<UUID, CosmeticRarity> currentTab = new HashMap<>();
    private final Set<UUID> viewers = new HashSet<>();

    public ExchangeGUI(CosmeticRegistry registry, PlayerDataManager dataManager,
                       CosmeticProvider provider, GachaConfig gachaConfig) {
        this.registry = registry;
        this.dataManager = dataManager;
        this.provider = provider;
        this.gachaConfig = gachaConfig;
    }

    public void open(Player player) {
        open(player, currentTab.getOrDefault(player.getUniqueId(), CosmeticRarity.COMMON));
    }

    public void open(Player player, CosmeticRarity tab) {
        UUID uuid = player.getUniqueId();
        currentTab.put(uuid, tab);

        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // 탭
        inv.setItem(1, tabButton(CosmeticRarity.COMMON, tab));
        inv.setItem(3, tabButton(CosmeticRarity.RARE, tab));
        inv.setItem(5, tabButton(CosmeticRarity.UNIQUE, tab));
        inv.setItem(7, tabButton(CosmeticRarity.LEGENDARY, tab));

        // 미보유 치장 표시 (1차 출시: WEAPON 만 — 차후 타입 풀 때 필터 완화)
        Set<String> owned = provider.listOwned(uuid);
        List<CosmeticDefinition> unowned = registry.getByRarity(tab).stream()
                .filter(d -> d.getType() == CosmeticType.WEAPON)
                .filter(d -> !owned.contains(d.getId()))
                .toList();

        long cost = gachaConfig.getShardExchangeCost(tab);
        long playerShards = Math.max(0, dataManager.getShards(uuid));

        int slot = ITEMS_START;
        for (CosmeticDefinition def : unowned) {
            if (slot >= ITEMS_END) break;
            inv.setItem(slot++, exchangeIcon(def, cost, playerShards));
        }

        if (unowned.isEmpty()) {
            ItemStack none = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta meta = none.getItemMeta();
            meta.displayName(Component.text("§7" + tab.getDisplayName() + " §7등급 전체 보유 완료!")
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("§8이 등급에서 교환할 치장이 없습니다.")));
            none.setItemMeta(meta);
            inv.setItem(22, none);
        }

        // 하단
        inv.setItem(45, actionButton(Material.ARROW, "§e◀ 뒤로", "BACK", null, List.of()));
        inv.setItem(49, info(Material.AMETHYST_SHARD,
                "§b내 가루: §f" + String.format("%,d", playerShards),
                List.of(Component.text("§7" + tab.getDisplayName() + " §7등급 교환가: §b" + String.format("%,d", cost)))));

        player.openInventory(inv);
        viewers.add(uuid);
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

    public Optional<String> getPayload(ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return Optional.empty();
        var pdc = clicked.getItemMeta().getPersistentDataContainer();
        if (pdc.has(PAYLOAD_KEY, PersistentDataType.STRING)) {
            return Optional.ofNullable(pdc.get(PAYLOAD_KEY, PersistentDataType.STRING));
        }
        return Optional.empty();
    }

    // ─── 빌더 ───

    private ItemStack tabButton(CosmeticRarity rarity, CosmeticRarity active) {
        boolean selected = rarity == active;
        Material mat = switch (rarity) {
            case COMMON -> selected ? Material.GLOWSTONE_DUST : Material.WHITE_WOOL;
            case RARE -> selected ? Material.GLOWSTONE_DUST : Material.BLUE_WOOL;
            case UNIQUE -> selected ? Material.GLOWSTONE_DUST : Material.MAGENTA_WOOL;
            case LEGENDARY -> selected ? Material.GLOWSTONE_DUST : Material.ORANGE_WOOL;
        };
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text((selected ? "§a▶ " : "") + rarity.getDisplayName() + " 등급")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(selected ? "§8현재 탭" : "§8클릭하여 탭 전환")));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "TAB");
        meta.getPersistentDataContainer().set(PAYLOAD_KEY, PersistentDataType.STRING, rarity.name());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack exchangeIcon(CosmeticDefinition def, long cost, long playerShards) {
        ItemStack stack = provider.createShowcaseItem(def.getId());
        if (stack == null) stack = new ItemStack(def.getBaseItem());

        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(def.getDisplayName()).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (!def.getDescription().isEmpty()) {
            lore.add(Component.text(def.getDescription()).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }
        lore.add(Component.text("§7등급: " + def.getRarity().getDisplayName()));
        String applies = applicabilityLabel(def);
        if (!applies.isEmpty()) {
            lore.add(Component.text("§7적용: §f" + applies));
        }
        lore.add(Component.text("§7교환가: §b" + String.format("%,d", cost) + " 가루"));
        lore.add(Component.empty());
        if (playerShards >= cost) {
            lore.add(Component.text("§a▶ 클릭하여 교환"));
        } else {
            lore.add(Component.text("§c가루 부족 (§f" + String.format("%,d", playerShards) + "§c/§b" + String.format("%,d", cost) + "§c)"));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, "EXCHANGE");
        meta.getPersistentDataContainer().set(PAYLOAD_KEY, PersistentDataType.STRING, def.getId());
        stack.setItemMeta(meta);
        return stack;
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
}
