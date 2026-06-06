package com.exit.customitems.lamp.enchant;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 모든 커스텀 인챈트의 중앙 레지스트리.
 * 2단계에서 인챈트 구현체가 추가될 때마다 여기에 register(...) 한 줄씩 붙이면 된다.
 */
public class EnchantRegistry {

    private final Map<NamespacedKey, CustomEnchant> byKey = new HashMap<>();
    private final Map<EnchantCategory, List<CustomEnchant>> byCategory = new EnumMap<>(EnchantCategory.class);

    public EnchantRegistry() {
        for (EnchantCategory c : EnchantCategory.values()) {
            byCategory.put(c, new ArrayList<>());
        }
    }

    public void register(CustomEnchant enchant) {
        NamespacedKey key = enchant.getKey();
        if (byKey.containsKey(key)) {
            throw new IllegalStateException("Enchant already registered: " + key);
        }
        byKey.put(key, enchant);
        byCategory.get(enchant.getCategory()).add(enchant);
    }

    public Optional<CustomEnchant> get(NamespacedKey key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public List<CustomEnchant> getByCategory(EnchantCategory category) {
        return Collections.unmodifiableList(byCategory.get(category));
    }

    /** 해당 카테고리 중 item 에 적용 가능한 인챈트 목록. */
    public List<CustomEnchant> getApplicable(ItemStack item, EnchantCategory category) {
        List<CustomEnchant> out = new ArrayList<>();
        for (CustomEnchant e : byCategory.get(category)) {
            if (e.canApplyTo(item)) out.add(e);
        }
        return out;
    }

    public int size() {
        return byKey.size();
    }

    /** 등록된 전 인챈트 (read-only). 표시명 검색이나 dump 용. */
    public java.util.Collection<CustomEnchant> allEnchants() {
        return Collections.unmodifiableCollection(byKey.values());
    }
}

// === LEGACY DEAD CODE BELOW (compile-out) — kept for reference if yml dump needed later ===
/*
    public void dumpToYaml(File file, Logger logger) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
            "이 파일은 서버 시작 시마다 자동 생성됩니다.",
            "수정해도 다음 시작에 덮어쓰여집니다.",
            "현재 등록된 모든 커스텀 인챈트를 카테고리/등급/적용 대상별로 정리한 목록입니다."
        ));

        // === 전투 ===
        Map<String, Map<String, Object>> weaponBasic = new LinkedHashMap<>();
        Map<String, Map<String, Object>> weaponUnique = new LinkedHashMap<>();
        Map<String, Map<String, Object>> armorBasic = new LinkedHashMap<>();
        Map<String, Map<String, Object>> armorSet = new LinkedHashMap<>();

        for (CustomEnchant e : byCategory.get(EnchantCategory.COMBAT)) {
            boolean weapon = isWeaponEnchant(e);
            Map<String, Object> entry = describeCombat(e, weapon);
            String id = e.getKey().getKey();
            if (weapon) {
                (e.getTier() == EnchantTier.BASIC ? weaponBasic : weaponUnique).put(id, entry);
            } else {
                (e.getTier() == EnchantTier.BASIC ? armorBasic : armorSet).put(id, entry);
            }
        }
        yaml.set("combat.weapon.unique", weaponUnique);
        yaml.set("combat.weapon.basic", weaponBasic);
        yaml.set("combat.armor.set", armorSet);
        yaml.set("combat.armor.basic", armorBasic);

        // === 생활 ===
        Map<String, Map<String, Map<String, Object>>> lifeGroups = new LinkedHashMap<>();
        lifeGroups.put("common", new LinkedHashMap<>());
        lifeGroups.put("pickaxe", new LinkedHashMap<>());
        lifeGroups.put("hoe", new LinkedHashMap<>());
        lifeGroups.put("shovel", new LinkedHashMap<>());
        lifeGroups.put("fishing_rod", new LinkedHashMap<>());
        lifeGroups.put("axe", new LinkedHashMap<>());
        lifeGroups.put("other", new LinkedHashMap<>());

        for (CustomEnchant e : byCategory.get(EnchantCategory.LIFE)) {
            String group = lifeGroupOf(e);
            lifeGroups.get(group).put(e.getKey().getKey(), describeLife(e));
        }
        for (Map.Entry<String, Map<String, Map<String, Object>>> entry : lifeGroups.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                yaml.set("life." + entry.getKey(), entry.getValue());
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            if (logger != null) logger.log(Level.WARNING, "enchantslist.yml 저장 실패", e);
        }
    }

    private boolean isWeaponEnchant(CustomEnchant e) {
        return e.canApplyTo(new ItemStack(Material.IRON_SWORD))
            || e.canApplyTo(new ItemStack(Material.TRIDENT))
            || e.canApplyTo(new ItemStack(Material.MACE))
            || e.canApplyTo(new ItemStack(Material.BOW))
            || isSpearApplicable(e);
    }

    private boolean isSpearApplicable(CustomEnchant e) {
        for (String name : new String[]{"iron_spear", "wooden_spear", "diamond_spear", "netherite_spear"}) {
            Material m = Material.matchMaterial(name);
            if (m != null && e.canApplyTo(new ItemStack(m))) return true;
        }
        return false;
    }

    private Map<String, Object> describeCombat(CustomEnchant e, boolean isWeapon) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", e.getDisplayName());
        m.put("tier", e.getTier().displayName(isWeapon));
        m.put("trigger", e.getTrigger().name());
        m.put("applies_to", appliesToLabel(e));
        return m;
    }

    private Map<String, Object> describeLife(CustomEnchant e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", e.getDisplayName());
        m.put("applies_to", appliesToLabel(e));
        m.put("stackable", e.isStackable());
        return m;
    }

    private String lifeGroupOf(CustomEnchant e) {
        boolean p = e.canApplyTo(new ItemStack(Material.IRON_PICKAXE));
        boolean h = e.canApplyTo(new ItemStack(Material.IRON_HOE));
        boolean s = e.canApplyTo(new ItemStack(Material.IRON_SHOVEL));
        boolean r = e.canApplyTo(new ItemStack(Material.FISHING_ROD));
        boolean a = e.canApplyTo(new ItemStack(Material.IRON_AXE));
        int count = (p ? 1 : 0) + (h ? 1 : 0) + (s ? 1 : 0) + (r ? 1 : 0) + (a ? 1 : 0);
        if (count >= 4) return "common";
        if (p && !h && !s && !r && !a) return "pickaxe";
        if (h && !p && !s && !r && !a) return "hoe";
        if (s && !p && !h && !r && !a) return "shovel";
        if (r && !p && !h && !s && !a) return "fishing_rod";
        if (a && !p && !h && !s && !r) return "axe";
        return "other";
    }

    private List<String> appliesToLabel(CustomEnchant e) {
        List<String> out = new ArrayList<>();
        if (e.canApplyTo(new ItemStack(Material.IRON_SWORD))) out.add("검");
        if (isSpearApplicable(e)) out.add("창");
        if (e.canApplyTo(new ItemStack(Material.TRIDENT))) out.add("삼지창");
        if (e.canApplyTo(new ItemStack(Material.MACE))) out.add("철퇴");
        if (e.canApplyTo(new ItemStack(Material.BOW))) out.add("활");
        if (e.canApplyTo(new ItemStack(Material.IRON_HELMET))) out.add("방어구");
        if (e.canApplyTo(new ItemStack(Material.IRON_PICKAXE))) out.add("곡괭이");
        if (e.canApplyTo(new ItemStack(Material.IRON_HOE))) out.add("괭이");
        if (e.canApplyTo(new ItemStack(Material.IRON_SHOVEL))) out.add("삽");
        if (e.canApplyTo(new ItemStack(Material.FISHING_ROD))) out.add("낚싯대");
        if (e.canApplyTo(new ItemStack(Material.IRON_AXE))) out.add("도끼");
        return out;
    }
}
*/
