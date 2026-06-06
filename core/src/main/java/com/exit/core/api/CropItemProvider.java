package com.exit.core.api;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 작물 씨앗/열매 아이템 제공 인터페이스.
 * Farming 플러그인이 구현체를 ServiceRegistry에 등록하고,
 * Shop 등 다른 플러그인은 이 인터페이스만 통해 작물 아이템을 다룬다.
 *
 * <p>LampProvider와 유사한 패턴이지만, 작물은 "씨앗"과 "열매" 두 종류가 있어서
 * API가 두 쌍으로 나뉜다. 또한 일반/최고급(premium) 구분이 있는 것도 특징.
 *
 * <p>사용 예시 (Shop 플러그인, 구매 시점):
 * <pre>{@code
 * CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
 * if (crops == null) return;
 * ItemStack item = crops.createFruit("wheat", 1, false);
 * player.getInventory().addItem(item);
 * }</pre>
 *
 * <p>사용 예시 (Shop 플러그인, 판매 시점 — 플레이어 인벤토리에서 특정 작물 찾기):
 * <pre>{@code
 * for (ItemStack stack : player.getInventory().getContents()) {
 *     String cropId = crops.identifyFruit(stack);
 *     if ("wheat".equals(cropId)) { ... }
 * }
 * }</pre>
 */
public interface CropItemProvider {

    // ─── 카탈로그 ───

    /** 등록된 모든 작물의 메타데이터. Shop config 작성이나 카탈로그 GUI 구성에 사용. */
    List<CropInfo> listCrops();

    // ─── 씨앗 ───

    /**
     * 작물 씨앗 ItemStack 생성.
     * @param cropId 작물 식별자 ("wheat", "tomato" 등). 대소문자 무시.
     * @param amount 개수 (1~64)
     * @return 씨앗 ItemStack. cropId 미등록이면 null.
     */
    ItemStack createSeed(String cropId, int amount);

    /** 주어진 ItemStack이 우리 씨앗이면 cropId, 아니면 null. */
    String identifySeed(ItemStack item);

    // ─── 열매 ───

    /**
     * 작물 열매 ItemStack 생성.
     * @param cropId  작물 식별자
     * @param amount  개수 (1~64)
     * @param premium 최고급 여부 (가격 ×2 관례, Shop에서 활용)
     * @return 열매 ItemStack. cropId 미등록이면 null.
     */
    ItemStack createFruit(String cropId, int amount, boolean premium);

    /** 주어진 ItemStack이 우리 열매이면 cropId, 아니면 null. */
    String identifyFruit(ItemStack item);

    /** 주어진 ItemStack이 "최고급" 플래그를 가진 열매인지. 열매가 아니면 false. */
    boolean isPremium(ItemStack item);
}
