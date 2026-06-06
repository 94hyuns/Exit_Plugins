package com.exit.cosmetics.cosmetic;

import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticType;
import com.exit.cosmetics.registry.CosmeticRegistry;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 방어구 4종(HAT/CHEST/LEGS/FEET) 통합 핸들러 — Level 3 (실제 아이템 mutate).
 *
 * <p><b>접근 방식</b>: WeaponHandler 와 동일한 PDC backup 패턴. 실제 착용 중인
 * 갑옷 ItemStack 의 {@code minecraft:equippable.asset_id} 를 cosmetic 의 asset_id 로
 * 바꾸고, 원본 값은 PDC 에 백업. 따라서:
 * <ul>
 *   <li>아이템 자체의 lore / 인챈트 / 데이터 모두 보존 (단순 컴포넌트 교체)</li>
 *   <li>다른 플레이어 시점 / F5 / 인벤 슬롯 모두 일관된 외형</li>
 *   <li>플러그인 비활성화 시 PDC 백업으로 안전한 원복 가능</li>
 * </ul>
 *
 * <p><b>주기 체크 (매 5틱)</b>: 모든 온라인 플레이어의 갑옷 4슬롯을 확인해 다음 케이스 처리:
 * <ul>
 *   <li>새 갑옷을 착용했다 → 등록된 cosmetic 적용</li>
 *   <li>갑옷을 벗었다 → 이전 슬롯 아이템은 더 이상 신경 X (인벤에 그대로 둠)</li>
 *   <li>다른 cosmetic 으로 교체됐다 → 기존 원복 + 신규 적용</li>
 * </ul>
 *
 * <p><b>안전장치</b>:
 * <ul>
 *   <li>드롭/사망 시: {@link #revertStack} 으로 원본 복구 (listener 에서 호출)</li>
 *   <li>접속 종료 시: {@link #revertAll} 로 4슬롯 원본 복구 (서버 저장 직전)</li>
 *   <li>플러그인 비활성화 시: {@link #shutdownAll} 로 전원 원복</li>
 * </ul>
 */
public class ArmorHandler {

    public static final NamespacedKey APPLIED_KEY = new NamespacedKey("cosmetics", "armor_applied");
    public static final NamespacedKey ORIGINAL_ASSET_KEY = new NamespacedKey("cosmetics", "armor_original_asset");
    public static final NamespacedKey ORIGINAL_HAS_ASSET_KEY = new NamespacedKey("cosmetics", "armor_original_had_asset");

    private final JavaPlugin plugin;
    private final CosmeticRegistry registry;

    /** 플레이어별 슬롯별 장착 치장 ID. DB 와 동기화 상태. */
    private final Map<UUID, Map<CosmeticType, String>> equipped = new HashMap<>();

    private BukkitTask tickTask;

    public ArmorHandler(JavaPlugin plugin, CosmeticRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void start() {
        if (tickTask != null) return;
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    for (CosmeticType type : CosmeticType.values()) {
                        if (type.isArmorSlot()) syncSlot(p, type);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 5L);
    }

    public void apply(Player wearer, CosmeticDefinition def) {
        if (def == null || !def.getType().isArmorSlot()) return;
        equipped.computeIfAbsent(wearer.getUniqueId(), k -> new EnumMap<>(CosmeticType.class))
                .put(def.getType(), def.getId());
        syncSlot(wearer, def.getType());
    }

    public void clear(Player wearer, CosmeticType type) {
        if (type == null || !type.isArmorSlot()) return;
        Map<CosmeticType, String> map = equipped.get(wearer.getUniqueId());
        if (map != null) map.remove(type);
        // 현재 슬롯의 실제 아이템 원복
        ItemStack slot = getRealSlotItem(wearer, type);
        if (slot != null && isApplied(slot)) revertStack(slot);
    }

    /** Join/Respawn/WorldChange 후 호출 — 4슬롯 전부 sync. */
    public void resendAll(Player wearer) {
        for (CosmeticType type : CosmeticType.values()) {
            if (type.isArmorSlot()) syncSlot(wearer, type);
        }
    }

    /** 특정 슬롯 재평가 + 적용. 주기 체크 + ArmorChangeEvent 에서 호출. */
    public void refreshSlot(Player wearer, CosmeticType type) {
        syncSlot(wearer, type);
    }

    /**
     * 플레이어 슬롯의 실제 아이템을 이상적 상태로 동기화.
     * 주기 체크가 매번 호출하는 핵심 메서드.
     */
    public void syncSlot(Player player, CosmeticType type) {
        ItemStack real = getRealSlotItem(player, type);
        if (real == null || real.getType() == Material.AIR) return;

        String cosmeticId = getEquippedId(player.getUniqueId(), type);
        CosmeticDefinition def = cosmeticId != null ? registry.get(cosmeticId) : null;

        // applicable_to 검사: 비어있으면 항상 적용, 아니면 해당 Material 일 때만.
        boolean shouldApply = def != null && def.canApplyTo(real.getType());
        boolean alreadyApplied = isApplied(real);
        String appliedId = getAppliedId(real);

        if (shouldApply && alreadyApplied && def.getId().equals(appliedId)) {
            return; // 이미 올바른 상태
        }

        if (alreadyApplied) {
            // 기존 cosmetic 이 적용돼 있음 → 원복 먼저
            revertStack(real);
        }

        if (shouldApply) {
            applyToStack(real, def);
        }
    }

    /** 4슬롯 모두 원복. 접속 종료 시 호출 (서버 저장 직전). */
    public void revertAll(Player player) {
        for (CosmeticType type : CosmeticType.values()) {
            if (!type.isArmorSlot()) continue;
            ItemStack slot = getRealSlotItem(player, type);
            if (slot != null && isApplied(slot)) revertStack(slot);
        }
    }

    /**
     * 임의의 ItemStack 원복. 드롭/사망 아이템 처리용.
     * ItemStack 의 ItemMeta 를 변경한다.
     */
    public void revertStack(ItemStack stack) {
        if (stack == null || !isApplied(stack)) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte hadAsset = pdc.get(ORIGINAL_HAS_ASSET_KEY, PersistentDataType.BYTE);
        String originalAsset = pdc.get(ORIGINAL_ASSET_KEY, PersistentDataType.STRING);

        // ItemMeta 의 setItemMeta 가 호출되어야 하므로, equippable 변경은 ItemStack.setData 로 처리.
        // 먼저 PDC 만 정리.
        pdc.remove(APPLIED_KEY);
        pdc.remove(ORIGINAL_ASSET_KEY);
        pdc.remove(ORIGINAL_HAS_ASSET_KEY);
        stack.setItemMeta(meta);

        // 그 다음 ItemStack 의 EQUIPPABLE 컴포넌트 복구
        Equippable existing = stack.getData(DataComponentTypes.EQUIPPABLE);
        if (existing != null) {
            Equippable.Builder b = existing.toBuilder();
            if (hadAsset != null && hadAsset == 1 && originalAsset != null) {
                Key original = Key.key(originalAsset);
                b.assetId(original);
            } else {
                // 원래 assetId 가 없었으므로 reset 의미로 base material 의 default 로 복구.
                // Paper API 가 null setter 를 지원 안 하면, base 아이템 기본 컴포넌트로 강제 reset.
                ItemStack fresh = new ItemStack(stack.getType());
                Equippable freshEq = fresh.getData(DataComponentTypes.EQUIPPABLE);
                if (freshEq != null) {
                    stack.setData(DataComponentTypes.EQUIPPABLE, freshEq);
                    return;
                }
            }
            stack.setData(DataComponentTypes.EQUIPPABLE, b.build());
        }
    }

    /** 플러그인 비활성화 시 모든 온라인 플레이어 4슬롯 원복. */
    public void shutdownAll() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            revertAll(p);
        }
        equipped.clear();
    }

    // ─── 조회 ───

    public String getEquippedId(UUID uuid, CosmeticType type) {
        Map<CosmeticType, String> map = equipped.get(uuid);
        return map == null ? null : map.get(type);
    }

    public boolean isEquipped(UUID uuid, CosmeticType type) {
        return getEquippedId(uuid, type) != null;
    }

    public static boolean isApplied(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta.getPersistentDataContainer().has(APPLIED_KEY, PersistentDataType.STRING);
    }

    public static String getAppliedId(ItemStack stack) {
        if (!isApplied(stack)) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(APPLIED_KEY, PersistentDataType.STRING);
    }

    // ─── 내부 ───

    private ItemStack getRealSlotItem(Player wearer, CosmeticType type) {
        PlayerInventory inv = wearer.getInventory();
        return switch (type) {
            case HAT -> inv.getHelmet();
            case CHEST -> inv.getChestplate();
            case LEGS -> inv.getLeggings();
            case FEET -> inv.getBoots();
            default -> null;
        };
    }

    private void applyToStack(ItemStack stack, CosmeticDefinition def) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 원본 assetId 백업 (ItemStack.getData 로 현재 EQUIPPABLE 컴포넌트 확인)
        Equippable existing = stack.getData(DataComponentTypes.EQUIPPABLE);
        Key originalAsset = existing != null ? existing.assetId() : null;
        if (originalAsset != null) {
            pdc.set(ORIGINAL_HAS_ASSET_KEY, PersistentDataType.BYTE, (byte) 1);
            pdc.set(ORIGINAL_ASSET_KEY, PersistentDataType.STRING, originalAsset.asString());
        } else {
            pdc.set(ORIGINAL_HAS_ASSET_KEY, PersistentDataType.BYTE, (byte) 0);
        }
        pdc.set(APPLIED_KEY, PersistentDataType.STRING, def.getId());
        stack.setItemMeta(meta);

        // 새 assetId 적용
        if (existing != null) {
            String assetId = def.getAssetId() != null ? def.getAssetId() : def.getId();
            Equippable patched = existing.toBuilder()
                    .assetId(Key.key("cosmetics", assetId))
                    .build();
            stack.setData(DataComponentTypes.EQUIPPABLE, patched);
        }
    }
}
