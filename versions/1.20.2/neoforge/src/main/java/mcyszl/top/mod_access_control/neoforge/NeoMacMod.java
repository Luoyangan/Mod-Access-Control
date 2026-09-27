// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.neoforge;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.neoforge.net.NeoNet;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Mod Access Control —— NeoForge 入口（MC 1.20.2 / NeoForge 20.2.x）。
 *
 * <p>服务器端准入控制模组（客户端同样需要安装以自动应答握手）。
 * 注意：1.20.2 的 NeoForge 20.2.x 是包名大迁移的第一版
 * （net.minecraftforge.* -> net.neoforged.*）：@Mod 注解位于
 * {@code net.neoforged.fml.common.Mod}，游戏事件总线为 {@link NeoForge#EVENT_BUS}；
 * 网络通道注册不依赖事件，见 {@code net.NeoNet}。</p>
 */
@Mod(Mac.MOD_ID)
public final class NeoMacMod {

    public NeoMacMod() {
        Mac.setLogger(new NeoLog());
        Holder.init();
        NeoNet.init();

        // 服务端事件（专用服务器或集成服务器均可触发；enforce 范围由配置决定）
        NeoForge.EVENT_BUS.addListener(NeoEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(NeoEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(NeoEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(NeoEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(NeoEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(NeoEvents::onRegisterCommands);

        Mac.logger().info("Mod Access Control (NeoForge) 初始化完成 (loader={}, version={})",
                Mac.LOADER_NEOFORGE, Holder.bridge().modVersion());
    }
}
