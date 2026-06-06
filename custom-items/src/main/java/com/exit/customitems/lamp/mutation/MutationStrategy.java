package com.exit.customitems.lamp.mutation;

import com.exit.customitems.lamp.enchant.RolledEnchant;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 변성램프 적용 시 카테고리별 동작을 정의하는 전략 인터페이스.
 *
 * <p>현재는 무기({@link WeaponMutationStrategy}) 만 구현. 추후 방어구/생활도구 등 확장 가능.
 * MutationApplier 가 대상 아이템에 맞는 strategy 를 선택해서 호출.
 */
public interface MutationStrategy {

    /** 이 strategy 가 해당 아이템을 처리할 수 있는지. */
    boolean canApply(ItemStack target);

    /**
     * 성공 시 호출. 대상에 추가될 보너스 인챈트 라인들을 생성.
     * 기존 인챈트와 합쳐서 저장하는 책임은 호출자에게 있음.
     *
     * <p>초대박(MEGA_SUCCESS) 케이스에서는 MutationApplier 가 본 메서드를 2회 호출 —
     * 1차 결과를 existing 에 합쳐 2차를 굴리면 자연스럽게 중복이 제거된다.
     *
     * @param target  대상 아이템 (변경 안 함, 단지 컨텍스트 조회용)
     * @param existing 이미 부여된 인챈트 목록 (중복 회피 등 참고)
     * @return 추가할 인챈트 라인들. 빈 리스트면 추가할 게 없음 (성공이지만 풀이 빈 경우).
     */
    List<RolledEnchant> rollBonus(ItemStack target, List<RolledEnchant> existing);

    /**
     * 이 strategy 가 사용하는 결과 확률.
     * <p>합: success + megaSuccess + destroy + fail(=나머지) = 1.0.
     * <p>default = 기존 동작 (방어구 호환): 70% 성공 / 0% 초대박 / 10% 파괴 / 20% 실패.
     */
    default Probabilities probabilities() {
        return new Probabilities(0.70, 0.00, 0.10);
    }

    /**
     * 결과 확률 묶음.
     * @param success      일반 성공 (UNIQUE 1줄 추가)
     * @param megaSuccess  초대박 (UNIQUE 2줄 추가, 황금 이름)
     * @param destroy      파괴 (아이템 소실)
     */
    record Probabilities(double success, double megaSuccess, double destroy) {}
}
