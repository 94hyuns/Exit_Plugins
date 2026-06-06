package com.exit.core.api;

import java.util.UUID;

/**
 * 상점 거래 통계 기록 API.
 * Shop 플러그인이 구현체를 ServiceRegistry 에 등록.
 *
 * <p>다른 플러그인(Fishing 등) 이 자체 GUI 에서 처리한 거래를 Shop 의 일일 리포트에
 * 합산시키기 위해 사용한다. Shop 내부 GUI 거래는 ShopListener/BulkSellService 가 직접 기록.
 */
public interface ShopStatsRecorder {

    /**
     * 낚시 상인 판매 기록. (낚시 플러그인이 매 sell 직후 호출)
     *
     * @param seller   판매 플레이어
     * @param revenue  지급된 총 금액
     * @param fishCount 판매된 어획 수
     */
    void recordFishSell(UUID seller, long revenue, int fishCount);
}
