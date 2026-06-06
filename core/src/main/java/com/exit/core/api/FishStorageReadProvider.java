package com.exit.core.api;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 어부 보관함 read-only 조회. Fishing 플러그인이 구현체를 등록.
 */
public interface FishStorageReadProvider {
    /** UUID 기준 보관함 내용 전체. 길이는 페이지 수에 따라 가변(43/129/215). null/AIR 슬롯 포함 가능. */
    ItemStack[] load(UUID uuid);
}
