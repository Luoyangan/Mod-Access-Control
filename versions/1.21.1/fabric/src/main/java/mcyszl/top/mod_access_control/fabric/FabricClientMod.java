// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.fabric.net.FabricNet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Fabric 客户端入口：注册 S2C 全局接收器，自动应答服务端的两阶段握手。
 */
public final class FabricClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 注意：接收器可能在 Netty 线程被调用，此时玩家尚未进入游戏，直接发包会抛
        // "Cannot send packets when not in game"——必须切回主线程再应答
        // （execute 在已是主线程时会内联执行，无重复排队副作用）。
        // S2C：收到登录阶段请求 -> 回送最小必要信息（协议 + 加载器 + 必需 Mod 版本）。
        ClientPlayNetworking.registerGlobalReceiver(FabricNet.Stage1RequestPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        FabricNet.respondStage1(payload.json(), clientLanguage(),
                                out -> ctx.responseSender().sendPacket(out))));
        // S2C：收到完整列表请求 -> 回送完整 Mod 列表。
        ClientPlayNetworking.registerGlobalReceiver(FabricNet.Stage2RequestPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() ->
                        FabricNet.respondStage2(payload.json(),
                                out -> ctx.responseSender().sendPacket(out))));
        Mac.logger().info("MAC Fabric 客户端握手接收器已注册");
    }

    /** 当前客户端界面语言（如 zh_cn）：随登录阶段应答上报，供服务端按玩家语言渲染文案。 */
    private static String clientLanguage() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected();
        } catch (Exception e) {
            return null;
        }
    }
}
