package com.exit.job.gui;

import com.exit.job.manager.JobConfigManager;
import com.exit.job.manager.JobManager;
import com.exit.job.model.JobData;
import com.exit.job.model.JobDefinition;
import com.exit.job.model.JobType;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 3직업 요약 GUI. 순수 마크 GUI (리소스팩 미사용).
 *
 * 6행 인벤토리. 슬롯 11/13/15에 광부/어부/농부 카드 가로 배치.
 * 카드 클릭 시 JobDetailGUI 로 이동.
 */
public class JobOverviewGUI {

    public static final NamespacedKey ACTION_KEY = NamespacedKey.fromString("job:action");

    private final JobManager jobManager;
    private final JobConfigManager configManager;
    /** 현재 GUI 열고 있는 플레이어 → 조회 대상 매핑. */
    private final Map<UUID, UUID> targetMap = new HashMap<>();

    public JobOverviewGUI(JobManager jobManager, JobConfigManager configManager) {
        this.jobManager = jobManager;
        this.configManager = configManager;
    }

    public void open(Player viewer, UUID target) {
        String suffix = target.equals(viewer.getUniqueId()) ? "직업 정보" : "직업 정보 (조회)";
        Component title = Component.text("§e" + suffix).decoration(TextDecoration.ITALIC, false);

        Inventory inv = Bukkit.createInventory(null, 54, title);

        renderCard(inv, 11, JobType.MINER, target);
        renderCard(inv, 13, JobType.FISHER, target);
        renderCard(inv, 15, JobType.FARMER, target);

        inv.setItem(49, makeButton(Material.BARRIER, "§c✕ 닫기", "CLOSE"));

        viewer.openInventory(inv);
        targetMap.put(viewer.getUniqueId(), target);
    }

    private void renderCard(Inventory inv, int slot, JobType type, UUID target) {
        JobDefinition def = configManager.getDefinition(type);
        JobData data = jobManager.get(target, type);

        long expForNext = configManager.expForNextLevel(data.level());
        boolean isMax = data.level() >= configManager.maxLevel();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(def != null ? def.description() : "")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("§7현재 레벨: §bLv. " + data.level()
                + (isMax ? " §6(MAX)" : ""))
                .decoration(TextDecoration.ITALIC, false));
        if (!isMax) {
            lore.add(Component.text("§7경험치: §e" + data.exp() + " §8/ §7" + expForNext)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(progressBar(data.exp(), expForNext))
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("§8클릭 → 능력 상세 보기")
                .decoration(TextDecoration.ITALIC, false));

        Material mat = def != null ? def.icon() : Material.PAPER;
        String name = def != null ? def.displayName() : type.id();
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("§e" + name)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                "OPEN_DETAIL|" + type.id());
        stack.setItemMeta(meta);
        inv.setItem(slot, stack);
    }

    /** §색이 입혀진 진행도 바. ▰▰▰▱▱ 형태. */
    public static String progressBar(long current, long max) {
        if (max <= 0) return "§a▰▰▰▰▰▰▰▰▰▰";
        int filled = (int) Math.round(10.0 * current / max);
        if (filled < 0) filled = 0;
        if (filled > 10) filled = 10;
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < filled; i++) sb.append("▰");
        sb.append("§7");
        for (int i = filled; i < 10; i++) sb.append("▱");
        return sb.toString();
    }

    public UUID getTarget(UUID viewer) {
        return targetMap.getOrDefault(viewer, viewer);
    }

    /** 이 viewer 가 현재 overview GUI 를 열고 있는지. 클릭 차단 게이트용. */
    public boolean isViewing(UUID viewer) {
        return targetMap.containsKey(viewer);
    }

    public void close(UUID viewer) {
        targetMap.remove(viewer);
    }

    private static ItemStack makeButton(Material mat, String name, String actionTag) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (actionTag != null) {
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, actionTag);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
