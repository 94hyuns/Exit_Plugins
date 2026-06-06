package com.exit.shop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 바닐라 ShopItem 의 yml 메타 옵션을 ItemStack 에 적용.
 *
 * 지원 키:
 *   display-name        : "&e두쫀쿠" 같은 레거시 색상 코드 문자열
 *   custom-model-data   : 양의 정수 (리소스팩 CMD)
 *   enchantments        : ["sharpness:5", "mending:1", ...]
 *                         - ENCHANTED_BOOK 이면 stored enchant
 *                         - 그 외엔 일반 enchant
 *   lore                : ["§7첫 줄", "§7둘째 줄"]
 */
public final class VanillaMetaApplier {

    private VanillaMetaApplier() {}

    public static void apply(ItemStack stack, Map<String, Object> meta, Logger logger, String itemIdForLog) {
        if (stack == null || meta == null || meta.isEmpty()) return;
        ItemMeta im = stack.getItemMeta();
        if (im == null) return;

        Object dn = meta.get("display-name");
        if (dn != null) {
            Component name = LegacyComponentSerializer.legacySection()
                    .deserialize(dn.toString().replace('&', '§'))
                    .decoration(TextDecoration.ITALIC, false);
            im.displayName(name);
        }

        Object cmd = meta.get("custom-model-data");
        if (cmd instanceof Number n && n.intValue() > 0) {
            var c = im.getCustomModelDataComponent();
            c.setFloats(List.of((float) n.intValue()));
            im.setCustomModelDataComponent(c);
        }

        Object lore = meta.get("lore");
        if (lore instanceof List<?> ll && !ll.isEmpty()) {
            List<Component> comps = new ArrayList<>(ll.size());
            for (Object o : ll) {
                comps.add(LegacyComponentSerializer.legacySection()
                        .deserialize(o.toString().replace('&', '§'))
                        .decoration(TextDecoration.ITALIC, false));
            }
            im.lore(comps);
        }

        Object ench = meta.get("enchantments");
        if (ench instanceof List<?> list && !list.isEmpty()) {
            boolean storage = im instanceof EnchantmentStorageMeta;
            for (Object o : list) {
                String[] parts = o.toString().split(":");
                if (parts.length != 2) {
                    if (logger != null) logger.warning("[Shop] '" + itemIdForLog + "' enchantment 표기 오류: " + o);
                    continue;
                }
                Enchantment e = lookupEnchantment(parts[0]);
                if (e == null) {
                    if (logger != null) logger.warning("[Shop] '" + itemIdForLog + "' 알 수 없는 enchantment: " + parts[0]);
                    continue;
                }
                int level;
                try { level = Integer.parseInt(parts[1].trim()); }
                catch (NumberFormatException ex) {
                    if (logger != null) logger.warning("[Shop] '" + itemIdForLog + "' enchantment 레벨 파싱 실패: " + parts[1]);
                    continue;
                }
                if (storage) {
                    ((EnchantmentStorageMeta) im).addStoredEnchant(e, level, true);
                } else {
                    im.addEnchant(e, level, true);
                }
            }
        }

        stack.setItemMeta(im);
    }

    @SuppressWarnings("deprecation")
    private static Enchantment lookupEnchantment(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        NamespacedKey nk = NamespacedKey.minecraft(key);
        Enchantment e = Registry.ENCHANTMENT.get(nk);
        if (e != null) return e;
        return Enchantment.getByKey(nk);
    }
}
