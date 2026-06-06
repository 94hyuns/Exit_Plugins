package com.exit.job.gui;

import com.exit.job.manager.JobConfigManager;
import com.exit.job.manager.JobManager;
import com.exit.job.model.JobData;
import com.exit.job.model.JobDefinition;
import com.exit.job.model.JobType;
import com.exit.job.model.PerkInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 직업별 능력 상세 GUI. 순수 마크 GUI (리소스팩 미사용).
 *
 * 6행 인벤토리.
 *  - 슬롯 4: 직업 카드 (Lv + EXP 진행도)
 *  - 슬롯 19~25 (3행): 능력 가로 배치
 *  - 슬롯 49: 닫기, 슬롯 45: 뒤로 가기
 *
 * 능력 상태:
 *  - 해금됨 (현재 레벨 ≥ perk level): 인챈트 광택 + 초록 lore
 *  - 잠김 (레벨 미달): 회색 + 잠김 lore
 */
public class JobDetailGUI {

    public static final NamespacedKey ACTION_KEY = NamespacedKey.fromString("job:action");

    private final JobManager jobManager;
    private final JobConfigManager configManager;
    /** viewer UUID → (target UUID, JobType). */
    private final Map<UUID, Context> contexts = new HashMap<>();

    public record Context(UUID target, JobType type) {}

    public JobDetailGUI(JobManager jobManager, JobConfigManager configManager) {
        this.jobManager = jobManager;
        this.configManager = configManager;
    }

    public void open(Player viewer, UUID target, JobType type) {
        JobDefinition def = configManager.getDefinition(type);
        if (def == null) {
            viewer.sendMessage(Component.text("직업 정의를 찾을 수 없습니다.", NamedTextColor.RED));
            return;
        }

        Component title = Component.text("§e" + def.displayName() + " 능력")
                .decoration(TextDecoration.ITALIC, false);

        Inventory inv = Bukkit.createInventory(null, 54, title);

        inv.setItem(4, makeJobCard(target, type, def));

        List<PerkInfo> perks = def.perks();
        int currentLevel = jobManager.getLevel(target, type);
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < perks.size() && i < slots.length; i++) {
            inv.setItem(slots[i], makePerkIcon(perks.get(i), currentLevel));
        }
        int[] slots2 = {28, 29, 30, 31, 32, 33, 34};
        for (int i = 7; i < perks.size() && i - 7 < slots2.length; i++) {
            inv.setItem(slots2[i - 7], makePerkIcon(perks.get(i), currentLevel));
        }

        inv.setItem(45, makeButton(Material.ARROW, "§e◀ 뒤로", "BACK"));
        inv.setItem(49, makeButton(Material.BARRIER, "§c✕ 닫기", "CLOSE"));

        viewer.openInventory(inv);
        contexts.put(viewer.getUniqueId(), new Context(target, type));
    }

    private ItemStack makeJobCard(UUID target, JobType type, JobDefinition def) {
        JobData data = jobManager.get(target, type);
        long expForNext = configManager.expForNextLevel(data.level());
        boolean isMax = data.level() >= configManager.maxLevel();

        ItemStack stack = new ItemStack(def.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("§e" + def.displayName())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(def.description()).color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("§7레벨: §bLv. " + data.level() + (isMax ? " §6(MAX)" : ""))
                .decoration(TextDecoration.ITALIC, false));
        if (!isMax) {
            lore.add(Component.text("§7경험치: §e" + data.exp() + " §8/ §7" + expForNext)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(JobOverviewGUI.progressBar(data.exp(), expForNext))
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack makePerkIcon(PerkInfo perk, int currentLevel) {
        boolean unlocked = currentLevel >= perk.level();
        Material mat = unlocked ? Material.ENCHANTED_BOOK : Material.BOOK;

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();

        String prefix = unlocked ? "§a✔ " : "§7§o🔒 ";
        meta.displayName(Component.text(prefix + perk.name())
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7해금 레벨: §eLv. " + perk.level())
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        // 긴 description 을 단어 단위로 줄바꿈해서 lore 여러 줄로
        for (String line : wrapText(perk.description(), 28)) {
            lore.add(Component.text("§7" + line).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        if (unlocked) {
            lore.add(Component.text("§a✔ 해금됨")
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            int delta = perk.level() - currentLevel;
            lore.add(Component.text("§c🔒 잠김")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("§7(앞으로 " + delta + "레벨)")
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        if (unlocked) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public Context getContext(UUID viewer) {
        return contexts.get(viewer);
    }

    public void close(UUID viewer) {
        contexts.remove(viewer);
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

    /**
     * 텍스트를 maxLen 길이 기준으로 단어 경계에서 줄바꿈.
     * 단어 하나가 maxLen 초과해도 그냥 한 줄로 (안 자름).
     * 한글은 한 글자 = 약 2 길이로 가중 (단순 char 단위).
     */
    private static java.util.List<String> wrapText(String text, int maxLen) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) { out.add(""); return out; }
        StringBuilder cur = new StringBuilder();
        int curWidth = 0;
        for (String word : text.split(" ")) {
            int wordWidth = displayWidth(word);
            if (curWidth > 0 && curWidth + 1 + wordWidth > maxLen) {
                out.add(cur.toString());
                cur.setLength(0);
                curWidth = 0;
            }
            if (curWidth > 0) { cur.append(' '); curWidth++; }
            cur.append(word);
            curWidth += wordWidth;
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** 한글(CJK) 은 2, 그 외 1 로 폭 계산. */
    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            w += (c >= 0x1100 && c <= 0xFFEF && !(c >= 0x2000 && c <= 0x206F)) ? 2 : 1;
        }
        return w;
    }
}
