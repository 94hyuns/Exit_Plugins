package com.exit.customitems.lamp.listener;

import com.exit.customitems.lamp.WildernessChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.List;

/**
 * 마을월드(= wilderness 아닌 월드) 에서 지옥문 설치 차단.
 *
 * <p>플레이어가 흑요석 프레임 + 부싯돌·강철로 지옥문을 점화하려고 할 때
 * 발생하는 {@link PortalCreateEvent} (reason = FIRE) 를 받아서 cancel.
 * 야생 월드(world_wild, world_nether, world_the_end 등) 에서는 정상 작동.
 *
 * <p>NETHER_PAIR (네더 측 자동 페어 생성) 는 양쪽 차원 모두 야생이라
 * 정상 작동 — 별도 차단 불필요.
 */
public class NetherPortalListener implements Listener {

    private final WildernessChecker wilderness;

    public NetherPortalListener(WildernessChecker wilderness) {
        this.wilderness = wilderness;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onPortalCreate(PortalCreateEvent event) {
        // 플레이어가 점화한 경우만 차단 (NETHER_PAIR 자동 생성은 양쪽 다 야생이므로 통과)
        if (event.getReason() != PortalCreateEvent.CreateReason.FIRE) return;

        List<BlockState> blocks = event.getBlocks();
        if (blocks.isEmpty()) return;

        // 첫 블록의 위치로 월드 판정 (포털 모든 블록이 같은 월드)
        if (wilderness.isWilderness(blocks.get(0).getLocation())) return;

        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            player.sendMessage(Component.text(
                "이 월드에서는 지옥문을 만들 수 없습니다.", NamedTextColor.RED));
        }
    }
}
