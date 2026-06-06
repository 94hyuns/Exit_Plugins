package com.example.land.managers;

import com.example.land.LandPlugin;
import com.example.land.data.ChunkPos;
import com.example.land.data.ClaimedChunk;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * 관리구역(ADMIN) 청크 저장소. plugins/Land/admin_chunks.yml 한 파일.
 *
 * 형식:
 * <pre>
 * version: 1
 * chunks:
 *   world_village:
 *     - "3,5"
 *     - "3,6"
 *     - "4,5"
 *   world_other:
 *     - "0,0"
 * </pre>
 *
 * 손편집 또는 외부 도구(village_admin_chunk_map.html) 의 다운로드로 덮어쓴 뒤
 * /땅 관리 리로드 명령으로 즉시 반영.
 *
 * 파일이 없으면 빈 Map. 파싱 실패 시 ERROR 로그 + 빈 Map (자료 보존을 위해 절대 자동 덮어쓰지 않음).
 */
public class AdminChunkStore {

    private static final String FILENAME = "admin_chunks.yml";
    private static final int CURRENT_VERSION = 1;

    private final LandPlugin plugin;
    private final File file;

    /** 마지막 load() 가 실패했는지 — 실패 상태에서는 save() 자동 차단 (덮어쓰기 방지) */
    private boolean lastLoadFailed = false;

    public AdminChunkStore(LandPlugin plugin) {
        this.plugin = plugin;
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists()) dataDir.mkdirs();
        this.file = new File(dataDir, FILENAME);
    }

    public Map<ChunkPos, ClaimedChunk> load() {
        Map<ChunkPos, ClaimedChunk> result = new HashMap<>();
        if (!file.exists()) {
            lastLoadFailed = false;
            return result;
        }

        YamlConfiguration cfg;
        try {
            cfg = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "admin_chunks.yml 파싱 실패. 자동 저장 비활성화 (자료 보존). 파일을 점검하세요.", e);
            lastLoadFailed = true;
            return result;
        }

        ConfigurationSection chunksSec = cfg.getConfigurationSection("chunks");
        if (chunksSec == null) {
            lastLoadFailed = false;
            return result;
        }

        int loaded = 0, malformed = 0;
        for (String world : chunksSec.getKeys(false)) {
            List<String> entries = chunksSec.getStringList(world);
            for (String entry : entries) {
                int[] xz = parseEntry(entry);
                if (xz == null) {
                    malformed++;
                    continue;
                }
                ChunkPos pos = new ChunkPos(world, xz[0], xz[1]);
                result.put(pos, ClaimedChunk.admin(pos));
                loaded++;
            }
        }
        if (malformed > 0) {
            plugin.getLogger().warning("admin_chunks.yml: " + malformed + "개의 항목이 형식 오류로 무시됨 (예상 형식: \"x,z\").");
        }
        plugin.getLogger().info("admin_chunks.yml 로드: " + loaded + "청크.");
        lastLoadFailed = false;
        return result;
    }

    /** 메모리에 있는 ADMIN 청크 전체를 admin_chunks.yml 에 덮어쓴다. */
    public void save(Collection<ClaimedChunk> adminChunks) {
        if (lastLoadFailed) {
            plugin.getLogger().warning(
                "admin_chunks.yml 의 직전 로드가 실패해 save() 를 차단했습니다. " +
                "원본 파일을 백업/수정한 뒤 /땅 관리 리로드 후 다시 시도하세요.");
            return;
        }

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("version", CURRENT_VERSION);

        // 월드별 그룹화
        Map<String, List<String>> byWorld = new TreeMap<>();
        for (ClaimedChunk c : adminChunks) {
            if (!c.isAdmin()) continue;
            byWorld.computeIfAbsent(c.getPos().getWorld(), k -> new ArrayList<>())
                   .add(c.getPos().getX() + "," + c.getPos().getZ());
        }
        // 결정적 정렬 (diff 친화적)
        for (List<String> list : byWorld.values()) list.sort(Comparator.naturalOrder());

        for (Map.Entry<String, List<String>> e : byWorld.entrySet()) {
            cfg.set("chunks." + e.getKey(), e.getValue());
        }
        if (byWorld.isEmpty()) {
            cfg.createSection("chunks");
        }

        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "admin_chunks.yml 저장 실패", ex);
        }
    }

    /** 마이그레이션 진입점 — DB 에서 추출한 ADMIN 좌표 리스트를 yml 로 export */
    public static void writeMigratedAdminChunks(LandPlugin plugin, List<ChunkPos> positions) {
        AdminChunkStore store = new AdminChunkStore(plugin);
        // 빈 파일에서 시작이므로 lastLoadFailed=false
        List<ClaimedChunk> chunks = new ArrayList<>(positions.size());
        for (ChunkPos p : positions) chunks.add(ClaimedChunk.admin(p));
        store.save(chunks);
    }

    private static int[] parseEntry(String entry) {
        if (entry == null) return null;
        String[] parts = entry.split(",");
        if (parts.length != 2) return null;
        try {
            return new int[] {
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
