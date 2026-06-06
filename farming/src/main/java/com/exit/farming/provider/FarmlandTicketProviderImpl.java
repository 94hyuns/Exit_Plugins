package com.exit.farming.provider;

import com.exit.core.api.FarmlandTicketProvider;
import com.exit.farming.FarmingPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 경작지 발급 / 식별.
 *
 * <p><b>잔재 메모</b>: 인터페이스 이름이 {@code FarmlandTicketProvider} 지만
 * 실제론 "종이 티켓"이 아닌 "경작지 블록 아이템(흙 + CustomModelData)"을 발급한다.
 * 예전 종이 티켓 시스템에서 마이그레이션 됐고, Core 인터페이스명은 호환성 위해 유지.
 *
 * <h3>발급 아이템</h3>
 * <ul>
 *   <li>Material: {@code DIRT} (인벤토리 표시)</li>
 *   <li>CustomModelData: 99 (리소스팩에서 farmland 모양 텍스처 매핑)</li>
 *   <li>PDC: {@code farmland_ticket=1} 로 우리 발급물임을 식별</li>
 * </ul>
 *
 * <h3>배치 흐름</h3>
 * 플레이어가 들고 우클릭 → 일반 흙처럼 배치됨 → BlockPlaceEvent 에서
 * {@link com.exit.farming.listener.FarmlandProtectionListener} 가 가로채:
 * <ol>
 *   <li>배치된 DIRT 를 FARMLAND 로 전환</li>
 *   <li>클레임 등록 (소유자 = 배치자)</li>
 *   <li>age=0 (dry) — 물 인접 시 바닐라가 자동 wet 처리</li>
 * </ol>
 */
public class FarmlandTicketProviderImpl implements FarmlandTicketProvider {

    public static final NamespacedKey KEY_TICKET;
    /** 리소스팩 CMD 인덱스. items/dirt.json 의 range_dispatch threshold 와 일치해야 함. */
    public static final int CUSTOM_MODEL_DATA = 99;

    static {
        FarmingPlugin plugin = FarmingPlugin.getInstance();
        KEY_TICKET = new NamespacedKey(plugin, "farmland_ticket");
    }

    @Override
    public ItemStack createTicket(int amount) {
        ItemStack item = new ItemStack(Material.DIRT, Math.max(1, Math.min(amount, 64)));
        item.editMeta(meta -> {
            // CustomModelData (리소스팩용)
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) CUSTOM_MODEL_DATA));
            meta.setCustomModelDataComponent(cmd);

            meta.displayName(Component.text("경작지 블록")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("배치하여 경작지를 만듭니다", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("마을 서버에서만 배치 가능", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("배치하면 자동으로 소유권이 등록됩니다", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.getPersistentDataContainer().set(KEY_TICKET, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /**
     * 우리 발급 경작지 블록인지 식별.
     * Material.DIRT 이면서 PDC 태그가 있어야만 true.
     */
    @Override
    public boolean isTicket(ItemStack item) {
        if (item == null || item.getType() != Material.DIRT) return false;
        if (!item.hasItemMeta()) return false;
        Byte v = item.getItemMeta().getPersistentDataContainer()
                .get(KEY_TICKET, PersistentDataType.BYTE);
        return v != null && v == 1;
    }
}
