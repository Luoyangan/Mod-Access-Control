package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.forge.command.MacCommand;
import mcyszl.top.mod_access_control.forge.net.ForgeNet;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Forge 事件接线（1.12.2）：玩家加入离开 / 刻驱动 / 命令注册 / 管理员通知。
 *
 * <p>1.12.2 的事件总线要求 {@code @SubscribeEvent} 方法挂在
 * {@code MinecraftForge.EVENT_BUS} 上（服务器生命周期事件由 @Mod 主类处理）。</p>
 */
public final class ForgeEvents {

    private ForgeEvents() {
    }

    /** 玩家进入世界：统一交给核心做预检（开关/豁免/未装模组/试运行）后开启握手。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP sp = (EntityPlayerMP) e.player;
        String uuid = sp.getUniqueID().toString();
        String name = sp.getGameProfile().getName();
        boolean hasChannel = ForgeNet.remoteHasChannel(sp);
        MinecraftServer server = MacForgeBridge.server();
        boolean isOp = server != null && server.getPlayerList().canSendCommands(sp.getGameProfile());
        Holder.service().handleLoginAttempt(uuid, name, hasChannel, isOp);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP sp = (EntityPlayerMP) e.player;
        Holder.service().onPlayerLeave(sp.getUniqueID().toString());
    }

    /** 服务器刻驱动（仅 END 相位处理一次）。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Holder.service().onServerTick();
    }

    /** 1.12.2 的命令注册由主类在 FMLServerStartingEvent 里完成（registerServerCommand）。 */

    /** 向在线管理员广播纯文本（聊天 + 音效提示）。 */
    public static void alertOps(String text) {
        MinecraftServer server = MacForgeBridge.server();
        if (server == null) {
            Mac.logger().warn("(无服务器上下文) 管理员通知: {}", text);
            return;
        }
        TextComponentString line = new TextComponentString("[MAC] " + text);
        line.getStyle().setColor(TextFormatting.YELLOW);
        boolean any = false;
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (server.getPlayerList().canSendCommands(p.getGameProfile())) {
                p.sendMessage(line);
                p.playSound(SoundEvents.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
                any = true;
            }
        }
        if (!any) {
            Mac.logger().warn("(无在线管理员) 管理员通知: {}", text);
        }
    }
}
