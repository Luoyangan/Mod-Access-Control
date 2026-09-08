package mcyszl.top.mod_access_control.neoforge;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.neoforge.command.MacCommand;
import mcyszl.top.mod_access_control.neoforge.net.NeoNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * NeoForge 事件接线：服务器生命周期 / 玩家加入离开 / 刻驱动 / 命令注册 / 管理员通知。
 */
public final class NeoEvents {

    private NeoEvents() {
    }

    public static void onServerStarted(ServerStartedEvent e) {
        Holder.service().onServerStarted();
    }

    public static void onServerStopping(ServerStoppingEvent e) {
        Holder.service().onServerStopping();
    }

    /** 1.20.1 的 ServerTickEvent 无 Post/Pre 子类，需按 phase 过滤（仅 END 处理）。 */
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Holder.service().onServerTick();
    }

    /** 玩家进入世界（PLAY 相位开始）：统一交给核心做预检（开关/豁免/未装模组/试运行）后开启握手。 */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        String uuid = sp.getStringUUID();
        String name = sp.getGameProfile().getName();
        boolean hasChannel = NeoNet.remoteHasChannel(sp.connection.connection);
        boolean isOp = sp.server != null && sp.server.getPlayerList().isOp(sp.getGameProfile());
        Holder.service().handleLoginAttempt(uuid, name, hasChannel, isOp);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        Holder.service().onPlayerLeave(sp.getStringUUID());
    }

    public static void onRegisterCommands(RegisterCommandsEvent e) {
        MacCommand.register(e.getDispatcher());
    }

    /** 向在线管理员广播纯文本（聊天），并以 ActionBar + 音效提示。 */
    public static void alertOps(String text) {
        MinecraftServer server = MacNeoBridge.server();
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
