package com.exit.job.perk;

import com.exit.job.manager.JobConfigManager;
import com.exit.job.manager.JobManager;
import com.exit.job.model.JobDefinition;
import com.exit.job.model.JobType;
import com.exit.job.model.PerkInfo;
import org.bukkit.entity.Player;

/**
 * 플레이어 입장/레벨 변경 시 해금된 능력의 PerkApplier.apply 를,
 * 잠긴 능력의 PerkApplier.remove 를 호출.
 */
public class PerkApplyManager {

    private final JobManager jobManager;
    private final JobConfigManager configManager;
    private final PerkRegistry registry;

    public PerkApplyManager(JobManager jobManager, JobConfigManager configManager, PerkRegistry registry) {
        this.jobManager = jobManager;
        this.configManager = configManager;
        this.registry = registry;
    }

    /** 한 직업의 능력만 다시 적용. addExp 로 레벨이 변경됐을 때 호출. */
    public void reapply(Player player, JobType type) {
        JobDefinition def = configManager.getDefinition(type);
        if (def == null) return;
        int level = jobManager.getLevel(player.getUniqueId(), type);
        for (PerkInfo perk : def.perks()) {
            PerkApplier applier = registry.get(type, perk.id());
            if (applier == null) continue;
            if (level >= perk.level()) applier.apply(player);
            else applier.remove(player);
        }
    }

    /** 입장 시 모든 직업의 해금 능력 적용. */
    public void applyAll(Player player) {
        for (JobType t : JobType.values()) reapply(player, t);
    }

    /** 퇴장 시 모든 PerkApplier 제거 (Attribute 영구 누수 방지). */
    public void removeAll(Player player) {
        for (JobType t : JobType.values()) {
            JobDefinition def = configManager.getDefinition(t);
            if (def == null) continue;
            for (PerkInfo perk : def.perks()) {
                PerkApplier applier = registry.get(t, perk.id());
                if (applier != null) applier.remove(player);
            }
        }
    }
}
