package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.EnchantDispatcher;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.impl.life.AbstractMobLootEnchant;
import com.exit.customitems.lamp.enchant.impl.life.HunterEnchant;
import com.exit.customitems.util.RollUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * 동물/몬스터 처치 시 전리품 인챈트 처리.
 *
 * <ul>
 *   <li><b>사냥꾼</b> ({@link HunterEnchant}) — 모든 엔티티 대상, 본연 드랍 비례 증가.</li>
 *   <li><b>MobLoot 6종</b> ({@link AbstractMobLootEnchant}) — 엔티티 종류별 매칭. 현재 등록 해제 상태이나
 *       기존 부여된 아이템 호환 위해 처리 코드는 유지.</li>
 * </ul>
 *
 * LIFE_ACTION 규칙: 실제 처치에 사용된 주손 도구에 인챈트가 붙어있을 때만.
 */
public class MobKillListener implements Listener {

    private final EnchantDispatcher dispatcher;
    private final NamespacedKey hunterKey;

    public MobKillListener(Plugin plugin, EnchantDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.hunterKey = HunterEnchant.keyOf(plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack tool = killer.getInventory().getItemInMainHand();
        if (!ToolCategory.isLifeTool(tool)) return;

        List<ItemStack> extraDrops = new ArrayList<>();

        // --- MobLoot 6종 (등록 해제 상태지만 호환 위해 유지) ---
        for (RolledEnchant r : dispatcher.loadAll(tool)) {
            if (!(r.enchant() instanceof AbstractMobLootEnchant loot)) continue;
            if (!loot.matches(event.getEntityType())) continue;
            if (!RollUtil.percentRoll(r.values()[0])) continue;

            int copies = RollUtil.asCount(r.values()[1]);
            List<ItemStack> snapshot = new ArrayList<>(event.getDrops());
            for (int i = 0; i < copies; i++) {
                for (ItemStack drop : snapshot) {
                    if (drop != null && drop.getType() != Material.AIR) {
                        extraDrops.add(drop.clone());
                    }
                }
            }
        }

        // --- 사냥꾼 (stackable, 모든 엔티티 대상) ---
        for (RolledEnchant r : dispatcher.findAllInItem(tool, hunterKey)) {
            int[] v = r.values();
            if (!RollUtil.percentRoll(v[0])) continue;

            int copies = RollUtil.asCount(v[1]);
            List<ItemStack> snapshot = new ArrayList<>(event.getDrops());
            for (int i = 0; i < copies; i++) {
                for (ItemStack drop : snapshot) {
                    if (drop != null && drop.getType() != Material.AIR) {
                        extraDrops.add(drop.clone());
                    }
                }
            }
        }

        event.getDrops().addAll(extraDrops);
    }
}
