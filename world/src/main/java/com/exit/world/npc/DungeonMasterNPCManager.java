package com.exit.world.npc;

import com.exit.core.api.NpcInfo;
import com.exit.core.api.NpcService;
import com.exit.core.api.NpcSpawnSpec;
import com.exit.core.registry.ServiceRegistry;
import com.exit.world.manager.DungeonRegistry;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * 던전 마스터 NPC 어댑터.
 * NMS 실제 구현은 Core 의 {@link NpcService} 에 위임. owner+id 매핑만 담당.
 */
public class DungeonMasterNPCManager {

    public static final String OWNER = "world";
    public static final String NPC_ID = "dungeon_master";

    private final JavaPlugin plugin;
    private final DungeonRegistry registry;

    public DungeonMasterNPCManager(JavaPlugin plugin, DungeonRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    private NpcService service() {
        return ServiceRegistry.get(NpcService.class).orElse(null);
    }

    /** 서버 부팅 시 호출 — yml 의 위치에 NPC 가 없으면 스폰. */
    public void loadAndSpawn() {
        NpcService svc = service();
        if (svc == null) {
            plugin.getLogger().warning("[World] NpcService 미등록 — 던전 마스터 NPC 스킵 (Core 1.6.0+ 필요)");
            return;
        }
        if (svc.get(OWNER, NPC_ID).isPresent()) {
            // 이미 persist 로 복원됨 — 이름/위치 변경은 명령어로
            return;
        }
        Location loc = registry.getNpcLocation();
        if (loc == null) {
            plugin.getLogger().info("[World] dungeons.yml 에 NPC 위치 미설정 — /던전마스터 spawn 으로 수동 생성 가능");
            return;
        }
        boolean ok = svc.spawn(new NpcSpawnSpec(
                OWNER, NPC_ID, loc, registry.getNpcName(), registry.getNpcSkin(), true));
        if (ok) plugin.getLogger().info("[World] 던전 마스터 NPC 스폰 완료");
    }

    public boolean spawn(Location location, String skinOwner) {
        NpcService svc = service();
        if (svc == null) return false;
        svc.remove(OWNER, NPC_ID);
        return svc.spawn(new NpcSpawnSpec(
                OWNER, NPC_ID, location, registry.getNpcName(), skinOwner, true));
    }

    public boolean remove() {
        NpcService svc = service();
        if (svc == null) return false;
        return svc.remove(OWNER, NPC_ID);
    }

    public boolean setSkin(String playerName) {
        NpcService svc = service();
        if (svc == null) return false;
        Optional<NpcInfo> info = svc.get(OWNER, NPC_ID);
        if (info.isEmpty()) return false;
        return spawn(info.get().location(), playerName);
    }

    public boolean isDungeonMaster(int entityId) {
        NpcService svc = service();
        if (svc == null) return false;
        return svc.getByEntityId(entityId)
                .filter(info -> OWNER.equals(info.owner()) && NPC_ID.equals(info.id()))
                .isPresent();
    }
}
