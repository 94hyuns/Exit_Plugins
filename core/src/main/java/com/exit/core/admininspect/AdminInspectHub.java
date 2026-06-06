package com.exit.core.admininspect;

import com.exit.core.api.CropStorageReadProvider;
import com.exit.core.api.EconomyProvider;
import com.exit.core.api.FishStorageReadProvider;
import com.exit.core.api.GambleStatsProvider;
import com.exit.core.api.JobProvider;
import com.exit.core.api.MineralStorageReadProvider;
import com.exit.core.registry.ServiceRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdminInspectHub implements InventoryHolder {

    private final UUID targetUuid;
    private final String targetName;
    private Inventory inventory;

    public AdminInspectHub(UUID targetUuid, String targetName) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player viewer) {
        Component title = plain("검사: " + targetName);
        inventory = Bukkit.createInventory(this, 9, title);

        ItemStack filler = filler();
        for (int i = 0; i < 9; i++) inventory.setItem(i, filler);

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        boolean online = target.isOnline();

        inventory.setItem(0, inventoryButton(target, online));
        inventory.setItem(2, storageButton(Material.COD, "어부 보관함",
                ServiceRegistry.isRegistered(FishStorageReadProvider.class)));
        inventory.setItem(3, storageButton(Material.DIAMOND_PICKAXE, "광부 보관함",
                ServiceRegistry.isRegistered(MineralStorageReadProvider.class)));
        inventory.setItem(4, storageButton(Material.WHEAT, "농부 보관함",
                ServiceRegistry.isRegistered(CropStorageReadProvider.class)));
        inventory.setItem(6, jobInfoButton(targetUuid));
        inventory.setItem(7, gambleStatsButton(targetUuid));
        inventory.setItem(8, balanceButton(targetUuid));

        viewer.openInventory(inventory);
    }

    private ItemStack inventoryButton(OfflinePlayer target, boolean online) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.displayName(plain("인벤토리 보기").color(NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            if (online) {
                lore.add(plain("클릭하여 인벤토리/장비를 확인").color(NamedTextColor.GRAY));
            } else {
                lore.add(plain("오프라인 — 표시 불가").color(NamedTextColor.RED));
            }
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack storageButton(Material icon, String name, boolean available) {
        ItemStack it = new ItemStack(icon);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(plain(name).color(NamedTextColor.YELLOW));
            List<Component> lore = new ArrayList<>();
            if (available) {
                lore.add(plain("클릭하여 보관함 내용 확인").color(NamedTextColor.GRAY));
            } else {
                lore.add(plain("해당 플러그인 미연결").color(NamedTextColor.RED));
            }
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack jobInfoButton(UUID uuid) {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(plain("직업 정보").color(NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
            if (jobs == null) {
                lore.add(plain("JobProvider 미등록").color(NamedTextColor.RED));
            } else {
                addJobLine(lore, jobs, uuid, "miner", "광부");
                addJobLine(lore, jobs, uuid, "fisher", "어부");
                addJobLine(lore, jobs, uuid, "farmer", "농부");
            }
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void addJobLine(List<Component> lore, JobProvider jobs, UUID uuid, String id, String label) {
        int lv = jobs.getLevel(uuid, id);
        long exp = jobs.getExp(uuid, id);
        lore.add(plain(label + " Lv." + lv + " (경험치 " + exp + ")")
                .color(NamedTextColor.WHITE));
    }

    private ItemStack gambleStatsButton(UUID uuid) {
        ItemStack it = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(plain("도박 통계").color(NamedTextColor.LIGHT_PURPLE));
            List<Component> lore = new ArrayList<>();
            GambleStatsProvider stats = ServiceRegistry.get(GambleStatsProvider.class).orElse(null);
            if (stats == null) {
                lore.add(plain("ExitGamble 미연결").color(NamedTextColor.RED));
            } else {
                GambleStatsProvider.Stats s = stats.getAll(uuid);
                lore.add(plain("─ 슬롯머신 ─").color(NamedTextColor.GOLD));
                lore.add(plain(String.format("  베팅 누적:  %,d 원", s.slotBet())).color(NamedTextColor.GRAY));
                lore.add(plain(String.format("  지급 누적:  %,d 원", s.slotPayout())).color(NamedTextColor.GRAY));
                lore.add(plain(String.format("  순손익:     %+,d 원", s.slotNet()))
                        .color(s.slotNet() >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
                lore.add(plain(" ").color(NamedTextColor.GRAY));
                lore.add(plain("─ 복권 ─").color(NamedTextColor.GOLD));
                lore.add(plain(String.format("  베팅 누적:  %,d 원", s.lotteryBet())).color(NamedTextColor.GRAY));
                lore.add(plain(String.format("  당첨 누적:  %,d 원", s.lotteryPayout())).color(NamedTextColor.GRAY));
                lore.add(plain(String.format("  순손익:     %+,d 원", s.lotteryNet()))
                        .color(s.lotteryNet() >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
                lore.add(plain(" ").color(NamedTextColor.GRAY));
                lore.add(plain(String.format("종합 순손익: %+,d 원", s.totalNet()))
                        .color(s.totalNet() >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack balanceButton(UUID uuid) {
        ItemStack it = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(plain("화폐 잔액").color(NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
            if (eco == null) {
                lore.add(plain("EconomyProvider 미등록").color(NamedTextColor.RED));
            } else {
                long bal = eco.getBalance(uuid);
                if (bal < 0) {
                    lore.add(plain("계좌 없음").color(NamedTextColor.RED));
                } else {
                    lore.add(plain(String.format("%,d 원", bal)).color(NamedTextColor.YELLOW));
                }
            }
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(plain(" "));
            it.setItemMeta(meta);
        }
        return it;
    }

    static Component plain(String s) {
        return Component.text(s)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false);
    }

    public static final int SLOT_INVENTORY = 0;
    public static final int SLOT_FISH = 2;
    public static final int SLOT_MINERAL = 3;
    public static final int SLOT_CROP = 4;
}
