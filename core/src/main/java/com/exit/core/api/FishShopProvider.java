package com.exit.core.api;

import org.bukkit.entity.Player;

/**
 * 낚시 상점 GUI 오픈 인터페이스.
 * Fishing 플러그인이 구현체를 ServiceRegistry에 등록하고,
 * Shop 플러그인은 FISHING 카테고리 NPC와 상호작용 시 이 인터페이스를 통해
 * 낚시 상점 GUI를 오픈한다.
 *
 * <p>낚시는 일반 Shop의 정적 가격 모델(buy/sell 고정)과 본질이 다르다:
 * 물고기마다 길이(cm)/질량(g)이 다르고, 계절에 따라 가격 배수가 달라진다.
 * 따라서 Shop의 Chest GUI가 아닌 Fishing 전용 GUI가 필요하며,
 * 그 진입점만 Shop이 관리하고 실제 로직은 Fishing에 남긴다.
 *
 * <p>사용 예시 (Shop 플러그인):
 * <pre>{@code
 * FishShopProvider fish = ServiceRegistry.get(FishShopProvider.class).orElse(null);
 * if (fish != null) fish.openShop(player);
 * }</pre>
 */
public interface FishShopProvider {

    /** 플레이어에게 낚시 상점 GUI를 연다 (계절별 제철 표시, 전체 판매 배럴, 도감). */
    void openShop(Player player);
}
