package com.exit.core.api;

/**
 * 작물 타입 메타데이터. Shop 등 외부 플러그인이 작물 카탈로그를 구성할 때 사용.
 *
 * @param cropId       작물 식별자 (소문자 영문, 예: "wheat", "tomato")
 * @param koreanName   표시명 (예: "밀", "토마토")
 * @param customModelData 리소스팩 CMD 인덱스 (1~13)
 * @param repeatable   우클릭 수확 가능 여부 (포도/블루베리/크랜베리/완두콩/토마토)
 */
public record CropInfo(String cropId, String koreanName, int customModelData, boolean repeatable) {}
