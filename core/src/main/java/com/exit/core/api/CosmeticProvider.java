package com.exit.core.api;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 치장 아이템 제공 인터페이스.
 * Cosmetics 플러그인이 구현체를 ServiceRegistry에 등록하고,
 * Shop / 옷장 GUI 등 다른 플러그인은 이 인터페이스만 통해 치장을 다룬다.
 *
 * <p>LampProvider와의 차이: 램프는 인벤토리 아이템이라 createLamp(ItemStack)이 주 API지만,
 * 치장은 <b>DB 소유권 플래그</b>라 grantCosmetic(uuid, id)이 주 API다.
 * Shop에서 구매 시 인벤토리 addItem 대신 grantCosmetic을 호출한다.
 *
 * <p>사용 예시 (Shop 플러그인, 구매 시점):
 * <pre>{@code
 * CosmeticProvider cos = ServiceRegistry.get(CosmeticProvider.class).orElse(null);
 * if (cos == null) return;
 * if (cos.hasCosmetic(uuid, "crown_gold")) {
 *     player.sendMessage("이미 보유 중입니다.");
 *     return;
 * }
 * // ... 결제 ...
 * cos.grantCosmetic(uuid, "crown_gold");
 * }</pre>
 */
public interface CosmeticProvider {

    // ─── 카탈로그 ───

    /** 등록된 모든 치장의 메타데이터. 상점 config 작성이나 옷장 GUI 구성에 사용. */
    List<CosmeticInfo> listDefinitions();

    /** cosmeticId로 단일 치장 정의 조회. 없으면 empty. */
    Optional<CosmeticInfo> find(String cosmeticId);

    // ─── 소유권 ───

    /**
     * 플레이어에게 치장을 지급 (소유권 부여). Shop 구매 시 호출.
     *
     * @return 지급 성공 시 true. cosmeticId가 존재하지 않거나 이미 보유 중이면 false.
     */
    boolean grantCosmetic(UUID uuid, String cosmeticId);

    /** 플레이어의 해당 치장 소유 여부. */
    boolean hasCosmetic(UUID uuid, String cosmeticId);

    /** 플레이어가 보유한 모든 cosmeticId 집합. 없으면 빈 Set. */
    Set<String> listOwned(UUID uuid);

    // ─── 장착 ───

    /**
     * 플레이어가 cosmeticId를 장착. 같은 슬롯(HAT/WING/TRAIL)에 이미 다른 치장이
     * 장착되어 있다면 자동 교체. 미보유면 false.
     */
    boolean equipCosmetic(UUID uuid, String cosmeticId);

    /** 특정 슬롯의 치장 해제. 해제 성공 시 true, 원래 미장착이면 false. */
    boolean unequipCosmetic(UUID uuid, String slot);

    /**
     * 특정 슬롯에 장착 중인 cosmeticId 조회.
     *
     * @param slot "HAT" / "WING" / "TRAIL" (대소문자 구분 없음)
     */
    Optional<String> getEquipped(UUID uuid, String slot);

    // ─── 상점 전시용 ───

    /**
     * 상점 GUI / 옷장 GUI에 표시할 샘플 아이콘. 리소스팩 CustomModelData가 적용된 ItemStack.
     * 치장 실체(장착/해제)와는 무관하며, 오직 전시 목적.
     *
     * @return cosmeticId가 존재하지 않으면 null.
     */
    ItemStack createShowcaseItem(String cosmeticId);
}
