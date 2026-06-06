package com.exit.farming.provider;

import com.exit.core.api.CropInfo;
import com.exit.core.api.CropItemProvider;
import com.exit.farming.crop.Crop;
import com.exit.farming.item.CropItem;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Core의 CropItemProvider 인터페이스 구현.
 * Shop 플러그인이 ServiceRegistry 경유로 호출하여 13작물의 씨앗/열매를 발급받는다.
 *
 * 내부적으로 기존 CropItem 클래스의 정적 메서드를 그대로 위임.
 */
public class CropItemProviderImpl implements CropItemProvider {

    @Override
    public List<CropInfo> listCrops() {
        return java.util.Arrays.stream(Crop.values())
                .map(c -> new CropInfo(c.id(), c.koreanName(), c.customModelData(), c.repeatable()))
                .collect(Collectors.toList());
    }

    @Override
    public ItemStack createSeed(String cropId, int amount) {
        Crop c = Crop.byId(cropId == null ? null : cropId.toLowerCase());
        if (c == null) return null;
        return CropItem.createSeed(c, amount);
    }

    @Override
    public String identifySeed(ItemStack item) {
        Crop c = CropItem.identifySeed(item);
        return c == null ? null : c.id();
    }

    @Override
    public ItemStack createFruit(String cropId, int amount, boolean premium) {
        Crop c = Crop.byId(cropId == null ? null : cropId.toLowerCase());
        if (c == null) return null;
        return CropItem.createFruit(c, amount, premium);
    }

    @Override
    public String identifyFruit(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String kind = pdc.get(CropItem.KEY_KIND, org.bukkit.persistence.PersistentDataType.STRING);
        if (!"fruit".equals(kind)) return null;
        return pdc.get(CropItem.KEY_CROP_ID, org.bukkit.persistence.PersistentDataType.STRING);
    }

    @Override
    public boolean isPremium(ItemStack item) {
        if (identifyFruit(item) == null) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        Byte v = pdc.get(CropItem.KEY_PREMIUM, org.bukkit.persistence.PersistentDataType.BYTE);
        return v != null && v == 1;
    }
}
