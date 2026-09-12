// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.platform.PlatformBridge;
import mcyszl.top.mod_access_control.fabric.net.FabricNet;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric 平台桥：向 common 核心提供“取加载器信息 / 发网络包 / 断线 / 通知管理员”等平台动作。
 */
public final class MacFabricBridge implements PlatformBridge {

    /** 当前服务器实例（事件回调里维护），供按 uuid 查找玩家。 */
    private static volatile MinecraftServer server;

    /**
     * JOIN 事件里记住的玩家引用。1.16.5 Fabric 的 JOIN 事件触发早于玩家注册进
     * {@code PlayerList.playersByUUID}，此时按 uuid 查列表会返回 null，
     * 因此握手发包必须直接使用该引用。
     */
    private static final Map<String, ServerPlayer> JOINED = new ConcurrentHashMap<>();

    private volatile String macVersion;

    @Override
    public String loaderType() {
        return Mac.LOADER_FABRIC;
    }

    @Override
    public String loaderVersion() {
        return FabricLoader.getInstance().getModContainer(Mac.LOADER_FABRIC)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    @Override
    public String modVersion() {
        String v = macVersion;
        if (v == null) {
            v = FabricLoader.getInstance().getModContainer(Mac.MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("dev");
            macVersion = v;
        }
        return v;
    }

    @Override
    public Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(Mac.MOD_ID + ".json");
    }

    @Override
    public boolean dedicatedServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    @Override
    public void sendToClient(String playerUuid, String kind, String payloadJson) {
        ServerPlayer p = player(playerUuid);
        if (p != null) {
            FabricNet.sendToPlayer(p, kind, payloadJson);
        } else {
            Mac.logger().warn("[网络] sendToClient 找不到在线玩家 uuid={}，消息 {} 未发送", playerUuid, kind);
        }
    }

    @Override
    public void disconnectPlayer(String playerUuid, KickMessage message) {
        ServerPlayer p = player(playerUuid);
        if (p != null) {
            p.connection.disconnect(Feedback.disconnect(message));
        } else {
            Mac.logger().warn("[网络] disconnectPlayer 找不到在线玩家 uuid={}，无法踢出", playerUuid);
        }
    }

    @Override
    public void notifyOps(String text) {
        FabricEvents.alertOps(text);
    }

    /** 记录当前服务器（供查询在线玩家）。 */
    public static void attachServer(MinecraftServer s) {
        server = s;
        if (s == null) {
            JOINED.clear();
        }
    }

    /** JOIN 事件：直接记住玩家实例（时序早于玩家列表注册）。 */
    public static void onJoin(ServerPlayer sp) {
        JOINED.put(sp.getStringUUID(), sp);
    }

    /** DISCONNECT 事件：移除玩家引用。 */
    public static void onLeave(String uuid) {
        JOINED.remove(uuid);
    }

    /** 当前服务器实例；null 表示未运行。 */
    public static MinecraftServer server() {
        return server;
    }

    /** 按 uuid 查在线玩家；优先取 JOIN 事件记住的实例，不在线返回 null。 */
    public static ServerPlayer player(String playerUuid) {
        ServerPlayer remembered = JOINED.get(playerUuid);
        if (remembered != null) {
            return remembered;
        }
        MinecraftServer s = server;
        if (s == null) {
            return null;
        }
        try {
            return s.getPlayerList().getPlayer(UUID.fromString(playerUuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
