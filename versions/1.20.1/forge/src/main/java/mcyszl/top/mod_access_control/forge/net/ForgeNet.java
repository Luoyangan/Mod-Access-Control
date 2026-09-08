package mcyszl.top.mod_access_control.forge.net;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.network.Json;
import mcyszl.top.mod_access_control.core.network.MacPackets;
import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Request;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Response;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage2Response;
import mcyszl.top.mod_access_control.forge.Holder;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Forge 网络适配层（MC 1.20.1 / Forge 47.x）。
 *
 * <p>协议：四个逻辑消息（stage1_req / stage1_resp / stage2_req / stage2_resp），
 * 每个承载一段 JSON 字符串；全部挂在同一条 SimpleChannel 的 PLAY 相位上
 * （“登录阶段”与“进入游戏阶段”均在玩家进入世界前后依次完成）。</p>
 *
 * <p>1.20.1 使用旧版 SimpleChannel 体系（{@link NetworkRegistry#newSimpleChannel} +
 * {@code SimpleChannel.messageBuilder} + {@link NetworkEvent}），与 1.21.1 的
 * ChannelBuilder/CustomPayloadEvent 不同；JSON 结构仍由 common 核心定义。</p>
 */
public final class ForgeNet {

    private static SimpleChannel channel;

    private ForgeNet() {
    }

    /** 在 {@code @Mod} 构造阶段调用：注册通道与四个消息。 */
    public static void init() {
        // 版本谓词恒真：允许客户端“未安装本模组”（无通道），是否安装由
        // remoteHasChannel 在登录阶段单独判定；协议版本一致性由 common 握手校验。
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(Mac.MOD_ID, Mac.CHANNEL_PATH),
                () -> String.valueOf(Mac.PROTOCOL_VERSION),
                v -> true,
                v -> true);

        int id = 0;
        // S2C：登录阶段请求（最小数据）
        channel.messageBuilder(Stage1RequestS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Stage1RequestS2C::encode)
                .decoder(Stage1RequestS2C::decode)
                .consumerMainThread(Stage1RequestS2C::handle)
                .add();
        // C2S：登录阶段应答
        channel.messageBuilder(Stage1ResponseC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Stage1ResponseC2S::encode)
                .decoder(Stage1ResponseC2S::decode)
                .consumerMainThread(Stage1ResponseC2S::handle)
                .add();
        // S2C：进入游戏阶段请求（完整列表）
        channel.messageBuilder(Stage2RequestS2C.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Stage2RequestS2C::encode)
                .decoder(Stage2RequestS2C::decode)
                .consumerMainThread(Stage2RequestS2C::handle)
                .add();
        // C2S：进入游戏阶段应答
        channel.messageBuilder(Stage2ResponseC2S.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Stage2ResponseC2S::encode)
                .decoder(Stage2ResponseC2S::decode)
                .consumerMainThread(Stage2ResponseC2S::handle)
                .add();
        Mac.logger().info("Forge 网络通道已注册 (channel={}:{})", Mac.MOD_ID, Mac.CHANNEL_PATH);
    }

    /** 服务端向单个玩家发送一条服务端请求。 */
    public static void sendToPlayer(ServerPlayer player, String kind, String payloadJson) {
        if (channel == null || player == null) {
            return;
        }
        try {
            if (MacPackets.KIND_STAGE1_REQUEST.equals(kind)) {
                channel.send(PacketDistributor.PLAYER.with(() -> player), new Stage1RequestS2C(payloadJson));
            } else if (MacPackets.KIND_STAGE2_REQUEST.equals(kind)) {
                channel.send(PacketDistributor.PLAYER.with(() -> player), new Stage2RequestS2C(payloadJson));
            } else {
                Mac.logger().warn("未知的服务端消息种类，忽略: {}", kind);
            }
        } catch (Exception e) {
            Mac.logger().error("发送消息 {} 到 {} 失败", kind, player.getGameProfile().getName());
        }
    }

    /** 远端（客户端）是否携带本模组的通道（即是否安装了本模组）。 */
    public static boolean remoteHasChannel(Connection connection) {
        return channel != null && connection != null && channel.isRemotePresent(connection);
    }

    // ------------------------------------------------------------------ 客户端应答

    private static void respondStage1(String json) {
        Stage1Request req = Json.fromJson(json, Stage1Request.class);
        Stage1Response resp = new Stage1Response();
        resp.protocol = Mac.PROTOCOL_VERSION;
        resp.loaderType = Mac.LOADER_FORGE;
        resp.loaderVersion = localVersion(Mac.LOADER_FORGE);
        resp.macVersion = localVersion(Mac.MOD_ID);
        Map<String, String> local = localVersions();
        resp.modVersions = new HashMap<>();
        List<String> ids = req.requiredIds == null ? List.of() : req.requiredIds;
        for (String rid : ids) {
            resp.modVersions.put(rid, local.get(rid)); // 未安装 => null => 视为缺失
        }
        sendBack(new Stage1ResponseC2S(Json.toJson(resp)));
    }

    private static void respondStage2() {
        Stage2Response resp = new Stage2Response();
        resp.mods = new ArrayList<>();
        for (Map.Entry<String, String> e : localVersions().entrySet()) {
            resp.mods.add(new ClientMod(e.getKey(), e.getValue()));
        }
        sendBack(new Stage2ResponseC2S(Json.toJson(resp)));
    }

    /** 客户端经同一条 SimpleChannel 向服务器回送消息。 */
    private static void sendBack(Object msg) {
        if (channel != null) {
            channel.sendToServer(msg);
        }
    }

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
    public static final class Stage1RequestS2C {
        public final String json;

        public Stage1RequestS2C(String json) {
            this.json = json;
        }

        static void encode(Stage1RequestS2C m, FriendlyByteBuf buf) {
            buf.writeUtf(m.json);
        }

        static Stage1RequestS2C decode(FriendlyByteBuf buf) {
            return new Stage1RequestS2C(buf.readUtf());
        }

        static void handle(Stage1RequestS2C m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.setPacketHandled(true);
            if (!ctx.getDirection().getReceptionSide().isClient()) {
                return;
            }
            try {
                respondStage1(m.json);
            } catch (Exception e) {
                Mac.logger().error("处理登录阶段请求失败", e);
            }
        }
    }

    /** C2S：登录阶段应答。 */
    public static final class Stage1ResponseC2S {
        public final String json;

        public Stage1ResponseC2S(String json) {
            this.json = json;
        }

        static void encode(Stage1ResponseC2S m, FriendlyByteBuf buf) {
            buf.writeUtf(m.json);
        }

        static Stage1ResponseC2S decode(FriendlyByteBuf buf) {
            return new Stage1ResponseC2S(buf.readUtf());
        }

        static void handle(Stage1ResponseC2S m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.setPacketHandled(true);
            if (!ctx.getDirection().getReceptionSide().isServer()) {
                return;
            }
            ServerPlayer sp = ctx.getSender();
            if (sp == null) {
                return;
            }
            try {
                Stage1Response resp = Json.fromJson(m.json, Stage1Response.class);
                if (resp != null) {
                    Holder.service().receiveStage1(sp.getStringUUID(), resp);
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage1 应答失败(player={})", sp.getGameProfile().getName());
            }
        }
    }

    /** S2C：进入游戏阶段请求。 */
    public static final class Stage2RequestS2C {
        public final String json;

        public Stage2RequestS2C(String json) {
            this.json = json;
        }

        static void encode(Stage2RequestS2C m, FriendlyByteBuf buf) {
            buf.writeUtf(m.json);
        }

        static Stage2RequestS2C decode(FriendlyByteBuf buf) {
            return new Stage2RequestS2C(buf.readUtf());
        }

        static void handle(Stage2RequestS2C m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.setPacketHandled(true);
            if (!ctx.getDirection().getReceptionSide().isClient()) {
                return;
            }
            try {
                respondStage2();
            } catch (Exception e) {
                Mac.logger().error("处理完整列表请求失败", e);
            }
        }
    }

    /** C2S：进入游戏阶段应答（完整 Mod 列表）。 */
    public static final class Stage2ResponseC2S {
        public final String json;

        public Stage2ResponseC2S(String json) {
            this.json = json;
        }

        static void encode(Stage2ResponseC2S m, FriendlyByteBuf buf) {
            buf.writeUtf(m.json);
        }

        static Stage2ResponseC2S decode(FriendlyByteBuf buf) {
            return new Stage2ResponseC2S(buf.readUtf());
        }

        static void handle(Stage2ResponseC2S m, Supplier<NetworkEvent.Context> sup) {
            NetworkEvent.Context ctx = sup.get();
            ctx.setPacketHandled(true);
            if (!ctx.getDirection().getReceptionSide().isServer()) {
                return;
            }
            ServerPlayer sp = ctx.getSender();
            if (sp == null) {
                return;
            }
            try {
                Stage2Response resp = Json.fromJson(m.json, Stage2Response.class);
                if (resp != null) {
                    Holder.service().receiveStage2(sp.getStringUUID(), resp);
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage2 应答失败(player={})", sp.getGameProfile().getName());
            }
        }
    }
}
