package com.exit.farming.storage;

import com.exit.core.api.JobProvider;
import com.exit.core.registry.ServiceRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * {@code plugins/Farming/crop_storage/<uuid>.yml} sparse 직렬화 + auto-collect 플래그.
 *
 * <p>용량은 농부 레벨에 따라 페이지 단위로 확장 (2026-05-17, Farming 1.10.0):
 * <ul>
 *   <li>기본: 1 페이지 = 43 슬롯</li>
 *   <li>Lv8 +3 페이지 → 4 페이지 = 172 슬롯</li>
 * </ul>
 * 페이지 = GUI 한 화면 (6×9 - 상단 UI 9 - 하단 nav 2). yml sparse 저장이라 SIZE 변경에 안전.
 */
public class CropStorageManager {

    /** 페이지당 슬롯 수 (GUI 6×9 - 상단 UI 9 - 하단 nav 2). */
    public static final int PAGE_SIZE = 43;

    /** 기존 SIZE = 45 호환용 (deprecated). 새 코드는 capacityFor() 사용. */
    @Deprecated
    public static final int SIZE = 45;

    private final JavaPlugin plugin;
    private final File dir;
    private final Map<UUID, Boolean> autoCollectCache = new ConcurrentHashMap<>();
    /** 자동 재심기 (농부 Lv10 perk) on/off. 기본 ON (구버전 호환). */
    private final Map<UUID, Boolean> autoReplantCache = new ConcurrentHashMap<>();

    public CropStorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "crop_storage");
        if (!dir.exists()) dir.mkdirs();
    }

    /** 농부 레벨에 따른 총 페이지 수 (최소 1). Job 미설치 시 1. */
    public int pagesFor(UUID uuid) {
        JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
        if (jobs == null) return 1;
        int level = jobs.getLevel(uuid, "farmer");
        return (level >= 8) ? 4 : 1;  // Lv8 +3 페이지 → 4 페이지 (172칸)
    }

    /** 농부 레벨에 따른 총 슬롯 수 = pagesFor × PAGE_SIZE. */
    public int capacityFor(UUID uuid) {
        return pagesFor(uuid) * PAGE_SIZE;
    }

    public ItemStack[] load(UUID uuid) {
        int cap = capacityFor(uuid);
        ItemStack[] out = new ItemStack[cap];
        File f = new File(dir, uuid + ".yml");
        if (!f.exists()) return out;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        autoCollectCache.put(uuid, yml.getBoolean("auto-collect", false));
        autoReplantCache.put(uuid, yml.getBoolean("auto-replant", true));
        if (!yml.isConfigurationSection("slot")) return out;
        var section = yml.getConfigurationSection("slot");
        for (String key : section.getKeys(false)) {
            int idx;
            try { idx = Integer.parseInt(key); } catch (NumberFormatException e) { continue; }
            if (idx < 0 || idx >= cap) continue;
            ItemStack stack = section.getItemStack(key);
            if (stack != null) out[idx] = stack;
        }
        return out;
    }

    public void save(UUID uuid, ItemStack[] slots) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("auto-collect", autoCollectCache.getOrDefault(uuid, false));
        yml.set("auto-replant", autoReplantCache.getOrDefault(uuid, true));
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = slots[i];
            if (s == null || s.getType().isAir()) continue;
            yml.set("slot." + i, s);
        }
        try {
            yml.save(new File(dir, uuid + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Farming] 농부 보관함 저장 실패: " + uuid, e);
        }
    }

    public ItemStack[] takeAll(UUID uuid) {
        int cap = capacityFor(uuid);
        ItemStack[] data = load(uuid);
        save(uuid, new ItemStack[cap]);
        return data;
    }

    public boolean isAutoCollect(UUID uuid) {
        Boolean cached = autoCollectCache.get(uuid);
        if (cached != null) return cached;
        load(uuid);
        return autoCollectCache.getOrDefault(uuid, false);
    }

    public void setAutoCollect(UUID uuid, boolean v) {
        autoCollectCache.put(uuid, v);
        // load() 가 cache 덮어쓰는 버그 회피
        File f = new File(dir, uuid + ".yml");
        YamlConfiguration yml = f.exists()
                ? YamlConfiguration.loadConfiguration(f)
                : new YamlConfiguration();
        yml.set("auto-collect", v);
        try {
            yml.save(f);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Farming] 농부 보관함 auto-collect 저장 실패: " + uuid, e);
        }
    }

    public boolean isAutoReplant(UUID uuid) {
        Boolean cached = autoReplantCache.get(uuid);
        if (cached != null) return cached;
        load(uuid);  // 캐시 채우기
        return autoReplantCache.getOrDefault(uuid, true);  // 기본 ON
    }

    public void setAutoReplant(UUID uuid, boolean v) {
        autoReplantCache.put(uuid, v);
        File f = new File(dir, uuid + ".yml");
        YamlConfiguration yml = f.exists()
                ? YamlConfiguration.loadConfiguration(f)
                : new YamlConfiguration();
        yml.set("auto-replant", v);
        try {
            yml.save(f);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Farming] 농부 보관함 auto-replant 저장 실패: " + uuid, e);
        }
    }

    public ItemStack tryDeposit(UUID uuid, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) return null;
        ItemStack[] slots = load(uuid);
        int remaining = stack.getAmount();
        int maxStack = stack.getMaxStackSize();
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            ItemStack s = slots[i];
            if (s != null && s.isSimilar(stack)) {
                int room = maxStack - s.getAmount();
                if (room > 0) {
                    int take = Math.min(room, remaining);
                    s.setAmount(s.getAmount() + take);
                    remaining -= take;
                }
            }
        }
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            if (slots[i] == null || slots[i].getType().isAir()) {
                int take = Math.min(maxStack, remaining);
                ItemStack copy = stack.clone();
                copy.setAmount(take);
                slots[i] = copy;
                remaining -= take;
            }
        }
        save(uuid, slots);
        if (remaining <= 0) return null;
        ItemStack leftover = stack.clone();
        leftover.setAmount(remaining);
        return leftover;
    }
}
