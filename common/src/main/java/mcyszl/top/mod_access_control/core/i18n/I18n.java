// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.i18n;

import mcyszl.top.mod_access_control.core.Mac;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 服务端文案双语言字典（zh_cn / en_us，代码内置、不走文件）。
 *
 * <p>覆盖：命令输出 / 服务端日志 / 管理广播 / 违规摘要 / 玩家踢出消息。
 * 踢出消息统一由服务端按玩家客户端语言渲染（{@link #trTo(String, String, Object...)}），
 * 客户端界面与服务端日志显示一致，不再走客户端翻译键解析。</p>
 *
 * <p>语言来源：配置 {@code language: auto|zh_cn|en_us}；
 * {@code auto} 按服务器系统 Locale（zh 开头判 zh_cn，否则 en_us）。</p>
 */
public final class I18n {

    public static final String ZH_CN = "zh_cn";
    public static final String EN_US = "en_us";

    private static volatile String locale = ZH_CN;
    /** 调用线程内的语言作用域（命令回显 / 踢出文案按执行者或玩家的客户端语言）。 */
    private static final ThreadLocal<String> SCOPED = new ThreadLocal<>();
    private static final Map<String, String> ZH = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        // ---------------- 踢出标题（服务端按玩家客户端语言渲染为纯文本下发）
        put("mac.kick.header.no_client_mod",
                "连接被拒绝：客户端未安装「Mod Access Control / 模组准入控制」。\n本服务器要求所有客户端安装该模组以完成准入校验。",
                "Connection refused: the client does not have \"Mod Access Control\" installed.\nThis server requires all clients to install this mod for access verification.");
        put("mac.kick.header.protocol",
                "准入协议版本不兼容（服务器={}, 客户端={}）。\n请从服务器指定的渠道更新模组后再加入。",
                "Access protocol version mismatch (server={}, client={}).\nPlease update the mod from the channel specified by the server.");
        put("mac.kick.header.loader",
                "加载器不兼容：服务器要求 {}，你的客户端为 {}。\n请使用与服务器一致的 Mod 加载器。",
                "Incompatible mod loader: the server requires {}, your client uses {}.\nPlease use the same mod loader as the server.");
        put("mac.kick.header.timeout",
                "准入校验超时。若你已安装本模组，请重启游戏后重试；若仍未解决，请联系服务器管理员。",
                "Access verification timed out. If the mod is installed, restart the game and retry; if it persists, contact the server administrator.");
        put("mac.kick.header.violation",
                "准入校验未通过，客户端 Mod 列表存在以下问题：",
                "Access verification failed. Problems found in your client mod list:");
        put("mac.kick.header.mac_version",
                "连接被拒绝：本模组版本不在服务器允许列表内。\n服务器要求的版本: {}；你的版本: {}。",
                "Connection refused: this mod version is not on the server's allow list.\nAllowed versions: {}; your version: {}.");

        // ---------------- 踢出底部提示区（客户端可见）
        put("mac.kick.hint.generic",
                "提示：请按服务器要求安装正确的模组版本后重新加入。",
                "Hint: install the correct mod versions required by the server and rejoin.");
        put("mac.kick.hint.timeout",
                "提示：如多次失败，请尝试更新游戏/模组版本后重连。",
                "Hint: if this keeps failing, try updating your game/mod version and reconnect.");
        put("mac.kick.hint.violation",
                "提示：请移除或补齐上述 Mod 后重新加入；如有疑问请联系服务器管理。",
                "Hint: remove or add the mods listed above and rejoin; contact server staff if unsure.");
        put("mac.kick.hint.mac_version",
                "提示：请把 Mod Access Control 更新/切换到服务器要求的版本后重新加入。",
                "Hint: update or switch Mod Access Control to the version required by the server and rejoin.");

        // ---------------- 违规明细行（客户端可见 / 管理摘要共用键名）
        put("mac.line.missing_required",
                "缺少必需 Mod: {}（要求 {}）",
                "Missing required mod: {} (required {})");
        put("mac.line.version_mismatch",
                "Mod 版本不符: {}（要求 {}，你的是 {}）",
                "Mod version mismatch: {} (required {}, yours {})");
        put("mac.line.not_whitelisted",
                "安装了白名单之外的 Mod: {}（版本 {}）",
                "Mod outside the whitelist: {} (version {})");
        put("mac.line.not_whitelisted_version",
                "Mod 版本不符: {}（允许 {}，你的是 {}）",
                "Mod version mismatch: {} (allowed {}, yours {})");
        put("mac.line.blacklisted",
                "安装了被禁止的 Mod: {}（版本 {}）",
                "Forbidden mod installed: {} (version {})");
        put("mac.line.blacklisted_version",
                "被禁止的 Mod 版本: {}（禁止 {}，你的是 {}）",
                "Forbidden mod version: {} (forbidden {}, yours {})");
        put("mac.line.loader",
                "加载器不兼容（期望 {}，实际 {}）",
                "Incompatible mod loader (expected {}, got {})");

        // ---------------- 服务端日志 / 管理广播
        put("mac.server.started",
                "[MAC] Mod Access Control 已启动 (loader={}, version={})",
                "[MAC] Mod Access Control started (loader={}, version={})");
        put("mac.server.stopped",
                "[MAC] Mod Access Control 已停止。",
                "[MAC] Mod Access Control stopped.");
        put("mac.server.notify.violation",
                "违规拦截: {} -> {}",
                "Violation blocked: {} -> {}");
        put("mac.server.notify.dryrun",
                "试运行: {} -> {}（未实际踢出）",
                "Dry-run: {} -> {} (not actually kicked)");
        put("mac.server.notify.noclientmod_dryrun",
                "试运行: {} 客户端未安装本模组（本将拦截，未实际踢出）",
                "Dry-run: {} has no access-control mod (would be blocked, not kicked)");
        put("mac.server.precheck.violation_only",
                "试运行(NO_CLIENT_MOD 未拦截): 客户端未安装本模组",
                "Dry-run (NO_CLIENT_MOD not blocked): client lacks this mod");
        put("mac.server.precheck.dryrun",
                "试运行(未拦截): {}",
                "Dry-run (not blocked): {}");
        put("mac.server.precheck.blocked",
                "[MAC] 预拦截玩家 {}: {}",
                "[MAC] Pre-blocked player {}: {}");
        put("mac.server.deny",
                "[MAC] 拒绝玩家 {} 进入: {}",
                "[MAC] Denied player {}: {}");
        put("mac.server.deny_dryrun",
                "[MAC][试运行] 本将拒绝玩家 {}: {}",
                "[MAC][dry-run] Would deny player {}: {}");
        put("mac.server.recheck_kick",
                "[MAC] 定期复检发现玩家 {} 违规，正在执行踢出",
                "[MAC] Periodic recheck found violation for player {}, kicking");
        put("mac.server.recheck_done",
                "[MAC] 强制复检完成，共扫描 {} 名在线玩家",
                "[MAC] Forced recheck done, scanned {} online players");
        put("mac.server.pass_stage1",
                "[MAC] 玩家 {} 通过登录阶段校验，请求完整 Mod 列表",
                "[MAC] Player {} passed login-stage check, requesting full mod list");
        put("mac.server.verified",
                "[MAC] 玩家 {} 通过全部准入校验，允许进入游戏（加载 {} 个客户端 Mod）",
                "[MAC] Player {} passed all access checks and may join ({} client mods loaded)");
        put("mac.server.session_end",
                "[MAC] 玩家 {} 离开，会话结束(phase={})",
                "[MAC] Player {} left, session ended (phase={})");
        put("mac.server.exempt_hit",
                "[MAC] 玩家 {} 命中豁免名单，跳过准入校验",
                "[MAC] Player {} is exempt, skipping access verification");
        put("mac.server.no_require_pass",
                "[MAC] 客户端准入未强制开启(requireClientMod=false)，放行玩家 {}",
                "[MAC] Client verification not enforced (requireClientMod=false), allowing player {}");

        // ---------------- /mac status 输出
        put("mac.server.status.title", "== Mod Access Control ==", "== Mod Access Control ==");
        put("mac.server.status.enabled", "启用: {}", "Enabled: {}");
        put("mac.server.status.scope",
                "强制范围: {}", "Enforcement scope: {}");
        put("mac.server.status.scope.both", "专用服务器+集成服务器", "dedicated + integrated servers");
        put("mac.server.status.scope.dedicated", "仅专用服务器", "dedicated servers only");
        put("mac.server.status.required_mode", "必需Mod校验模式: {}", "Required-mod check mode: {}");
        put("mac.server.status.required_count", "必需Mod数量: {}", "Required mods: {}");
        put("mac.server.status.policy_mode", "策略模式: {}", "Policy mode: {}");
        put("mac.server.status.active_mode", "当前生效策略: {}", "Active policy: {}");
        put("mac.server.status.recheck_interval", "复检间隔(秒): {}", "Recheck interval (s): {}");
        put("mac.server.status.whitelist_count", "白名单数量: {}", "Whitelist entries: {}");
        put("mac.server.status.blacklist_count", "黑名单数量: {}", "Blacklist entries: {}");
        put("mac.server.status.ignored", "忽略列表: {}", "Ignored ids: {}");
        put("mac.server.status.dryrun", "试运行(不踢): {}", "Dry-run (no kick): {}");
        put("mac.server.status.exempt", "豁免: {} 条{}", "Exemptions: {}{}");
        put("mac.server.status.exempt.ops", "（默认豁免 OP）", " (OPs exempt by default)");
        put("mac.server.status.language", "服务端语言: {}", "Server language: {}");
        put("mac.server.status.kick_details", "踢出明细: {}", "Kick details: {}");
        put("mac.server.status.admin_details", "管理明细: {}", "Admin details: {}");
        put("mac.server.status.allowed_mac",
                "允许的本模组版本: {}", "Allowed mod versions: {}");
        put("mac.server.status.allowed_mac.all", "全部", "all");
        put("mac.server.status.sessions",
                "在线会话: 已通过 {} / 校验中 {}", "Online sessions: verified {} / pending {}");

        // ---------------- /mac check / audit / learn
        put("mac.server.check.line",
                "玩家 {} 状态: {} | 加载器: {} | 客户端Mod数: {} | 加入于 tick {}",
                "Player {} status: {} | loader: {} | client mods: {} | joined at tick {}");
        put("mac.server.check.unknown", "找不到玩家 {} 的在线会话。", "No online session found for player {}.");
        put("mac.server.audit.count", " 个Mod: ", " mods: ");
        put("mac.server.audit.unknown_version", "未知", "unknown");
        put("mac.server.learn.no_record",
                "找不到玩家 {} 的在线会话或历史 Mod 记录。",
                "No online session or mod history found for player {}.");
        put("mac.server.learn.source.live", "在线完整清单", "live full list");
        put("mac.server.learn.source.history", "最近一次记录", "latest history record");
        put("mac.server.learn.done",
                "已用玩家 {} 的{}更新{}：新增 {}，已存在 {}，跳过 {}",
                "Updated {} from player {}'s {}: added {}, already present {}, skipped {}");
        put("mac.server.learn.list.whitelist", "白名单", "whitelist");
        put("mac.server.learn.list.blacklist", "黑名单", "blacklist");
        put("mac.server.learn.sample", "；示例: {}", "; examples: {}");

        locale = ZH_CN;
    }

    private static void put(String key, String zh, String en) {
        ZH.put(key, zh);
        EN.put(key, en);
    }

    /** 供核心其他类（如 HelpText）注册的双语文案（同键覆盖）。 */
    public static void putOverride(String key, String zh, String en) {
        ZH.put(key, zh);
        EN.put(key, en);
    }

    private I18n() {
    }

    /** 按配置刷新当前语言；无法识别的值按 auto 处理。 */
    public static void refresh(String configLanguage) {
        String lang = configLanguage == null ? "auto" : configLanguage.trim().toLowerCase(Locale.ROOT);
        if (ZH_CN.equals(lang)) {
            locale = ZH_CN;
        } else if (EN_US.equals(lang)) {
            locale = EN_US;
        } else {
            // auto：跟随服务器系统语言（zh 开头判中文）。
            locale = Locale.getDefault().getLanguage().startsWith("zh") ? ZH_CN : EN_US;
        }
        ensureExtendedRegistered();
    }

    /** 触发扩展文案（命令回显 / 分页帮助）注册。 */
    private static void ensureExtendedRegistered() {
        CommandText.register();
        try {
            Class.forName("mcyszl.top.mod_access_control.core.feedback.HelpText");
        } catch (Throwable ignored) {
            // 核心最小化裁剪时（无 HelpText）忽略
        }
    }

    /** 当前语言键（zh_cn / en_us）。 */
    public static String locale() {
        return locale;
    }

    /** 是否中文（用于适配层选择文案之外的本地化细节）。 */
    public static boolean isChinese() {
        return ZH_CN.equals(currentLocale());
    }

    /**
     * 把客户端语言代码（如 {@code zh_cn} / {@code en_us} / {@code zh_tw}）规整为
     * 受支持的语言键；{@code null} / 空 / {@code auto} / 无法识别时返回 {@code null}
     * （表示沿用服务端语言）。
     */
    public static String normalizeClientLanguage(String clientLanguage) {
        if (clientLanguage == null) {
            return null;
        }
        String lang = clientLanguage.trim().toLowerCase(Locale.ROOT);
        if (lang.isEmpty() || "auto".equals(lang)) {
            return null;
        }
        return lang.startsWith("zh") ? ZH_CN : EN_US;
    }

    /**
     * 取指定客户端语言下的文案（不改变当前作用域）；{@code clientLanguage}
     * 无法识别时使用当前语言。用于“服务端代为渲染玩家可见文本”的场景
     * （玩家踢出消息 / 踢出底部提示）。
     */
    public static String trTo(String clientLanguage, String key, Object... args) {
        String normalized = normalizeClientLanguage(clientLanguage);
        String fmt = lookupIn(normalized == null ? currentLocale() : normalized, key);
        return Mac.format(fmt, args);
    }

    /**
     * 在指定客户端语言的作用域内执行 {@code action}（命令回显随命令执行者的
     * 客户端语言）；语言无法识别时直接执行，沿用服务端语言。
     */
    public static <T> T withLocale(String clientLanguage, java.util.function.Supplier<T> action) {
        String normalized = normalizeClientLanguage(clientLanguage);
        if (normalized == null) {
            return action.get();
        }
        String prev = SCOPED.get();
        SCOPED.set(normalized);
        try {
            return action.get();
        } finally {
            if (prev == null) {
                SCOPED.remove();
            } else {
                SCOPED.set(prev);
            }
        }
    }

    /**
     * 取当前语言的文案并做 {} 占位符替换；键缺失时回退另一语言，
     * 再缺失返回键名本身（便于发现漏配）。
     */
    public static String tr(String key, Object... args) {
        String fmt = lookup(key);
        return Mac.format(fmt, args);
    }

    /** 不带参数取文案（等价 tr(key)）。 */
    public static String tr(String key) {
        return lookup(key);
    }

    /** 当前生效语言：线程作用域优先，否则为服务端配置语言。 */
    private static String currentLocale() {
        String scoped = SCOPED.get();
        return scoped == null ? locale : scoped;
    }

    private static String lookup(String key) {
        return lookupIn(currentLocale(), key);
    }

    private static String lookupIn(String loc, String key) {
        Map<String, String> dict = ZH_CN.equals(loc) ? ZH : EN;
        String s = dict.get(key);
        if (s == null) {
            s = (ZH_CN.equals(loc) ? EN : ZH).get(key);
        }
        return s == null ? key : s;
    }
}
