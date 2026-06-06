package com.exit.cosmetics.cosmetic;

import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticType;
import com.exit.cosmetics.registry.CosmeticRegistry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
 * 무기 스킨 핸들러 — Level 2 구현 (1인칭까지 스킨 적용).
 *
 * <p><b>접근 방식</b>: 실제 주손 ItemStack의 CustomModelData를 수정하고, 원본 CMD는
 * PersistentDataContainer에 백업. 플러그인이 적용한 스킨은 항상 복구 가능.
 *
 * <p><b>주기 체크 (매 5틱)</b>: 모든 온라인 플레이어의 주손을 확인해 다음 케이스를 처리:
 * <ul>
 *   <li>applicable 아이템을 새로 들었다 → 스킨 적용</li>
 *   <li>applicable 아닌 아이템을 들었다 → 이전 적용 상태 원복</li>
 *   <li>주손 슬롯이 비었다 → 마지막 슬롯 원복</li>
 *   <li>플레이어가 장착 치장을 변경했다 → 새 스킨 적용</li>
 * </ul>
 *
 * <p><b>안전장치</b>:
 * <ul>
 *   <li>드롭/사망 시: {@link #revertStack}으로 원본 복구 (listener에서 호출)</li>
 *   <li>접속 종료 시: {@link #revertHand}으로 원본 복구 (서버 저장 직전)</li>
 *   <li>플러그인 비활성화 시: {@link #shutdownAll}로 전원 원복</li>
 * </ul>
 */
public class WeaponHandler {

    public static final NamespacedKey APPLIED_KEY = new NamespacedKey("cosmetics", "weapon_applied");
    public static final NamespacedKey ORIGINAL_CMD_KEY = new NamespacedKey("cosmetics", "weapon_original_cmd");
    public static final NamespacedKey ORIGINAL_HAS_CMD_KEY = new NamespacedKey("cosmetics", "weapon_original_had_cmd");

    /** custom-items 플러그인이 박는 weapon_type 키. 값이 있으면 보호된 커스텀 무기. */
    private static final NamespacedKey CUSTOMITEMS_WEAPON_TYPE_KEY =
            new NamespacedKey("customitems", "weapon_type");

    private final JavaPlugin plugin;
    private final CosmeticRegistry registry;

    /** 플레이어별 카테고리별 장착 무기 스킨 ID. DB와 동기화 상태.
     *  카테고리가 다르면 동시 장착 가능 (예: SWORD + BOW). */
    private final Map<UUID, Map<WeaponCategory, String>> equipped = new HashMap<>();

    private BukkitTask tickTask;

    public WeaponHandler(JavaPlugin plugin, CosmeticRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void start() {
        if (tickTask != null) return;
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    syncHand(p);
                }
            }
        }.runTaskTimer(plugin, 20L, 5L);
    }

    public void apply(Player wearer, CosmeticDefinition def) {
        if (def == null || def.getType() != CosmeticType.WEAPON) return;
        WeaponCategory cat = WeaponCategory.fromMaterial(def.getBaseItem());
        equipped.computeIfAbsent(wearer.getUniqueId(), k -> new EnumMap<>(WeaponCategory.class))
                .put(cat, def.getId());
        syncHand(wearer);
    }

    /** 특정 카테고리 cosmetic 해제. */
    public void clear(Player wearer, WeaponCategory cat) {
        Map<WeaponCategory, String> map = equipped.get(wearer.getUniqueId());
        if (map != null) map.remove(cat);
        // 주손이 해당 카테고리면 즉시 원복
        ItemStack hand = wearer.getInventory().getItemInMainHand();
        if (hand != null && hand.getType() != Material.AIR
                && WeaponCategory.fromMaterial(hand.getType()) == cat
                && isApplied(hand)) {
            revertStack(hand);
        }
    }

    /** 모든 카테고리 해제. */
    public void clearAll(Player wearer) {
        equipped.remove(wearer.getUniqueId());
        revertHand(wearer);
    }

    public boolean isEquipped(UUID uuid, WeaponCategory cat) {
        Map<WeaponCategory, String> map = equipped.get(uuid);
        return map != null && map.containsKey(cat);
    }

    public String getEquippedId(UUID uuid, WeaponCategory cat) {
        Map<WeaponCategory, String> map = equipped.get(uuid);
        return map == null ? null : map.get(cat);
    }

    /**
     * 플레이어의 현재 주손 상태를 이상적 상태로 동기화.
     * 손에 든 아이템의 카테고리에 해당하는 cosmetic 을 적용.
     */
    public void syncHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        // 보호된 커스텀 무기(Frostmourne 등)는 cosmetic 적용 대상에서 제외
        if (isProtectedCustomWeapon(hand)) return;

        WeaponCategory cat = WeaponCategory.fromMaterial(hand.getType());
        String cosmeticId = getEquippedId(player.getUniqueId(), cat);
        CosmeticDefinition def = cosmeticId != null ? registry.get(cosmeticId) : null;

        boolean shouldApply = def != null && def.canApplyTo(hand.getType());
        boolean alreadyApplied = isApplied(hand);
        String appliedId = getAppliedId(hand);

        if (shouldApply && alreadyApplied && def.getId().equals(appliedId)) {
            return; // 이미 올바른 상태
        }

        if (alreadyApplied) {
            // 기존 스킨이 적용돼 있음 → 원복 먼저
            revertStack(hand);
        }

        if (shouldApply) {
            applyToStack(hand, def);
        }
    }

    /**
     * 플레이어 주손 아이템을 원복. 치장 해제 시 + 접속 종료 시 호출.
     */
    public void revertHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;
        if (isApplied(hand)) revertStack(hand);
    }

    /**
     * 임의의 ItemStack을 원복. 드롭/사망 아이템 처리용.
     * 이 메서드는 ItemStack의 ItemMeta를 변경한다.
     */
    public void revertStack(ItemStack stack) {
        if (stack == null || !isApplied(stack)) return;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte hadCmd = pdc.get(ORIGINAL_HAS_CMD_KEY, PersistentDataType.BYTE);
        if (hadCmd != null && hadCmd == 1) {
            Integer originalCmd = pdc.get(ORIGINAL_CMD_KEY, PersistentDataType.INTEGER);
            if (originalCmd != null) meta.setCustomModelData(originalCmd);
        } else {
            meta.setCustomModelData(null);
        }
        pdc.remove(APPLIED_KEY);
        pdc.remove(ORIGINAL_CMD_KEY);
        pdc.remove(ORIGINAL_HAS_CMD_KEY);
        stack.setItemMeta(meta);
    }

    /**
     * 플러그인 비활성화 시 모든 온라인 플레이어의 주손 원복.
     * 서버 재시작/리로드 시 스킨이 고착되는 걸 방지.
     */
    public void shutdownAll() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            revertHand(p);
        }
        equipped.clear();
    }

    /** 레거시 호환 — 모든 카테고리 해제. */
    public void clear(Player wearer) {
        clearAll(wearer);
    }

    // ─── 상태 확인 ───

    /** custom-items 가 등록한 보호 무기인지. 값 존재만으로 판단 (현재는 FROSTMOURNE 만). */
    private static boolean isProtectedCustomWeapon(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        return pdc.has(CUSTOMITEMS_WEAPON_TYPE_KEY, PersistentDataType.STRING);
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

    private void applyToStack(ItemStack stack, CosmeticDefinition def) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // 원본 CMD 백업
        if (meta.hasCustomModelData()) {
            pdc.set(ORIGINAL_HAS_CMD_KEY, PersistentDataType.BYTE, (byte) 1);
            pdc.set(ORIGINAL_CMD_KEY, PersistentDataType.INTEGER, meta.getCustomModelData());
        } else {
            pdc.set(ORIGINAL_HAS_CMD_KEY, PersistentDataType.BYTE, (byte) 0);
        }
        pdc.set(APPLIED_KEY, PersistentDataType.STRING, def.getId());

        if (def.getModelData() > 0) meta.setCustomModelData(def.getModelData());
        stack.setItemMeta(meta);
    }
}
