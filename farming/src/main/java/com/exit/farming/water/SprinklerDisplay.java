package com.exit.farming.water;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 배치된 스프링클러의 외형(ItemDisplay) + 클릭 핸들러(Interaction) 엔티티 관리.
 *
 * <p><b>구조</b>: 기반 블록은 BARRIER (서바이벌에선 안 보임). 위에 두 엔티티를 얹는다.
 * <ul>
 *   <li>{@link ItemDisplay}: sprinkler 3D 모델 렌더. 모델 elements 가 0..16 픽셀 = 1블록 공간 →
 *       entity 를 블록 코너에 두면 모델이 정확히 그 블록을 차지.</li>
 *   <li>{@link Interaction}: 1×1 hitbox. 플레이어 좌클릭/우클릭 감지용.
 *       BARRIER 는 서바이벌에서 파괴 불가하므로 이 엔티티 좌클릭으로 회수 트리거.</li>
 * </ul>
 *
 * <p>두 엔티티 모두 PDC 태그 ({@link WaterToolKeys#SPRINKLER_DISPLAY}, {@link WaterToolKeys#SPRINKLER_INTERACTION})
 * 가 붙어 검색 가능.
 */
public final class SprinklerDisplay {

    private SprinklerDisplay() {}

    /** display + interaction 둘 다 보장. 없으면 spawn, 있으면 그대로. */
    public static void ensure(Block barrier, WaterTier tier) {
        if (findDisplay(barrier) == null) spawnDisplay(barrier, tier);
        if (findInteraction(barrier) == null) spawnInteraction(barrier, tier);
    }

    public static ItemDisplay findDisplay(Block barrier) {
        Location center = barrier.getLocation().add(0.5, 0.5, 0.5);
        for (Entity e : barrier.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8)) {
            if (!(e instanceof ItemDisplay disp)) continue;
            if (disp.getPersistentDataContainer().has(
                    WaterToolKeys.SPRINKLER_DISPLAY, PersistentDataType.STRING)) {
                return disp;
            }
        }
        return null;
    }

    public static Interaction findInteraction(Block barrier) {
        Location center = barrier.getLocation().add(0.5, 0.5, 0.5);
        for (Entity e : barrier.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8)) {
            if (!(e instanceof Interaction inter)) continue;
            if (inter.getPersistentDataContainer().has(
                    WaterToolKeys.SPRINKLER_INTERACTION, PersistentDataType.STRING)) {
                return inter;
            }
        }
        return null;
    }

    public static ItemDisplay spawnDisplay(Block barrier, WaterTier tier) {
        // 모델 elements 가 이제 0..16 px (= 1블록 풀) 로 정의됨 (나무 슬랩 0..8 + sprinkler 8..16).
        // FIXED context + entity scale 1.0 = 1블록 크기로 렌더. translation 으로 모델 중심을
        // 블록 코너에 맞춤 (모델 좌표 0..16 = 블록 0..1, 엔티티는 블록 중심).
        Location spawnLoc = barrier.getLocation().add(0.5, 0.5, 0.5);
        return barrier.getWorld().spawn(spawnLoc, ItemDisplay.class, disp -> {
            disp.setItemStack(SprinklerItem.create(tier));
            disp.setBillboard(Display.Billboard.FIXED);
            disp.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Quaternionf()
            ));
            disp.setBrightness(new Display.Brightness(15, 15));
            disp.setPersistent(true);
            disp.setInvulnerable(true);
            disp.getPersistentDataContainer().set(
                    WaterToolKeys.SPRINKLER_DISPLAY,
                    PersistentDataType.STRING,
                    tier.id()
            );
        });
    }

    public static Interaction spawnInteraction(Block barrier, WaterTier tier) {
        // Interaction hitbox = entity 위치 중심 (x ± width/2), 위로 height.
        // 블록 1칸 정확히 덮으려면 entity 를 블록 바닥 중심(x+0.5, y, z+0.5)에 두고 width/height=1.
        Location loc = barrier.getLocation().add(0.5, 0.0, 0.5);
        return barrier.getWorld().spawn(loc, Interaction.class, inter -> {
            inter.setInteractionWidth(1.0f);
            inter.setInteractionHeight(1.0f);
            inter.setResponsive(true);
            inter.setPersistent(true);
            inter.getPersistentDataContainer().set(
                    WaterToolKeys.SPRINKLER_INTERACTION,
                    PersistentDataType.STRING,
                    tier.id()
            );
        });
    }

    /** 해당 위치의 우리 sprinkler display + interaction 둘 다 제거. */
    public static void remove(Block barrier) {
        ItemDisplay d = findDisplay(barrier);
        if (d != null) d.remove();
        Interaction i = findInteraction(barrier);
        if (i != null) i.remove();
    }
}
