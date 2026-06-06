package com.exit.job.perk;

import org.bukkit.entity.Player;

/**
 * 능력 효과 부여/제거 인터페이스.
 *
 * <p>Attribute / PotionEffect 처럼 "상태"로 적용되는 능력만 구현.
 * 이벤트 분기가 필요한 능력 (확률, 수확 광역화 등) 은 별도 Listener 에서
 * JobManager.getLevel() 직접 조회하므로 PerkApplier 가 필요 없다.
 *
 * <p>{@link #apply}/{@link #remove} 는 모두 멱등.
 */
public interface PerkApplier {
    void apply(Player player);
    void remove(Player player);
}
