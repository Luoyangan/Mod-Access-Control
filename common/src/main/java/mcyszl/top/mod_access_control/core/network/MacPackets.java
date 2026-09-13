// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.network;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分阶段握手协议的消息定义（与加载器无关的纯数据结构）。
 *
 * <ul>
 *   <li><b>登录阶段（Stage 1）</b>：服务端下发 {@link Stage1Request}（只需回传协议、
 *       加载器与“必需 Mod 版本信息”，数据量最小），客户端以 {@link Stage1Response} 回应。</li>
 *   <li><b>进入游戏阶段（Stage 2）</b>：服务端下发 {@link Stage2Request}，客户端上报
 *       {@link Stage2Response}（完整 Mod 列表），用于白名单/黑名单与严格校验。</li>
 * </ul>
 */
public final class MacPackets {

    /** 消息种类（适配层据此解析 envelope 中的 JSON）。 */
    public static final String KIND_STAGE1_REQUEST = "stage1_req";
    public static final String KIND_STAGE1_RESPONSE = "stage1_resp";
    public static final String KIND_STAGE2_REQUEST = "stage2_req";
    public static final String KIND_STAGE2_RESPONSE = "stage2_resp";

    private MacPackets() {
    }

    /** 登录阶段请求（S→C）：告知本机协议版本，并请求必需 Mod 的存在/版本信息。 */
    public static class Stage1Request {
        public int protocol;
        @SerializedName("check_mode")
        public String checkMode;
        /** 需要客户端上报的必需 mod id 列表。 */
        @SerializedName("required_ids")
        public List<String> requiredIds = new ArrayList<>();
    }

    /** 登录阶段响应（C→S）：仅包含最小必要信息。 */
    public static class Stage1Response {
        public int protocol;
        @SerializedName("loader_type")
        public String loaderType;
        @SerializedName("loader_version")
        public String loaderVersion;
        @SerializedName("mac_version")
        public String macVersion;
        /** 客户端界面语言（如 {@code zh_cn} / {@code en_us}），用于服务端按玩家语言渲染文案。 */
        public String language;
        /** 每个必需 mod id -> 客户端本地版本；不存在则值为 null。 */
        @SerializedName("mod_versions")
        public Map<String, String> modVersions;
    }

    /** 进入游戏阶段请求（S→C）：请求完整 Mod 列表。 */
    public static class Stage2Request {
        /** 预留：完整模式标记（后续可扩展）。 */
        public boolean full = true;
    }

    /** 客户端单个 Mod 的信息。 */
    public static class ClientMod {
        public String id;
        public String version;

        public ClientMod() {
        }

        public ClientMod(String id, String version) {
            this.id = id;
            this.version = version;
        }
    }

    /** 进入游戏阶段响应（C→S）：完整 Mod 标识 + 版本。 */
    public static class Stage2Response {
        public List<ClientMod> mods = new ArrayList<>();
    }
}
