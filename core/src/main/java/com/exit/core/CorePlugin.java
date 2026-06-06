package com.exit.core;

import com.exit.core.admininspect.AdminInspectCommand;
import com.exit.core.admininspect.AdminInspectListener;
import com.exit.core.chestdeposit.ChestDepositListener;
import com.exit.core.command.HelpCommand;
import com.exit.core.data.PlayerDataManager;
import com.exit.core.koreancmd.HangulCommandAliasListener;
import com.exit.core.api.NpcService;
import com.exit.core.npc.NpcEventListener;
import com.exit.core.npc.NpcServiceImpl;
import com.exit.core.registry.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class CorePlugin extends JavaPlugin {

    private static CorePlugin instance;
    private PlayerDataManager playerDataManager;
    private NpcServiceImpl npcService;

    @Override
    public void onEnable() {
        instance = this;
        playerDataManager = new PlayerDataManager(this);
        playerDataManager.init();

        // 한글 명령어 → 두벌식 romanization 자동 alias (모든 플러그인 글로벌)
        HangulCommandAliasListener hangulAlias = new HangulCommandAliasListener(this);
        Bukkit.getPluginManager().registerEvents(hangulAlias, this);
        // Core 보다 먼저 로드된 플러그인 커버용 지연 스캔 (1초 후)
        Bukkit.getScheduler().runTaskLater(this, hangulAlias::rebuild, 20L);

        // 바닐라 상자 "동일 아이템 입금" 버튼
        Bukkit.getPluginManager().registerEvents(new ChestDepositListener(this), this);

        // /관리자검사 - 플레이어 정보 통합 조회
        Bukkit.getPluginManager().registerEvents(new AdminInspectListener(), this);
        if (getCommand("관리자검사") != null) {
            AdminInspectCommand adminInspect = new AdminInspectCommand();
            getCommand("관리자검사").setExecutor(adminInspect);
            getCommand("관리자검사").setTabCompleter(adminInspect);
        } else {
            getLogger().warning("/관리자검사 명령어 등록 실패: plugin.yml 의 commands 섹션을 확인하세요.");
        }

        // /도움 - 초보자 도움말
        if (getCommand("도움") != null) {
            getCommand("도움").setExecutor(new HelpCommand());
        } else {
            getLogger().warning("/도움 명령어 등록 실패: plugin.yml 의 commands 섹션을 확인하세요.");
        }

        // 공용 NPC 서비스 (다른 플러그인이 NpcService 로 NPC 스폰/클릭 핸들러 등록)
        npcService = new NpcServiceImpl(this);
        ServiceRegistry.register(NpcService.class, npcService);
        Bukkit.getPluginManager().registerEvents(new NpcEventListener(this, npcService), this);
        // 다른 플러그인이 onEnable 에서 클릭 핸들러 등록할 시간 + 월드 로드 보장
        Bukkit.getScheduler().runTaskLater(this, npcService::loadAndSpawn, 40L);

        getLogger().info("Core 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        if (npcService != null) {
            npcService.save();
            npcService.removeAllInMemory();
        }
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
