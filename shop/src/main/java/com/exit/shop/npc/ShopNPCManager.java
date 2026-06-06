package com.exit.shop.npc;

import com.exit.core.api.NpcInfo;
import com.exit.core.api.NpcService;
import com.exit.core.api.NpcSpawnSpec;
import com.exit.core.registry.ServiceRegistry;
import com.exit.shop.model.ShopCategory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;

/**
 * 상점 NPC 어댑터.
 *
 * v1.5.0 부터 NMS 실제 구현은 Core 의 {@link NpcService} 에 위임.
 * 이 클래스는 ShopCategory ↔ npcId 매핑 + 기존 npcs.yml 마이그레이션만 담당.
 *
 * NpcService 가 없으면 (예: 구버전 Core) NPC 기능 비활성화.
 */
public class ShopNPCManager {

    public static final String OWNER = "shop";
    public static final String NPC_ID_PREFIX = "shop_";

    private final JavaPlugin plugin;
    private final File legacyFile;
    private final File migratedMarker;

    public ShopNPCManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.legacyFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.migratedMarker = new File(plugin.getDataFolder(), "npcs.yml.migrated");
    }

    // ─── 라우팅 ───

    public static String npcIdOf(ShopCategory cat) {
        return NPC_ID_PREFIX + cat.name().toLowerCase();
    }

    public static ShopCategory fromNpcId(String npcId) {
        if (npcId == null || !npcId.startsWith(NPC_ID_PREFIX)) return null;
        try {
            return ShopCategory.valueOf(npcId.substring(NPC_ID_PREFIX.length()).toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private NpcService service() {
        return ServiceRegistry.get(NpcService.class).orElse(null);
    }

    // ─── 마이그레이션 (첫 부팅 시 npcs.yml → Core) ───

    public void loadAndSpawn() {
        NpcService svc = service();
        if (svc == null) {
            plugin.getLogger().warning("[Shop] NpcService 미등록 — NPC 작업 스킵 (Core 1.6.1+ 필요)");
            return;
        }

        // 1) 마이그레이션 (첫 부팅 시 1회)
        if (legacyFile.exists() && !migratedMarker.exists()) {
            migrateLegacy(svc);
        }

        // 2) 이름 enforce — 매 부팅마다.
        //    save() 가 displayName 을 저장 안 하던 버그로 "NPC" 로 깨진 NPC 복구.
        enforceDisplayNames(svc);
    }

    private void migrateLegacy(NpcService svc) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(legacyFile);
        int count = 0;
        for (ShopCategory cat : ShopCategory.values()) {
            String path = cat.name().toLowerCase();
            if (!cfg.contains(path + ".world")) continue;

            String worldName = cfg.getString(path + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("[Shop] 마이그레이션: " + cat.name() + " 의 world '" + worldName + "' 없음, 스킵");
                continue;
            }
            Location loc = new Location(world,
                    cfg.getDouble(path + ".x"),
                    cfg.getDouble(path + ".y"),
                    cfg.getDouble(path + ".z"),
                    (float) cfg.getDouble(path + ".yaw"),
                    (float) cfg.getDouble(path + ".pitch"));
            String skin = cfg.getString(path + ".skin", "Steve");

            String npcId = npcIdOf(cat);
            if (svc.get(OWNER, npcId).isPresent()) continue;

            boolean ok = svc.spawn(new NpcSpawnSpec(
                    OWNER, npcId, loc, cat.getNpcName(), skin, true));
            if (ok) count++;
        }

        if (count > 0) {
            try {
                java.nio.file.Files.move(legacyFile.toPath(), migratedMarker.toPath());
                plugin.getLogger().info("[Shop] " + count + "개 NPC 마이그레이션 완료 → "
                        + migratedMarker.getName() + " (이후 부팅은 Core 에서 직접 로드)");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[Shop] npcs.yml rename 실패", e);
            }
        }
    }

    private void enforceDisplayNames(NpcService svc) {
        int fixed = 0;
        for (ShopCategory cat : ShopCategory.values()) {
            String npcId = npcIdOf(cat);
            NpcInfo info = svc.get(OWNER, npcId).orElse(null);
            if (info == null) continue;
            String expected = cat.getNpcName();
            if (expected.equals(info.displayName())) continue;
            // 이름 불일치 → 동일 위치에 동일 스킨으로 재스폰
            String skin = info.skinOwner() == null ? "Steve" : info.skinOwner();
            svc.remove(OWNER, npcId);
            boolean ok = svc.spawn(new NpcSpawnSpec(
                    OWNER, npcId, info.location(), expected, skin, true));
            if (ok) {
                fixed++;
                plugin.getLogger().info("[Shop] NPC '" + expected + "' 이름 복구 (이전: " + info.displayName() + ")");
            }
        }
        if (fixed > 0) {
            plugin.getLogger().info("[Shop] " + fixed + "개 NPC 이름 자동 복구 완료");
        }
    }

    // ─── 스폰/제거/스킨 ───

    public boolean spawnNPC(Location location, ShopCategory category, String skinOwner) {
        NpcService svc = service();
        if (svc == null) {
            plugin.getLogger().warning("[Shop] NpcService 미등록 — spawnNPC 불가");
            return false;
        }
        String npcId = npcIdOf(category);
        // 기존 동일 카테고리 NPC 제거 후 재스폰
        svc.remove(OWNER, npcId);
        return svc.spawn(new NpcSpawnSpec(
                OWNER, npcId, location, category.getNpcName(), skinOwner, true));
    }

    public boolean spawnNPC(Location location, ShopCategory category) {
        return spawnNPC(location, category, getSkinOwner(category));
    }

    public boolean removeNPC(ShopCategory category) {
        NpcService svc = service();
        if (svc == null) return false;
        return svc.remove(OWNER, npcIdOf(category));
    }

    public boolean setSkin(ShopCategory category, String playerName) {
        NpcService svc = service();
        if (svc == null) return false;
        Optional<NpcInfo> info = svc.get(OWNER, npcIdOf(category));
        if (info.isEmpty()) return false;
        Location loc = info.get().location();
        return spawnNPC(loc, category, playerName);
    }

    // ─── 조회 ───

    public Optional<ShopCategory> getCategory(int entityId) {
        NpcService svc = service();
        if (svc == null) return Optional.empty();
        return svc.getByEntityId(entityId)
                .filter(info -> OWNER.equals(info.owner()))
                .map(info -> fromNpcId(info.id()));
    }

    public boolean isShopNPC(int entityId) {
        return getCategory(entityId).isPresent();
    }

    public String getSkinOwner(ShopCategory category) {
        NpcService svc = service();
        if (svc == null) return "Steve";
        return svc.get(OWNER, npcIdOf(category))
                .map(NpcInfo::skinOwner)
                .orElse("Steve");
    }

    // ─── No-op (Core 가 처리하지만 호환성 위해 메서드 유지) ───

    /** @deprecated Core 가 PlayerJoin/ChangedWorld/Respawn 에서 자동 처리. 호출 무시됨. */
    @Deprecated
    public void showAll(Player player) { /* no-op */ }

    /** @deprecated Core 가 onDisable 에서 자동 처리. 호출 무시됨. */
    @Deprecated
    public void removeAll() { /* no-op */ }
}
