package com.exit.customitems.lamp.enchant;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 램프로 부여 가능한 커스텀 인챈트 하나를 정의한다. 2단계에서 인챈트 24종을 이 인터페이스로 구현.
 *
 * <p><b>2단계에서의 구현 흐름:</b>
 * <ol>
 *   <li>이 인터페이스를 구현한 클래스를 {@code impl/} 아래에 만들기</li>
 *   <li>{@code CustomItemsPlugin#onEnable} 의 registry.register(...) 블록에 등록</li>
 *   <li>실제 효과는 해당 클래스 내부의 이벤트 리스너 또는 별도 Handler에서 처리</li>
 * </ol>
 */
public interface CustomEnchant {

    /** 이 인챈트의 고유 식별자. PDC 저장 및 조회에 사용. */
    NamespacedKey getKey();

    /** 로어에 표시될 표시명. 예: "생명력 흡수", "크리티컬 확률". */
    String getDisplayName();

    /** 속하는 램프 카테고리 (LIFE/COMBAT). */
    EnchantCategory getCategory();

    /**
     * 적용 범위 분류. 기본값은 {@link EnchantScope#TOOL_SPECIFIC}.
     * 카테고리의 모든 도구에 적용 가능한 공통 인챈트는 {@link EnchantScope#COMMON} 으로 오버라이드.
     * 이 값이 롤 가중치 계산에 사용됨.
     */
    default EnchantScope getScope() { return EnchantScope.TOOL_SPECIFIC; }

    /**
     * 전투 인챈트의 줄 분류. COMBAT 카테고리에서만 의미가 있으며, 1줄 롤 시 BASIC 풀에서,
     * 2줄 롤 시 BASIC 1개 + SET 1개로 분리 롤된다. LIFE 카테고리에서는 무시된다.
     * 기본값은 {@link EnchantTier#BASIC}.
     */
    default EnchantTier getTier() { return EnchantTier.BASIC; }

    /**
     * 같은 도구에 이 인챈트가 여러 줄 동시 붙을 수 있는지.
     * <p>true: 확률+개수 기반 인챈트. 한 도구에 중복 롤 가능, 각 줄이 독립적으로 발동한다.
     * <p>false(기본): 레벨/스위치형 인챈트. 한 도구에는 한 줄만 존재.
     */
    default boolean isStackable() { return false; }

    /** 발동 트리거. */
    EnchantTrigger getTrigger();

    /**
     * 이 인챈트가 갖는 수치값들의 스펙. 램프가 이 인챈트를 뽑으면 각 스펙마다 값을 하나씩 롤한다.
     * 순서는 구현마다 고정이며, {@link #renderLore(int[])} 에서 같은 순서로 참조.
     */
    List<ValueSpec> getValueSpecs();

    /**
     * 이 인챈트를 해당 아이템에 붙일 수 있는지 판정.
     * 예: "공격력"은 검/도끼/쇠뇌 등 무기에만, "체력 칸 증가"는 방어구에만.
     */
    boolean canApplyTo(ItemStack item);

    /**
     * 롤된 값들을 받아 로어에 표시할 한 줄 Component를 생성.
     * values.length == getValueSpecs().size().
     */
    Component renderLore(int[] values);
}
