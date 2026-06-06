package com.exit.shop.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.Map;

/**
 * 상점에서 거래 가능한 아이템 정의.
 *
 * <p><b>거래 방향</b> (config 항목으로 결정):
 * <ul>
 *   <li>buyable  : config에 buy: 또는 sell: 둘 중 하나라도 있으면 true</li>
 *   <li>sellable : config에 sell: 가 있으면 true</li>
 * </ul>
 *
 * <p><b>가격 모델</b>:
 * <ul>
 *   <li>판매 가능 (sell 있음): sellPriceBase 저장. buy = currentSellPrice × buyMultiplier 자동 계산.</li>
 *   <li>판매 불가, 구매만 (씨앗·티켓·램프): buyPriceFixed 고정값 저장.</li>
 * </ul>
 *
 * <p><b>buyMultiplier</b>: 품목별 배수. config에 명시 안 하면 기본 10.0.
 * sellable 아이템에서만 의미 있음.
 *
 * <p><b>provider</b>:
 * <ul>
 *   <li>null              : 바닐라 (Material 기반)</li>
 *   <li>"CustomItems"     : 램프</li>
 *   <li>"Farming"         : 작물(씨앗/열매), 경작지 티켓</li>
 * </ul>
 *
 * <p><b>variant</b>: Farming 카테고리 분기 + GUI 탭 분류용.
 * <ul>
 *   <li>"FRUIT"  : 작물 열매 (열매 탭, 판매 가능)</li>
 *   <li>"SEED"   : 작물 씨앗 (씨앗 탭, 구매 전용)</li>
 *   <li>"TICKET" : 경작지 티켓 (씨앗 탭, 구매 전용)</li>
 *   <li>null     : 그 외 (바닐라/램프)</li>
 * </ul>
 */
public class ShopItem {

    private final String id;
    private final Material material;
    private final String displayName;
    private final long buyPriceFixed;       // 구매 전용 아이템에서만 의미. sellable이면 0.
    private final long sellPriceBase;       // 판매 가능 아이템의 base sell. 구매 전용이면 0.
    private final double buyMultiplier;     // sellable 아이템의 buy = sell × this. 기본 10.0.
    private final ShopCategory category;
    private final boolean buyable;
    private final boolean sellable;
    /** GUI 에서 구매 버튼 자체를 숨김. sellable 아이템(광물·열매)에 buy 차단용. */
    private final boolean buyDisabled;
    /** GUI 에서 16개 묶음 구매 버튼만 숨김 (1개 구매는 유지). 인챈트북·변성램프용. */
    private final boolean bulkBuyDisabled;
    /** 16개 묶음 구매 가격 override. 0 이면 단가 × 16. 램프 묶음 할인 등. */
    private final long buyPriceBulk;
    private final String provider;
    private final String typeId;
    private final String variant;
    /** 바닐라 아이템에만 의미. yml의 추가 메타 필드(display-name / custom-model-data / enchantments / lore)를 raw로 보관. */
    private final Map<String, Object> meta;

    public ShopItem(String id, Material material, String displayName,
                    long buyPriceFixed, long sellPriceBase, double buyMultiplier,
                    ShopCategory category, boolean buyable, boolean sellable,
                    boolean buyDisabled, boolean bulkBuyDisabled, long buyPriceBulk,
                    String provider, String typeId, String variant,
                    Map<String, Object> meta) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.buyPriceFixed = buyPriceFixed;
        this.sellPriceBase = sellPriceBase;
        this.buyMultiplier = buyMultiplier;
        this.category = category;
        this.buyable = buyable;
        this.sellable = sellable;
        this.buyDisabled = buyDisabled;
        this.bulkBuyDisabled = bulkBuyDisabled;
        this.buyPriceBulk = buyPriceBulk;
        this.provider = provider;
        this.typeId = typeId;
        this.variant = variant;
        this.meta = meta == null ? Collections.emptyMap() : meta;
    }

    public String getId() { return id; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public long getBuyPriceFixed() { return buyPriceFixed; }
    public long getSellPriceBase() { return sellPriceBase; }
    public double getBuyMultiplier() { return buyMultiplier; }
    public ShopCategory getCategory() { return category; }
    public boolean isBuyable() { return buyable && !buyDisabled; }
    public boolean isSellable() { return sellable; }
    public boolean isBulkBuyDisabled() { return bulkBuyDisabled || buyDisabled; }
    public long getBuyPriceBulk() { return buyPriceBulk; }
    public String getProvider() { return provider; }
    public String getTypeId() { return typeId; }
    public String getVariant() { return variant; }
    public Map<String, Object> getMeta() { return meta; }

    public boolean isCustomItem() { return provider != null; }
    public boolean hasFixedBuyPrice() { return !sellable; }
}
