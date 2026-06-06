package com.exit.core.npc;

import com.exit.core.api.NpcClickHandler;
import com.exit.core.api.NpcInfo;
import com.exit.core.api.NpcService;
import com.exit.core.api.NpcSpawnSpec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class NpcServiceImpl implements NpcService {

    private static final String NPCS_FILE = "npcs.yml";

    private final Plugin plugin;
    private final Map<String, Map<String, FakePlayer>> npcsByOwner = new HashMap<>();
    private final Map<Integer, NpcInfo> entityIdIndex = new HashMap<>();
    private final Map<String, NpcInfo> infoByKey = new HashMap<>();
    private final Map<String, NpcClickHandler> handlers = new HashMap<>();
    private final File file;

    public NpcServiceImpl(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), NPCS_FILE);
    }

    @Override
    public void registerClickHandler(String owner, NpcClickHandler handler) {
        handlers.put(owner, handler);
    }

    public NpcClickHandler getHandler(String owner) {
        return handlers.get(owner);
    }

    @Override
    public synchronized boolean spawn(NpcSpawnSpec spec) {
        if (spec.location() == null || spec.location().getWorld() == null) {
            plugin.getLogger().warning("[NpcService] spawn 실패: location 없음");
            return false;
        }
        Map<String, FakePlayer> bucket = npcsByOwner.computeIfAbsent(spec.owner(), k -> new HashMap<>());
        if (bucket.containsKey(spec.id())) {
            plugin.getLogger().warning("[NpcService] 이미 존재: " + spec.owner() + "/" + spec.id());
            return false;
        }
        String dispName = spec.displayName() == null ? "NPC" : spec.displayName();
        FakePlayer fp = new FakePlayer(plugin, spec.location(), dispName, spec.skinOwner());
        bucket.put(spec.id(), fp);

        NpcInfo info = new NpcInfo(spec.owner(), spec.id(), spec.location().clone(),
                dispName, spec.skinOwner(), fp.getEntityId());
        entityIdIndex.put(fp.getEntityId(), info);
        infoByKey.put(key(spec.owner(), spec.id()), info);

        fp.showToAll();

        if (spec.persist()) save();
        return true;
    }

    @Override
    public synchronized boolean remove(String owner, String id) {
        Map<String, FakePlayer> bucket = npcsByOwner.get(owner);
        if (bucket == null) return false;
        FakePlayer fp = bucket.remove(id);
        if (fp == null) return false;
        fp.remove();
        entityIdIndex.remove(fp.getEntityId());
        infoByKey.remove(key(owner, id));
        save();
        return true;
    }

    @Override
    public Optional<NpcInfo> get(String owner, String id) {
        return Optional.ofNullable(infoByKey.get(key(owner, id)));
    }

    @Override
    public Optional<NpcInfo> getByEntityId(int entityId) {
        return Optional.ofNullable(entityIdIndex.get(entityId));
    }

    @Override
    public Collection<NpcInfo> getByOwner(String owner) {
        List<NpcInfo> list = new ArrayList<>();
        for (NpcInfo info : infoByKey.values()) {
            if (info.owner().equals(owner)) list.add(info);
        }
        return list;
    }

    @Override
    public Collection<NpcInfo> all() {
        return new ArrayList<>(infoByKey.values());
    }

    @Override
    public void showAllTo(Player player) {
        World w = player.getWorld();
        for (Map<String, FakePlayer> bucket : npcsByOwner.values()) {
            for (FakePlayer fp : bucket.values()) {
                if (!fp.getLocation().getWorld().equals(w)) continue;
                fp.show(player);
            }
        }
    }

    public void loadAndSpawn() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ownersSec = cfg.getConfigurationSection("owners");
        if (ownersSec == null) return;

        int count = 0;
        for (String owner : ownersSec.getKeys(false)) {
            ConfigurationSection ownerSec = ownersSec.getConfigurationSection(owner);
            if (ownerSec == null) continue;
            for (String id : ownerSec.getKeys(false)) {
                ConfigurationSection s = ownerSec.getConfigurationSection(id);
                if (s == null) continue;
                String worldName = s.getString("world");
                World w = worldName == null ? null : Bukkit.getWorld(worldName);
                if (w == null) {
                    plugin.getLogger().warning("[NpcService] " + owner + "/" + id + ": world '" + worldName + "' 없음");
                    continue;
                }
                Location loc = new Location(w,
                        s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                        (float) s.getDouble("yaw", 0), (float) s.getDouble("pitch", 0));
                String skin = s.getString("skin");
                String displayName = s.getString("displayName", "NPC");

                NpcSpawnSpec spec = new NpcSpawnSpec(owner, id, loc, displayName, skin, false);
                spawn(spec);
                count++;
            }
        }
        plugin.getLogger().info("[NpcService] NPC " + count + "개 로드");
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Map<String, FakePlayer>> ownerEntry : npcsByOwner.entrySet()) {
            String owner = ownerEntry.getKey();
            for (Map.Entry<String, FakePlayer> e : ownerEntry.getValue().entrySet()) {
                String id = e.getKey();
                FakePlayer fp = e.getValue();
                Location l = fp.getLocation();
                String base = "owners." + owner + "." + id;
                cfg.set(base + ".world", l.getWorld().getName());
                cfg.set(base + ".x", l.getX());
                cfg.set(base + ".y", l.getY());
                cfg.set(base + ".z", l.getZ());
                cfg.set(base + ".yaw", l.getYaw());
                cfg.set(base + ".pitch", l.getPitch());
                cfg.set(base + ".displayName", fp.getDisplayName());
                cfg.set(base + ".skin", fp.getSkinOwnerName());
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[NpcService] npcs.yml 저장 실패", e);
        }
    }

    public void removeAllInMemory() {
        for (Map<String, FakePlayer> bucket : npcsByOwner.values()) {
            for (FakePlayer fp : bucket.values()) fp.remove();
        }
        npcsByOwner.clear();
        entityIdIndex.clear();
        infoByKey.clear();
    }

    private static String key(String owner, String id) { return owner + "/" + id; }
}
