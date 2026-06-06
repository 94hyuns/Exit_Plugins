package com.exit.customitems.lamp;

import org.bukkit.Location;

/**
 * "야생 한정" 인챈트 발동 가능 지역인지 판정.
 *
 * <p>1단계에서는 config.yml 의 {@code wilderness-worlds} 에 지정된 월드 이름 매칭.
 * 추후 land 플러그인이 도입되면 이 클래스의 구현을 "클레임되지 않은 청크인지" 기준으로 교체.
 * 외부 호출부(리스너)는 이 API만 사용하므로 교체 시 영향 없음.
 */
public class WildernessChecker {

    private final LampConfig config;

    public WildernessChecker(LampConfig config) {
        this.config = config;
    }

    public boolean isWilderness(Location location) {
        if (location == null || location.getWorld() == null) return false;
        return config.getWildernessWorlds().contains(location.getWorld().getName());
    }
}
