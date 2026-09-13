// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.api;

import java.util.Map;

/**
 * Mod Access Control 公共 API（v1.1）。
 *
 * <p>供其他 mod 以本模组为前置（hard dependency）时调用：查询玩家校验状态、
 * 读取客户端 Mod 清单、管理豁免名单、订阅违规事件。</p>
 *
 * <p>用法：在自己 mod 的元数据中声明对 {@code mod_access_control} 的依赖
 * （Fabric: fabric.mod.json {@code depends}；Forge/NeoForge: mods.toml 依赖项），
 * 然后直接调用本类的静态方法。所有方法在核心未就绪时安全返回默认值
 * （{@code available()} 可先行判断）。</p>
 */
public final class MacApi {

    /** 违规事件监听器：玩家被拦截（或试运行中记录违规）时回调。 */
    public interface ViolationListener {
        /**
         * @param uuid       玩家 UUID（预拦截场景可能为空串）
         * @param playerName 玩家名
         * @param reason     违规类型（DisconnectReason 枚举名，如 POLICY_VIOLATION）
         * @param summary    服务端语言的违规摘要
         * @param kicked     是否实际断开（试运行模式下为 false）
         */
        void onViolation(String uuid, String playerName, String reason, String summary, boolean kicked);
    }

    /** 平台实现接口（由各加载器适配层在启动时注入，外部开发者无需关心）。 */
    public interface Impl {
        boolean isVerified(String uuidOrName);

        Map<String, String> getSessionMods(String uuidOrName);

        boolean isExempt(String uuid, String playerName, boolean op);

        boolean addExempt(String entry);

        boolean removeExempt(String entry);

        boolean addViolationListener(ViolationListener listener);

        String modVersion();

        int protocolVersion();

        String loaderType();
    }

    private static volatile Impl impl;

    /** 由适配层在模组初始化时调用（外部开发者不要调用）。 */
    public static void install(Impl platformImpl) {
        impl = platformImpl;
    }

    /** 核心是否已就绪（服务端启动完成后为 true）。 */
    public static boolean available() {
        return impl != null;
    }

    /** 指定玩家（uuid 或玩家名）当前会话是否已通过全部准入校验。 */
    public static boolean isVerified(String uuidOrName) {
        Impl i = impl;
        return i != null && i.isVerified(uuidOrName);
    }

    /** 查询玩家（在线会话）的完整 mod 清单（id -> 版本）；无会话返回空映射。 */
    public static Map<String, String> getSessionMods(String uuidOrName) {
        Impl i = impl;
        return i == null ? java.util.Collections.<String, String>emptyMap() : i.getSessionMods(uuidOrName);
    }

    /** 该玩家是否命中豁免名单（exemptPlayers / exemptOps）。 */
    public static boolean isExempt(String uuid, String playerName, boolean op) {
        Impl i = impl;
        return i != null && i.isExempt(uuid, playerName, op);
    }

    /** 新增豁免条目（玩家名或 {@code uuid:} 前缀 UUID）；已存在返回 false。 */
    public static boolean addExempt(String entry) {
        Impl i = impl;
        return i != null && i.addExempt(entry);
    }

    /** 移除豁免条目（忽略大小写）；不存在返回 false。 */
    public static boolean removeExempt(String entry) {
        Impl i = impl;
        return i != null && i.removeExempt(entry);
    }

    /** 注册违规监听器；重复注册同一实例返回 false。 */
    public static boolean addViolationListener(ViolationListener listener) {
        Impl i = impl;
        return i != null && i.addViolationListener(listener);
    }

    /** 本模组版本字符串（未就绪返回空串）。 */
    public static String modVersion() {
        Impl i = impl;
        return i == null ? "" : i.modVersion();
    }

    /** 握手协议版本（未就绪返回 -1）。 */
    public static int protocolVersion() {
        Impl i = impl;
        return i == null ? -1 : i.protocolVersion();
    }

    /** 当前加载器标识（forge / fabric / neoforge；未就绪返回空串）。 */
    public static String loaderType() {
        Impl i = impl;
        return i == null ? "" : i.loaderType();
    }

    private MacApi() {
    }
}
