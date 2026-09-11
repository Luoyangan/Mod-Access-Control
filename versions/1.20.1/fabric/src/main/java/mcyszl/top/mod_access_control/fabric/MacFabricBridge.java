// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.platform.PlatformBridge;
import mcyszl.top.mod_access_control.fabric.net.FabricNet;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Fabric 平台桥：向 common 核心提供“取加载器信息 / 发网络包 / 断线 / 通知管理员”等平台动作。
 */
public final class MacFabricBridge implements PlatformBridge {

    /** 当前服务器实例（事件回调里维护），供按 uuid 查找玩家。 */
    private static volatile MinecraftServer server;

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
        }
    }

    @Override
    public void disconnectPlayer(String playerUuid, KickMessage message) {
        ServerPlayer p = player(playerUuid);
        if (p != null) {
            p.connection.disconnect(Feedback.disconnect(message));
        }
    }

    @Override
    public void notifyOps(String text) {
        FabricEvents.alertOps(text);
    }

    /** 记录当前服务器（供查询在线玩家）。 */
    public static void attachServer(MinecraftServer s) {
        server = s;
    }

    /** 当前服务器实例；null 表示未运行。 */
    public static MinecraftServer server() {
        return server;
    }

    /** 按 uuid 查在线玩家；不在线返回 null。 */
    public static ServerPlayer player(String playerUuid) {
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
