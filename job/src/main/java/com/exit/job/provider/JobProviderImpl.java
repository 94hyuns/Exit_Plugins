package com.exit.job.provider;

import com.exit.core.api.JobProvider;
import com.exit.job.manager.JobManager;
import com.exit.job.model.JobType;

import java.util.UUID;

/**
 * Core JobProvider 인터페이스 구현. ServiceRegistry 등록 후
 * 다른 플러그인이 EXP 부여할 때 이 구현체로 라우팅.
 */
public class JobProviderImpl implements JobProvider {

    private final JobManager manager;

    public JobProviderImpl(JobManager manager) {
        this.manager = manager;
    }

    @Override
    public void addExp(UUID player, String jobId, long amount) {
        JobType type = JobType.fromId(jobId);
        if (type == null) return;
        manager.addExp(player, type, amount);
    }

    @Override
    public int getLevel(UUID player, String jobId) {
        JobType type = JobType.fromId(jobId);
        if (type == null) return 0;
        return manager.getLevel(player, type);
    }

    @Override
    public long getExp(UUID player, String jobId) {
        JobType type = JobType.fromId(jobId);
        if (type == null) return 0;
        return manager.getExp(player, type);
    }
}
