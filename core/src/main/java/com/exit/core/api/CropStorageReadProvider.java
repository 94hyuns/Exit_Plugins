package com.exit.core.api;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 농부 보관함 read-only 조회. Farming 플러그인이 구현체를 등록.
 */
public interface CropStorageReadProvider {
    /** UUID 기준 보관함 내용 (45칸 고정). null/AIR 슬롯 포함 가능. */
    ItemStack[] load(UUID uuid);
}
