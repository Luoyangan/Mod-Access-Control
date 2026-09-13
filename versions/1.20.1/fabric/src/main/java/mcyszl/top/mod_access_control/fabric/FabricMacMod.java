// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.api.MacApi;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.fabric.net.FabricNet;
import net.fabricmc.api.ModInitializer;

/**
 * Mod Access Control —— Fabric 入口（客户端 / 专用服务器均会加载）。
 *
 * <p>服务器端准入控制模组（客户端同样需要安装以自动应答握手）。
 * 客户端 S2C 接收器见 {@code FabricClientMod}（仅物理客户端加载）。</p>
 */
public final class FabricMacMod implements ModInitializer {

    @Override
    public void onInitialize() {
        Mac.setLogger(new FabricLog());
        Holder.init();
        FabricNet.init();
        FabricEvents.init();
        // 公共 API 门面：供以本模组为前置的其他 mod 调用
        MacApi.install(new MacApiImpl(Holder.service()));
        Mac.logger().info("Mod Access Control (Fabric) 初始化完成 (loader={}, version={})",
                Mac.LOADER_FABRIC, Holder.bridge().modVersion());
    }
}
