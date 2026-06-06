package com.exit.job.api;

import java.util.UUID;

/**
 * Job 의 어부 EXP 외부 노출 hook.
 *
 * <p>PlayerFishEvent.CAUGHT_FISH 가 발생하지 않는 경로 (예: custom-items 의 AutoReel
 * 인챈트가 hook 을 직접 reelIn 하고 dropItem 으로 처리) 에서 물고기 1마리 잡힐 때마다
 * 직접 호출. 구현체가 fishing config 의 exp-per-catch × premium-multiplier 적용 후 부여.
 *
 * <p>등록: {@code com.exit.core.registry.ServiceRegistry.register(FishingExpHook.class, ...)}
 */
public interface FishingExpHook {
    void grantCatch(UUID player, boolean premium);
}
