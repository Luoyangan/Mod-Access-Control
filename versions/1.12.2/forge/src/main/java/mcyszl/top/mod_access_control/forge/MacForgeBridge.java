// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.platform.PlatformBridge;
import mcyszl.top.mod_access_control.forge.net.ForgeNet;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Forge 平台桥（MC 1.12.2 / Forge 14.23.5.x）：向 common 核心提供
 * “取加载器信息 / 发网络包 / 断线 / 通知管理员”等平台动作。
 */
public final class MacForgeBridge implements PlatformBridge {

    private volatile String macVersion;

    @Override
    public String loaderType() {
        return Mac.LOADER_FORGE;
    }

    @Override
    public String loaderVersion() {
        return ForgeVersion.getVersion();
    }

    @Override
    public String modVersion() {
        String v = macVersion;
        if (v == null) {
            v = "dev";
            try {
                ModContainer container = Loader.instance().getIndexedModList().get(Mac.MOD_ID);
                if (container != null) {
                    v = String.valueOf(container.getVersion());
                }
            } catch (Exception ignore) {
                // 元数据未就绪时回退到占位版本
            }
            macVersion = v;
        }
        return v;
    }

    @Override
    public Path configFile() {
        return Loader.instance().getConfigDir().toPath().resolve(Mac.MOD_ID + ".json");
    }

    @Override
    public boolean dedicatedServer() {
        return FMLCommonHandler.instance().getSide().isServer();
    }

    @Override
    public void sendToClient(String playerUuid, String kind, String payloadJson) {
        EntityPlayerMP p = player(playerUuid);
        if (p != null) {
            ForgeNet.sendToPlayer(p, kind, payloadJson);
        }
    }

    @Override
    public void disconnectPlayer(String playerUuid, KickMessage message) {
        EntityPlayerMP p = player(playerUuid);
        if (p != null) {
            Feedback.kick(p, message);
        }
    }

    @Override
    public String clientLanguage(String playerUuid) {
        EntityPlayerMP p = player(playerUuid);
        if (p == null) {
            return null;
        }
        // 1.12.2 的 EntityPlayerMP#language 是私有字段且没有公开 getter，
        // 只能经 ObfuscationReflectionHelper 读取（运行时为 srg 名 field_71148_cg）。
        try {
            Object value = ObfuscationReflectionHelper.getPrivateValue(
                    EntityPlayerMP.class, p, "field_71148_cg");
            return value == null ? null : String.valueOf(value);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void notifyOps(String text) {
        ForgeEvents.alertOps(text);
    }

    /** 按 uuid 查在线玩家；不在线返回 null。 */
    public static EntityPlayerMP player(String playerUuid) {
        MinecraftServer server = server();
        if (server == null) {
            return null;
        }
        try {
            return server.getPlayerList().getPlayerByUUID(UUID.fromString(playerUuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 供 command/事件等引用服务端单例；返回 null 表示服务器未运行。 */
    public static MinecraftServer server() {
        return FMLCommonHandler.instance().getMinecraftServerInstance();
    }
}
