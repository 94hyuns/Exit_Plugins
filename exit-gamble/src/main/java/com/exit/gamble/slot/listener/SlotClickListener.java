package com.exit.gamble.slot.listener;

import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.gamble.slot.animation.SpinAnimation;
import com.exit.gamble.slot.config.SlotConfig;
import com.exit.gamble.slot.engine.ReelEngine;
import com.exit.gamble.slot.engine.SpinResult;
import com.exit.gamble.slot.gui.SlotActionKeys;
import com.exit.gamble.slot.gui.SlotGui;
import com.exit.gamble.slot.gui.SlotHolder;
import com.exit.gamble.slot.world.SlotMachine;
import com.exit.gamble.slot.world.SlotMachineManager;
import com.exit.gamble.slot.world.SlotMachineMirror;
import com.exit.gamble.slot.world.SlotMachineMirror.StatusKind;
import com.exit.gamble.stats.GambleStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class SlotClickListener implements Listener {

    private final Plugin plugin;
    private final SlotConfig config;
    private final ReelEngine engine;
    private final SlotGui gui;
    private final SlotActionKeys keys;
    private final SlotMachineMirror mirror;
    private final SlotMachineManager machineManager;
    private final GambleStatsManager stats;

    public SlotClickListener(Plugin plugin, SlotConfig config, ReelEngine engine,
                             SlotGui gui, SlotActionKeys keys,
                             SlotMachineMirror mirror, SlotMachineManager machineManager,
                             GambleStatsManager stats) {
        this.plugin = plugin;
        this.config = config;
        this.engine = engine;
        this.gui = gui;
        this.keys = keys;
        this.mirror = mirror;
        this.machineManager = machineManager;
        this.stats = stats;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SlotHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        String action = meta.getPersistentDataContainer().get(keys.actionKey(), PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case SlotActionKeys.ACTION_BET_0,
                 SlotActionKeys.ACTION_BET_1,
                 SlotActionKeys.ACTION_BET_2 -> handleBet(player, holder, action);
            case SlotActionKeys.ACTION_SPIN -> handleSpin(player, holder);
            case SlotActionKeys.ACTION_CLOSE -> player.closeInventory();
            default -> { /* unknown */ }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SlotHolder holder)) return;
        SlotMachine machine = holder.machine();
        if (machine == null) return;
        if (holder.isSpinning()) return;
        machineManager.release(machine);
        mirror.updateStatus(machine, StatusKind.IDLE, "", 0);
        mirror.resetReels(machine, config.symbols().get(0));
    }

    private void handleBet(Player player, SlotHolder holder, String action) {
        if (holder.isSpinning()) return;
        long bet = gui.betForAction(action);
        holder.setCurrentBet(bet);
        gui.renderBetButtons(holder.getInventory(), holder);
        gui.renderStatus(holder.getInventory(), holder, player, null);
        SlotMachine machine = holder.machine();
        if (machine != null) {
            machine.setCurrentBet(bet);
            mirror.updateStatus(machine, StatusKind.OCCUPIED, player.getName(), bet);
        }
    }

    private void handleSpin(Player player, SlotHolder holder) {
        if (holder.isSpinning()) return;

        long bet = holder.currentBet();
        if (!holder.isTestMode()) {
            EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
            if (eco == null) {
                player.sendMessage(Component.text("경제 시스템 미연동", NamedTextColor.RED));
                return;
            }
            if (eco.getBalance(player.getUniqueId()) < bet) {
                gui.renderStatus(holder.getInventory(), holder, player, "잔액 부족!");
                player.sendMessage(Component.text("잔액이 부족합니다. (필요: " + bet + "원)", NamedTextColor.RED));
                return;
            }
            if (!eco.subtractBalance(player.getUniqueId(), bet)) {
                player.sendMessage(Component.text("잔액 차감 실패", NamedTextColor.RED));
                return;
            }
            stats.addSlotBet(player.getUniqueId(), bet);
        }

        SpinResult result = engine.spin(bet);
        holder.setSpinning(true);
        holder.setFinalReels(result.reels());
        gui.renderSpinButton(holder.getInventory(), true);
        gui.renderStatus(holder.getInventory(), holder, player, "회전 중...");

        SlotMachine machine = holder.machine();
        if (machine != null) {
            machine.setSpinning(true);
            mirror.updateStatus(machine, StatusKind.SPINNING, player.getName(), bet);
        }

        new SpinAnimation(plugin, config, engine, gui, mirror, machineManager,
                stats, holder, result, player).start();
    }
}
