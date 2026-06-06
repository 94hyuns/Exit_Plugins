package com.exit.shop.model;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 상점 아이템 등록소 — 100% config.yml 기반 (1.2.0~).
 *
 * <p><b>거래 방향 자동 판단</b>:
 * <ul>
 *   <li>config에 buy: 만 → 구매 전용 (씨앗·티켓·램프). buyable=true, sellable=false.</li>
 *   <li>config에 sell: 만 → 판매 전용 (열매). buyable=true(자동, sell × mul), sellable=true.</li>
 *   <li>config에 buy + sell → 양방향. buy 값은 무시하고 sell × buy-multiplier 자동 계산.</li>
 *   <li>둘 다 없음 → 등록 거부.</li>
 * </ul>
 *
 * <p><b>variant 자동 판단</b> (provider=Farming일 때):
 * <ul>
 *   <li>type="FARMLAND_BLOCK"           → variant=TICKET (인터페이스명 잔재)</li>
 *   <li>type=cropId, sellable           → variant=FRUIT</li>
 *   <li>type=cropId, !sellable          → variant=SEED</li>
 * </ul>
 *
 * <p>변환 후 GUI 라우팅:
 * <ul>
 *   <li>SEED+TICKET → 작물 상점 GUI의 "씨앗" 탭</li>
 *   <li>FRUIT       → 작물 상점 GUI의 "열매" 탭</li>
 * </ul>
 *
 * <p><b>buy-multiplier</b>: 항목별 배수. 미지정 시 기본 10.0.
 */
public class ShopItemRegistry {

    private final Map<String, ShopItem> items = new LinkedHashMap<>();

    public void loadFromConfig(File configFile, Logger logger) {
        if (!configFile.exists()) {
            logger.warning("[Shop] config.yml 없음 — 상점이 비어있음.");
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        List<Map<?, ?>> list = config.getMapList("items");
        if (list.isEmpty()) {
            logger.warning("[Shop] config.yml의 items: 섹션 비어있음.");
            return;
        }

        items.clear();
        int loaded = 0, skipped = 0;
        for (Map<?, ?> raw : list) {
            try {
                ShopItem item = parseEntry(raw, logger);
                if (item != null) {
                    if (items.containsKey(item.getId())) {
                        logger.warning("[Shop] id '" + item.getId() + "' 중복 — 덮어씀.");
                    }
                    items.put(item.getId(), item);
                    loaded++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Shop] 파싱 실패: " + raw, e);
                skipped++;
            }
        }
        logger.info("[Shop] config.yml 로드 — " + loaded + "종 등록 (" + skipped + "종 skip)");
    }

    public void reload(File configFile, Logger logger) {
        loadFromConfig(configFile, logger);
    }

    private ShopItem parseEntry(Map<?, ?> raw, Logger logger) {
        String id = str(raw, "id");
        String categoryStr = str(raw, "category");
        String provider = str(raw, "provider");
        String typeId = str(raw, "type");

        if (id == null || categoryStr == null) {
            logger.warning("[Shop] id 또는 category 누락 — skip: " + raw);
            return null;
        }

        ShopCategory category;
        try {
            category = ShopCategory.valueOf(categoryStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("[Shop] 알 수 없는 category '" + categoryStr + "' — skip: " + id);
            return null;
        }
        if (category == ShopCategory.FISHING) {
            logger.warning("[Shop] FISHING 카테고리에는 아이템 등록 불가 — skip: " + id);
            return null;
        }

        // 가격 — buy / sell 둘 중 하나는 있어야 함
        Long buyBoxed = numNullable(raw, "buy");
        Long sellBoxed = numNullable(raw, "sell");
        if (sellBoxed == null) sellBoxed = numNullable(raw, "sellPrice"); // 하위호환

        if (buyBoxed == null && sellBoxed == null) {
            logger.warning("[Shop] buy: 와 sell: 모두 없음 — skip: " + id);
            return null;
        }

        boolean sellable = sellBoxed != null;
        boolean buyable = sellable || buyBoxed != null; // sellable이면 자동 buyable

        long sellPriceBase = sellable ? sellBoxed : 0L;
        long buyPriceFixed = (!sellable && buyBoxed != null) ? buyBoxed : 0L;

        // buy multiplier (sellable에서만 의미. 명시 안 하면 기본 10.0)
        Object mulRaw = raw.get("buy-multiplier");
        if (mulRaw == null) mulRaw = raw.get("buyMultiplier");
        double buyMultiplier = 10.0;
        if (mulRaw instanceof Number n) buyMultiplier = n.doubleValue();
        else if (mulRaw instanceof String s) {
            try { buyMultiplier = Double.parseDouble(s); }
            catch (NumberFormatException e) {
                logger.warning("[Shop] '" + id + "' buy-multiplier 파싱 실패 → 10.0");
            }
        }
        if (buyMultiplier <= 0) {
            logger.warning("[Shop] '" + id + "' buy-multiplier <= 0 → 10.0으로 보정");
            buyMultiplier = 10.0;
        }

        // 표시명
        String displayName = str(raw, "displayName");
        if (displayName == null) displayName = str(raw, "name");
        if (displayName == null) displayName = id;

        // 아이콘 / Material
        String iconStr = str(raw, "icon");
        String materialStr = str(raw, "material");
        Material material = Material.BARRIER;
        if (materialStr != null) {
            Material parsed = Material.matchMaterial(materialStr);
            if (parsed != null) material = parsed;
            else logger.warning("[Shop] 알 수 없는 material '" + materialStr + "' → BARRIER (" + id + ")");
        } else if (iconStr != null) {
            Material parsed = Material.matchMaterial(iconStr);
            if (parsed != null) material = parsed;
            else logger.warning("[Shop] 알 수 없는 icon '" + iconStr + "' → BARRIER (" + id + ")");
        } else if (provider == null) {
            logger.warning("[Shop] provider/material/icon 모두 없음 — skip: " + id);
            return null;
        }

        // variant 결정
        String variant = inferVariant(provider, typeId, sellable);

        // 약간의 sanity check
        if ("Farming".equals(provider) && variant == null) {
            logger.warning("[Shop] '" + id + "' Farming provider인데 type 지정 없음 — skip");
            return null;
        }

        // GUI 옵션 플래그
        boolean buyDisabled = bool(raw, "buy-disabled", false);
        boolean bulkBuyDisabled = bool(raw, "bulk-buy-disabled", false);
        Long bulkBoxed = numNullable(raw, "buy-price-16");
        long buyPriceBulk = bulkBoxed != null ? bulkBoxed : 0L;

        // 추가 메타 옵션(바닐라 아이템 한정) — yml에 명시되어 있으면 raw로 보관.
        // 적용은 ShopListener / ShopGUI 가 ItemStack 만들 때 담당.
        Map<String, Object> meta = new LinkedHashMap<>();
        copyIfPresent(raw, meta, "display-name");
        copyIfPresent(raw, meta, "custom-model-data");
        copyIfPresent(raw, meta, "enchantments");
        copyIfPresent(raw, meta, "lore");

        return new ShopItem(id, material, displayName,
                buyPriceFixed, sellPriceBase, buyMultiplier,
                category, buyable, sellable,
                buyDisabled, bulkBuyDisabled, buyPriceBulk,
                provider, typeId, variant,
                meta.isEmpty() ? null : meta);
    }

    private static void copyIfPresent(Map<?, ?> src, Map<String, Object> dst, String key) {
        Object v = src.get(key);
        if (v != null) dst.put(key, v);
    }

    private String inferVariant(String provider, String typeId, boolean sellable) {
        if (!"Farming".equals(provider) || typeId == null) return null;
        // FARMLAND_BLOCK 가 정식. FARMLAND_TICKET 은 v1.1 호환용 별칭.
        if ("FARMLAND_BLOCK".equalsIgnoreCase(typeId) || "FARMLAND_TICKET".equalsIgnoreCase(typeId)) {
            return "TICKET";
        }
        return sellable ? "FRUIT" : "SEED";
    }

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static boolean bool(Map<?, ?> m, String key, boolean def) {
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return def;
    }

    private static Long numNullable(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    // ─── 조회 API ───

    public ShopItem get(String id) { return items.get(id); }

    public List<ShopItem> getByCategory(ShopCategory category) {
        return items.values().stream()
                .filter(i -> i.getCategory() == category)
                .collect(Collectors.toList());
    }

    public Collection<ShopItem> getAll() {
        return Collections.unmodifiableCollection(items.values());
    }
}
