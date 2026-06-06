package com.exit.core.api;

import java.util.UUID;

/**
 * 경제 시스템 인터페이스.
 * Economy 플러그인이 구현체를 ServiceRegistry에 등록하고,
 * Land, NPC상점 등 다른 플러그인은 이 인터페이스만 통해 경제 기능을 사용한다.
 */
public interface EconomyProvider {

    /** 잔액 조회. 미등록 플레이어는 -1 반환 */
    long getBalance(UUID uuid);

    /** 잔액 추가 (입금, 관리자 지급 등). 성공 시 true */
    boolean addBalance(UUID uuid, long amount);

    /** 잔액 차감. 잔액 부족 시 false */
    boolean subtractBalance(UUID uuid, long amount);

    /** 잔액 직접 설정 (관리자용). 성공 시 true */
    boolean setBalance(UUID uuid, long amount);
}
