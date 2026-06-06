package com.exit.fishing.provider;

import com.exit.core.api.FishStorageReadProvider;
import com.exit.fishing.storage.FishStorageManager;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class FishStorageReadProviderImpl implements FishStorageReadProvider {

    private final FishStorageManager manager;

    public FishStorageReadProviderImpl(FishStorageManager manager) {
        this.manager = manager;
    }

    @Override
    public ItemStack[] load(UUID uuid) {
        return manager.load(uuid);
    }
}
