// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.i18n.Lang;
import mcyszl.top.mod_access_control.forge.net.ForgeNet;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
        // Forge 60+：事件总线按事件类拆分为静态 BUS 字段
        ServerStartedEvent.BUS.addListener(ForgeEvents::onServerStarted);
        ServerStoppingEvent.BUS.addListener(ForgeEvents::onServerStopping);
        TickEvent.ServerTickEvent.Post.BUS.addListener(ForgeEvents::onServerTick);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(ForgeEvents::onPlayerLoggedIn);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(ForgeEvents::onPlayerLoggedOut);
        RegisterCommandsEvent.BUS.addListener(ForgeEvents::onRegisterCommands);

        Mac.logger().info(Lang.tr("Mod Access Control (Forge) initialized (loader={}, version={})"),
                Mac.LOADER_FORGE, Holder.bridge().modVersion());
    }
}
