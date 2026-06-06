package com.exit.job.manager;

import com.exit.job.model.JobData;
import com.exit.job.model.JobType;
import com.exit.job.perk.PerkApplyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 플레이어별 직업 데이터 관리.
 *
 * <p>저장: plugins/Job/players/&lt;uuid&gt;.yml
 * <pre>
 * miner:
 *   level: 5
 *   exp: 1240
 * fisher:
 *   level: 2
 *   exp: 380
 * farmer:
 *   level: 7
 *   exp: 980
 * </pre>
 *
 * <p>메모리 캐시는 ConcurrentHashMap. 변경 시 dirty 플래그 표시 후
 * 60초 주기로 flush. 플레이어 quit 시 즉시 flush.
 */
public class JobManager {

    private final JavaPlugin plugin;
    private final JobConfigManager configManager;
    private final File playersFolder;

    /** UUID → JobType → JobData. */
    private final ConcurrentHashMap<UUID, EnumMap<JobType, JobData>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> dirty = new ConcurrentHashMap<>();

    /** 순환 참조 회피: JobPlugin onEnable 에서 후주입. null 가능 (테스트/초기화 중). */
    private PerkApplyManager perkApplyManager;

    public JobManager(JavaPlugin plugin, JobConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playersFolder = new File(plugin.getDataFolder(), "players");
        playersFolder.mkdirs();
    }

    public void setPerkApplyManager(PerkApplyManager perkApplyManager) {
        this.perkApplyManager = perkApplyManager;
    }

    private void reapplyPerks(UUID uuid, JobType type) {
        if (perkApplyManager == null) return;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) perkApplyManager.reapply(p, type);
    }

    // ─── 데이터 로드/저장 ───

    /** 플레이어 데이터 로드. 파일 없으면 모든 직업 INITIAL 로 초기화. */
    public EnumMap<JobType, JobData> loadOrInit(UUID uuid) {
        EnumMap<JobType, JobData> existing = cache.get(uuid);
        if (existing != null) return existing;

        EnumMap<JobType, JobData> data = new EnumMap<>(JobType.class);
        File f = new File(playersFolder, uuid + ".yml");
        if (f.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            for (JobType t : JobType.values()) {
                if (yml.contains(t.id())) {
                    int level = yml.getInt(t.id() + ".level", 1);
                    long exp = yml.getLong(t.id() + ".exp", 0L);
                    data.put(t, new JobData(level, exp));
                } else {
                    data.put(t, JobData.INITIAL);
                }
            }
        } else {
            // 신규 플레이어 — 모든 직업 Lv1
            for (JobType t : JobType.values()) data.put(t, JobData.INITIAL);
            dirty.put(uuid, true);
        }

        cache.put(uuid, data);
        return data;
    }

    public void flush(UUID uuid) {
        if (!Boolean.TRUE.equals(dirty.get(uuid))) return;
        EnumMap<JobType, JobData> data = cache.get(uuid);
        if (data == null) return;

        YamlConfiguration yml = new YamlConfiguration();
        for (var entry : data.entrySet()) {
            String key = entry.getKey().id();
            yml.set(key + ".level", entry.getValue().level());
            yml.set(key + ".exp", entry.getValue().exp());
        }
        try {
            yml.save(new File(playersFolder, uuid + ".yml"));
            dirty.remove(uuid);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Job] " + uuid + " 저장 실패", e);
        }
    }

    public void flushAll() {
        for (UUID uuid : cache.keySet()) flush(uuid);
    }

    public void unload(UUID uuid) {
        flush(uuid);
        cache.remove(uuid);
    }

    // ─── 조회 API ───

    public JobData get(UUID uuid, JobType type) {
        return loadOrInit(uuid).getOrDefault(type, JobData.INITIAL);
    }

    public int getLevel(UUID uuid, JobType type) { return get(uuid, type).level(); }
    public long getExp(UUID uuid, JobType type) { return get(uuid, type).exp(); }

    // ─── EXP 부여 / 레벨업 ───

    /**
     * EXP 추가. 누적 EXP가 다음 레벨 임계치 이상이면 레벨업 + 메시지/사운드.
     * 한 번에 여러 레벨 점프 가능 (큰 값 add 시).
     */
    public void addExp(UUID uuid, JobType type, long amount) {
        if (amount <= 0) return;
        EnumMap<JobType, JobData> data = loadOrInit(uuid);
        JobData current = data.getOrDefault(type, JobData.INITIAL);

        int oldLevel = current.level();
        int level = oldLevel;
        long exp = current.exp() + amount;
        boolean leveledUp = false;

        while (level < configManager.maxLevel()) {
            long needed = configManager.expForNextLevel(level);
            if (exp < needed) break;
            exp -= needed;
            level++;
            leveledUp = true;
        }
        // max level 도달 시 EXP는 0으로 클램프 (의미 없는 누적 방지)
        if (level >= configManager.maxLevel()) exp = 0;

        data.put(type, new JobData(level, exp));
        dirty.put(uuid, true);

        if (leveledUp) {
            notifyLevelUp(uuid, type, level);
            reapplyPerks(uuid, type);
            fireLevelUpEvent(uuid, type, oldLevel, level);
        }
    }

    private void fireLevelUpEvent(UUID uuid, JobType type, int oldLevel, int newLevel) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;
        Bukkit.getPluginManager().callEvent(
                new com.exit.job.api.event.JobLevelUpEvent(p, type, oldLevel, newLevel));
    }

    /** OP 명령용: 레벨/EXP 직접 설정. */
    public void setLevel(UUID uuid, JobType type, int level) {
        level = Math.max(1, Math.min(level, configManager.maxLevel()));
        EnumMap<JobType, JobData> data = loadOrInit(uuid);
        data.put(type, new JobData(level, 0));
        dirty.put(uuid, true);
        reapplyPerks(uuid, type);
    }

    public void setExp(UUID uuid, JobType type, long exp) {
        EnumMap<JobType, JobData> data = loadOrInit(uuid);
        JobData c = data.getOrDefault(type, JobData.INITIAL);
        data.put(type, new JobData(c.level(), Math.max(0, exp)));
        dirty.put(uuid, true);
    }

    private void notifyLevelUp(UUID uuid, JobType type, int newLevel) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;
        var def = configManager.getDefinition(type);
        String name = def != null ? def.displayName() : type.id();
        p.sendMessage(Component.text("✦ ").color(NamedTextColor.GOLD)
                .append(Component.text(name + " 레벨업! ").color(NamedTextColor.YELLOW))
                .append(Component.text("Lv. " + newLevel).color(NamedTextColor.AQUA)));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);
    }
}
