package com.exit.core.admininspect;

import com.exit.core.api.CropStorageReadProvider;
import com.exit.core.api.FishStorageReadProvider;
import com.exit.core.api.MineralStorageReadProvider;
import com.exit.core.registry.ServiceRegistry;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.Function;

public class AdminInspectListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof AdminInspectHub)
                && !(holder instanceof ReadOnlyInventoryView)
                && !(holder instanceof ReadOnlyStorageView)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (event.getClickedInventory() != top) return;

        if (holder instanceof AdminInspectHub hub) {
            handleHubClick(hub, viewer, event.getRawSlot());
        } else if (holder instanceof ReadOnlyStorageView view) {
            handleStorageNav(view, viewer, event.getRawSlot());
        }
    }

    private void handleHubClick(AdminInspectHub hub, Player viewer, int slot) {
        UUID uuid = hub.getTargetUuid();
        String name = hub.getTargetName();

        if (slot == AdminInspectHub.SLOT_INVENTORY) {
            Player target = Bukkit.getPlayerExact(name);
            if (target == null || !target.isOnline()) {
                viewer.sendMessage(AdminInspectHub.plain("대상이 오프라인입니다.")
                        .color(NamedTextColor.RED));
                return;
            }
            Player viewerRef = viewer;
            Player targetRef = target;
            schedule(viewer, () -> new ReadOnlyInventoryView().open(viewerRef, targetRef));
            return;
        }

        if (slot == AdminInspectHub.SLOT_FISH) {
            openStorage(viewer, name,
                    ServiceRegistry.get(FishStorageReadProvider.class).orElse(null),
                    p -> p.load(uuid), "어부 보관함");
        } else if (slot == AdminInspectHub.SLOT_MINERAL) {
            openStorage(viewer, name,
                    ServiceRegistry.get(MineralStorageReadProvider.class).orElse(null),
                    p -> p.load(uuid), "광부 보관함");
        } else if (slot == AdminInspectHub.SLOT_CROP) {
            openStorage(viewer, name,
                    ServiceRegistry.get(CropStorageReadProvider.class).orElse(null),
                    p -> p.load(uuid), "농부 보관함");
        }
    }

    private <P> void openStorage(Player viewer, String targetName, P provider,
                                 Function<P, ItemStack[]> loader, String label) {
        if (provider == null) {
            viewer.sendMessage(AdminInspectHub.plain(label + " 플러그인이 연결되지 않았습니다.")
                    .color(NamedTextColor.RED));
            return;
        }
        ItemStack[] data;
        try {
            data = loader.apply(provider);
        } catch (Throwable t) {
            viewer.sendMessage(AdminInspectHub.plain(label + " 로드 실패: " + t.getMessage())
                    .color(NamedTextColor.RED));
            return;
        }
        Player viewerRef = viewer;
        schedule(viewer, () -> new ReadOnlyStorageView(targetName, label, data).open(viewerRef, 0));
    }

    private void handleStorageNav(ReadOnlyStorageView view, Player viewer, int slot) {
        int next;
        if (slot == ReadOnlyStorageView.SLOT_PREV && view.getPage() > 0) {
            next = view.getPage() - 1;
        } else if (slot == ReadOnlyStorageView.SLOT_NEXT && view.getPage() < view.getTotalPages() - 1) {
            next = view.getPage() + 1;
        } else {
            return;
        }
        Player viewerRef = viewer;
        ReadOnlyStorageView newView = new ReadOnlyStorageView(
                view.getTargetName(), view.getLabel(), view.getData());
        schedule(viewer, () -> newView.open(viewerRef, next));
    }

    private void schedule(Player viewer, Runnable r) {
        viewer.closeInventory();
        Plugin core = Bukkit.getPluginManager().getPlugin("Core");
        if (core == null) return;
        Bukkit.getScheduler().runTask(core, r);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder instanceof AdminInspectHub
                || holder instanceof ReadOnlyInventoryView
                || holder instanceof ReadOnlyStorageView) {
            event.setCancelled(true);
        }
    }
}
