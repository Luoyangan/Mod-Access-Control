// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric.net;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.network.Json;
import mcyszl.top.mod_access_control.core.network.MacPackets;
import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Request;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Response;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage2Request;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage2Response;
import mcyszl.top.mod_access_control.fabric.Holder;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fabric 网络适配层。
 *
 * <p>协议与 Forge / NeoForge 完全一致：四个逻辑消息（stage1_req / stage1_resp /
 * stage2_req / stage2_resp）分别注册为独立的 {@link CustomPacketPayload}，负载均为
 * 一段 JSON 字符串（结构由 common 核心定义）。两阶段握手全部在 PLAY 相位完成。</p>
 *
 * <p>说明：客户端 S2C 全局接收器在 {@code FabricClientMod}（client 入口点）中注册；
 * 本类仅做类型注册、服务端 C2S 接收与“构建应答 JSON”等两侧共用的工作。</p>
 */
public final class FabricNet {

    private FabricNet() {
    }

    /** 在入口点调用（客户端 / 专用服务器都会执行）。 */
    public static void init() {
        // 类型注册必须在两端都执行：发端用于编码，收端用于解码。
        PayloadTypeRegistry.playC2S().register(Stage1ResponsePayload.TYPE, Stage1ResponsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Stage2ResponsePayload.TYPE, Stage2ResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Stage1RequestPayload.TYPE, Stage1RequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Stage2RequestPayload.TYPE, Stage2RequestPayload.CODEC);

        // 服务端接收 C2S 应答（网络线程），转发到主线程处理。
        ServerPlayNetworking.registerGlobalReceiver(Stage1ResponsePayload.TYPE, FabricNet::onStage1Response);
        ServerPlayNetworking.registerGlobalReceiver(Stage2ResponsePayload.TYPE, FabricNet::onStage2Response);
        Mac.logger().info("MAC Fabric 网络通道已注册");
    }

    /** 服务端向单个玩家发送一条服务端请求（只应在确认对方带本模组后调用）。 */
    public static void sendToPlayer(ServerPlayer player, String kind, String payloadJson) {
        if (player == null) {
            return;
        }
        try {
            if (MacPackets.KIND_STAGE1_REQUEST.equals(kind)) {
                if (!ServerPlayNetworking.canSend(player, Stage1RequestPayload.TYPE)) {
                    Mac.logger().warn("客户端 {} 未注册本模组通道，无法发送 stage1 请求",
                            player.getGameProfile().getName());
                    return;
                }
                ServerPlayNetworking.send(player, new Stage1RequestPayload(payloadJson));
            } else if (MacPackets.KIND_STAGE2_REQUEST.equals(kind)) {
                if (!ServerPlayNetworking.canSend(player, Stage2RequestPayload.TYPE)) {
                    return;
                }
                ServerPlayNetworking.send(player, new Stage2RequestPayload(payloadJson));
            } else {
                Mac.logger().warn("未知的服务端消息种类，忽略: {}", kind);
            }
        } catch (Exception e) {
            Mac.logger().error("发送消息 {} 到 {} 失败", kind, player.getGameProfile().getName());
        }
    }

    // ------------------------------------------------------------------ C2S 服务端处理

    private static void onStage1Response(Stage1ResponsePayload payload, ServerPlayNetworking.Context ctx) {
        ctx.server().execute(() -> {
            ServerPlayer sp = ctx.player();
            if (sp == null) {
                return;
            }
            try {
                Stage1Response resp = Json.fromJson(payload.json(), Stage1Response.class);
                if (resp != null) {
                    Holder.service().receiveStage1(sp.getStringUUID(), resp);
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage1 应答失败(player={})", sp.getGameProfile().getName());
            }
        });
    }

    private static void onStage2Response(Stage2ResponsePayload payload, ServerPlayNetworking.Context ctx) {
        ctx.server().execute(() -> {
            ServerPlayer sp = ctx.player();
            if (sp == null) {
                return;
            }
            try {
                Stage2Response resp = Json.fromJson(payload.json(), Stage2Response.class);
                if (resp != null) {
                    Holder.service().receiveStage2(sp.getStringUUID(), resp);
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage2 应答失败(player={})", sp.getGameProfile().getName());
            }
        });
    }

    // ------------------------------------------------------------------ 客户端应答构建

    /** 客户端应答 stage1（out 负责把结果包发回服务器）。 */
    public static void respondStage1(String json, Consumer<CustomPacketPayload> out) {
        try {
            Stage1Request req = Json.fromJson(json, Stage1Request.class);
            Stage1Response resp = new Stage1Response();
            resp.protocol = Mac.PROTOCOL_VERSION;
            resp.loaderType = Mac.LOADER_FABRIC;
            resp.loaderVersion = localVersion(Mac.LOADER_FABRIC);
            resp.macVersion = localVersion(Mac.MOD_ID);
            Map<String, String> local = localVersions();
            resp.modVersions = new HashMap<>();
            List<String> ids = req == null || req.requiredIds == null ? List.of() : req.requiredIds;
            for (String rid : ids) {
                resp.modVersions.put(rid, local.get(rid)); // 未安装 => null => 视为缺失
            }
            out.accept(new Stage1ResponsePayload(Json.toJson(resp)));
        } catch (Exception e) {
            Mac.logger().error("构建 stage1 应答失败", e);
        }
    }

    /** 客户端应答 stage2（上报完整 Mod 列表）。 */
    public static void respondStage2(String json, Consumer<CustomPacketPayload> out) {
        try {
            Stage2Response resp = new Stage2Response();
            resp.mods = new ArrayList<>();
            for (Map.Entry<String, String> e : localVersions().entrySet()) {
                resp.mods.add(new ClientMod(e.getKey(), e.getValue()));
            }
            out.accept(new Stage2ResponsePayload(Json.toJson(resp)));
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
    // 消息载体（每个 = 一个协议消息种类；负载为 JSON 字符串）
    // ==================================================================

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Mac.MOD_ID, path);
    }

    private static FriendlyByteBuf fb(ByteBuf buf) {
        return buf instanceof FriendlyByteBuf f ? f : new FriendlyByteBuf(buf);
    }

    /** S2C：登录阶段请求。 */
    public record Stage1RequestPayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Stage1RequestPayload> TYPE =
                new CustomPacketPayload.Type<>(id("s1_req"));
        public static final StreamCodec<ByteBuf, Stage1RequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> fb(buf).writeUtf(p.json()),
                buf -> new Stage1RequestPayload(fb(buf).readUtf(32767)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：登录阶段应答。 */
    public record Stage1ResponsePayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Stage1ResponsePayload> TYPE =
                new CustomPacketPayload.Type<>(id("s1_resp"));
        public static final StreamCodec<ByteBuf, Stage1ResponsePayload> CODEC = StreamCodec.of(
                (buf, p) -> fb(buf).writeUtf(p.json()),
                buf -> new Stage1ResponsePayload(fb(buf).readUtf(32767)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C：进入游戏阶段请求。 */
    public record Stage2RequestPayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Stage2RequestPayload> TYPE =
                new CustomPacketPayload.Type<>(id("s2_req"));
        public static final StreamCodec<ByteBuf, Stage2RequestPayload> CODEC = StreamCodec.of(
                (buf, p) -> fb(buf).writeUtf(p.json()),
                buf -> new Stage2RequestPayload(fb(buf).readUtf(32767)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：进入游戏阶段应答（完整 Mod 列表）。 */
    public record Stage2ResponsePayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Stage2ResponsePayload> TYPE =
                new CustomPacketPayload.Type<>(id("s2_resp"));
        public static final StreamCodec<ByteBuf, Stage2ResponsePayload> CODEC = StreamCodec.of(
                (buf, p) -> fb(buf).writeUtf(p.json()),
                buf -> new Stage2ResponsePayload(fb(buf).readUtf(32767)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
