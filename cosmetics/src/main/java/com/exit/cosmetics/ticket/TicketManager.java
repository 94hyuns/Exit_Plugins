package com.exit.cosmetics.ticket;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 뽑기권 ItemStack 관리 (치장 뽑기권 + 탈것 뽑기권 통합).
 *
 * <p>두 뽑기권은 PersistentDataContainer의 TICKET_KIND_KEY 값(COSMETIC/MOUNT)으로 구분.
 * 기존 TICKET_KEY는 호환성 유지용으로 그대로 두고, 신규 키로 종류 식별.
 */
public class TicketManager {

    public static final NamespacedKey TICKET_KEY = new NamespacedKey("cosmetics", "ticket");
    public static final NamespacedKey TICKET_KIND_KEY = new NamespacedKey("cosmetics", "ticket_kind");

    public enum Kind { COSMETIC, MOUNT }

    private TicketSpec cosmeticSpec = new TicketSpec(Material.PAPER, 0,
            "§e치장 뽑기권", List.of("§7우클릭하여 사용"));
    private TicketSpec mountSpec = new TicketSpec(Material.PAPER, 0,
            "§b탈것 뽑기권", List.of("§7우클릭하여 사용"));

    public void load(FileConfiguration config) {
        cosmeticSpec = readSpec(config, "ticket", cosmeticSpec);
        mountSpec = readSpec(config, "mount_ticket", mountSpec);
    }

    private TicketSpec readSpec(FileConfiguration config, String prefix, TicketSpec defaults) {
        Material mat = defaults.material;
        String matStr = config.getString(prefix + ".base_item");
        if (matStr != null) {
            Material parsed = Material.matchMaterial(matStr);
            if (parsed != null) mat = parsed;
        }
        int model = config.getInt(prefix + ".model_data", defaults.modelData);
        String name = config.getString(prefix + ".display_name", defaults.displayName);
        List<String> lore = config.getStringList(prefix + ".lore");
        if (lore.isEmpty()) lore = defaults.lore;
        return new TicketSpec(mat, model, name, lore);
    }

    /** 치장 뽑기권 n장 생성. */
    public ItemStack createTicket(int amount) {
        return create(cosmeticSpec, Kind.COSMETIC, amount);
    }

    /** 탈것 뽑기권 n장 생성. */
    public ItemStack createMountTicket(int amount) {
        return create(mountSpec, Kind.MOUNT, amount);
    }

    private ItemStack create(TicketSpec spec, Kind kind, int amount) {
        ItemStack stack = new ItemStack(spec.material, Math.max(1, Math.min(amount, 64)));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(Component.text(spec.displayName).decoration(TextDecoration.ITALIC, false));
        if (spec.modelData > 0) meta.setCustomModelData(spec.modelData);

        List<Component> loreComponents = new ArrayList<>();
        for (String line : spec.lore) {
            loreComponents.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        meta.getPersistentDataContainer().set(TICKET_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(TICKET_KIND_KEY, PersistentDataType.STRING, kind.name());
        stack.setItemMeta(meta);
        return stack;
    }

    /** 주어진 아이템이 (어떤 종류든) 뽑기권인지 식별. */
    public static boolean isTicket(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(TICKET_KEY, PersistentDataType.BYTE);
    }

    /** 뽑기권 종류 식별. 뽑기권이 아니면 null. KIND 키가 없는 구버전 뽑기권은 COSMETIC 으로 간주. */
    public static Kind identify(ItemStack stack) {
        if (!isTicket(stack)) return null;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(TICKET_KIND_KEY, PersistentDataType.STRING)) return Kind.COSMETIC;
        String v = pdc.get(TICKET_KIND_KEY, PersistentDataType.STRING);
        try { return Kind.valueOf(v); } catch (IllegalArgumentException e) { return Kind.COSMETIC; }
    }

    /**
     * 플레이어의 손에서 뽑기권 1장 소비. 수량이 1이면 제거, 2 이상이면 1 감소.
     *
     * @return 소비 성공 여부 (뽑기권이 아니면 false)
     */
    public boolean consumeOne(Player player, org.bukkit.inventory.EquipmentSlot slot) {
        ItemStack hand = (slot == org.bukkit.inventory.EquipmentSlot.OFF_HAND)
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        if (!isTicket(hand)) return false;

        if (hand.getAmount() <= 1) {
            if (slot == org.bukkit.inventory.EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
        return true;
    }

    private record TicketSpec(Material material, int modelData, String displayName, List<String> lore) {}
}
