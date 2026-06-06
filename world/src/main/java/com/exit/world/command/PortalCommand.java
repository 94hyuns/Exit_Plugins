package com.exit.world.command;

import com.exit.world.gui.PortalGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /포탈 및 /portal 명령어 처리.
 * 플레이어가 실행하면 포탈 GUI를 열어준다.
 */
public class PortalCommand implements CommandExecutor {

    private final PortalGUI portalGUI;

    public PortalCommand(PortalGUI portalGUI) {
        this.portalGUI = portalGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }
        portalGUI.open(player);
        return true;
    }
}
