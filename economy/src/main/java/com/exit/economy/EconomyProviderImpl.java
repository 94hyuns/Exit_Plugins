package com.exit.economy;

import com.exit.core.api.EconomyProvider;
import com.exit.core.data.PlayerDataManager;

import java.util.UUID;

public class EconomyProviderImpl implements EconomyProvider {

    private final PlayerDataManager dataManager;

    public EconomyProviderImpl(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public long getBalance(UUID uuid) {
        return dataManager.getBalance(uuid);
    }

    @Override
    public boolean addBalance(UUID uuid, long amount) {
        return dataManager.addBalance(uuid, amount);
    }

    @Override
    public boolean subtractBalance(UUID uuid, long amount) {
        return dataManager.subtractBalance(uuid, amount);
    }

    @Override
    public boolean setBalance(UUID uuid, long amount) {
        return dataManager.setBalance(uuid, amount);
    }
}
