package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.fabric.command.MacCommand;
import mcyszl.top.mod_access_control.fabric.net.FabricNet;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Fabric 事件接线：服务器生命周期 / 玩家加入离开 / 刻驱动 / 命令注册 / 管理员通知。
 */
public final class FabricEvents {

    private FabricEvents() {
    }

    /** 在 main 入口点注册所有事件回调。 */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            MacFabricBridge.attachServer(server);
            Holder.service().onServerStarted();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Holder.service().onServerStopping();
            MacFabricBridge.attachServer(null);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> Holder.service().onServerTick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer sp = handler.getPlayer();
            if (sp == null) {
                return;
            }
            String uuid = sp.getStringUUID();
            String name = sp.getGameProfile().getName();
            boolean hasChannel = ServerPlayNetworking.canSend(sp, FabricNet.Stage1RequestPayload.TYPE);
            boolean isOp = server.getPlayerList().isOp(sp.getGameProfile());
            Holder.service().handleLoginAttempt(uuid, name, hasChannel, isOp);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer sp = handler.getPlayer();
            if (sp != null) {
                Holder.service().onPlayerLeave(sp.getStringUUID());
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MacCommand.register(dispatcher));
    }

    /** 向在线管理员广播纯文本（聊天），并以 ActionBar + 音效提示。 */
    public static void alertOps(String text) {
        MinecraftServer server = MacFabricBridge.server();
        if (server == null) {
            Mac.logger().warn("(无服务器上下文) 管理员通知: {}", text);
            return;
        }
        Component line = Component.literal("[MAC] " + text)
                .withStyle(ChatFormatting.YELLOW);
        Component bar = Component.literal("\u26A0 " + text) // 警示符号
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        boolean any = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().isOp(p.getGameProfile())) {
                p.sendSystemMessage(line);
                p.displayClientMessage(bar, true);
                p.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.MASTER, 0.5f, 1.0f);
                any = true;
            }
        }
        if (!any) {
            Mac.logger().warn("(无在线管理员) 管理员通知: {}", text);
        }
    }
}
