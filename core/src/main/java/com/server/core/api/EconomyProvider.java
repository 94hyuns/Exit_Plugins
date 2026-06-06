package com.server.core.api;

import java.util.UUID;

/**
 * 경제 시스템 인터페이스.
 * Economy 플러그인이 구현체를 ServiceRegistry에 등록하고,
 * 다른 플러그인(land 등)은 이 인터페이스만 통해 경제 기능을 사용한다.
 */
public interface EconomyProvider {

    /**
     * 플레이어 잔액 조회
     * @return 잔액 (미등록 플레이어는 -1)
     */
    long getBalance(UUID uuid);

    /**
     * 잔액 추가 (입금, 관리자 지급 등)
     * @return 성공 여부
     */
    boolean addBalance(UUID uuid, long amount);

    /**
     * 잔액 차감. 잔액 부족 시 false.
     * @return 성공 여부
     */
    boolean subtractBalance(UUID uuid, long amount);

    /**
     * 잔액 직접 설정 (관리자용)
     * @return 성공 여부
     */
    boolean setBalance(UUID uuid, long amount);
}
