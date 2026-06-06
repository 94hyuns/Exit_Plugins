package com.exit.farming.farmland;

/**
 * 월드별 농사 정책.
 *
 * FREE       : 기본 바닐라 (씨앗 심기/괭이질 자유). FREE 월드에선 클레임 시스템이 동작하지 않음.
 * MANAGED    : 클레임된 경작지 위에서만 농사 가능. 괭이질로 흙→farmland 불가. 티켓으로만 가능.
 * FORBIDDEN  : 농사 완전 금지 (씨앗 심기 차단, 괭이질 차단).
 */
public enum FarmlandPolicy {
    FREE,
    MANAGED,
    FORBIDDEN
}
