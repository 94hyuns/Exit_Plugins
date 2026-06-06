package com.example.land;

import com.example.land.commands.LandCommand;
import com.example.land.listeners.ControllerListener;
import com.example.land.listeners.PlayerListener;
import com.example.land.listeners.ProtectionListener;
import com.example.land.managers.InfoModeManager;
import com.example.land.managers.LandDatabase;
import com.example.land.managers.LandManager;
import com.example.land.managers.WorldProtectionManager;
import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class LandPlugin extends JavaPlugin {

    private LandDatabase database;
    private LandManager landManager;
    private InfoModeManager infoModeManager;
    private WorldProtectionManager worldProtectionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (getEconomy() == null) {
            getLogger().severe("EconomyProvider를 찾을 수 없습니다. Economy 플러그인이 로드됐는지 확인하세요.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        database = new LandDatabase(this);
        database.init();

        landManager = new LandManager(this, database);
        landManager.load();

        worldProtectionManager = new WorldProtectionManager(this);
        worldProtectionManager.load();

        infoModeManager = new InfoModeManager(this);
        infoModeManager.startTask();

        LandCommand landCommand = new LandCommand(this);
        getCommand("land").setExecutor(landCommand);
        getCommand("land").setTabCompleter(landCommand);

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ControllerListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getLogger().info("Land 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (infoModeManager != null) infoModeManager.stopTask();
        if (database != null) database.close();
        getLogger().info("Land 플러그인이 비활성화되었습니다.");
    }

    public EconomyProvider getEconomy() {
        return ServiceRegistry.get(EconomyProvider.class).orElse(null);
    }

    public boolean isAllowedWorld(org.bukkit.World world) {
        List<String> allowed = getConfig().getStringList("land.allowed-worlds");
        return allowed.contains(world.getName());
    }

    /**
     * N번째 슬롯(1-based)의 구매 가격. 슬롯이 prices 리스트 범위 밖이면 -1.
     * 구버전 config 폴백: prices 리스트가 비어 있으면 옛 price-per-chunk 단일 키 사용.
     */
    public long getPriceForSlot(int slot) {
        List<Integer> prices = getConfig().getIntegerList("land.prices");
        if (prices.isEmpty()) return getConfig().getLong("land.price-per-chunk", 500L);
        if (slot < 1 || slot > prices.size()) return -1;
        return prices.get(slot - 1);
    }

    /** 1인당 최대 청크 수 = prices 리스트 길이. 폴백 시 9. */
    public int getMaxChunksPerPlayer() {
        List<Integer> prices = getConfig().getIntegerList("land.prices");
        return prices.isEmpty() ? 9 : prices.size();
    }

    public LandManager getLandManager() { return landManager; }
    public InfoModeManager getInfoModeManager() { return infoModeManager; }
    public WorldProtectionManager getWorldProtectionManager() { return worldProtectionManager; }
}

