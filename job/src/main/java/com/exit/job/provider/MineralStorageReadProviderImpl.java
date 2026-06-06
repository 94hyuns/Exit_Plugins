package com.exit.job.provider;

import com.exit.core.api.MineralStorageReadProvider;
import com.exit.job.storage.MineralStorageManager;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MineralStorageReadProviderImpl implements MineralStorageReadProvider {

    private final MineralStorageManager manager;

    public MineralStorageReadProviderImpl(MineralStorageManager manager) {
        this.manager = manager;
    }

    @Override
    public ItemStack[] load(UUID uuid) {
        return manager.load(uuid);
    }
}
