package com.exit.farming.item;

import com.exit.farming.FarmingPlugin;
import com.exit.farming.crop.Crop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 씨앗/열매 ItemStack 생성 및 식별.
 *
 * - 씨앗 : Material.WHEAT_SEEDS + CMD (1~13) + PDC(crop_id)
 * - 열매 : Material.BEETROOT  + CMD (1~13) + PDC(crop_id, premium)
 */
public final class CropItem {

    public static final NamespacedKey KEY_CROP_ID;
    public static final NamespacedKey KEY_PREMIUM;
    public static final NamespacedKey KEY_KIND;  // "seed" | "fruit"

    static {
        FarmingPlugin plugin = FarmingPlugin.getInstance();
        KEY_CROP_ID = new NamespacedKey(plugin, "crop_id");
        KEY_PREMIUM = new NamespacedKey(plugin, "crop_premium");
        KEY_KIND = new NamespacedKey(plugin, "crop_kind");
    }

    private CropItem() {}

    public static ItemStack createSeed(Crop crop, int amount) {
        ItemStack item = new ItemStack(Material.WHEAT_SEEDS, amount);
        item.editMeta(meta -> {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) crop.customModelData()));
            meta.setCustomModelDataComponent(cmd);

            meta.displayName(Component.text(crop.koreanName() + " 씨앗", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));

            var pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_CROP_ID, PersistentDataType.STRING, crop.id());
            pdc.set(KEY_KIND, PersistentDataType.STRING, "seed");
        });
        return item;
    }

    public static ItemStack createFruit(Crop crop, int amount, boolean premium) {
        ItemStack item = new ItemStack(Material.BEETROOT, amount);
        item.editMeta(meta -> {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) crop.customModelData()));
            meta.setCustomModelDataComponent(cmd);

            Component name;
            if (premium) {
                name = Component.text("✦ 최고급 " + crop.koreanName())
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false);
            } else {
                name = Component.text(crop.koreanName(), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false);
            }
            meta.displayName(name);

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("영양 만점이다", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (premium) {
                lore.add(Component.text("아주 비싸게 팔릴 것만 같다", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);

            var pdc = meta.getPersistentDataContainer();
            pdc.set(KEY_CROP_ID, PersistentDataType.STRING, crop.id());
            pdc.set(KEY_KIND, PersistentDataType.STRING, "fruit");
            pdc.set(KEY_PREMIUM, PersistentDataType.BYTE, (byte) (premium ? 1 : 0));
        });
        return item;
    }

    /** 플레이어 손에 든 아이템이 우리 씨앗이면 해당 Crop 반환 */
    public static Crop identifySeed(ItemStack item) {
        if (item == null || item.getType() != Material.WHEAT_SEEDS) return null;
        if (!item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String kind = pdc.get(KEY_KIND, PersistentDataType.STRING);
        if (!"seed".equals(kind)) return null;
        String id = pdc.get(KEY_CROP_ID, PersistentDataType.STRING);
        return id == null ? null : Crop.byId(id);
    }

    public static boolean isOurSeed(ItemStack item) {
        return identifySeed(item) != null;
    }

    /**
     * 원본 바닐라 씨앗/작물 아이템 (행운 인챈트 달려 우리 PDC 없는 것) 식별.
     * vanilla-suppress 기능에서 사용.
     */
    public static boolean isVanillaSeed(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        if (m != Material.WHEAT_SEEDS && m != Material.CARROT
                && m != Material.POTATO && m != Material.BEETROOT_SEEDS) {
            return false;
        }
        if (!item.hasItemMeta()) return true;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return !pdc.has(KEY_CROP_ID, PersistentDataType.STRING);
    }
}
