package com.exit.world;

import com.exit.core.api.NpcService;
import com.exit.core.registry.ServiceRegistry;
import com.exit.world.boss.BossArenaListener;
import com.exit.world.boss.BossArenaManager;
import com.exit.world.command.DungeonMasterCommand;
import com.exit.world.command.PortalCommand;
import com.exit.world.generator.VoidWorldGenerator;
import com.exit.world.gui.DungeonConfirmGUI;
import com.exit.world.gui.DungeonTabGUI;
import com.exit.world.gui.PortalGUI;
import com.exit.world.listener.WorldListener;
import com.exit.world.manager.DungeonEntry;
import com.exit.world.manager.DungeonRegistry;
import com.exit.world.manager.WorldManager;
import com.exit.world.npc.DungeonMasterNPCManager;
import org.bukkit.Bukkit;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class WorldPlugin extends JavaPlugin {

    private WorldManager worldManager;
    private DungeonRegistry dungeonRegistry;
    private DungeonMasterNPCManager dungeonNPCManager;
    private BossArenaManager bossArenaManager;

    @Override
    public void onEnable() {
        copyDefaultYml("worlds.yml");
        copyDefaultYml("dungeons.yml");

        worldManager = new WorldManager(getDataFolder(), getLogger(), this);
        worldManager.initialize();

        dungeonRegistry = new DungeonRegistry(this);
        dungeonRegistry.load();

        dungeonNPCManager = new DungeonMasterNPCManager(this, dungeonRegistry);

        // 보스 아레나: dungeonRegistry 에서 bossArena 정의 가진 항목만 등록
        bossArenaManager = new BossArenaManager(this, worldManager);
        boolean mmAvailable = getServer().getPluginManager().getPlugin("MythicMobs") != null;
        for (DungeonEntry e : dungeonRegistry.getByTab(DungeonEntry.Tab.BOSS)) {
            if (e.bossArena() != null) {
                if (!mmAvailable) {
                    getLogger().warning("[World] MythicMobs 미설치 — " + e.key() + " 보스 아레나 비활성화");
                    continue;
                }
                bossArenaManager.register(e.bossArena());
            }
        }
        if (mmAvailable) {
            getServer().getPluginManager().registerEvents(new BossArenaListener(bossArenaManager), this);
            getServer().getPluginManager().registerEvents(
                    new com.exit.world.cleanup.MobAutoCleanupListener(this, worldManager), this);
        }

        PortalGUI portalGUI = new PortalGUI(worldManager);
        DungeonTabGUI dungeonTabGUI = new DungeonTabGUI(dungeonRegistry);
        DungeonConfirmGUI dungeonConfirmGUI = new DungeonConfirmGUI(dungeonRegistry);

        PortalCommand portalCommand = new PortalCommand(portalGUI);
        DungeonMasterCommand dungeonCommand = new DungeonMasterCommand(dungeonNPCManager, dungeonRegistry, bossArenaManager);

        getCommand("포탈").setExecutor(portalCommand);
        getCommand("portal").setExecutor(portalCommand);
        getCommand("던전마스터").setExecutor(dungeonCommand);
        getCommand("던전마스터").setTabCompleter(dungeonCommand);

        WorldListener listener = new WorldListener(
                worldManager, portalGUI, dungeonRegistry, dungeonTabGUI, dungeonConfirmGUI,
                bossArenaManager, this);
        getServer().getPluginManager().registerEvents(listener, this);

        // 던전 시간 제한 (time-limit-sec) 자동 텔포
        getServer().getPluginManager().registerEvents(
                new com.exit.world.listener.DungeonTimeLimitListener(this, dungeonRegistry, worldManager), this);

        // NPC 클릭 핸들러 + 자동 스폰 — Core 가 초기화된 후 (3초 지연, Shop 패턴 참고)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            NpcService npc = ServiceRegistry.get(NpcService.class).orElse(null);
            if (npc == null) {
                getLogger().severe("[World] Core NpcService 미등록 — 던전 마스터 NPC 비활성화 (Core 1.6.0+ 필요)");
            } else {
                npc.registerClickHandler(DungeonMasterNPCManager.OWNER, (player, npcId, attack) -> {
                    if (attack) return;
                    if (!DungeonMasterNPCManager.NPC_ID.equals(npcId)) return;
                    listener.onDungeonMasterClick(player);
                });
                dungeonNPCManager.loadAndSpawn();
            }
        }, 60L);

        getLogger().info("[World] 활성화 완료.");
    }

    /**
     * Paper가 월드 생성 시 이 메서드를 호출해서 생성기를 가져간다.
     * 프리빌드 월드는 보이드 제너레이터를 사용하여 미생성 청크가 플랫 지형으로 채워지는 것을 방지.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return switch (worldName) {
            case "world_village", "world_dungeon", "world_dungeon2",
                 "world_boss1_1", "world_boss1_2",
                 "world_boss2_1", "world_boss2_2",
                 "world_burning1", "world_burning2"
                    -> new VoidWorldGenerator();
            default -> null;
        };
    }

    @Override
    public void onDisable() {
        getLogger().info("[World] 비활성화 완료.");
    }

    private void copyDefaultYml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }
}
