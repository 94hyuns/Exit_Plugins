package com.example.land.managers;

import com.example.land.LandPlugin;
import com.example.land.data.ChunkPos;
import com.example.land.data.ClaimedChunk;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class InfoModeManager {

    private final LandPlugin plugin;
    private final Set<UUID> activePlayers = new HashSet<>();
    private BukkitTask task;

    public InfoModeManager(LandPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        int interval = plugin.getConfig().getInt("land.info-update-ticks", 10);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void stopTask() {
        if (task != null) task.cancel();
    }

    public boolean toggle(UUID uuid) {
        if (activePlayers.contains(uuid)) {
            activePlayers.remove(uuid);
            return false;
        } else {
            activePlayers.add(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) showActionBar(player);
            return true;
        }
    }

    public boolean isActive(UUID uuid) { return activePlayers.contains(uuid); }
    public void remove(UUID uuid) { activePlayers.remove(uuid); }

    private void tick() {
        for (UUID uuid : new HashSet<>(activePlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            if (!plugin.isAllowedWorld(player.getWorld())) continue;
            showActionBar(player);
            drawChunkBorder(player, player.getLocation().getChunk());
        }
    }

    private void showActionBar(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ChunkPos pos = ChunkPos.of(chunk);
        ClaimedChunk claimed = plugin.getLandManager().getChunk(pos);

        Component msg;
        if (claimed == null) {
            msg = Component.text("청크 소유자 = 없음").color(NamedTextColor.GRAY);
        } else if (claimed.isAdmin()) {
            msg = Component.text("[관리구역]").color(NamedTextColor.RED);
        } else {
            String name = Bukkit.getOfflinePlayer(claimed.getOwner()).getName();
            boolean isOwn = claimed.getOwner().equals(player.getUniqueId());
            if (isOwn) {
                msg = Component.text("청크 소유자 = ").color(NamedTextColor.WHITE)
                        .append(Component.text("(나)").color(NamedTextColor.GREEN));
            } else {
                msg = Component.text("청크 소유자 = ").color(NamedTextColor.WHITE)
                        .append(Component.text("(" + name + ")").color(NamedTextColor.RED));
            }
        }
        player.sendActionBar(msg);
    }

    /** 현재 밟고 있는 청크 경계선 FLAME */
    private void drawChunkBorder(Player player, Chunk chunk) {
        int cx = chunk.getX() * 16;
        int cz = chunk.getZ() * 16;
        double y = player.getLocation().getY() + 0.5;

        for (int i = 0; i <= 16; i += 2) {
            spawnIfVisible(player, new Location(player.getWorld(), cx + i, y, cz));
            spawnIfVisible(player, new Location(player.getWorld(), cx + i, y, cz + 16));
            spawnIfVisible(player, new Location(player.getWorld(), cx, y, cz + i));
            spawnIfVisible(player, new Location(player.getWorld(), cx + 16, y, cz + i));
        }
    }

    private void spawnIfVisible(Player player, Location loc) {
        if (player.getLocation().distanceSquared(loc) < 1024) {
            player.spawnParticle(Particle.FLAME, loc, 1, 0, 0, 0, 0);
        }
    }
}
