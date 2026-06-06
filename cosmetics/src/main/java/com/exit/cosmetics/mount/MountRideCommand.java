package com.exit.cosmetics.mount;

import com.exit.cosmetics.gui.MountGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /ride (aliases: 탈것, xkfrjt) — 탈것 GUI 오픈.
 */
public class MountRideCommand implements CommandExecutor {

    private final MountManager manager;
    private final MountGui gui;

    public MountRideCommand(MountManager manager, MountGui gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용 가능합니다.").color(NamedTextColor.RED));
            return true;
        }
        MountDefinition active = manager.getActiveDefinition(player.getUniqueId());
        gui.open(player, active != null ? active.getId() : null);
        return true;
    }
}
