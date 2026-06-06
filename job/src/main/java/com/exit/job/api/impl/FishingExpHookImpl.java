package com.exit.job.api.impl;

import com.exit.job.api.FishingExpHook;
import com.exit.job.manager.JobConfigManager;
import com.exit.job.manager.JobManager;
import com.exit.job.model.JobType;

import java.util.UUID;

public class FishingExpHookImpl implements FishingExpHook {

    private final JobManager jobManager;
    private final JobConfigManager configManager;

    public FishingExpHookImpl(JobManager jobManager, JobConfigManager configManager) {
        this.jobManager = jobManager;
        this.configManager = configManager;
    }

    @Override
    public void grantCatch(UUID player, boolean premium) {
        if (player == null) return;
        int base = configManager.fishingExpPerCatch();
        if (base <= 0) return;
        double mult = premium ? Math.max(1.0, configManager.fishingPremiumMultiplier()) : 1.0;
        int exp = (int) Math.round(base * mult);
        if (exp <= 0) return;
        jobManager.addExp(player, JobType.FISHER, exp);
    }
}
