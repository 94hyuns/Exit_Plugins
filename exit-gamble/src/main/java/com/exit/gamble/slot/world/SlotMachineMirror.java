package com.exit.gamble.slot.world;

import com.exit.gamble.slot.symbol.SlotSymbol;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SlotMachineMirror {

    public enum StatusKind { IDLE, OCCUPIED, SPINNING, WIN, JACKPOT, LOSE }

    public void updateReel(SlotMachine machine, int reelIdx, SlotSymbol symbol) {
        if (machine == null) return;
        UUID id = machine.reelId(reelIdx);
        Entity e = Bukkit.getEntity(id);
        if (e instanceof ItemDisplay disp) {
            disp.setItemStack(new ItemStack(symbol.material()));
        }
    }

    public void resetReels(SlotMachine machine, SlotSymbol idleSymbol) {
        if (machine == null) return;
        for (int i = 0; i < 3; i++) updateReel(machine, i, idleSymbol);
    }

    public void updateStatus(SlotMachine machine, StatusKind kind, String playerName, long amount) {
        if (machine == null) return;
        Entity e = Bukkit.getEntity(machine.statusId());
        if (!(e instanceof TextDisplay td)) return;

        Component comp = switch (kind) {
            case IDLE -> Component.text("비어있음", NamedTextColor.GRAY);
            case OCCUPIED -> Component.text(playerName + " 님 사용 중 (베팅 " + amount + "원)", NamedTextColor.AQUA);
            case SPINNING -> Component.text("회전 중...", NamedTextColor.YELLOW);
            case WIN -> Component.text(playerName + " 님 +" + amount + "원 당첨", NamedTextColor.GREEN);
            case JACKPOT -> Component.text("★ " + playerName + " 잭팟 +" + amount + "원 ★", NamedTextColor.GOLD);
            case LOSE -> Component.text("꽝", NamedTextColor.DARK_GRAY);
        };
        td.text(comp.decoration(TextDecoration.ITALIC, false));
    }
}
