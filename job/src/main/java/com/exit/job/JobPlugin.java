package com.exit.job;

import com.exit.core.api.JobProvider;
import com.exit.core.api.MineralStorageReadProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.job.api.FishingExpHook;
import com.exit.job.api.MiningExpHook;
import com.exit.job.api.impl.FishingExpHookImpl;
import com.exit.job.api.impl.MiningExpHookImpl;
import com.exit.job.command.JobCommand;
import com.exit.job.gui.JobDetailGUI;
import com.exit.job.gui.JobOverviewGUI;
import com.exit.job.listener.JobListener;
import com.exit.job.manager.JobConfigManager;
import com.exit.job.manager.JobManager;
import com.exit.job.perk.PerkApplyManager;
import com.exit.job.perk.PerkRegistry;
import com.exit.job.provider.JobProviderImpl;
import com.exit.job.provider.MineralStorageReadProviderImpl;
import org.bukkit.plugin.java.JavaPlugin;

public class JobPlugin extends JavaPlugin {

    private static JobPlugin INSTANCE;
    private JobConfigManager configManager;
    private JobManager jobManager;
    private JobOverviewGUI overviewGUI;
    private JobDetailGUI detailGUI;
    private PerkRegistry perkRegistry;
    private PerkApplyManager perkApplyManager;
    private com.exit.job.storage.MineralStorageManager mineralStorageManager;

    public static JobPlugin getInstance() { return INSTANCE; }

    @Override
    public void onEnable() {
        INSTANCE = this;
        saveDefaultConfig();

        configManager = new JobConfigManager(this);
        configManager.load();

        jobManager = new JobManager(this, configManager);

        perkRegistry = new PerkRegistry(this);
        perkApplyManager = new PerkApplyManager(jobManager, configManager, perkRegistry);
        jobManager.setPerkApplyManager(perkApplyManager);

        overviewGUI = new JobOverviewGUI(jobManager, configManager);
        detailGUI = new JobDetailGUI(jobManager, configManager);

        ServiceRegistry.register(JobProvider.class, new JobProviderImpl(jobManager));
        ServiceRegistry.register(MiningExpHook.class, new MiningExpHookImpl(jobManager, configManager));
        ServiceRegistry.register(FishingExpHook.class, new FishingExpHookImpl(jobManager, configManager));

        // 광부의 보관함 (miner Lv2 perk)
        mineralStorageManager = new com.exit.job.storage.MineralStorageManager(this, jobManager);
        com.exit.job.storage.MineralStorageGUI mineralStorageGUI =
                new com.exit.job.storage.MineralStorageGUI(this, mineralStorageManager);
        getServer().getPluginManager().registerEvents(
                new com.exit.job.storage.MineralStorageListener(
                        this, mineralStorageManager, mineralStorageGUI, jobManager, configManager),
                this);

        // 관리자 read-only 보관함 조회 (Core /관리자검사)
        ServiceRegistry.register(MineralStorageReadProvider.class,
                new MineralStorageReadProviderImpl(mineralStorageManager));

        getServer().getPluginManager().registerEvents(
                new JobListener(jobManager, configManager, overviewGUI, detailGUI, perkApplyManager), this);

        // 보관함 재발급 (OP 명령)
        com.exit.job.command.ReissueStorageCommand reissueCmd =
                new com.exit.job.command.ReissueStorageCommand();
        var reissue = getCommand("보관함재발급");
        if (reissue != null) {
            reissue.setExecutor(reissueCmd);
            reissue.setTabCompleter(reissueCmd);
        }

        JobCommand cmd = new JobCommand(jobManager, overviewGUI);
        var info = getCommand("직업정보");
        if (info != null) info.setExecutor(cmd);
        var admin = getCommand("직업관리");
        if (admin != null) {
            admin.setExecutor(cmd);
            admin.setTabCompleter(cmd);
        }

        getServer().getScheduler().runTaskTimer(this, jobManager::flushAll, 1200L, 1200L);

        // 온라인 플레이어 데이터 로드 + perk 즉시 적용 (reload 시나리오 대비)
        for (var p : getServer().getOnlinePlayers()) {
            jobManager.loadOrInit(p.getUniqueId());
            perkApplyManager.applyAll(p);
        }

        getLogger().info("[Job] 직업 시스템 활성화 완료. " + configManager.all().size() + "직업 등록.");
    }

    @Override
    public void onDisable() {
        ServiceRegistry.unregister(JobProvider.class);
        ServiceRegistry.unregister(MiningExpHook.class);
        ServiceRegistry.unregister(FishingExpHook.class);
        ServiceRegistry.unregister(MineralStorageReadProvider.class);
        if (perkApplyManager != null) {
            for (var p : getServer().getOnlinePlayers()) perkApplyManager.removeAll(p);
        }
        if (jobManager != null) jobManager.flushAll();
    }

    public JobConfigManager getConfigManager() { return configManager; }
    public JobManager getJobManager() { return jobManager; }
    public com.exit.job.storage.MineralStorageManager getMineralStorageManager() { return mineralStorageManager; }
}
