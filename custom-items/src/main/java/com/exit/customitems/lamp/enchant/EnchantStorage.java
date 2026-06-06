package com.exit.customitems.lamp.enchant;

import com.exit.customitems.lamp.LampKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 도구 ItemStack PDC 에 램프 인챈트 목록을 저장/로드.
 *
 * <p>저장 포맷 (단일 String):
 * <pre>
 *   customitems:lifesteal|50;customitems:critrate|300,500
 * </pre>
 * 인챈트 간 구분자: {@code ;}, 키-값 구분자: {@code |}, 값 간 구분자: {@code ,}.
 */
public class EnchantStorage {

    private static final String ENCHANT_SEP = ";";
    private static final String KV_SEP      = "|";
    private static final String VALUE_SEP   = ",";

    private final LampKeys keys;
    private final EnchantRegistry registry;

    public EnchantStorage(LampKeys keys, EnchantRegistry registry) {
        this.keys = keys;
        this.registry = registry;
    }

    public List<RolledEnchant> load(ItemStack item) {
        List<RolledEnchant> out = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return out;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(keys.enchants, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return out;

        for (String entry : raw.split(ENCHANT_SEP)) {
            if (entry.isEmpty()) continue;
            int sep = entry.indexOf(KV_SEP);
            if (sep < 0) continue;
            String keyStr = entry.substring(0, sep);
            String valStr = entry.substring(sep + 1);

            NamespacedKey key = NamespacedKey.fromString(keyStr);
            if (key == null) continue;
            CustomEnchant enchant = registry.get(key).orElse(null);
            if (enchant == null) continue;  // 등록 해제된 인챈트는 조용히 스킵

            String[] parts = valStr.isEmpty() ? new String[0] : valStr.split(VALUE_SEP);
            int[] values = new int[parts.length];
            boolean ok = true;
            for (int i = 0; i < parts.length; i++) {
                try {
                    values[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) { ok = false; break; }
            }
            if (!ok) continue;
            if (values.length != enchant.getValueSpecs().size()) continue;
            out.add(new RolledEnchant(enchant, values));
        }
        return out;
    }

    /** 저장. null/빈 리스트를 저장하면 키를 제거. */
    public void save(ItemStack item, List<RolledEnchant> rolled) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (rolled == null || rolled.isEmpty()) {
            pdc.remove(keys.enchants);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rolled.size(); i++) {
                if (i > 0) sb.append(ENCHANT_SEP);
                RolledEnchant r = rolled.get(i);
                sb.append(r.enchant().getKey().toString()).append(KV_SEP);
                int[] values = r.values();
                for (int j = 0; j < values.length; j++) {
                    if (j > 0) sb.append(VALUE_SEP);
                    sb.append(values[j]);
                }
            }
            pdc.set(keys.enchants, PersistentDataType.STRING, sb.toString());
        }
        item.setItemMeta(meta);
    }

    public boolean has(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(keys.enchants, PersistentDataType.STRING);
        return raw != null && !raw.isEmpty();
    }
}
