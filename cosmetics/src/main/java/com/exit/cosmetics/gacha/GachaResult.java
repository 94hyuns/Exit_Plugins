package com.exit.cosmetics.gacha;

import com.exit.cosmetics.model.CosmeticDefinition;

/**
 * 단일 뽑기 결과.
 *
 * @param definition 뽑힌 치장
 * @param duplicate  중복 여부 (true면 가루로 변환됨)
 * @param shardsGained 중복 변환으로 얻은 가루 (duplicate=false면 0)
 */
public record GachaResult(CosmeticDefinition definition, boolean duplicate, long shardsGained) {}
