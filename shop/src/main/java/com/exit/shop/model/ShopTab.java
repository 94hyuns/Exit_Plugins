package com.exit.shop.model;

import org.bukkit.Material;

import java.util.Set;

/**
 * 카테고리 내 GUI 탭 정의.
 *
 * <p><b>탭 아이콘 결정 우선순위</b>:
 * <ol>
 *   <li>{@code iconProvider} 지정 → CropItemProvider 등 외부 제공자 통해 발급된 ItemStack 사용
 *       (예: provider=Farming, type=wheat, variant=SEED → CropItemProvider.createSeed("wheat", 1))</li>
 *   <li>그 외 → {@code icon} Material + 선택적 {@code customModelData} 로 ItemStack 생성</li>
 * </ol>
 * provider 방식의 장점: 본문 아이템과 똑같이 PDC + CMD 박힌 ItemStack 이라
 * 리소스팩 텍스처가 자동으로 매칭됨.
 *
 * @param id              식별자 (영문 대문자/숫자/_). 액션 라우팅 키로 사용.
 * @param name            표시명 (legacy color code §a 등 지원)
 * @param icon            탭 버튼 Material (provider 미사용 시 기본)
 * @param customModelData 리소스팩 CMD 인덱스. 0이면 적용 안 함. (provider 미사용 시만 적용)
 * @param iconProvider    선택적 provider 키 ("Farming" / "CustomItems"). null 이면 Material 모드.
 * @param iconType        provider 내부 타입 (cropId, "FARMLAND_BLOCK", "LIFE" 등)
 * @param iconVariant     "SEED" / "FRUIT" / "TICKET" (Farming 분기). 그 외 provider 면 무시.
 * @param slot            탭이 표시될 슬롯 (0~8, 0행 내). 같은 카테고리 내 중복 금지.
 * @param isDefault       카테고리 첫 진입 시 이 탭으로 열림. true 인 탭이 여러개면 첫 번째가 우선.
 * @param filter          이 탭에 표시될 ShopItem 필터 조건
 */
public record ShopTab(
        String id,
        String name,
        Material icon,
        int customModelData,
        String iconProvider,
        String iconType,
        String iconVariant,
        int slot,
        boolean isDefault,
        Filter filter
) {

    public boolean hasCustomModelData() { return customModelData > 0; }

    /** provider 기반 아이콘 사용 여부 (true면 createTabButton이 ServiceRegistry 경유로 발급). */
    public boolean usesProvider() { return iconProvider != null; }

    /**
     * 탭 표시 대상 ShopItem 필터.
     */
    public record Filter(Set<String> variants, Set<Material> materials, Set<String> ids) {

        public boolean isEmpty() {
            return variants.isEmpty() && materials.isEmpty() && ids.isEmpty();
        }

        public boolean matches(ShopItem item) {
            if (isEmpty()) return true;
            if (!variants.isEmpty() && item.getVariant() != null && variants.contains(item.getVariant())) return true;
            if (!materials.isEmpty() && item.getMaterial() != null && materials.contains(item.getMaterial())) return true;
            if (!ids.isEmpty() && ids.contains(item.getId())) return true;
            return false;
        }
    }
}
