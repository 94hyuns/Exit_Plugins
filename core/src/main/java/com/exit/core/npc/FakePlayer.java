package com.exit.core.npc;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

/**
 * NMS 패킷 기반 가짜 플레이어 엔티티. Core 공용 NPC 구현.
 *
 * 서버에는 실제 엔티티로 등록되지 않으며, 클라이언트에만 패킷으로 존재한다.
 * 상호작용은 Paper의 PlayerUseUnknownEntityEvent 로 잡는다.
 *
 * ⚠ NMS 의존: Paper 1.21.x Mojang-mapped 기준.
 */
public class FakePlayer {

    private final Plugin plugin;
    private final ServerPlayer nmsPlayer;
    private final Location location;
    private final String displayName;
    private final String skinOwnerName;

    public FakePlayer(Plugin plugin, Location location, String displayName, String skinOwnerName) {
        this.plugin = plugin;
        this.location = location.clone();
        this.displayName = displayName;
        this.skinOwnerName = skinOwnerName;

        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        GameProfile profile = new GameProfile(UUID.randomUUID(), displayName);
        if (skinOwnerName != null && !skinOwnerName.isEmpty()) {
            applySkinToProfile(profile, skinOwnerName);
        }

        this.nmsPlayer = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        this.nmsPlayer.setPos(location.getX(), location.getY(), location.getZ());
        this.nmsPlayer.setYRot(location.getYaw());
        this.nmsPlayer.setXRot(location.getPitch());

        // 모든 스킨 레이어 표시
        nmsPlayer.getEntityData().set(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);

        attachFakeConnection(server, profile);
    }

    private void attachFakeConnection(MinecraftServer server, GameProfile profile) {
        try {
            Connection fakeConn = new Connection(PacketFlow.CLIENTBOUND);
            CommonListenerCookie cookie = createFakeCookie(profile);
            ServerGamePacketListenerImpl fakeListener =
                    new ServerGamePacketListenerImpl(server, fakeConn, nmsPlayer, cookie);

            Field connField = ServerPlayer.class.getDeclaredField("connection");
            connField.setAccessible(true);
            connField.set(nmsPlayer, fakeListener);
        } catch (Exception e) {
            plugin.getLogger().warning("[Core/Npc] 가짜 connection 주입 실패: " + e.getMessage());
        }
    }

    private static CommonListenerCookie createFakeCookie(GameProfile profile) throws Exception {
        Constructor<?> best = null;
        for (Constructor<?> c : CommonListenerCookie.class.getDeclaredConstructors()) {
            if (best == null || c.getParameterCount() > best.getParameterCount()) {
                best = c;
            }
        }
        if (best == null) throw new IllegalStateException("CommonListenerCookie 생성자 없음");

        Class<?>[] types = best.getParameterTypes();
        Object[] args = new Object[types.length];
        ClientInformation defaultInfo = ClientInformation.createDefault();

        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == int.class)          args[i] = 0;
            else if (t == long.class)    args[i] = 0L;
            else if (t == boolean.class) args[i] = false;
            else if (t == float.class)   args[i] = 0f;
            else if (t == double.class)  args[i] = 0.0;
            else if (t.isAssignableFrom(GameProfile.class))       args[i] = profile;
            else if (t.isAssignableFrom(ClientInformation.class)) args[i] = defaultInfo;
            else args[i] = null;
        }
        best.setAccessible(true);
        return (CommonListenerCookie) best.newInstance(args);
    }

    private void applySkinToProfile(GameProfile profile, String skinName) {
        try {
            PlayerProfile paperProfile = Bukkit.createProfile(skinName);
            paperProfile.complete();

            for (ProfileProperty prop : paperProfile.getProperties()) {
                if (prop.getName().equals("textures")) {
                    Property texProp = new Property("textures", prop.getValue(), prop.getSignature());
                    putTextureProperty(profile, texProp);
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Core/Npc] 스킨 로드 실패 (" + skinName + "): " + e.getMessage());
        }
    }

    private static void putTextureProperty(GameProfile profile, Property property) throws Exception {
        Object propertyMap;
        try {
            propertyMap = profile.getClass().getMethod("getProperties").invoke(profile);
        } catch (NoSuchMethodException nsme) {
            propertyMap = profile.getClass().getMethod("properties").invoke(profile);
        }

        java.lang.reflect.Method putMethod = null;
        for (java.lang.reflect.Method m : propertyMap.getClass().getMethods()) {
            if (m.getName().equals("put") && m.getParameterCount() == 2) {
                putMethod = m;
                break;
            }
        }
        if (putMethod == null) {
            throw new NoSuchMethodException("put(K, V) on " + propertyMap.getClass().getName());
        }
        putMethod.invoke(propertyMap, "textures", property);
    }

    public void show(org.bukkit.entity.Player viewer) {
        if (!viewer.isOnline()) return;
        ServerGamePacketListenerImpl conn = ((CraftPlayer) viewer).getHandle().connection;

        conn.send(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                nmsPlayer
        ));

        conn.send(new ClientboundAddEntityPacket(
                nmsPlayer.getId(),
                nmsPlayer.getUUID(),
                location.getX(), location.getY(), location.getZ(),
                location.getPitch(), location.getYaw(),
                nmsPlayer.getType(),
                0,
                Vec3.ZERO,
                location.getYaw()
        ));

        conn.send(new ClientboundRotateHeadPacket(
                nmsPlayer,
                (byte) ((location.getYaw() * 256f) / 360f)
        ));

        var data = nmsPlayer.getEntityData().getNonDefaultValues();
        if (data != null) {
            conn.send(new ClientboundSetEntityDataPacket(nmsPlayer.getId(), data));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewer.isOnline()) {
                ((CraftPlayer) viewer).getHandle().connection.send(
                        new ClientboundPlayerInfoRemovePacket(List.of(nmsPlayer.getUUID()))
                );
            }
        }, 40L);
    }

    public void hide(org.bukkit.entity.Player viewer) {
        if (!viewer.isOnline()) return;
        ServerGamePacketListenerImpl conn = ((CraftPlayer) viewer).getHandle().connection;
        conn.send(new ClientboundPlayerInfoRemovePacket(List.of(nmsPlayer.getUUID())));
        conn.send(new ClientboundRemoveEntitiesPacket(nmsPlayer.getId()));
    }

    public void showToAll() {
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) show(p);
    }

    public void hideFromAll() {
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) hide(p);
    }

    public void remove() { hideFromAll(); }

    public int getEntityId() { return nmsPlayer.getId(); }
    public UUID getUUID() { return nmsPlayer.getUUID(); }
    public Location getLocation() { return location.clone(); }
    public String getDisplayName() { return displayName; }
    public String getSkinOwnerName() { return skinOwnerName; }
}
