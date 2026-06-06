package com.exit.customitems.lamp.mutation;

import com.exit.customitems.lamp.LampKeys;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.LoreRenderer;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 변성램프 적용 로직. 결과 확률은 대상별 {@link MutationStrategy#probabilities()} 에서 가져옴.
 *
 * <ul>
 *   <li>무기 (WeaponMutationStrategy): 50% 성공 / 20% 초대박(2줄) / 10% 파괴 / 20% 실패</li>
 *   <li>방어구 (ArmorMutationStrategy): 70% 성공 / 0% 초대박 / 10% 파괴 / 20% 실패 (default)</li>
 * </ul>
 *
 * <p>호출 전제: 람프 = MUTATION 타입, 대상 = 적합한 무기/방어구 + 기존 람프 인챈트 보유 + 미변성.
 * 이 클래스는 결과 분기만 담당하며 입력 검증은 호출자(LampHandler) 가 처리.
 */
public class MutationApplier {

    public enum Outcome { SUCCESS, MEGA_SUCCESS, FAIL, DESTROYED }

    private final LampKeys keys;
    private final EnchantStorage storage;
    private final LoreRenderer loreRenderer;
    private final List<MutationStrategy> strategies;

    private final double pSuccess;
    private final double pDestroy;

    public MutationApplier(LampKeys keys, EnchantStorage storage, LoreRenderer loreRenderer,
                           List<MutationStrategy> strategies) {
        this(keys, storage, loreRenderer, strategies, 0.70, 0.10);
    }

    public MutationApplier(LampKeys keys, EnchantStorage storage, LoreRenderer loreRenderer,
                           List<MutationStrategy> strategies,
                           double pSuccess, double pDestroy) {
        this.keys = keys;
        this.storage = storage;
        this.loreRenderer = loreRenderer;
        this.strategies = strategies;
        this.pSuccess = pSuccess;
        this.pDestroy = pDestroy;
    }

    /** 이미 변성-락이 걸린 아이템인지. */
    public boolean isMutated(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Byte v = pdc.get(keys.lampMutated, PersistentDataType.BYTE);
        return v != null && v != 0;
    }

    public MutationStrategy findStrategy(ItemStack target) {
        for (MutationStrategy s : strategies) {
            if (s.canApply(target)) return s;
        }
        return null;
    }

    /**
     * 결과 분기 후 처리.
     * <ul>
     *   <li>SUCCESS: UNIQUE 1줄 추가 + 빨간 이름 + 락</li>
     *   <li>MEGA_SUCCESS: UNIQUE 2줄 추가 + 황금 이름 + 락 (무기 변성 한정)</li>
     *   <li>FAIL: 인챈트 변경 없음. 락만 + lore 표식</li>
     *   <li>DESTROYED: 호출자에게 대상 아이템 제거 알림 (caller 가 setAmount(0))</li>
     * </ul>
     *
     * <p>확률은 strategy 의 {@link MutationStrategy#probabilities()} 우선 사용.
     * strategy 가 null 인 경우(미지원 대상) 생성자 fallback 값 적용.
     */
    public Outcome apply(Player player, ItemStack target) {
        MutationStrategy strategy = findStrategy(target);
        MutationStrategy.Probabilities probs = (strategy != null)
                ? strategy.probabilities()
                : new MutationStrategy.Probabilities(pSuccess, 0.0, pDestroy);

        double roll = ThreadLocalRandom.current().nextDouble();
        // 구간 누적: [0, destroy) → DESTROY, [destroy, +mega) → MEGA, [+mega, +success) → SUCCESS, rest → FAIL
        double cum = probs.destroy();
        if (roll < cum) return Outcome.DESTROYED;

        cum += probs.megaSuccess();
        if (roll < cum) {
            // MEGA_SUCCESS 시도 — strategy 없거나 풀 부족이면 SUCCESS / FAIL 로 다운그레이드
            if (strategy == null) return failOnly(target);
            List<RolledEnchant> existing = storage.load(target);
            List<RolledEnchant> bonus1 = strategy.rollBonus(target, existing);
            if (bonus1.isEmpty()) return failOnly(target);

            List<RolledEnchant> afterFirst = new ArrayList<>(existing);
            afterFirst.addAll(bonus1);
            List<RolledEnchant> bonus2 = strategy.rollBonus(target, afterFirst);

            List<RolledEnchant> combined = new ArrayList<>(afterFirst);
            combined.addAll(bonus2);
            storage.save(target, combined);
            loreRenderer.render(target, combined);
            markMutated(target);
            if (!bonus2.isEmpty()) {
                markMegaSuccessName(target);
                return Outcome.MEGA_SUCCESS;
            }
            // 둘째줄 못 박음 (풀 1개뿐) → SUCCESS 다운그레이드
            markSuccessName(target);
            return Outcome.SUCCESS;
        }

        cum += probs.success();
        if (roll < cum) {
            // 일반 성공
            if (strategy == null) return failOnly(target);
            List<RolledEnchant> existing = storage.load(target);
            List<RolledEnchant> bonus = strategy.rollBonus(target, existing);
            if (bonus.isEmpty()) return failOnly(target);

            List<RolledEnchant> combined = new ArrayList<>(existing.size() + bonus.size());
            combined.addAll(existing);
            combined.addAll(bonus);
            storage.save(target, combined);
            loreRenderer.render(target, combined);
            markMutated(target);
            markSuccessName(target);
            return Outcome.SUCCESS;
        }

        // 나머지 = FAIL (락만)
        return failOnly(target);
    }

    private Outcome failOnly(ItemStack target) {
        markMutated(target);
        markFailLore(target);
        return Outcome.FAIL;
    }

    private void markMutated(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.lampMutated, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    /** 성공 시 아이템 이름을 빨간색으로 변경. 기존 이름 있으면 보존, 없으면 vanilla translation key. */
    private void markSuccessName(ItemStack item) {
        recolorDisplayName(item, NamedTextColor.RED);
    }

    /** 초대박(UNIQUE 2줄) 시 아이템 이름을 황금색으로. 상위 등급 시각 구분. */
    private void markMegaSuccessName(ItemStack item) {
        recolorDisplayName(item, NamedTextColor.GOLD);
    }

    private void recolorDisplayName(ItemStack item, NamedTextColor color) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        Component current = meta.displayName();
        if (current == null) {
            current = Component.translatable(item.getType().translationKey());
        }
        meta.displayName(current.color(color)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
    }

    /** 실패 시 lore 끝에 보라색 "(변성됨)" 줄 추가. 인챈트 lore (1~2줄) 뒤이므로 3번째 줄에 위치. */
    private void markFailLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(Component.text("(변성됨)")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * 변성 결과 표식이 이미 적용됐는지 검사.
     * displayName 색이 RED 이거나 lore 중 "(변성됨)" 포함이면 표식 있음으로 간주.
     * 마이그레이터의 멱등성 보장에 사용.
     */
    public boolean hasMutationDisplay(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Component name = meta.displayName();
        if (name != null && (NamedTextColor.RED.equals(name.color())
                || NamedTextColor.GOLD.equals(name.color()))) return true;
        List<Component> lore = meta.lore();
        if (lore != null) {
            for (Component line : lore) {
                String plain = PlainTextComponentSerializer.plainText().serialize(line);
                if (plain.contains("(변성됨)")) return true;
            }
        }
        return false;
    }

    /**
     * 외부에서 호출 가능한 표식 적용 — 마이그레이터에서 기존 변성 아이템에 사용.
     * @param success true 면 빨간 이름, false 면 lore 에 (변성됨)
     */
    public void applyMarker(ItemStack item, boolean success) {
        if (success) markSuccessName(item);
        else markFailLore(item);
    }
}
