package com.exit.fishing.item;

import com.exit.fishing.FishingPlugin;
import com.exit.fishing.fish.FishRank;
import com.exit.fishing.fish.FishRegistry;
import com.exit.fishing.fish.FishSpecies;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 낚은 물고기 ItemStack 생성/파싱 유틸.
 *
 * - Material: COD (리소스팩 cod.json의 range_dispatch 사용)
 * - CustomModelData: 어종 CMD (float 1.0f ~ 24.0f)
 * - PDC tags (robust parsing):
 *     fish_id         : 어종 id (영문)
 *     fish_length_cm  : 길이
 *     fish_mass_g     : 질량
 *     fish_rank       : 등급 name()
 *     fish_premium    : 최고급 여부 (0/1)
 *
 * 하위 호환: 원본 스크립트는 아이템의 name/lore만 사용했다. 본 플러그인은 PDC 우선,
 * 없으면 name 기반 fallback (예: 외부에서 만든 아이템).
 */
public final class FishItem {

    public static final NamespacedKey KEY_ID;
    public static final NamespacedKey KEY_LENGTH;
    public static final NamespacedKey KEY_MASS;
    public static final NamespacedKey KEY_RANK;
    public static final NamespacedKey KEY_PREMIUM;

    static {
        FishingPlugin plugin = FishingPlugin.getInstance();
        KEY_ID = new NamespacedKey(plugin, "fish_id");
        KEY_LENGTH = new NamespacedKey(plugin, "fish_length_cm");
        KEY_MASS = new NamespacedKey(plugin, "fish_mass_g");
        KEY_RANK = new NamespacedKey(plugin, "fish_rank");
        KEY_PREMIUM = new NamespacedKey(plugin, "fish_premium");
    }

    private FishItem() {}

    public static ItemStack create(FishSpecies species, int lengthCm, int massG, FishRank rank, boolean premium) {
        ItemStack item = new ItemStack(Material.COD);
        item.editMeta(meta -> {
            // CustomModelData (float 기반)
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) species.customModelData()));
            meta.setCustomModelDataComponent(cmd);

            // 이름
            Component displayName;
            if (premium) {
                displayName = Component.text("✦ 최고급 " + species.koreanName())
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false);
            } else {
                displayName = Component.text(species.koreanName())
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false);
            }
            meta.displayName(displayName);

            // 로어
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("길이 : ", NamedTextColor.WHITE)
                    .append(Component.text(lengthCm + " cm", NamedTextColor.DARK_AQUA))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("질량 : ", NamedTextColor.WHITE)
                    .append(Component.text(massG + " g", NamedTextColor.GOLD))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("등급 : ", NamedTextColor.WHITE)
                    .append(Component.text(rank.display(), rank.color()))
                    .decoration(TextDecoration.ITALIC, false));
            if (premium) {
                lore.add(Component.empty());
                lore.add(Component.text("아주 비싸게 팔릴 것만 같다", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            // PDC
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_ID, PersistentDataType.STRING, species.id());
            pdc.set(KEY_LENGTH, PersistentDataType.INTEGER, lengthCm);
            pdc.set(KEY_MASS, PersistentDataType.INTEGER, massG);
            pdc.set(KEY_RANK, PersistentDataType.STRING, rank.name());
            pdc.set(KEY_PREMIUM, PersistentDataType.BYTE, (byte) (premium ? 1 : 0));
        });
        return item;
    }

    /** 도감/상점 디스플레이용 (스탯 없는 순수 샘플 아이템) */
    public static ItemStack displayOnly(FishSpecies species) {
        ItemStack item = new ItemStack(Material.COD);
        item.editMeta(meta -> {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) species.customModelData()));
            meta.setCustomModelDataComponent(cmd);
            meta.displayName(Component.text(species.koreanName(), NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        });
        return item;
    }

    public static boolean isFish(ItemStack item) {
        if (item == null || item.getType() != Material.COD) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_ID, PersistentDataType.STRING);
    }

    public static String getFishId(ItemStack item) {
        if (!isFish(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }

    public static int getLength(ItemStack item) {
        if (!isFish(item)) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(KEY_LENGTH, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static int getMass(ItemStack item) {
        if (!isFish(item)) return 0;
        Integer v = item.getItemMeta().getPersistentDataContainer().get(KEY_MASS, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static boolean isPremium(ItemStack item) {
        if (!isFish(item)) return false;
        Byte v = item.getItemMeta().getPersistentDataContainer().get(KEY_PREMIUM, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    public static FishSpecies getSpecies(ItemStack item) {
        String id = getFishId(item);
        return id == null ? null : FishRegistry.byId(id);
    }
}
