package com.exit.fishing.fish;

import com.exit.fishing.season.Season;

/**
 * 물고기 종 정의.
 * - id: 내부 식별자 (영문 소문자)
 * - koreanName: 표시명
 * - customModelData: 리소스팩 CMD 인덱스 (1~24)
 * - season: 제철 계절
 * - description: 도감 설명
 */
public record FishSpecies(
        String id,
        String koreanName,
        int customModelData,
        Season season,
        String description
) {}
