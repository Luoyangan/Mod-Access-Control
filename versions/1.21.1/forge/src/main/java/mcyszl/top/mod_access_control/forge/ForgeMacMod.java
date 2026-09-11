// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.forge.net.ForgeNet;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mod Access Control —— Forge 入口。
 *
 * <p>服务器端准入控制模组（客户端同样需要安装以自动应答握手）。</p>
 */
@Mod(Mac.MOD_ID)
public final class ForgeMacMod {

    public ForgeMacMod() {
        Mac.setLogger(new ForgeLog());
        Holder.init();
        ForgeNet.init();

        // 服务端事件（专用服务器或集成服务器均可触发；enforce 范围由配置决定）
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(ForgeEvents::onRegisterCommands);

        Mac.logger().info("Mod Access Control (Forge) 初始化完成 (loader={}, version={})",
                Mac.LOADER_FORGE, Holder.bridge().modVersion());
    }
}
