package mcyszl.top.mod_access_control.neoforge;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.neoforge.net.NeoNet;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

/**
 * Mod Access Control —— NeoForge 入口（MC 1.20.1 / NeoForge 47.x）。
 *
 * <p>服务器端准入控制模组（客户端同样需要安装以自动应答握手）。
 * 注意：1.20.1 的 NeoForge 47.x 仍沿用 {@code net.minecraftforge.*} 命名空间
 * （当时尚未迁移到 net.neoforged.*），公开 API 与 Forge 47.x 一致。</p>
 */
@Mod(Mac.MOD_ID)
public final class NeoMacMod {

    public NeoMacMod() {
        Mac.setLogger(new NeoLog());
        Holder.init();
        NeoNet.init();

        // 服务端事件（专用服务器或集成服务器均可触发；enforce 范围由配置决定）
        MinecraftForge.EVENT_BUS.addListener(NeoEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(NeoEvents::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(NeoEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(NeoEvents::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(NeoEvents::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(NeoEvents::onRegisterCommands);

        Mac.logger().info("Mod Access Control (NeoForge) 初始化完成 (loader={}, version={})",
                Mac.LOADER_NEOFORGE, Holder.bridge().modVersion());
    }
}
