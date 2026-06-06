package com.exit.job.model;

/**
 * 직업 능력 정의 (config.yml에서 로드).
 *
 * <p>{@code id} 는 효과 구현체와 연결되는 키. 예) {@code max_health_1, ore_drop_x2_chance, lava_immunity}.
 * 텍스트(name/description/level)는 yml 자유 수정 가능, id 만 유지하면 효과는 그대로 동작.
 */
public record PerkInfo(String id, int level, String name, String description) {}
