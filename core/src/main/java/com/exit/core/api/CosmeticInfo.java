package com.exit.core.api;

/**
 * 치장 아이템 메타데이터. 상점/옷장 GUI에서 카탈로그를 구성할 때 사용.
 *
 * @param cosmeticId  치장 식별자 (예: "crown_gold"). 대소문자 구분.
 * @param type        치장 타입 ("HAT" / "WING" / "TRAIL"). Cosmetics.CosmeticType enum과 문자열 매칭.
 * @param displayName 사용자에게 보여줄 이름 (예: "황금 왕관")
 * @param description 간단한 설명 (예: "왕의 풍모")
 */
public record CosmeticInfo(String cosmeticId, String type, String displayName, String description) {}
