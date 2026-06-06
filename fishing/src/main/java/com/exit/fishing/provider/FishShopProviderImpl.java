package com.exit.fishing.provider;

import com.exit.core.api.FishShopProvider;
import com.exit.fishing.FishingPlugin;
import com.exit.fishing.gui.FishShopGUI;
import com.exit.fishing.season.SeasonManager;
import org.bukkit.entity.Player;

/**
 * Core의 FishShopProvider 인터페이스 구현.
 * Shop 플러그인의 낚시 NPC에서 ServiceRegistry 경유로 호출됨.
 *
 * 기존 배럴 기반 진입점 대신 이 구현체가 유일한 낚시 상점 오픈 경로가 된다.
 */
public class FishShopProviderImpl implements FishShopProvider {

    private final FishingPlugin plugin;
    private final SeasonManager seasonManager;

    public FishShopProviderImpl(FishingPlugin plugin, SeasonManager seasonManager) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
    }

    @Override
    public void openShop(Player player) {
        FishShopGUI.open(player, seasonManager);
    }
}
