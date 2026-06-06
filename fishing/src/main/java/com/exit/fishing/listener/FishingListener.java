package com.exit.fishing.listener;

import com.exit.core.api.JobProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.fishing.FishingPlugin;
import com.exit.fishing.fish.FishRank;
import com.exit.fishing.fish.FishRegistry;
import com.exit.fishing.fish.FishSpecies;
import com.exit.fishing.item.FishItem;
import com.exit.fishing.season.SeasonManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 낚시 이벤트 처리.
 *
 * - PlayerFishEvent (HIGHEST priority): 잡힌 fish entity 의 ItemStack 을 커스텀 FishItem 으로 교체
 * - EntityPickupItemEvent (HIGH priority): pickup 직전 안전망 — 만약 다른 플러그인 또는 vanilla 가
 *   ItemStack 을 raw fish (Material.SALMON 등) 로 되돌려놓았다면 그 시점에 다시 강제 교체.
 *   "연어가 raw 로 잡힘" 같은 케이스 방지.
 */
public class FishingListener implements Listener {

    private final FishingPlugin plugin;
    private final SeasonManager seasons;

    /** 낚인 entity UUID → 우리가 의도한 ItemStack. pickup 시 검증용. */
    private final Map<UUID, ItemStack> pendingFish = new ConcurrentHashMap<>();

    public FishingListener(FishingPlugin plugin, SeasonManager seasons) {
        this.plugin = plugin;
        this.seasons = seasons;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item caughtItem)) return;

        Player player = event.getPlayer();
        FishSpecies species = pickSpecies();
        if (species == null) return;

        int[] sizeScore = rollSize();
        int lengthCm = sizeScore[0];
        int massG = sizeScore[1];

        // fisher 능력 fish_min_length_10 (구 Lv4): 2026-05-14 부로 비활성.
        // Lv4 perk 가 보관함 페이지 확장으로 변경됨. 추후 다른 레벨로 재배치 시 주석 해제.
        JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
        // int fisherLevel = (jobs != null) ? jobs.getLevel(player.getUniqueId(), "fisher") : 0;
        // if (fisherLevel >= 4) {
        //     int minLength = plugin.getConfig().getInt("job-exp.min-length-base", 30) + 10;
        //     if (lengthCm < minLength) lengthCm = minLength;
        // }

        int score = lengthCm + massG;
        FishRank rank = FishRank.ofScore(score);

        int premiumOneIn = Math.max(1, plugin.getConfig().getInt("premium.chance-one-in", 1000));
        boolean premium = ThreadLocalRandom.current().nextInt(premiumOneIn) == 0;

        ItemStack customFish = FishItem.create(species, lengthCm, massG, rank, premium);
        caughtItem.setItemStack(customFish);

        // 안전망: pickup 시 검증용으로 매핑 저장. 5초 후 자동 정리.
        UUID id = caughtItem.getUniqueId();
        pendingFish.put(id, customFish.clone());
        Bukkit.getScheduler().runTaskLater(plugin, () -> pendingFish.remove(id), 100L);

        // 어부 EXP 부여 (Job 미설치 시 no-op)
        if (jobs != null) {
            int baseExp = Math.max(0, plugin.getConfig().getInt("job-exp.per-catch", 10));
            double mult = premium ? Math.max(1.0, plugin.getConfig().getDouble("job-exp.premium-multiplier", 2.0)) : 1.0;
            int finalExp = (int) Math.round(baseExp * mult);
            if (finalExp > 0) jobs.addExp(player.getUniqueId(), "fisher", finalExp);
        }

        // 안내 메시지
        String prefix = plugin.getConfig().getString("prefix", "&6[ &fserver &6]");
        String prem = premium ? " &6[최고급]" : "";
        Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(
                prefix + " &7길이 : &3" + lengthCm + " cm &7질량 : &6" + massG + " g &f"
                        + species.koreanName() + prem + "&7을 낚았다! &7[&f" + rank.display() + "&7]"
        );
        player.sendMessage(msg);
    }

    /**
     * 능력 bite_time_minus_3s (Lv8): 어부 레벨 8 이상이면 hook 의 wait time 을 -60 tick (3초).
     * 낚시대의 Lure 인챈트와 별도로 적용 (vanilla 가 Lure 효과를 별도 처리).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCast(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;
        JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
        if (jobs == null) return;
        if (jobs.getLevel(event.getPlayer().getUniqueId(), "fisher") < 8) return;

        int reduction = plugin.getConfig().getInt("job-exp.bite-time-reduction-ticks", 60);
        var hook = event.getHook();
        hook.setMinWaitTime(Math.max(0, hook.getMinWaitTime() - reduction));
        hook.setMaxWaitTime(Math.max(20, hook.getMaxWaitTime() - reduction));
    }

    /**
     * Pickup 시점 안전망. caughtItem 의 ItemStack 이 어떤 이유로든 raw vanilla fish 로 변해 있으면
     * 우리가 PlayerFishEvent 에서 만든 customFish 로 강제 복구.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Item item = event.getItem();
        ItemStack expected = pendingFish.remove(item.getUniqueId());
        if (expected == null) return;
        ItemStack actual = item.getItemStack();
        if (FishItem.isFish(actual)) return;  // 이미 우리 커스텀 fish
        // raw vanilla fish (cod / salmon / pufferfish / tropical_fish) 또는 다른 무언가 → 강제 교체
        if (isVanillaFish(actual.getType()) || actual.getType() == Material.AIR) {
            item.setItemStack(expected);
        }
    }

    private static boolean isVanillaFish(Material m) {
        return m == Material.COD || m == Material.SALMON
                || m == Material.PUFFERFISH || m == Material.TROPICAL_FISH;
    }

    private FishSpecies pickSpecies() {
        var season = seasons.current();
        List<FishSpecies> inSeason = FishRegistry.inSeason(season);
        List<FishSpecies> catchable = FishRegistry.catchableIn(season);
        List<FishSpecies> middle = catchable.stream()
                .filter(f -> !inSeason.contains(f))
                .toList();

        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean pickInSeason = r.nextInt(7) < 4;
        List<FishSpecies> pool = (pickInSeason && !inSeason.isEmpty()) ? inSeason
                : (!middle.isEmpty() ? middle : catchable);
        if (pool.isEmpty()) return null;
        return pool.get(r.nextInt(pool.size()));
    }

    private int[] rollSize() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(10) + 1;
        int cm, g;
        if (roll <= 5) {
            cm = r.nextInt(30, 101);
            g = r.nextInt(100, 301);
        } else if (roll <= 7) {
            cm = r.nextInt(101, 251);
            g = r.nextInt(301, 801);
        } else if (roll <= 9) {
            cm = r.nextInt(251, 351);
            g = r.nextInt(801, 1701);
        } else {
            cm = r.nextInt(351, 501);
            g = r.nextInt(1701, 3001);
        }
        return new int[]{cm, g};
    }
}
