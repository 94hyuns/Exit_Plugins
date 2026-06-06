package com.exit.farming;

import com.exit.core.api.CropItemProvider;
import com.exit.core.api.CropStorageReadProvider;
import com.exit.core.api.FarmlandTicketProvider;
import com.exit.core.api.WaterToolProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.farming.command.FarmingCommand;
import com.exit.farming.farmland.FarmlandClaimManager;
import com.exit.farming.farmland.WorldPolicyManager;
import com.exit.farming.listener.CropListener;
import com.exit.farming.listener.FarmlandProtectionListener;
import com.exit.farming.listener.VanillaSuppressListener;
import com.exit.farming.provider.CropItemProviderImpl;
import com.exit.farming.provider.CropStorageReadProviderImpl;
import com.exit.farming.provider.FarmlandTicketProviderImpl;
import com.exit.farming.water.CropTracker;
import com.exit.farming.water.FarmlandDryTicker;
import com.exit.farming.water.SprinklerStore;
import com.exit.farming.water.SprinklerTicker;
import com.exit.farming.water.WaterToolListener;
import com.exit.farming.water.WaterToolProviderImpl;
import org.bukkit.plugin.java.JavaPlugin;

public class FarmingPlugin extends JavaPlugin {

    private static FarmingPlugin INSTANCE;

    private WorldPolicyManager policyManager;
    private FarmlandClaimManager claimManager;
    private SprinklerStore sprinklerStore;
    private CropTracker cropTracker;
    private com.exit.farming.storage.CropStorageManager cropStorageManager;

    public static FarmingPlugin getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        saveDefaultConfig();

        policyManager = new WorldPolicyManager(this);
        policyManager.load();

        claimManager = new FarmlandClaimManager(this);
        claimManager.load();
        // 주기적 flush (1분마다)
        getServer().getScheduler().runTaskTimer(this, claimManager::flush, 1200L, 1200L);

        // Core 인터페이스 구현체 등록
        FarmlandTicketProviderImpl ticketProvider = new FarmlandTicketProviderImpl();
        ServiceRegistry.register(CropItemProvider.class, new CropItemProviderImpl());
        ServiceRegistry.register(FarmlandTicketProvider.class, ticketProvider);
        ServiceRegistry.register(WaterToolProvider.class, new WaterToolProviderImpl());

        // 작물 위치 추적 (씨앗 위치별 Crop 매핑)
        cropTracker = new CropTracker(this);
        cropTracker.load();
        getServer().getScheduler().runTaskTimer(this, cropTracker::flush, 1200L, 1200L);

        // Listeners
        getServer().getPluginManager().registerEvents(
                new CropListener(this, policyManager, claimManager, cropTracker), this);
        getServer().getPluginManager().registerEvents(
                new VanillaSuppressListener(this), this);
        getServer().getPluginManager().registerEvents(
                new FarmlandProtectionListener(this, policyManager, claimManager, ticketProvider), this);

        // 물 도구 (물뿌리개 + 스프링쿨러)
        sprinklerStore = new SprinklerStore(this);
        // onEnable 시점엔 world_village 같은 멀티월드가 아직 로드 안 됐을 수 있음 →
        // Bukkit.getWorld() null 로 모든 항목이 skipped 되는 리부트 버그 회피.
        // 다음 tick (모든 플러그인 onEnable 완료 후) 에 안전하게 로드.
        getServer().getScheduler().runTask(this, sprinklerStore::load);
        getServer().getPluginManager().registerEvents(
                new WaterToolListener(this, sprinklerStore, cropTracker), this);
        new SprinklerTicker(this, sprinklerStore, cropTracker).start();
        // sprinklers.yml 주기적 flush (1분마다)
        getServer().getScheduler().runTaskTimer(this, sprinklerStore::flush, 1200L, 1200L);

        // 클레임 farmland 직접 dry 처리 (vanilla 가 물 인접 시 dry 안 함 → 우리가 대신)
        new FarmlandDryTicker(this, claimManager).start();

        // 농부의 보관함 (farmer Lv2 perk)
        cropStorageManager = new com.exit.farming.storage.CropStorageManager(this);
        com.exit.farming.storage.CropStorageGUI cropStorageGUI =
                new com.exit.farming.storage.CropStorageGUI(this, cropStorageManager);
        com.exit.farming.storage.CropStorageListener cropStorageListener =
                new com.exit.farming.storage.CropStorageListener(this, cropStorageManager, cropStorageGUI);
        getServer().getPluginManager().registerEvents(cropStorageListener, this);
        // 범위 자동수집 (Lv6 10x10) — 0.5초 주기 nearby 스캔
        cropStorageListener.startRangeCollectTask();

        // 관리자 read-only 보관함 조회 (Core /관리자검사)
        ServiceRegistry.register(CropStorageReadProvider.class,
                new CropStorageReadProviderImpl(cropStorageManager));

        // Cooking Pack 연동 (MythicMobs 설치된 경우만)
        if (getServer().getPluginManager().getPlugin("MythicMobs") != null) {
            getServer().getPluginManager().registerEvents(
                    new com.exit.farming.cooking.CookingIntegrationListener(this), this);
            getServer().getPluginManager().registerEvents(
                    new com.exit.farming.cooking.CookingFoodEffectsListener(this), this);
            getLogger().info("[Farming/Cooking] cooking_pot 우리 작물 통합 + 음식 효과 활성화");
        } else {
            getLogger().info("[Farming/Cooking] MythicMobs 미설치 — cooking_pot 통합 비활성화");
        }

        // Commands
        FarmingCommand exec = new FarmingCommand();
        register("씨앗모음", exec);
        register("과일모음", exec);
        register("경작지정보", exec);
        register("물뿌리개", exec);
        register("스프링쿨러", exec);

        getLogger().info("농사 플러그인 활성화됨. 바닐라 작물 봉인: "
                + getConfig().getBoolean("vanilla-suppress", true)
                + ", 경작지 클레임: " + claimManager.size()
                + " (스프링쿨러는 다음 tick 에 로드)");
    }

    @Override
    public void onDisable() {
        ServiceRegistry.unregister(CropItemProvider.class);
        ServiceRegistry.unregister(FarmlandTicketProvider.class);
        ServiceRegistry.unregister(WaterToolProvider.class);
        ServiceRegistry.unregister(CropStorageReadProvider.class);
        if (claimManager != null) claimManager.save();
        if (sprinklerStore != null) sprinklerStore.flush();
        if (cropTracker != null) cropTracker.flush();
    }

    private void register(String name, FarmingCommand exec) {
        var cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("plugin.yml에 " + name + " 명령어 등록이 누락됨");
            return;
        }
        cmd.setExecutor(exec);
    }

    public WorldPolicyManager getPolicyManager() { return policyManager; }
    public FarmlandClaimManager getClaimManager() { return claimManager; }
    public com.exit.farming.storage.CropStorageManager getCropStorageManager() { return cropStorageManager; }
    public SprinklerStore getSprinklerStore() { return sprinklerStore; }
}
