package com.exit.job.api;

import org.bukkit.Material;

import java.util.UUID;

/**
 * Job 의 광부 EXP 외부 노출 hook.
 *
 * <p>BlockBreakEvent 가 발생하지 않는 경로 (예: custom-items 의 십자/폭발 채굴 부수
 * 블록) 에서 광물 1칸 깨질 때마다 직접 호출. 구현체가 mining.ores config 를 보고
 * 매칭 시 광부 EXP 부여, 비매칭이면 no-op.
 *
 * <p>Silk-touch 검사 / 월드 검사 / 게임모드 검사는 호출 측 책임 (호출하지 않으면 됨).
 *
 * <p>등록: {@code com.exit.core.registry.ServiceRegistry.register(MiningExpHook.class, ...)}
 */
public interface MiningExpHook {
    void grantOreBreak(UUID player, Material oreType);
}
