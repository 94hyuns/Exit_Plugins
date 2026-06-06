package com.exit.customitems.lamp.enchant;

import com.exit.customitems.lamp.LampKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 도구의 로어를 갱신한다.
 *
 * <p>기존 로어를 보존하기 위해 "램프 로어 라인 수"를 PDC에 저장하고,
 * 갱신 시 로어 <b>맨 위</b>에서 해당 라인만큼만 제거 후 새 라인을 삽입한다.
 * 유저가 직접 달아둔 다른 로어 라인은 건드리지 않는다.
 *
 * <p>로어 형식:
 * <pre>
 *   ✦ 램프 인챈트          (헤더, LIGHT_PURPLE)
 *   · 생명력 흡수 0.5      (AQUA)
 *   · 크리티컬 확률 3.0%   (AQUA)
 *   [기존 사용자 로어...]
 * </pre>
 */
public class LoreRenderer {

    private static final Component HEADER = Component.text("✦ 램프 인챈트")
        .color(NamedTextColor.LIGHT_PURPLE)
        .decoration(TextDecoration.ITALIC, false);

    private final LampKeys keys;

    public LoreRenderer(LampKeys keys) {
        this.keys = keys;
    }

    public void render(ItemStack item, List<RolledEnchant> rolled) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 1. 기존 로어에서 램프 라인만 걷어냄
        List<Component> existing = meta.lore();
        if (existing == null) existing = new ArrayList<>();
        else existing = new ArrayList<>(existing);

        Integer prevCount = pdc.get(keys.loreLineCount, PersistentDataType.INTEGER);
        if (prevCount != null && prevCount > 0) {
            int remove = Math.min(prevCount, existing.size());
            for (int i = 0; i < remove; i++) existing.remove(0);
        }

        // 2. 새 램프 라인 빌드
        List<Component> lampLines = new ArrayList<>();
        if (rolled != null && !rolled.isEmpty()) {
            lampLines.add(HEADER);
            for (RolledEnchant r : rolled) {
                Component line = Component.text("· ")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(r.enchant().renderLore(r.values())
                        .colorIfAbsent(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                lampLines.add(line);
            }
        }

        // 3. 합치고 저장
        List<Component> newLore = new ArrayList<>(lampLines.size() + existing.size());
        newLore.addAll(lampLines);
        newLore.addAll(existing);

        meta.lore(newLore.isEmpty() ? null : newLore);

        if (lampLines.isEmpty()) {
            pdc.remove(keys.loreLineCount);
        } else {
            pdc.set(keys.loreLineCount, PersistentDataType.INTEGER, lampLines.size());
        }

        item.setItemMeta(meta);
    }
}
