package com.exit.cosmetics.cosmetic;

import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticType;
import com.exit.cosmetics.registry.CosmeticRegistry;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 날개/망토 핸들러. ItemDisplay 엔티티를 플레이어의 passenger로 부착한다.
 * passenger는 플레이어 위치/회전을 자동으로 따라간다.
 *
 * <p>좌표 체계: passenger의 Transformation translation은 플레이어 눈높이 기준 로컬 좌표.
 * config의 offset [x, y, z]는 [좌우, 상하, 앞뒤]로 사용되며, z 음수가 플레이어 등쪽.
 *
 * <p>주의: addPassenger는 패킷이 아니라 실제 엔티티 관계를 만든다. 월드 이동/리스폰/접속 시
 * 플레이어 엔티티가 재생성되면 passenger 관계가 끊어지므로 재부착 필요.
 */
public class WingHandler {

    private final CosmeticRegistry registry;

    /** 플레이어별 현재 장착 날개 ID + 스폰된 ItemDisplay 엔티티. */
    private final Map<UUID, String> equippedId = new HashMap<>();
    private final Map<UUID, ItemDisplay> displays = new HashMap<>();

    public WingHandler(CosmeticRegistry registry) {
        this.registry = registry;
    }

    public void apply(Player wearer, CosmeticDefinition definition) {
        if (definition == null || definition.getType() != CosmeticType.WING) return;

        clear(wearer); // 기존 display 제거
        equippedId.put(wearer.getUniqueId(), definition.getId());
        spawnAndAttach(wearer, definition);
    }

    public void clear(Player wearer) {
        UUID uuid = wearer.getUniqueId();
        ItemDisplay old = displays.remove(uuid);
        if (old != null && !old.isDead()) old.remove();
        equippedId.remove(uuid);
    }

    /**
     * Display 엔티티 재스폰 + 재부착. 월드 이동/리스폰 시 기존 엔티티는 청크 언로드로
     * 정리되거나 passenger 관계가 끊기므로 다시 만든다.
     */
    public void resend(Player wearer) {
        String id = equippedId.get(wearer.getUniqueId());
        if (id == null) return;
        CosmeticDefinition def = registry.get(id);
        if (def == null) return;

        // 기존 엔티티가 살아있으면 제거하고 새로 생성 (월드 이동 시 구 월드 엔티티는 무효)
        ItemDisplay old = displays.remove(wearer.getUniqueId());
        if (old != null && !old.isDead()) old.remove();

        spawnAndAttach(wearer, def);
    }

    public boolean isEquipped(UUID uuid) {
        return equippedId.containsKey(uuid);
    }

    public String getEquippedId(UUID uuid) {
        return equippedId.get(uuid);
    }

    // ─── 내부 ───

    private void spawnAndAttach(Player wearer, CosmeticDefinition def) {
        ItemStack item = new ItemStack(def.getBaseItem());
        ItemMeta meta = item.getItemMeta();
        if (meta != null && def.getModelData() > 0) {
            meta.setCustomModelData(def.getModelData());
            item.setItemMeta(meta);
        }

        ItemDisplay display = wearer.getWorld().spawn(wearer.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setPersistent(false);  // 서버 저장 대상 아님 — 청크 저장/복구 금지
            d.setInvulnerable(true);
            d.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);

            // 로컬 오프셋 적용
            float ox = (float) def.getOffsetX();
            float oy = (float) def.getOffsetY();
            float oz = (float) def.getOffsetZ();
            float scale = (float) def.getScale();

            Transformation t = new Transformation(
                    new Vector3f(ox, oy, oz),
                    new AxisAngle4f(0f, 0f, 0f, 0f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 0f, 0f)
            );
            d.setTransformation(t);
        });

        wearer.addPassenger(display);
        displays.put(wearer.getUniqueId(), display);
    }

    /** 플러그인 비활성화 시 모든 display 정리. */
    public void shutdownAll() {
        for (ItemDisplay d : displays.values()) {
            if (d != null && !d.isDead()) d.remove();
        }
        displays.clear();
        equippedId.clear();
    }
}
