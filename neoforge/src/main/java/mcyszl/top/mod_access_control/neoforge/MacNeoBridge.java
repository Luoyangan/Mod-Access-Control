package mcyszl.top.mod_access_control.neoforge;

import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.platform.PlatformBridge;
import mcyszl.top.mod_access_control.neoforge.net.NeoNet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.nio.file.Path;
import java.util.UUID;

/**
 * NeoForge 平台桥：向 common 核心提供“取加载器信息 / 发网络包 / 断线 / 通知管理员”等平台动作。
 */
public final class MacNeoBridge implements PlatformBridge {

    private volatile String macVersion;

    @Override
    public String loaderType() {
        return Mac.LOADER_NEOFORGE;
    }

    @Override
    public String loaderVersion() {
        return ModList.get().getModContainerById(Mac.LOADER_NEOFORGE)
                .map(c -> String.valueOf(c.getModInfo().getVersion()))
                .orElse("?");
    }

    @Override
    public String modVersion() {
        String v = macVersion;
        if (v == null) {
            v = ModList.get().getModContainerById(Mac.MOD_ID)
                    .map(c -> String.valueOf(c.getModInfo().getVersion()))
                    .orElse("dev");
            macVersion = v;
        }
        return v;
    }

    @Override
    public Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve(Mac.MOD_ID + ".json");
    }

    @Override
    public boolean dedicatedServer() {
        return FMLEnvironment.dist.isDedicatedServer();
    }

    @Override
    public void sendToClient(String playerUuid, String kind, String payloadJson) {
        ServerPlayer p = player(playerUuid);
        if (p != null) {
            NeoNet.sendToPlayer(p, kind, payloadJson);
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
        NeoEvents.alertOps(text);
    }

    /** 按 uuid 查在线玩家；不在线返回 null。 */
    public static ServerPlayer player(String playerUuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(playerUuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 供 command/事件等引用服务端单例；返回 null 表示服务器未运行。 */
    public static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }
}
