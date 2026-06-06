package com.exit.fishing.storage;

import com.exit.core.api.JobProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.fishing.fish.FishRegistry;
import com.exit.fishing.fish.FishSpecies;
import com.exit.fishing.item.FishItem;
import com.exit.fishing.season.Season;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 플레이어별 보관함 데이터의 디스크 영속화.
 *
 * <p>저장: {@code plugins/Fishing/storage/<uuid>.yml}
 * <pre>
 * slot:
 *   '0': &lt;ItemStack 직렬화&gt;
 *   '7': &lt;...&gt;
 * </pre>
 * AIR/null 슬롯은 저장 안 함 (sparse).
 *
 * <p>용량은 어부 레벨에 따라 페이지 단위로 확장 (2026-05-14, Fishing 1.4.0):
 * <ul>
 *   <li>기본: 1 페이지 = 43 슬롯</li>
 *   <li>Lv4 +2 페이지 → 3 페이지 = 129 슬롯</li>
 *   <li>Lv6 +2 페이지 → 5 페이지 = 215 슬롯</li>
 * </ul>
 * 페이지 = GUI 한 화면 (6×9 - 상단 UI 9 - 하단 nav 2). yml 의 sparse 저장이라
 * SIZE 변경에 안전 — 페이지 축소 시 초과 슬롯의 데이터는 보존되며 페이지 확장하면 다시 보임.
 */
public class FishStorageManager {

    /** 페이지당 슬롯 수 (GUI 6×9 - 상단 UI 9 - 하단 nav 2). */
    public static final int PAGE_SIZE = 43;

    /** 기존 SIZE = 45 호환용 (deprecated). 새 코드는 capacityFor() 사용. */
    @Deprecated
    public static final int SIZE = 45;

    private final JavaPlugin plugin;
    private final File dir;
    private final java.util.Map<UUID, Boolean> autoCollectCache = new java.util.concurrent.ConcurrentHashMap<>();

    public FishStorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "storage");
        if (!dir.exists()) dir.mkdirs();
    }

    /** 어부 레벨에 따른 총 페이지 수 (최소 1). Job 미설치 시 1. */
    public int pagesFor(UUID uuid) {
        JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
        if (jobs == null) return 1;
        int level = jobs.getLevel(uuid, "fisher");
        int pages = 1;
        if (level >= 4) pages += 3;  // Lv4 → 4페이지 (172칸)
        if (level >= 6) pages += 3;  // Lv6 → 7페이지 (301칸)
        return pages;
    }

    /** 어부 레벨에 따른 총 슬롯 수 = pagesFor × PAGE_SIZE. */
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

    /** 일괄 판매용. 전부 비우고 직전 내용 반환. */
    public ItemStack[] takeAll(UUID uuid) {
        int cap = capacityFor(uuid);
        ItemStack[] data = load(uuid);
        save(uuid, new ItemStack[cap]);
        return data;
    }

    /**
     * 계절 일괄 판매용. 지정 계절에 속하는 물고기만 추출하고 보관함에서 제거.
     * 비매칭 슬롯은 그대로 보존 (save 로 되돌림).
     *
     * @return 추출된 in-season 아이템 배열. 매칭 안 된 인덱스는 null.
     */
    public ItemStack[] extractInSeason(UUID uuid, Season target) {
        ItemStack[] data = load(uuid);
        ItemStack[] extracted = new ItemStack[data.length];
        for (int i = 0; i < data.length; i++) {
            ItemStack s = data[i];
            if (s == null || s.getType().isAir()) continue;
            String id = FishItem.getFishId(s);
            if (id == null) continue;
            FishSpecies sp = FishRegistry.byId(id);
            if (sp == null || sp.season() != target) continue;
            extracted[i] = s;
            data[i] = null;
        }
        save(uuid, data);
        return extracted;
    }

    public void save(UUID uuid, ItemStack[] slots) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("auto-collect", autoCollectCache.getOrDefault(uuid, false));
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = slots[i];
            if (s == null || s.getType().isAir()) continue;
            yml.set("slot." + i, s);
        }
        try {
            yml.save(new File(dir, uuid + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Fishing] 보관함 저장 실패: " + uuid, e);
        }
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
            plugin.getLogger().log(Level.WARNING, "[Fishing] 어부 보관함 auto-collect 저장 실패: " + uuid, e);
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
