package com.exit.shop.tab;

import com.exit.shop.model.ShopCategory;
import com.exit.shop.model.ShopItem;
import com.exit.shop.model.ShopTab;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 카테고리별 GUI 탭 등록소.
 *
 * <p>config.yml 의 tabs 섹션 스키마:
 * <pre>
 * tabs:
 *   CROP:
 *     - id: SEED
 *       name: "§a씨앗 / 경작지 티켓"
 *       icon: WHEAT_SEEDS
 *       slot: 2
 *       default: true
 *       filter:
 *         variants: [SEED, TICKET]
 *     - id: FRUIT
 *       name: "§e열매 (수확물)"
 *       icon: WHEAT
 *       slot: 6
 *       filter:
 *         variants: [FRUIT]
 * </pre>
 *
 * <p>filter 종류 (or 결합):
 * <ul>
 *   <li>variants: ShopItem.variant 매칭</li>
 *   <li>materials: Material 이름 리스트 매칭</li>
 *   <li>ids: 명시적 id 리스트 매칭</li>
 * </ul>
 *
 * <p>탭 미설정 카테고리는 기존 단일 페이지 모드. {@link #hasTabs(ShopCategory)} 가 false 반환.
 */
public class ShopTabRegistry {

    private final EnumMap<ShopCategory, List<ShopTab>> tabsByCategory = new EnumMap<>(ShopCategory.class);

    public void loadFromConfig(File configFile, Logger logger) {
        tabsByCategory.clear();
        if (!configFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection tabsSection = config.getConfigurationSection("tabs");
        if (tabsSection == null) {
            logger.info("[Shop] config.yml 에 tabs 섹션 없음 — 모든 카테고리 단일 페이지 모드.");
            return;
        }

        for (String categoryKey : tabsSection.getKeys(false)) {
            ShopCategory category;
            try {
                category = ShopCategory.valueOf(categoryKey.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                logger.warning("[Shop] tabs: 알 수 없는 카테고리 '" + categoryKey + "' — skip");
                continue;
            }
            if (category == ShopCategory.FISHING) {
                logger.warning("[Shop] tabs: FISHING 카테고리는 동적이므로 탭 미지원 — skip");
                continue;
            }

            List<Map<?, ?>> tabList = tabsSection.getMapList(categoryKey);
            if (tabList.isEmpty()) continue;

            List<ShopTab> parsed = new ArrayList<>();
            Set<Integer> usedSlots = new HashSet<>();
            for (Map<?, ?> raw : tabList) {
                try {
                    ShopTab tab = parseEntry(category, raw, logger);
                    if (tab == null) continue;
                    if (!usedSlots.add(tab.slot())) {
                        logger.warning("[Shop] tabs: " + category + " 카테고리에서 슬롯 "
                                + tab.slot() + " 중복 — '" + tab.id() + "' skip");
                        continue;
                    }
                    parsed.add(tab);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "[Shop] tabs 파싱 실패 (" + category + "): " + raw, e);
                }
            }
            if (!parsed.isEmpty()) {
                tabsByCategory.put(category, Collections.unmodifiableList(parsed));
                logger.info("[Shop] tabs: " + category + " 에 " + parsed.size() + "개 탭 등록");
            }
        }
    }

    public void reload(File configFile, Logger logger) {
        loadFromConfig(configFile, logger);
    }

    private ShopTab parseEntry(ShopCategory category, Map<?, ?> raw, Logger logger) {
        String id = str(raw, "id");
        if (id == null) {
            logger.warning("[Shop] tabs (" + category + "): id 누락 — skip");
            return null;
        }

        String name = str(raw, "name");
        if (name == null) name = id;

        String iconStr = str(raw, "icon");
        Material icon = Material.STONE;
        if (iconStr != null) {
            Material parsed = Material.matchMaterial(iconStr);
            if (parsed != null) icon = parsed;
            else logger.warning("[Shop] tabs (" + category + "/" + id + "): 알 수 없는 icon '"
                    + iconStr + "' → STONE");
        }

        Object slotRaw = raw.get("slot");
        int slot;
        if (slotRaw instanceof Number n) slot = n.intValue();
        else {
            logger.warning("[Shop] tabs (" + category + "/" + id + "): slot 필드 누락 또는 잘못됨 — skip");
            return null;
        }
        if (slot < 0 || slot > 8) {
            logger.warning("[Shop] tabs (" + category + "/" + id + "): slot 은 0~8 이어야 함 — skip");
            return null;
        }

        Object def = raw.get("default");
        boolean isDefault = def instanceof Boolean b && b;

        // custom-model-data (선택, provider 미사용 시만 적용)
        int cmd = 0;
        Object cmdRaw = raw.get("custom-model-data");
        if (cmdRaw == null) cmdRaw = raw.get("customModelData");
        if (cmdRaw instanceof Number cn) cmd = Math.max(0, cn.intValue());

        // 아이콘 provider (선택). 지정 시 ItemStack 을 외부 provider 통해 받아옴
        // (예: icon-provider: Farming + icon-type: wheat + icon-variant: SEED → CropItemProvider.createSeed)
        String iconProvider = str(raw, "icon-provider");
        if (iconProvider == null) iconProvider = str(raw, "iconProvider");
        String iconType = str(raw, "icon-type");
        if (iconType == null) iconType = str(raw, "iconType");
        String iconVariant = str(raw, "icon-variant");
        if (iconVariant == null) iconVariant = str(raw, "iconVariant");
        if (iconVariant != null) iconVariant = iconVariant.toUpperCase(Locale.ROOT);

        // filter
        Set<String> variants = new HashSet<>();
        Set<Material> materials = new HashSet<>();
        Set<String> ids = new HashSet<>();
        Object filterRaw = raw.get("filter");
        if (filterRaw instanceof Map<?, ?> filterMap) {
            collectStrings(filterMap.get("variants"), variants, s -> s.toUpperCase(Locale.ROOT));
            collectStrings(filterMap.get("ids"), ids, s -> s);
            Object matRaw = filterMap.get("materials");
            if (matRaw instanceof List<?> list) {
                for (Object o : list) {
                    if (o == null) continue;
                    Material m = Material.matchMaterial(o.toString());
                    if (m != null) materials.add(m);
                    else logger.warning("[Shop] tabs (" + category + "/" + id
                            + "): 알 수 없는 material '" + o + "' — skip");
                }
            }
        }
        ShopTab.Filter filter = new ShopTab.Filter(
                Collections.unmodifiableSet(variants),
                Collections.unmodifiableSet(materials),
                Collections.unmodifiableSet(ids));

        return new ShopTab(id.toUpperCase(Locale.ROOT), name, icon, cmd,
                iconProvider, iconType, iconVariant,
                slot, isDefault, filter);
    }

    @SuppressWarnings("unchecked")
    private static void collectStrings(Object raw, Set<String> out, java.util.function.Function<String, String> mapper) {
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) out.add(mapper.apply(o.toString()));
            }
        }
    }

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    // ─── 조회 API ───

    public boolean hasTabs(ShopCategory category) {
        List<ShopTab> tabs = tabsByCategory.get(category);
        return tabs != null && !tabs.isEmpty();
    }

    public List<ShopTab> getTabs(ShopCategory category) {
        return tabsByCategory.getOrDefault(category, Collections.emptyList());
    }

    public Optional<ShopTab> getDefault(ShopCategory category) {
        List<ShopTab> tabs = getTabs(category);
        if (tabs.isEmpty()) return Optional.empty();
        return tabs.stream().filter(ShopTab::isDefault).findFirst()
                .or(() -> Optional.of(tabs.get(0)));
    }

    public Optional<ShopTab> getById(ShopCategory category, String id) {
        if (id == null) return Optional.empty();
        String upper = id.toUpperCase(Locale.ROOT);
        return getTabs(category).stream().filter(t -> t.id().equals(upper)).findFirst();
    }

    /** 탭 + 카테고리 조합으로 매칭되는 ShopItem 목록 조회. ShopItemRegistry에서 카테고리 아이템 받아 필터링. */
    public List<ShopItem> filterItems(ShopTab tab, List<ShopItem> categoryItems) {
        return categoryItems.stream().filter(tab.filter()::matches).collect(Collectors.toList());
    }
}
