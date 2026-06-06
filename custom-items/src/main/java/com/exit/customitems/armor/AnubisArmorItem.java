package com.exit.customitems.armor;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 아누비스 방어구 세트 — 보스2 (Anubis) 처치 드롭 전용.
 *
 * <p>3부위 (투구 / 하의 / 신발). 흉갑 X. 모두 NETHERITE 베이스.
 * 시각:
 *   - 인벤 아이콘: CustomModelData (cosmetic 모델 재사용 — crown_gold / gilded_leggings / gilded_boots).
 *   - 착용 시 외형: Equippable.assetId (cosmetic equipment def 재사용).
 *
 * <p>각 부위는 생성 시점에 SPEED / SATURATION / HASTE 중 하나를 랜덤으로 가짐 ({@link Buff}).
 * ArmorTickTask 가 부위별 buff 종류를 집계해서 같은 buff 개수 = 레벨로 포션효과 적용.
 * 예: SPEED 헬멧 + SPEED 부츠 = SPEED Lv2, HASTE 하의 1개 = HASTE Lv1.
 *
 * <p>스탯: vanilla 네더라이트 attribute (helmet 3/3, leggings 6/3, boots 3/3 armor/toughness).
 */
public final class AnubisArmorItem {

    public static final String HELMET_TYPE_ID   = "ANUBIS_HELMET";
    public static final String LEGGINGS_TYPE_ID = "ANUBIS_LEGGINGS";
    public static final String BOOTS_TYPE_ID    = "ANUBIS_BOOTS";

    /** 각 부위가 가질 수 있는 버프 종류. */
    public enum Buff {
        SPEED(PotionEffectType.SPEED,      "신속",   "이동 속도 증가",  NamedTextColor.AQUA),
        SATURATION(PotionEffectType.SATURATION, "포만", "배고픔 자동 회복", NamedTextColor.GREEN),
        HASTE(PotionEffectType.HASTE,      "성급함", "채굴/공격 속도 증가", NamedTextColor.YELLOW);

        public final PotionEffectType effect;
        public final String koName;
        public final String description;
        public final NamedTextColor color;

        Buff(PotionEffectType effect, String koName, String description, NamedTextColor color) {
            this.effect = effect;
            this.koName = koName;
            this.description = description;
            this.color = color;
        }

        public static Buff random() {
            Buff[] values = values();
            return values[ThreadLocalRandom.current().nextInt(values.length)];
        }

        public static Buff fromString(String s) {
            if (s == null) return null;
            try { return Buff.valueOf(s.toUpperCase()); }
            catch (IllegalArgumentException e) { return null; }
        }

        /**
         * 부위에 부여될 버프 목록을 무작위로 굴림.
         * 50% = 1개, 30% = 2개, 20% = 3개. 중복 없이.
         */
        public static EnumSet<Buff> randomSet() {
            int roll = ThreadLocalRandom.current().nextInt(100);
            int count;
            if (roll < 50)      count = 1;   // 50%
            else if (roll < 80) count = 2;   // 30%
            else                count = 3;   // 20%
            List<Buff> all = new ArrayList<>(Arrays.asList(values()));
            java.util.Collections.shuffle(all);
            return EnumSet.copyOf(all.subList(0, count));
        }
    }

    // CMD — 기존 cosmetic 모델 재사용 (netherite_*.json items def 에 매핑돼 있음)
    private static final int HELMET_CMD   = 1007;  // crown_gold (NOTE: netherite_helmet 에 별도 추가 필요)
    private static final int LEGGINGS_CMD = 1206;  // gilded_leggings (이미 매핑됨)
    private static final int BOOTS_CMD    = 1306;  // gilded_boots (이미 매핑됨)

    // 착용 시 외형 (cosmetic equipment def 재사용)
    private static final Key HELMET_ASSET   = Key.key("cosmetics", "crown_gold");
    private static final Key LEGGINGS_ASSET = Key.key("cosmetics", "fully_gilded_kastenbrust");
    private static final Key BOOTS_ASSET    = Key.key("cosmetics", "fully_gilded_kastenbrust");

    private static final Key NETHERITE_EQUIP_SOUND = Key.key("minecraft", "item.armor.equip_netherite");

    private final ArmorKeys keys;

    public AnubisArmorItem(ArmorKeys keys) {
        this.keys = keys;
    }

    // === 랜덤 버프셋 (드롭 진입점). 50% 1개 / 30% 2개 / 20% 3개 ===
    public ItemStack createHelmet(int amount)   { return createHelmet(amount, Buff.randomSet()); }
    public ItemStack createLeggings(int amount) { return createLeggings(amount, Buff.randomSet()); }
    public ItemStack createBoots(int amount)    { return createBoots(amount, Buff.randomSet()); }

    // === 버프 지정 (테스트/관리 용도). 단일 buff = Lv1 1개 보장 ===
    public ItemStack createHelmet(int amount, Buff buff) {
        return createHelmet(amount, EnumSet.of(buff));
    }
    public ItemStack createLeggings(int amount, Buff buff) {
        return createLeggings(amount, EnumSet.of(buff));
    }
    public ItemStack createBoots(int amount, Buff buff) {
        return createBoots(amount, EnumSet.of(buff));
    }

    // === 버프셋 직접 지정 ===
    public ItemStack createHelmet(int amount, EnumSet<Buff> buffs) {
        return build(Material.NETHERITE_HELMET, amount, HELMET_TYPE_ID, HELMET_CMD,
                EquipmentSlot.HEAD, HELMET_ASSET, "아누비스의 왕관", "왕관 형상의 황금 장식.", "머리", buffs);
    }

    public ItemStack createLeggings(int amount, EnumSet<Buff> buffs) {
        return build(Material.NETHERITE_LEGGINGS, amount, LEGGINGS_TYPE_ID, LEGGINGS_CMD,
                EquipmentSlot.LEGS, LEGGINGS_ASSET, "아누비스의 황금 각반", "고대 이집트의 황금 갑옷.", "다리", buffs);
    }

    public ItemStack createBoots(int amount, EnumSet<Buff> buffs) {
        return build(Material.NETHERITE_BOOTS, amount, BOOTS_TYPE_ID, BOOTS_CMD,
                EquipmentSlot.FEET, BOOTS_ASSET, "아누비스의 황금 부츠", "사막을 걷는 자의 부츠.", "발", buffs);
    }

    private ItemStack build(Material mat, int amount, String typeId, int cmd,
                            EquipmentSlot slot, Key assetId,
                            String baseName, String flavor, String slotKo, EnumSet<Buff> buffs) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();

        if (buffs == null || buffs.isEmpty()) buffs = EnumSet.of(Buff.random());

        // 이름 — 버프 개수에 따라 등급 표시 (★ 1~3)
        String stars = "★".repeat(buffs.size());
        meta.displayName(Component.text(baseName + " " + stars)
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        // 인벤토리용 CustomModelData
        var cmdComp = meta.getCustomModelDataComponent();
        cmdComp.setFloats(List.of((float) cmd));
        meta.setCustomModelDataComponent(cmdComp);

        // Lore — 모든 버프 줄 단위 명시
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(flavor)
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("[ 아누비스 세트 효과 — " + buffs.size() + "버프 ]")
                .color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        for (Buff b : buffs) {
            lore.add(Component.text(" • " + b.koName + " — " + b.description)
                    .color(b.color).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text(" 같은 버프 부위 N개 = 해당 버프 LvN 상시")
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("슬롯: " + slotKo)
                .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // PDC — type + buff 목록 (콤마 구분)
        String buffStr = buffs.stream().map(Enum::name).collect(Collectors.joining(","));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.type, PersistentDataType.STRING, typeId);
        pdc.set(keys.anubisBuff, PersistentDataType.STRING, buffStr);

        item.setItemMeta(meta);

        // 착용 시 외형
        item.setData(DataComponentTypes.EQUIPPABLE,
                Equippable.equippable(slot)
                        .assetId(assetId)
                        .equipSound(NETHERITE_EQUIP_SOUND)
                        .damageOnHurt(true)
                        .swappable(true)
                        .build());

        return item;
    }

    public boolean isAnubisHelmet(ItemStack item)   { return matches(item, HELMET_TYPE_ID); }
    public boolean isAnubisLeggings(ItemStack item) { return matches(item, LEGGINGS_TYPE_ID); }
    public boolean isAnubisBoots(ItemStack item)    { return matches(item, BOOTS_TYPE_ID); }

    public boolean isAnubisPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String t = item.getItemMeta().getPersistentDataContainer().get(keys.type, PersistentDataType.STRING);
        return HELMET_TYPE_ID.equals(t) || LEGGINGS_TYPE_ID.equals(t) || BOOTS_TYPE_ID.equals(t);
    }

    /** ItemStack 에 박힌 버프 목록. 아누비스 갑옷이 아니거나 PDC 없으면 빈 EnumSet. */
    public EnumSet<Buff> readBuffs(ItemStack item) {
        EnumSet<Buff> empty = EnumSet.noneOf(Buff.class);
        if (!isAnubisPiece(item)) return empty;
        String s = item.getItemMeta().getPersistentDataContainer().get(keys.anubisBuff, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) return empty;
        EnumSet<Buff> result = EnumSet.noneOf(Buff.class);
        for (String token : s.split(",")) {
            Buff b = Buff.fromString(token.trim());
            if (b != null) result.add(b);
        }
        return result;
    }

    private boolean matches(ItemStack item, String typeId) {
        if (item == null || !item.hasItemMeta()) return false;
        return typeId.equals(
                item.getItemMeta().getPersistentDataContainer().get(keys.type, PersistentDataType.STRING));
    }
}
