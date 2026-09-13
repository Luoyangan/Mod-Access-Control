// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric.net;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.network.Json;
import mcyszl.top.mod_access_control.core.network.MacPackets;
import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Request;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Response;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage2Response;
import mcyszl.top.mod_access_control.fabric.Holder;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fabric 网络适配层（MC 1.16.5 / Fabric Loader 0.15.x / Fabric API 0.41.3+1.16）。
 *
 * <p>协议与 Forge / NeoForge 完全一致：四个逻辑消息（stage1_req / stage1_resp /
 * stage2_req / stage2_resp），负载均为一段 JSON 字符串（结构由 common 核心定义）。
 * 两阶段握手全部在 PLAY 相位完成。</p>
 *
 * <p>说明：客户端 S2C 全局接收器在 {@code FabricClientMod}（client 入口点）中注册；
 * 本类仅做服务端 C2S 接收、服务端下发与“构建应答 JSON”等两侧共用的工作。</p>
 */
public final class FabricNet {

    /** 四个逻辑消息各自的频道（resource path，命名空间为 MOD_ID）。 */
    public static final ResourceLocation STAGE1_REQUEST = id(MacPackets.KIND_STAGE1_REQUEST);
    public static final ResourceLocation STAGE1_RESPONSE = id(MacPackets.KIND_STAGE1_RESPONSE);
    public static final ResourceLocation STAGE2_REQUEST = id(MacPackets.KIND_STAGE2_REQUEST);
    public static final ResourceLocation STAGE2_RESPONSE = id(MacPackets.KIND_STAGE2_RESPONSE);

    private FabricNet() {
    }

    /** JSON 负载的最大字符数（与 writeUtf 默认 32767 上限一致）。 */
    private static final int MAX_JSON = 32767;

    /** 在 main 入口点调用（专用服务器也会执行）：注册服务端 C2S 全局接收器。 */
    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(STAGE1_RESPONSE, FabricNet::onStage1Response);
        ServerPlayNetworking.registerGlobalReceiver(STAGE2_RESPONSE, FabricNet::onStage2Response);
        Mac.logger().info("MAC Fabric 网络频道已注册");
    }

    /** 服务端向单个玩家发送一条服务端请求（只应在确认对方带本模组后调用）。 */
    public static void sendToPlayer(ServerPlayer player, String kind, String payloadJson) {
        if (player == null) {
            return;
        }
        try {
            if (MacPackets.KIND_STAGE1_REQUEST.equals(kind)) {
                if (!ServerPlayNetworking.canSend(player, STAGE1_REQUEST)) {
                    Mac.logger().warn("客户端 {} 未注册本模组频道，无法发送 stage1 请求",
                            player.getGameProfile().getName());
                    return;
                }
                send(player, STAGE1_REQUEST, payloadJson);
            } else if (MacPackets.KIND_STAGE2_REQUEST.equals(kind)) {
                if (!ServerPlayNetworking.canSend(player, STAGE2_REQUEST)) {
                    return;
                }
                send(player, STAGE2_REQUEST, payloadJson);
            } else {
                Mac.logger().warn("未知的服务端消息种类，忽略: {}", kind);
            }
        } catch (Exception e) {
            Mac.logger().error("发送消息 {} 到 {} 失败", kind, player.getGameProfile().getName());
        }
    }

    // ------------------------------------------------------------------ 服务端下发 / 客户端回送

    private static void send(ServerPlayer player, ResourceLocation channel, String payloadJson) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(payloadJson);
        ServerPlayNetworking.send(player, channel, buf);
    }

    /** 客户端经同一个频道向服务器回送消息。 */
    private static void sendToServer(ResourceLocation channel, String payloadJson) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(payloadJson);
        ClientPlayNetworking.send(channel, buf);
    }

    // ------------------------------------------------------------------ C2S 服务端处理

    private static void onStage1Response(MinecraftServer server, ServerPlayer player,
                                         ServerGamePacketListenerImpl handler,
                                         FriendlyByteBuf buf, PacketSender responseSender) {
        // 注意：1.16.5 专用服务器 jar 没有 readUtf() 无参重载（仅客户端 jar 有），
        // 必须使用两端都存在的 readUtf(int)。
        String json = buf.readUtf(MAX_JSON);
        server.execute(() -> {
            try {
                Stage1Response resp = Json.fromJson(json, Stage1Response.class);
                if (resp != null) {
                    Holder.service().receiveStage1(player.getStringUUID(), resp);
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage1 应答失败(player={})", player.getGameProfile().getName());
            }
        });
    }

    private static void onStage2Response(MinecraftServer server, ServerPlayer player,
                                         ServerGamePacketListenerImpl handler,
                                         FriendlyByteBuf buf, PacketSender responseSender) {
        String json = buf.readUtf(MAX_JSON);
        server.execute(() -> {
            try {
                Stage2Response resp = Json.fromJson(json, Stage2Response.class);
                if (resp != null) {
                    Holder.service().receiveStage2(player.getStringUUID(), resp);
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage2 应答失败(player={})", player.getGameProfile().getName());
            }
        });
    }

    // ------------------------------------------------------------------ 客户端应答构建

    /** 客户端应答 stage1（out 负责把结果包回发服务器）。 */
    public static void respondStage1(String json, String clientLanguage) {
        try {
            Stage1Request req = Json.fromJson(json, Stage1Request.class);
            Stage1Response resp = new Stage1Response();
            resp.protocol = Mac.PROTOCOL_VERSION;
            resp.loaderType = Mac.LOADER_FABRIC;
            resp.loaderVersion = localVersion(Mac.LOADER_FABRIC);
            resp.macVersion = localVersion(Mac.MOD_ID);
            resp.language = clientLanguage;
            Map<String, String> local = localVersions();
            resp.modVersions = new HashMap<>();
            List<String> ids = req == null || req.requiredIds == null
                    ? Collections.emptyList() : req.requiredIds;
            for (String rid : ids) {
                resp.modVersions.put(rid, local.get(rid)); // 未安装 => null => 视为缺失
            }
            sendToServer(STAGE1_RESPONSE, Json.toJson(resp));
        } catch (Exception e) {
            Mac.logger().error("构建 stage1 应答失败", e);
        }
    }

    /** 客户端应答 stage2（上报完整 Mod 列表）。 */
    public static void respondStage2() {
        try {
            Stage2Response resp = new Stage2Response();
            resp.mods = new ArrayList<>();
            for (Map.Entry<String, String> e : localVersions().entrySet()) {
                resp.mods.add(new ClientMod(e.getKey(), e.getValue()));
            }
            sendToServer(STAGE2_RESPONSE, Json.toJson(resp));
        } catch (Exception ex) {
            Mac.logger().error("构建 stage2 应答失败", ex);
        }
    }

    // ------------------------------------------------------------------ 本地 mod 信息

    /** 客户端本地 mod id -> 版本 全量映射（含基础设施 mod，服务端会过滤）。 */
    private static Map<String, String> localVersions() {
        Map<String, String> map = new HashMap<>();
        try {
            FabricLoader.getInstance().getAllMods().forEach(c -> {
                try {
                    map.put(c.getMetadata().getId(), c.getMetadata().getVersion().getFriendlyString());
                } catch (Exception ignore) {
                    // 单个 mod 版本读取失败不影响整体
                }
            });
        } catch (Exception e) {
            Mac.logger().warn("读取本地 mod 列表失败: {}", e.toString());
        }
        return map;
    }

    private static String localVersion(String modId) {
        try {
            return FabricLoader.getInstance().getModContainer(modId)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================================================================
    // 频道命名
    // ==================================================================

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Mac.MOD_ID, path);
    }
}
