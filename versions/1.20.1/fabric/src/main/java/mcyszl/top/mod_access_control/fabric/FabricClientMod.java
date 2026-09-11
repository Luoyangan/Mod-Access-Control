// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.fabric.net.FabricNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Fabric 客户端入口：注册 S2C 全局接收器，自动应答服务端的两阶段握手。
 */
public final class FabricClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // S2C：收到登录阶段请求 -> 回送最小必要信息（协议 + 加载器 + 必需 Mod 版本）。
        ClientPlayNetworking.registerGlobalReceiver(FabricNet.STAGE1_REQUEST,
                (client, handler, buf, responseSender) -> FabricNet.respondStage1(buf.readUtf()));
        // S2C：收到完整列表请求 -> 回送完整 Mod 列表。
        ClientPlayNetworking.registerGlobalReceiver(FabricNet.STAGE2_REQUEST,
                (client, handler, buf, responseSender) -> FabricNet.respondStage2());
        Mac.logger().info("MAC Fabric 客户端握手接收器已注册");
    }
}
