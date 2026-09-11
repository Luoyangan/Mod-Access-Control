// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.neoforge.net;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.network.Json;
import mcyszl.top.mod_access_control.core.network.MacPackets;
import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Request;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Response;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage2Response;
import mcyszl.top.mod_access_control.neoforge.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NeoForge 网络适配层（mod 事件总线自动注册）。
 *
 * <p>协议：四个逻辑消息（stage1_req / stage1_resp / stage2_req / stage2_resp），
 * 每个承载一段 JSON 字符串；全部挂在 {@link #registerPayloadHandlers} 注册的
 * PLAY 相位 payload 上（“登录阶段”与“进入游戏阶段”均在玩家进入世界前后依次完成）。</p>
 *
 * <p>为满足“三加载器协议一致”，JSON 的结构由 common 核心定义；本类只负责
 * 加/解码与线程切换（全部经 {@code context.enqueueWork} 切回主线程执行），
 * 客户端自动应答逻辑也集中于此。</p>
 */
@EventBusSubscriber(modid = Mac.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoNet {

    private NeoNet() {
    }

    // ------------------------------------------------------------------ 注册

    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Mac.MOD_ID)
                .versioned(Integer.toString(Mac.PROTOCOL_VERSION))
                .optional();
        // S2C：登录阶段请求（最小数据）
        registrar.playToClient(Stage1RequestS2C.TYPE, Stage1RequestS2C.STREAM_CODEC,
                NeoNet::handleStage1Req);
        // C2S：登录阶段应答
        registrar.playToServer(Stage1ResponseC2S.TYPE, Stage1ResponseC2S.STREAM_CODEC,
                NeoNet::handleStage1Resp);
        // S2C：进入游戏阶段请求（完整列表）
        registrar.playToClient(Stage2RequestS2C.TYPE, Stage2RequestS2C.STREAM_CODEC,
                NeoNet::handleStage2Req);
        // C2S：进入游戏阶段应答
        registrar.playToServer(Stage2ResponseC2S.TYPE, Stage2ResponseC2S.STREAM_CODEC,
                NeoNet::handleStage2Resp);
        Mac.logger().info("NeoForge 网络通道已注册 (channel={}:{})", Mac.MOD_ID, Mac.CHANNEL_PATH);
    }

    /** 服务端向单个玩家发送一条服务端请求（须在主线程调用）。 */
    public static void sendToPlayer(ServerPlayer player, String kind, String payloadJson) {
        if (player == null) {
            return;
        }
        try {
            if (MacPackets.KIND_STAGE1_REQUEST.equals(kind)) {
                PacketDistributor.sendToPlayer(player, new Stage1RequestS2C(payloadJson));
            } else if (MacPackets.KIND_STAGE2_REQUEST.equals(kind)) {
                PacketDistributor.sendToPlayer(player, new Stage2RequestS2C(payloadJson));
            } else {
                Mac.logger().warn("未知的服务端消息种类，忽略: {}", kind);
            }
        } catch (Exception e) {
            Mac.logger().error("发送消息 {} 到 {} 失败", kind, player.getGameProfile().getName());
        }
    }

    /** 远端（客户端）是否携带本模组的通道（即是否安装了本模组）。 */
    public static boolean remoteHasChannel(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            if (!(player.connection instanceof ICommonPacketListener listener)) {
                return false;
            }
            // 兼容通道 id 两种可能形态（registrar 通道 id 或 payload 类型 id）
            return listener.hasChannel(ResourceLocation.fromNamespaceAndPath(Mac.MOD_ID, Mac.CHANNEL_PATH))
                    || listener.hasChannel(ResourceLocation.fromNamespaceAndPath(Mac.MOD_ID, "stage1_req"));
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 客户端应答

    private static void handleStage1Req(Stage1RequestS2C msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Stage1Request req = Json.fromJson(msg.json(), Stage1Request.class);
                Stage1Response resp = new Stage1Response();
                resp.protocol = Mac.PROTOCOL_VERSION;
                resp.loaderType = Mac.LOADER_NEOFORGE;
                resp.loaderVersion = localVersion(Mac.LOADER_NEOFORGE);
                resp.macVersion = localVersion(Mac.MOD_ID);
                Map<String, String> local = localVersions();
                resp.modVersions = new HashMap<>();
                List<String> ids = req == null || req.requiredIds == null ? List.of() : req.requiredIds;
                for (String rid : ids) {
                    resp.modVersions.put(rid, local.get(rid)); // 未安装 => null => 视为缺失
                }
                PacketDistributor.sendToServer(new Stage1ResponseC2S(Json.toJson(resp)));
            } catch (Exception e) {
                Mac.logger().error("处理登录阶段请求失败", e);
            }
        });
    }

    private static void handleStage2Req(Stage2RequestS2C msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                Stage2Response resp = new Stage2Response();
                resp.mods = new ArrayList<>();
                for (Map.Entry<String, String> e : localVersions().entrySet()) {
                    resp.mods.add(new ClientMod(e.getKey(), e.getValue()));
                }
                PacketDistributor.sendToServer(new Stage2ResponseC2S(Json.toJson(resp)));
            } catch (Exception e) {
                Mac.logger().error("处理完整列表请求失败", e);
            }
        });
    }

    // ------------------------------------------------------------------ 服务端接收

    private static void handleStage1Resp(Stage1ResponseC2S msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                if (ctx.player() instanceof ServerPlayer sp) {
                    Stage1Response resp = Json.fromJson(msg.json(), Stage1Response.class);
                    if (resp != null) {
                        Holder.service().receiveStage1(sp.getStringUUID(), resp);
                    }
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage1 应答失败");
            }
        });
    }

    private static void handleStage2Resp(Stage2ResponseC2S msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                if (ctx.player() instanceof ServerPlayer sp) {
                    Stage2Response resp = Json.fromJson(msg.json(), Stage2Response.class);
                    if (resp != null) {
                        Holder.service().receiveStage2(sp.getStringUUID(), resp);
                    }
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage2 应答失败");
            }
        });
    }

    // ------------------------------------------------------------------ 本地信息

    /** 客户端本地 mod id -> 版本 全量映射（阶段应答用，含基础设施 mod，服务端会过滤）。 */
    private static Map<String, String> localVersions() {
        Map<String, String> map = new HashMap<>();
        try {
            for (IModInfo info : ModList.get().getMods()) {
                try {
                    map.put(info.getModId(), String.valueOf(info.getVersion()));
                } catch (Exception ignore) {
                    // 单个 mod 版本读取失败不影响整体
                }
            }
        } catch (Exception e) {
            Mac.logger().warn("读取本地 ModList 失败: {}", e.toString());
        }
        return map;
    }

    private static String localVersion(String modId) {
        try {
            return ModList.get().getModContainerById(modId)
                    .map(c -> String.valueOf(c.getModInfo().getVersion()))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================================================================
    // 消息载体（每个 = 一个协议消息种类；负载为 JSON 字符串）
    // ==================================================================

    /** S2C：登录阶段请求。 */
    public record Stage1RequestS2C(String json) implements CustomPacketPayload {
        public static final Type<Stage1RequestS2C> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mac.MOD_ID, "stage1_req"));
        public static final StreamCodec<FriendlyByteBuf, Stage1RequestS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> buf.writeUtf(msg.json()),
                buf -> new Stage1RequestS2C(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：登录阶段应答。 */
    public record Stage1ResponseC2S(String json) implements CustomPacketPayload {
        public static final Type<Stage1ResponseC2S> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mac.MOD_ID, "stage1_resp"));
        public static final StreamCodec<FriendlyByteBuf, Stage1ResponseC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> buf.writeUtf(msg.json()),
                buf -> new Stage1ResponseC2S(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S2C：进入游戏阶段请求。 */
    public record Stage2RequestS2C(String json) implements CustomPacketPayload {
        public static final Type<Stage2RequestS2C> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mac.MOD_ID, "stage2_req"));
        public static final StreamCodec<FriendlyByteBuf, Stage2RequestS2C> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> buf.writeUtf(msg.json()),
                buf -> new Stage2RequestS2C(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** C2S：进入游戏阶段应答（完整 Mod 列表）。 */
    public record Stage2ResponseC2S(String json) implements CustomPacketPayload {
        public static final Type<Stage2ResponseC2S> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Mac.MOD_ID, "stage2_resp"));
        public static final StreamCodec<FriendlyByteBuf, Stage2ResponseC2S> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> buf.writeUtf(msg.json()),
                buf -> new Stage2ResponseC2S(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
