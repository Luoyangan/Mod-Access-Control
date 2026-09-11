// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.forge.command.MacCommand;
import mcyszl.top.mod_access_control.forge.net.ForgeNet;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;

/**
 * Mod Access Control —— Forge 1.12.2 入口。
 *
 * <p>服务器端准入控制模组（客户端同样需要安装以自动应答握手）。</p>
 *
 * <p>版本号未在注解中硬编码，由 mcmod.info 的 {@code version} 字段提供
 * （gradle processResources 替换为构建版本）。</p>
 */
@Mod(modid = Mac.MOD_ID,
        name = Mac.MOD_NAME,
        acceptedMinecraftVersions = "[1.12,1.13)",
        dependencies = "after:forge")
public final class ForgeMacMod {

    public ForgeMacMod() {
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Mac.setLogger(new ForgeLog(event.getModLog()));
        Holder.init();
        ForgeNet.init();

        // 游戏内事件（玩家加入离开 / 刻驱动 / 命令注册）
        MinecraftForge.EVENT_BUS.register(ForgeEvents.class);

        Mac.logger().info("Mod Access Control (Forge 1.12.2) 初始化完成 (loader={}, forge={}, version={})",
                Mac.LOADER_FORGE, Holder.bridge().loaderVersion(), Holder.bridge().modVersion());
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        // 1.12.2 pre-Brigadier 命令注册入口
        event.registerServerCommand(new MacCommand());
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        Holder.service().onServerStarted();
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        Holder.service().onServerStopping();
    }
}
