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
        ClientPlayNetworking.registerGlobalReceiver(FabricNet.Stage1RequestPayload.TYPE,
                (payload, ctx) -> FabricNet.respondStage1(payload.json(),
                        out -> ctx.responseSender().sendPacket(out)));
        // S2C：收到完整列表请求 -> 回送完整 Mod 列表。
        ClientPlayNetworking.registerGlobalReceiver(FabricNet.Stage2RequestPayload.TYPE,
                (payload, ctx) -> FabricNet.respondStage2(payload.json(),
                        out -> ctx.responseSender().sendPacket(out)));
        Mac.logger().info("MAC Fabric 客户端握手接收器已注册");
    }
}
