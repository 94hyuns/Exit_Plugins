package com.exit.rewards.model;

import com.exit.core.api.WeaponProvider;
import com.exit.core.registry.ServiceRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;

/**
 * 단일 드롭 아이템 정의. config.yml의 drops 리스트 항목 하나에 대응.
 *
 * <p>두 가지 모드:
 * <ul>
 *   <li>Material-based: {@code item: DIAMOND} 처럼 vanilla Material 지정.</li>
 *   <li>Provider-based: {@code provider: CustomItems, type: FROSTMOURNE} —
 *       {@link WeaponProvider} 통해 외부 플러그인의 ItemStack 생성.</li>
 * </ul>
 */
public record RewardItem(
        Material material,        // null when provider-based
        String providerName,      // null when material-based ("CustomItems")
        String typeId,            // null when material-based ("FROSTMOURNE" 등)
        int minAmount,
        int maxAmount,
        double chance,
        String displayName        // nullable — 별도 표시 이름 override
) {

    private static final Random RANDOM = new Random();

    public ItemStack roll() {
        if (RANDOM.nextDouble() >= chance) return null;

        int amount = minAmount == maxAmount
                ? minAmount
                : minAmount + RANDOM.nextInt(maxAmount - minAmount + 1);

        ItemStack stack = createStack(amount);
        if (stack == null) return null;

        if (displayName != null && !displayName.isBlank()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(displayName);
                meta.displayName(name);
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private ItemStack createStack(int amount) {
        if (providerName != null) {
            if ("CustomItems".equalsIgnoreCase(providerName)) {
                WeaponProvider wp = ServiceRegistry.get(WeaponProvider.class).orElse(null);
                if (wp == null) return null;
                return wp.createWeapon(typeId, amount);
            }
            return null;
        }
        if (material != null) {
            return new ItemStack(material, amount);
        }
        return null;
    }
}
