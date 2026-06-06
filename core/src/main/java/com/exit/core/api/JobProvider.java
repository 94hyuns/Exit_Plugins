package com.exit.core.api;

import java.util.UUID;

/**
 * 직업 시스템 통합 인터페이스.
 *
 * <p>Job 플러그인이 구현체를 ServiceRegistry에 등록하고,
 * Fishing/Farming/Mining 등 행위 플러그인이 이 인터페이스로 EXP 부여.
 *
 * <p>jobId 표준값: "miner", "fisher", "farmer"
 *
 * <p>사용 예 (Fishing 측):
 * <pre>{@code
 * JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
 * if (jobs != null) jobs.addExp(player.getUniqueId(), "fisher", 10);
 * }</pre>
 */
public interface JobProvider {

    /**
     * 플레이어의 특정 직업에 EXP 추가.
     * 레벨업 자동 처리 (필요 EXP 누적치 도달 시 다음 레벨로 자동 진행 + 메시지/사운드).
     */
    void addExp(UUID player, String jobId, long amount);

    /** 현재 레벨. 데이터 없으면 1. */
    int getLevel(UUID player, String jobId);

    /** 현재 레벨에서의 EXP (다음 레벨까지 진행도). 데이터 없으면 0. */
    long getExp(UUID player, String jobId);
}
