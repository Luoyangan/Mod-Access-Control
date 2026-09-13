// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.neoforge;

import mcyszl.top.mod_access_control.api.MacApi;
import mcyszl.top.mod_access_control.core.Mac;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Mod Access Control —— NeoForge 入口。
 *
 * <p>服务器端准入控制模组（客户端同样需要安装以自动应答握手）。
 * 网络 payload 的注册走 {@code @Mod.EventBusSubscriber}（mod 总线），
 * 见 {@code net.NeoNet}；此处仅负责初始化与游戏事件总线接线。</p>
 */
@Mod(Mac.MOD_ID)
public final class NeoMacMod {

    public NeoMacMod(IEventBus modEventBus) {
        Mac.setLogger(new NeoLog());
        Holder.init();
        // 公共 API 门面：供以本模组为前置的其他 mod 调用
        MacApi.install(new MacApiImpl(Holder.service()));

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
