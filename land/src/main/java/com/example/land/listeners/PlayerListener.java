package com.example.land.listeners;

import com.example.land.LandPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final LandPlugin plugin;

    public PlayerListener(LandPlugin plugin) {
        this.plugin = plugin;
    }

    /** 퇴장 시 정보 모드 정리 */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getInfoModeManager().remove(event.getPlayer().getUniqueId());
    }

    /** 청크 이동 시 ActionBar 즉시 갱신은 InfoModeManager 타이머가 처리하므로
     *  여기선 별도 처리 불필요. 필요 시 청크 변경 감지 로직 추가 가능. */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // 청크가 바뀐 경우에만 처리 (성능 최적화)
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        // 타이머가 주기적으로 ActionBar를 갱신하므로 별도 처리 없음
    }
}
