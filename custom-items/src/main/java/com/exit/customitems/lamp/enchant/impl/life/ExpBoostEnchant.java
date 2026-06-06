package com.exit.customitems.lamp.enchant.impl.life;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantDispatcher;
import com.exit.customitems.lamp.enchant.EnchantScope;
import com.exit.customitems.lamp.enchant.EnchantTrigger;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.ValueSpec;
import com.exit.customitems.util.NumUtil;
import com.exit.customitems.util.RollUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 경험치 획득량 — 생활 활동(수확/채광/낚시/삽질/벌목) 시 [20~50]% 확률로
 * 경험치 구슬 [2~3]개 추가 드랍 (구슬당 {@link #XP_PER_ORB} exp).
 *
 * <p>폭발 채굴 / 트리 캐퍼시티 등 한 번에 여러 블록이 부수어질 때는
 * 각 블록의 단일 처리 경로에서 독립적으로 롤된다.
 */
public class ExpBoostEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_exp_boost";

    /** 드랍되는 경험치 구슬 1개당 경험치 양 (바닐라 광석 드랍 수준). */
    public static final int XP_PER_ORB = 3;

    private final NamespacedKey key;

    public ExpBoostEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "경험치 획득량"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantScope getScope()       { return EnchantScope.COMMON; }
    @Override public boolean isStackable()         { return true; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        return List.of(
            new ValueSpec(NumUtil.toStored(20.0), NumUtil.toStored(50.0), NumUtil.toStored(5.0)),
            new ValueSpec(NumUtil.toStored(2.0),  NumUtil.toStored(3.0),  NumUtil.toStored(1.0))
        );
    }

    @Override public boolean canApplyTo(ItemStack item) { return ToolCategory.isLifeTool(item); }

    @Override
    public Component renderLore(int[] values) {
        // 호환성: 기존 부여 아이템은 slot1 이 3~5 범위. 신규는 2~3.
        // lore 에서는 값 그대로 표시하되, 실제 드랍은 maybeDropExpOrbs 에서도 그대로 사용 (옛 아이템도 더 많이 드랍).
        // GUI 툴팁 길이 제한 — "(각 N exp)" 부가설명은 생략, 구슬당 exp 는 HTML 안내서에만 표기.
        return Component.text(
            "생활 활동 시 " + NumUtil.formatPercent1(values[0])
                + " 확률로 경험치 구슬 +" + NumUtil.formatInt(values[1])
        ).color(NamedTextColor.AQUA);
    }

    // ── 헬퍼: 5개 생활 리스너의 단일 블록/엔티티 경로에서 호출 ──

    /**
     * 주손 도구에 경험치 획득량 인챈트가 있으면, stackable 인챈트 각각 독립 롤하여
     * 발동 시 ExperienceOrb 를 K개 스폰. 도구 검증/스코프 검증 모두 포함.
     *
     * @param dispatcher 디스패처
     * @param expBoostKey {@link #keyOf(Plugin)} 결과 (각 리스너가 한 번만 생성하여 캐시)
     * @param player 활동 주체
     * @param loc 구슬 스폰 위치 (보통 부서진 블록 또는 잡힌 엔티티 위치)
     */
    public static void maybeDropExpOrbs(EnchantDispatcher dispatcher, NamespacedKey expBoostKey,
                                         Player player, Location loc) {
        if (player == null || loc == null || loc.getWorld() == null) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!ToolCategory.isLifeTool(tool)) return;

        for (RolledEnchant r : dispatcher.findAllInItem(tool, expBoostKey)) {
            int[] v = r.values();
            if (!RollUtil.percentRoll(v[0])) continue;
            int orbs = RollUtil.asCount(v[1]);
            for (int i = 0; i < orbs; i++) {
                loc.getWorld().spawn(loc, ExperienceOrb.class, o -> o.setExperience(XP_PER_ORB));
            }
        }
    }
}
