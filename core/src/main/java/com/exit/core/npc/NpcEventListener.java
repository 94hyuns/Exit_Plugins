package com.exit.core.npc;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.exit.core.api.NpcClickHandler;
import com.exit.core.api.NpcInfo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public class NpcEventListener implements Listener {

    private final Plugin plugin;
    private final NpcServiceImpl service;

    public NpcEventListener(Plugin plugin, NpcServiceImpl service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseUnknown(PlayerUseUnknownEntityEvent event) {
        Optional<NpcInfo> opt = service.getByEntityId(event.getEntityId());
        if (opt.isEmpty()) return;
        NpcInfo info = opt.get();
        NpcClickHandler handler = service.getHandler(info.owner());
        if (handler == null) return;
        handler.onClick(event.getPlayer(), info.id(), event.isAttack());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> service.showAllTo(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> service.showAllTo(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> service.showAllTo(event.getPlayer()), 10L);
    }
}
