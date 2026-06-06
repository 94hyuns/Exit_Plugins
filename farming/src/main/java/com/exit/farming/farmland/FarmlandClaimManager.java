package com.exit.farming.farmland;

import com.exit.farming.FarmingPlugin;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 경작지 클레임 저장소.
 *
 * - 위치 키: "world:x:y:z" (문자열)
 * - 값: 소유자 UUID
 *
 * 저장 파일: plugins/Farming/claims.yml
 *
 * 규모가 4~8인 서버이므로 YAML로 충분. 대규모라면 SQLite로 교체 가능.
 * 메모리에 전부 로드 후 비동기 flush(dirty flag)로 저장.
 */
public class FarmlandClaimManager {

    private final FarmingPlugin plugin;
    private final File file;
    private final Map<String, UUID> claims = new HashMap<>();
    private boolean dirty = false;

    public FarmlandClaimManager(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
    }

    public void load() {
        claims.clear();
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            String uuidStr = yml.getString(key);
            if (uuidStr == null) continue;
            try {
                claims.put(key, UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("claims.yml 깨진 항목 skip: " + key);
            }
        }
        plugin.getLogger().info("경작지 클레임 " + claims.size() + "개 로드.");
    }

    public void save() {
        if (!dirty) return;
        YamlConfiguration yml = new YamlConfiguration();
        for (var entry : claims.entrySet()) {
            yml.set(entry.getKey(), entry.getValue().toString());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "claims.yml 저장 실패", e);
        }
    }

    /** 주기적 flush. Bukkit scheduler에서 매 분 호출되는 게 권장. */
    public void flush() {
        if (dirty) save();
    }

    private String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public boolean isClaimed(Block block) {
        return claims.containsKey(key(block));
    }

    public UUID getOwner(Block block) {
        return claims.get(key(block));
    }

    public void claim(Block block, UUID owner) {
        claims.put(key(block), owner);
        dirty = true;
    }

    public boolean unclaim(Block block) {
        boolean removed = claims.remove(key(block)) != null;
        if (removed) dirty = true;
        return removed;
    }

    public int size() {
        return claims.size();
    }

    /** 청크 일괄 보정 등 외부 순회용. 수정 금지 (방어적 복사 권장). */
    public java.util.Map<String, java.util.UUID> getAll() {
        return java.util.Collections.unmodifiableMap(claims);
    }
}
