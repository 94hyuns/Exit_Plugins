package com.server.core;

import com.server.core.data.PlayerDataManager;
import com.server.core.registry.ServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public class CorePlugin extends JavaPlugin {

    private static CorePlugin instance;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        instance = this;

        playerDataManager = new PlayerDataManager(this);
        playerDataManager.init();

        getLogger().info("Core 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) playerDataManager.close();
        ServiceRegistry.clear();
        getLogger().info("Core 플러그인이 비활성화되었습니다.");
    }

    public static CorePlugin getInstance() {
        return instance;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }
}
