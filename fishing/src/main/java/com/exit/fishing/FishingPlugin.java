package com.exit.fishing;

import com.exit.core.api.FishProvider;
import com.exit.core.api.FishShopProvider;
import com.exit.core.api.FishStorageReadProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.fishing.command.FishingCommand;
import com.exit.fishing.gui.GuiListener;
import com.exit.fishing.listener.FishingListener;
import com.exit.fishing.provider.FishProviderImpl;
import com.exit.fishing.provider.FishShopProviderImpl;
import com.exit.fishing.provider.FishStorageReadProviderImpl;
import com.exit.fishing.season.SeasonManager;
import org.bukkit.plugin.java.JavaPlugin;

public class FishingPlugin extends JavaPlugin {

    private static FishingPlugin INSTANCE;

    private SeasonManager seasonManager;
    private com.exit.fishing.storage.FishStorageManager fishStorageManager;

    public static FishingPlugin getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        saveDefaultConfig();

        seasonManager = new SeasonManager(this);
        seasonManager.load();
        seasonManager.restartTask();

        // 어부 보관함 (Job Lv2 perk fish_storage)
        fishStorageManager = new com.exit.fishing.storage.FishStorageManager(this);
        com.exit.fishing.storage.FishStorageManager storageManager = fishStorageManager;
        com.exit.fishing.storage.FishStorageGUI storageGui =
                new com.exit.fishing.storage.FishStorageGUI(this, storageManager, seasonManager);

        // Listeners
        getServer().getPluginManager().registerEvents(new FishingListener(this, seasonManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this, seasonManager), this);
        getServer().getPluginManager().registerEvents(
                new com.exit.fishing.storage.FishStorageListener(this, storageManager, storageGui, seasonManager),
                this);

        // Commands
        FishingCommand exec = new FishingCommand(this, seasonManager);
        register("낚시", exec);
        register("낚시도감", exec);
        register("계절", exec);
        register("낚시리로드", exec);

        // Core에 낚시 상점 진입점 등록
        ServiceRegistry.register(FishShopProvider.class,
                new FishShopProviderImpl(this, seasonManager));

        // Core에 커스텀 물고기 생성 API 등록 (CustomItems 의 오토릴 등이 사용)
        ServiceRegistry.register(FishProvider.class,
                new FishProviderImpl(this, seasonManager));

        // 관리자 read-only 보관함 조회 (Core /관리자검사)
        ServiceRegistry.register(FishStorageReadProvider.class,
                new FishStorageReadProviderImpl(fishStorageManager));

        getLogger().info("낚시 플러그인 활성화됨. 현재 계절: " + seasonManager.current().korean());
    }

    @Override
    public void onDisable() {
        ServiceRegistry.unregister(FishShopProvider.class);
        ServiceRegistry.unregister(FishProvider.class);
        ServiceRegistry.unregister(FishStorageReadProvider.class);
        if (seasonManager != null) {
            seasonManager.shutdown();
        }
    }

    private void register(String name, FishingCommand exec) {
        var cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("plugin.yml에 " + name + " 명령어 등록이 누락됨");
            return;
        }
        cmd.setExecutor(exec);
        cmd.setTabCompleter(exec);
    }

    public com.exit.fishing.storage.FishStorageManager getFishStorageManager() { return fishStorageManager; }

    public SeasonManager getSeasonManager() {
        return seasonManager;
    }
}
