package com.exit.core.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Fishing 플러그인이 노출하는 커스텀 물고기 생성 API.
 * 다른 플러그인 (CustomItems 의 오토릴 인챈트 등) 이 ServiceRegistry 경유로 호출.
 *
 * <p>tier 의미:
 * <ul>
 *   <li>1 → 소형 (cm 30~100, g 100~300)</li>
 *   <li>2 → 중형 (cm 101~250, g 301~800)</li>
 *   <li>3 → 대형 (cm 251~350, g 801~1700)</li>
 *   <li>4 → 초대형 (cm 351~500, g 1701~3000)</li>
 * </ul>
 *
 * <p>{@link #rollFish(int)} 는 1 ~ maxTier 사이에서 가중 랜덤 선택 후
 * 해당 구간 안에서 cm/g 을 롤한다. 등급/계절 보정/최고급 확률은 구현체가 관리.
 *
 * <p>사용 예시 (CustomItems 의 오토릴):
 * <pre>{@code
 * FishProvider fp = ServiceRegistry.get(FishProvider.class).orElse(null);
 * if (fp != null) {
 *     ItemStack fish = fp.rollFish(maxTier);  // 커스텀 물고기 ItemStack
 * }
 * }</pre>
 */
public interface FishProvider {

    /**
     * 현재 계절 + 지정 maxTier 한도 내에서 커스텀 물고기 1개 생성.
     *
     * @param maxTier 1~4 (범위 밖이면 clamp)
     * @return 커스텀 물고기 ItemStack. 어종 풀이 비어있는 등 불가 시 null
     */
    ItemStack rollFish(int maxTier);

    /**
     * AutoReel 같이 외부에서 잡았을 때 플레이어에게 "낚았다" 메시지 전송.
     * fishing 플러그인 내부의 메시지 포맷과 동일하게 출력.
     */
    void sendCatchMessage(Player player, ItemStack fish);
}
