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
        // 注意：1.16.5 fabric-networking 在 Netty 线程调用本接收器，此时主线程可能尚未
        // 处理 GameJoin（client.player 未就绪），直接 send 会抛
        // "Cannot send packets when not in game!" —— 必须用 client.execute 切回主线程
        // （GameJoin 任务先入队，因此执行到这里时必然已在游戏中）。
        ClientPlayNetworking.registerGlobalReceiver(FabricNet.STAGE1_REQUEST,
                (client, handler, buf, responseSender) -> {
                    // 用 readUtf(int) 而非无参重载（后者在 1.16.5 专用服务器 jar 上不存在）
                    String json = buf.readUtf(32767);
                    client.execute(() -> FabricNet.respondStage1(json));
                });
        // S2C：收到完整列表请求 -> 回送完整 Mod 列表。
        ClientPlayNetworking.registerGlobalReceiver(FabricNet.STAGE2_REQUEST,
                (client, handler, buf, responseSender) -> client.execute(FabricNet::respondStage2));
        Mac.logger().info("MAC Fabric 客户端握手接收器已注册");
    }
}
