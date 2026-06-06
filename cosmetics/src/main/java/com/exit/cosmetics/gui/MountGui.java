package com.exit.cosmetics.gui;

import com.exit.core.api.CosmeticProvider;
import com.exit.cosmetics.mount.MountDefinition;
import com.exit.cosmetics.mount.MountRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 탈것 GUI. 27칸. 보유 탈것 아이콘 나열, 클릭 시 소환/해제 토글.
 *
 * <p>레이아웃:
 * - 0~17: 보유 탈것 (최대 18개)
 * - 18: 뒤로/닫기
 * - 22: 현재 소환 중인 탈것 정보
 */
public class MountGui {

    public static final NamespacedKey ACTION_KEY = new NamespacedKey("cosmetics", "mount_action");
    public static final NamespacedKey PAYLOAD_KEY = new NamespacedKey("cosmetics", "mount_payload");
    private static final Component TITLE = Component.text("내 탈것", NamedTextColor.AQUA);

    private final MountRegistry mountRegistry;
    private final CosmeticProvider provider;
    private final Set<UUID> viewers = new HashSet<>();

    public MountGui(MountRegistry mountRegistry, CosmeticProvider provider) {
        this.mountRegistry = mountRegistry;
        this.provider = provider;
    }

    public void open(Player player, String activeMountId) {
        UUID uuid = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        Set<String> owned = provider.listOwned(uuid);

        int slot = 0;
        boolean any = false;
        for (MountDefinition def : mountRegistry.getAll()) {
            if (slot >= 18) break;
            if (!owned.contains(def.getOwnershipKey())) continue;
            boolean isActive = def.getId().equals(activeMountId);
            inv.setItem(slot++, mountIcon(def, isActive));
            any = true;
        }

        if (!any) {
            inv.setItem(13, info(Material.PAPER, "§7보유한 탈것 없음",
                    List.of(
                            Component.text("§8치장 상인에게서 탈것 뽑기권을").decoration(TextDecoration.ITALIC, false),
                            Component.text("§8구매하여 탈것을 획득하세요.").decoration(TextDecoration.ITALIC, false)
                    )));
        }

        if (activeMountId != null) {
            MountDefinition def = mountRegistry.get(activeMountId);
            String name = def != null ? def.getDisplayName() : activeMountId;
            inv.setItem(22, actionButton(Material.BARRIER, "§c현재 탈것 해제",
                    "DESPAWN", null,
                    List.of(Component.text("§7현재 소환: §f" + name).decoration(TextDecoration.ITALIC, false))));
        }

        inv.setItem(18, actionButton(Material.ARROW, "§e◀ 닫기", "CLOSE", null, List.of()));

        player.openInventory(inv);
        viewers.add(uuid);
    }

    public boolean isViewing(UUID uuid) { return viewers.contains(uuid); }
    public void close(UUID uuid) { viewers.remove(uuid); }

    public Optional<String> getAction(ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return Optional.empty();
        var pdc = clicked.getItemMeta().getPersistentDataContainer();
        if (pdc.has(ACTION_KEY, PersistentDataType.STRING)) {
            return Optional.ofNullable(pdc.get(ACTION_KEY, PersistentDataType.STRING));
        }
        return Optional.empty();
    }

    public Optional<String> getPayload(ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return Optional.empty();
        var pdc = clicked.getItemMeta().getPersistentDataContainer();
        if (pdc.has(PAYLOAD_KEY, PersistentDataType.STRING)) {
            return Optional.ofNullable(pdc.get(PAYLOAD_KEY, PersistentDataType.STRING));
        }
        return Optional.empty();
    }

    // ─── 빌더 ───

    private ItemStack mountIcon(MountDefinition def, boolean active) {
        ItemStack stack = new ItemStack(def.getIconMaterial());
        ItemMeta meta = stack.getItemMeta();
        if (def.getIconModelData() > 0) meta.setCustomModelData(def.getIconModelData());

        String prefix = active ? "§a[소환중] " : "";
        meta.displayName(Component.text(prefix + def.getDisplayName()).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (!def.getDescription().isEmpty()) {
            lore.add(Component.text(def.getDescription()).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }
        lore.add(Component.text("§7등급: " + def.getRarity().getDisplayName()).decoration(TextDecoration.ITALIC, false));
        String typeKor = def.getMountType().name().equals("FLYING") ? "날탈" : "지상 탈것";
        lore.add(Component.text("§7분류: §f" + typeKor).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7이동속도: §f" + def.getMovementSpeed()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7체력: §f" + def.getMaxHealth()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text(active ? "§8▶ 클릭하여 해제" : "§8▶ 클릭하여 소환")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                active ? "DESPAWN" : "SUMMON");
        meta.getPersistentDataContainer().set(PAYLOAD_KEY, PersistentDataType.STRING, def.getId());
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack actionButton(Material mat, String name, String action, String payload, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        if (payload != null) {
            meta.getPersistentDataContainer().set(PAYLOAD_KEY, PersistentDataType.STRING, payload);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack info(Material mat, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }
}
