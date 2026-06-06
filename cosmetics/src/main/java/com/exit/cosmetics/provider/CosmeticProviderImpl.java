package com.exit.cosmetics.provider;

import com.exit.core.api.CosmeticInfo;
import com.exit.core.api.CosmeticProvider;
import com.exit.core.data.PlayerDataManager;
import com.exit.cosmetics.cosmetic.ArmorHandler;
import com.exit.cosmetics.cosmetic.TrailHandler;
import com.exit.cosmetics.cosmetic.WeaponCategory;
import com.exit.cosmetics.cosmetic.WeaponHandler;
import com.exit.cosmetics.cosmetic.WingHandler;
import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticType;
import com.exit.cosmetics.registry.CosmeticRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Core의 CosmeticProvider 구현체. 타입에 따라 적절한 핸들러로 위임.
 */
public class CosmeticProviderImpl implements CosmeticProvider {

    private final PlayerDataManager dataManager;
    private final CosmeticRegistry registry;
    private final ArmorHandler armorHandler;
    private final WeaponHandler weaponHandler;
    private final WingHandler wingHandler;
    private final TrailHandler trailHandler;

    public CosmeticProviderImpl(PlayerDataManager dataManager, CosmeticRegistry registry,
                                ArmorHandler armor, WeaponHandler weapon,
                                WingHandler wing, TrailHandler trail) {
        this.dataManager = dataManager;
        this.registry = registry;
        this.armorHandler = armor;
        this.weaponHandler = weapon;
        this.wingHandler = wing;
        this.trailHandler = trail;
    }

    @Override
    public List<CosmeticInfo> listDefinitions() {
        List<CosmeticInfo> list = new ArrayList<>();
        for (CosmeticDefinition d : registry.getAll()) {
            list.add(new CosmeticInfo(d.getId(), d.getType().name(), d.getDisplayName(), d.getDescription()));
        }
        return list;
    }

    @Override
    public Optional<CosmeticInfo> find(String cosmeticId) {
        CosmeticDefinition d = registry.get(cosmeticId);
        if (d == null) return Optional.empty();
        return Optional.of(new CosmeticInfo(d.getId(), d.getType().name(), d.getDisplayName(), d.getDescription()));
    }

    @Override
    public boolean grantCosmetic(UUID uuid, String cosmeticId) {
        if (registry.get(cosmeticId) == null) return false;
        return dataManager.grantCosmetic(uuid, cosmeticId);
    }

    @Override
    public boolean hasCosmetic(UUID uuid, String cosmeticId) {
        return dataManager.hasCosmetic(uuid, cosmeticId);
    }

    @Override
    public Set<String> listOwned(UUID uuid) {
        return dataManager.listOwnedCosmetics(uuid);
    }

    @Override
    public boolean equipCosmetic(UUID uuid, String cosmeticId) {
        CosmeticDefinition def = registry.get(cosmeticId);
        if (def == null) return false;
        if (!dataManager.hasCosmetic(uuid, cosmeticId)) return false;

        dataManager.setEquippedCosmetic(uuid, slotKeyFor(def), cosmeticId);

        Player p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline()) {
            applyByType(p, def);
        }
        return true;
    }

    /**
     * payload 는 다음 중 하나일 수 있음:
     * <ul>
     *   <li>CosmeticType 이름 (예: "HAT", "WEAPON") — 해당 타입 일괄 해제. WEAPON 의 경우 모든 카테고리 해제</li>
     *   <li>cosmetic_id (예: "sentient_sword") — 해당 cosmetic 만 해제 (WEAPON 카테고리별 해제용)</li>
     * </ul>
     */
    @Override
    public boolean unequipCosmetic(UUID uuid, String payload) {
        Player p = Bukkit.getPlayer(uuid);

        // 1) CosmeticType 으로 시도 (탭의 "전체 해제" 버튼 경로)
        CosmeticType type = CosmeticType.fromString(payload);
        if (type != null) {
            if (type == CosmeticType.WEAPON) {
                // 모든 WEAPON_<CAT> 슬롯 정리
                boolean any = false;
                for (WeaponCategory cat : WeaponCategory.values()) {
                    any |= dataManager.clearEquippedCosmetic(uuid, cat.slotKey());
                }
                if (!any) return false;
                if (p != null && p.isOnline()) weaponHandler.clearAll(p);
                return true;
            }
            if (!dataManager.clearEquippedCosmetic(uuid, type.name())) return false;
            if (p != null && p.isOnline()) clearByType(p, type);
            return true;
        }

        // 2) cosmetic_id 로 시도 (특정 cosmetic 만 해제)
        CosmeticDefinition def = registry.get(payload);
        if (def == null) return false;
        String slot = slotKeyFor(def);
        if (!dataManager.clearEquippedCosmetic(uuid, slot)) return false;
        if (p != null && p.isOnline()) {
            if (def.getType() == CosmeticType.WEAPON) {
                weaponHandler.clear(p, WeaponCategory.fromMaterial(def.getBaseItem()));
            } else {
                clearByType(p, def.getType());
            }
        }
        return true;
    }

    /** DB slot key. WEAPON 은 카테고리별로 분리 ("WEAPON_SWORD" 등), 나머지는 type 이름. */
    private String slotKeyFor(CosmeticDefinition def) {
        if (def.getType() == CosmeticType.WEAPON) {
            return WeaponCategory.fromMaterial(def.getBaseItem()).slotKey();
        }
        return def.getType().name();
    }

    @Override
    public Optional<String> getEquipped(UUID uuid, String slot) {
        CosmeticType type = CosmeticType.fromString(slot);
        if (type == null) return Optional.empty();
        return Optional.ofNullable(dataManager.getEquippedCosmetic(uuid, type.name()));
    }

    @Override
    public ItemStack createShowcaseItem(String cosmeticId) {
        CosmeticDefinition def = registry.get(cosmeticId);
        if (def == null) return null;

        ItemStack stack = new ItemStack(def.getBaseItem());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (def.getModelData() > 0) meta.setCustomModelData(def.getModelData());
            meta.displayName(Component.text(def.getDisplayName()).decoration(TextDecoration.ITALIC, false));
            if (!def.getDescription().isEmpty()) {
                meta.lore(List.of(Component.text(def.getDescription()).decoration(TextDecoration.ITALIC, false)));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ─── 내부 라우팅 ───

    private void applyByType(Player p, CosmeticDefinition def) {
        switch (def.getType()) {
            case HAT, CHEST, LEGS, FEET -> armorHandler.apply(p, def);
            case WEAPON -> weaponHandler.apply(p, def);
            case WING -> wingHandler.apply(p, def);
            case TRAIL -> trailHandler.apply(p, def);
            case MOUNT -> { /* 탈것은 MountManager.summon 으로 별도 소환 */ }
        }
    }

    private void clearByType(Player p, CosmeticType type) {
        switch (type) {
            case HAT, CHEST, LEGS, FEET -> armorHandler.clear(p, type);
            case WEAPON -> weaponHandler.clearAll(p);
            case WING -> wingHandler.clear(p);
            case TRAIL -> trailHandler.clear(p);
            case MOUNT -> { /* 탈것은 MountManager.despawn 으로 별도 처리 */ }
        }
    }
}
