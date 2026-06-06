package com.exit.job.api.impl;

import com.exit.job.api.MiningExpHook;
import com.exit.job.manager.JobConfigManager;
import com.exit.job.manager.JobManager;
import com.exit.job.model.JobType;
import org.bukkit.Material;

import java.util.UUID;

public class MiningExpHookImpl implements MiningExpHook {

    private final JobManager jobManager;
    private final JobConfigManager configManager;

    public MiningExpHookImpl(JobManager jobManager, JobConfigManager configManager) {
        this.jobManager = jobManager;
        this.configManager = configManager;
    }

    @Override
    public void grantOreBreak(UUID player, Material oreType) {
        if (player == null || oreType == null) return;
        Integer exp = configManager.miningOreExp().get(oreType);
        if (exp == null || exp <= 0) return;
        int scaled = configManager.applyMiningExpMultiplier(exp);
        jobManager.addExp(player, JobType.MINER, scaled);
    }
}
