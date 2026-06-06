package com.exit.farming.provider;

import com.exit.core.api.CropStorageReadProvider;
import com.exit.farming.storage.CropStorageManager;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class CropStorageReadProviderImpl implements CropStorageReadProvider {

    private final CropStorageManager manager;

    public CropStorageReadProviderImpl(CropStorageManager manager) {
        this.manager = manager;
    }

    @Override
    public ItemStack[] load(UUID uuid) {
        return manager.load(uuid);
    }
}
