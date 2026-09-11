// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge.net;

import io.netty.buffer.ByteBuf;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.network.Json;
import mcyszl.top.mod_access_control.core.network.MacPackets;
import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Request;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Response;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage2Response;
import mcyszl.top.mod_access_control.forge.Holder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Forge 网络适配层（MC 1.12.2 / Forge 14.23.5.x，pre-Brigadier 的
 * SimpleNetworkWrapper + IMessage/IIMessageHandler 体系）。
 *
 * <p>协议：四个逻辑消息（stage1_req / stage1_resp / stage2_req / stage2_resp），
 * 每个承载一段 JSON 字符串（长度前缀 + UTF-8 字节，避开 32767 字符限制的
 * writeString），挂载在同一条自定义通道上。</p>
 *
 * <p>“客户端是否安装本模组”通过 FML 握手期交换的远端模组表
 * （{@link NetworkDispatcher#getModList()}）判定，与协议版本校验解耦。</p>
 */
public final class ForgeNet {

    private static SimpleNetworkWrapper channel;

    private ForgeNet() {
    }

    /** 在 mod preInit 阶段调用：创建通道并注册四个消息。 */
    public static void init() {
        // 1.12.2 原版 SPacketCustomPayload 对通道名有 20 字符上限（readString(20)），
        // 因此直接用 mod id（18 字符）作为通道名，不带 "|path" 后缀。
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(Mac.MOD_ID);
        int id = 0;
        // C2S：登录阶段应答 / 进入游戏阶段应答
        channel.registerMessage(Stage1RespHandler.class, Stage1RespPacket.class, id++, Side.SERVER);
        channel.registerMessage(Stage2RespHandler.class, Stage2RespPacket.class, id++, Side.SERVER);
        // S2C：登录阶段请求 / 进入游戏阶段请求
        channel.registerMessage(Stage1ReqHandler.class, Stage1ReqPacket.class, id++, Side.CLIENT);
        channel.registerMessage(Stage2ReqHandler.class, Stage2ReqPacket.class, id++, Side.CLIENT);
        Mac.logger().info("Forge 网络通道已注册 (channel={})", Mac.MOD_ID);
    }

    /** 服务端向单个玩家发送一条服务端请求。 */
    public static void sendToPlayer(EntityPlayerMP player, String kind, String payloadJson) {
        if (channel == null || player == null) {
            return;
        }
        try {
            if (MacPackets.KIND_STAGE1_REQUEST.equals(kind)) {
                channel.sendTo(new Stage1ReqPacket(payloadJson), player);
            } else if (MacPackets.KIND_STAGE2_REQUEST.equals(kind)) {
                channel.sendTo(new Stage2ReqPacket(payloadJson), player);
            } else {
                Mac.logger().warn("未知的服务端消息种类，忽略: {}", kind);
            }
        } catch (Exception e) {
            Mac.logger().error("发送消息 {} 到 {} 失败", kind, player.getGameProfile().getName());
        }
    }

    /** 客户端是否安装了本模组：查 FML 握手期登记的远端模组表。 */
    public static boolean remoteHasChannel(EntityPlayerMP player) {
        try {
            NetworkManager manager = player.connection.netManager;
            NetworkDispatcher dispatcher = NetworkDispatcher.get(manager);
            if (dispatcher == null) {
                return false;
            }
            Map<String, String> mods = dispatcher.getModList();
            return mods != null && mods.containsKey(Mac.MOD_ID);
        } catch (Exception e) {
            return false;
        }
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
        List<String> ids = req.requiredIds == null ? new ArrayList<String>() : req.requiredIds;
        for (String rid : ids) {
            resp.modVersions.put(rid, local.get(rid)); // 未安装 => null => 视为缺失
        }
        sendBack(new Stage1RespPacket(Json.toJson(resp)));
    }

    private static void respondStage2() {
        Stage2Response resp = new Stage2Response();
        resp.mods = new ArrayList<>();
        for (Map.Entry<String, String> e : localVersions().entrySet()) {
            resp.mods.add(new ClientMod(e.getKey(), e.getValue()));
        }
        sendBack(new Stage2RespPacket(Json.toJson(resp)));
    }

    /** 客户端经同一条通道向服务器回送消息。 */
    private static void sendBack(IMessage msg) {
        if (channel != null) {
            channel.sendToServer(msg);
        }
    }

    /** 客户端本地 mod id -> 版本 全量映射（阶段应答用，服务端会过滤基础设施 mod）。 */
    private static Map<String, String> localVersions() {
        Map<String, String> map = new HashMap<>();
        try {
            for (ModContainer container : Loader.instance().getActiveModList()) {
                try {
                    map.put(container.getModId(), String.valueOf(container.getVersion()));
                } catch (Exception ignore) {
                    // 单个 mod 版本读取失败不影响整体
                }
            }
        } catch (Exception e) {
            Mac.logger().warn("读取本地 Mod 列表失败: {}", e.toString());
        }
        return map;
    }

    private static String localVersion(String modId) {
        try {
            ModContainer container = Loader.instance().getIndexedModList().get(modId);
            return container == null ? null : String.valueOf(container.getVersion());
        } catch (Exception e) {
            return null;
        }
    }

    /** 切换到客户端主线程执行应答（网络线程 -> 主线程）。 */
    private static void onClientThread(final String payload, final Runnable task) {
        IThreadListener scheduler = Minecraft.getMinecraft();
        scheduler.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    Mac.logger().error("处理服务端请求失败: {}", payload == null ? "" : payload);
                }
            }
        });
    }

    // ==================================================================
    // 消息载体（负载为 JSON 字符串：4 字节长度 + UTF-8 字节）
    // ==================================================================

    private static void writePayload(ByteBuf buf, String json) {
        byte[] data = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    private static String readPayload(ByteBuf buf) {
        int len = buf.readInt();
        byte[] data = new byte[len];
        buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /** S2C：登录阶段请求。 */
    public static final class Stage1ReqPacket implements IMessage {
        public String json;

        public Stage1ReqPacket() {
        }

        public Stage1ReqPacket(String json) {
            this.json = json;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            writePayload(buf, json);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            json = readPayload(buf);
        }
    }

    /** S2C：进入游戏阶段请求。 */
    public static final class Stage2ReqPacket implements IMessage {
        public String json;

        public Stage2ReqPacket() {
        }

        public Stage2ReqPacket(String json) {
            this.json = json;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            writePayload(buf, json);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            json = readPayload(buf);
        }
    }

    /** C2S：登录阶段应答。 */
    public static final class Stage1RespPacket implements IMessage {
        public String json;

        public Stage1RespPacket() {
        }

        public Stage1RespPacket(String json) {
            this.json = json;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            writePayload(buf, json);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            json = readPayload(buf);
        }
    }

    /** C2S：进入游戏阶段应答（完整 Mod 列表）。 */
    public static final class Stage2RespPacket implements IMessage {
        public String json;

        public Stage2RespPacket() {
        }

        public Stage2RespPacket(String json) {
            this.json = json;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            writePayload(buf, json);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            json = readPayload(buf);
        }
    }

    // ==================================================================
    // 消息处理器
    // ==================================================================

    /** S2C 处理：登录阶段请求（仅客户端执行）。 */
    public static final class Stage1ReqHandler implements IMessageHandler<Stage1ReqPacket, IMessage> {
        @Override
        public IMessage onMessage(final Stage1ReqPacket msg, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            onClientThread(msg.json, new Runnable() {
                @Override
                public void run() {
                    respondStage1(msg.json);
                }
            });
            return null;
        }
    }

    /** S2C 处理：进入游戏阶段请求（仅客户端执行）。 */
    public static final class Stage2ReqHandler implements IMessageHandler<Stage2ReqPacket, IMessage> {
        @Override
        public IMessage onMessage(final Stage2ReqPacket msg, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            onClientThread(msg.json, new Runnable() {
                @Override
                public void run() {
                    respondStage2();
                }
            });
            return null;
        }
    }

    /** C2S 处理：登录阶段应答（仅服务端执行，已在主线程）。 */
    public static final class Stage1RespHandler implements IMessageHandler<Stage1RespPacket, IMessage> {
        @Override
        public IMessage onMessage(Stage1RespPacket msg, MessageContext ctx) {
            if (ctx.side != Side.SERVER) {
                return null;
            }
            EntityPlayerMP sender = ctx.getServerHandler().player;
            try {
                Stage1Response resp = Json.fromJson(msg.json, Stage1Response.class);
                if (resp != null) {
                    Holder.service().receiveStage1(sender.getUniqueID().toString(), resp);
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage1 应答失败(player={})", sender.getGameProfile().getName());
            }
            return null;
        }
    }

    /** C2S 处理：进入游戏阶段应答（仅服务端执行，已在主线程）。 */
    public static final class Stage2RespHandler implements IMessageHandler<Stage2RespPacket, IMessage> {
        @Override
        public IMessage onMessage(Stage2RespPacket msg, MessageContext ctx) {
            if (ctx.side != Side.SERVER) {
                return null;
            }
            EntityPlayerMP sender = ctx.getServerHandler().player;
            try {
                Stage2Response resp = Json.fromJson(msg.json, Stage2Response.class);
                if (resp != null) {
                    Holder.service().receiveStage2(sender.getUniqueID().toString(), resp);
                }
            } catch (Exception e) {
                Mac.logger().error("解析 stage2 应答失败(player={})", sender.getGameProfile().getName());
            }
            return null;
        }
    }
}
