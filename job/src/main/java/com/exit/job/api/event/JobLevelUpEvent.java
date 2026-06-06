package com.exit.job.api.event;

import com.exit.job.model.JobType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 플레이어가 직업 레벨업했을 때 fire 되는 이벤트.
 *
 * <p>한 번에 여러 레벨 점프해도 한 번만 fire (newLevel 만 보고). oldLevel 은 변경 직전 값.
 * /직업관리 setlevel 로 강제 설정한 경우는 fire 되지 않음 (실제 자연 누적 / addExp 만).
 *
 * <p>예) 어부 Lv1 → addExp 큰 값으로 Lv5 점프 시: oldLevel=1, newLevel=5.
 */
public class JobLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final JobType type;
    private final int oldLevel;
    private final int newLevel;

    public JobLevelUpEvent(Player player, JobType type, int oldLevel, int newLevel) {
        this.player = player;
        this.type = type;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public Player getPlayer() { return player; }
    public JobType getType() { return type; }
    /** "miner" / "fisher" / "farmer". */
    public String getJobId() { return type.id(); }
    public int getOldLevel() { return oldLevel; }
    public int getNewLevel() { return newLevel; }

    @NotNull
    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}
