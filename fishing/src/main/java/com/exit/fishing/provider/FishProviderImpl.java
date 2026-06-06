package com.exit.fishing.provider;

import com.exit.core.api.FishProvider;
import com.exit.fishing.FishingPlugin;
import com.exit.fishing.fish.FishRank;
import com.exit.fishing.fish.FishRegistry;
import com.exit.fishing.fish.FishSpecies;
import com.exit.fishing.item.FishItem;
import com.exit.fishing.season.Season;
import com.exit.fishing.season.SeasonManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core 의 {@link FishProvider} 구현.
 *
 * <p>외부 호출자(CustomItems 의 오토릴 등) 가 ServiceRegistry 를 경유해
 * 호출하면 현재 계절 + 지정 maxTier 한도로 물고기를 한 마리 생성한다.
 *
 * <p>로직은 {@code FishingListener#pickSpecies()} / {@code rollSize()} 와 동일한 정신을
 * 따르되, tier cap 만 외부에서 제어할 수 있게 분리.
 */
public class FishProviderImpl implements FishProvider {

    /** tier 별 가중치 (원본 rollSize 의 1~10 균등 분포 재현: 5/2/2/1) */
    private static final int[] TIER_WEIGHTS = {50, 20, 20, 10};

    private final FishingPlugin plugin;
    private final SeasonManager seasons;

    public FishProviderImpl(FishingPlugin plugin, SeasonManager seasons) {
        this.plugin = plugin;
        this.seasons = seasons;
    }

    @Override
    public ItemStack rollFish(int maxTier) {
        int cap = Math.max(1, Math.min(4, maxTier));

        FishSpecies species = pickSpecies();
        if (species == null) return null;

        int rolledTier = pickTier(cap);
        int[] size = rollSizeForTier(rolledTier);  // {cm, g}
        int score = size[0] + size[1];
        FishRank rank = FishRank.ofScore(score);

        int premiumOneIn = Math.max(1, plugin.getConfig().getInt("premium.chance-one-in", 1000));
        boolean premium = ThreadLocalRandom.current().nextInt(premiumOneIn) == 0;

        return FishItem.create(species, size[0], size[1], rank, premium);
    }

    /** {@code FishingListener#pickSpecies()} 와 동일 로직 — 계절 4/7 in-season 비중. */
    private FishSpecies pickSpecies() {
        Season season = seasons.current();
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

    /** 1 ~ maxTier 사이 가중 랜덤 선택 (TIER_WEIGHTS 의 부분합 정규화). */
    private int pickTier(int maxTier) {
        int total = 0;
        for (int i = 0; i < maxTier; i++) total += TIER_WEIGHTS[i];
        int r = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (int i = 0; i < maxTier; i++) {
            acc += TIER_WEIGHTS[i];
            if (r < acc) return i + 1;
        }
        return maxTier;  // unreachable
    }

    @Override
    public void sendCatchMessage(Player player, ItemStack fish) {
        if (!FishItem.isFish(fish)) return;
        FishSpecies species = FishItem.getSpecies(fish);
        if (species == null) return;
        int cm = FishItem.getLength(fish);
        int g = FishItem.getMass(fish);
        boolean premium = FishItem.isPremium(fish);
        FishRank rank = FishRank.ofScore(cm + g);

        String prefix = plugin.getConfig().getString("prefix", "&6[ &fserver &6]");
        String prem = premium ? " &6[최고급]" : "";
        Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(
                prefix + " &7길이 : &3" + cm + " cm &7질량 : &6" + g + " g &f"
                        + species.koreanName() + prem + "&7을 낚았다! &7[&f" + rank.display() + "&7]"
        );
        player.sendMessage(msg);
    }

    /** tier (1~4) → cm/g 롤. {@code FishingListener#rollSize()} 의 구간을 그대로 사용. */
    private int[] rollSizeForTier(int tier) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int cm, g;
        switch (tier) {
            case 1 -> { cm = r.nextInt(30, 101);  g = r.nextInt(100, 301); }
            case 2 -> { cm = r.nextInt(101, 251); g = r.nextInt(301, 801); }
            case 3 -> { cm = r.nextInt(251, 351); g = r.nextInt(801, 1701); }
            case 4 -> { cm = r.nextInt(351, 501); g = r.nextInt(1701, 3001); }
            default -> { cm = r.nextInt(30, 101); g = r.nextInt(100, 301); }
        }
        return new int[]{cm, g};
    }
}
