package com.exit.shop.model;

/**
 * 상점 카테고리.
 * MINERAL: 광물 판매 NPC가 취급
 * CROP: 작물 판매 NPC가 취급
 * LAMP: 램프 상인 NPC가 취급 (커스텀 아이템, 구매 전용)
 */
public enum ShopCategory {
    MINERAL("광물 판매", "§6광물 상점"),
    CROP("작물 판매", "§a작물 상점"),
    LAMP("램프 상인", "§d램프 상점"),
    FISHING("낚시 상인", "§b낚시 상점"),
    GENERAL("잡화 상인", "§e잡화 상점"),
    DUNGEON("던전 상인", "§5던전 매입소"),
    COOKING("요리 상인", "§c요리 재료점");

    private final String npcName;
    private final String guiTitle;

    ShopCategory(String npcName, String guiTitle) {
        this.npcName = npcName;
        this.guiTitle = guiTitle;
    }

    public String getNpcName() { return npcName; }
    public String getGuiTitle() { return guiTitle; }

    /**
     * 일반 Shop GUI(ChestGUI) 를 띄우는 카테고리인지 여부.
     * FISHING 은 전용 GUI(FishShopProvider) 로 라우팅되므로 false.
     */
    public boolean usesDefaultGui() {
        return this != FISHING;
    }
}
