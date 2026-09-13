// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.i18n;

/**
 * /mac 命令回显文案（v1.1：双语单一来源）。
 *
 * <p>各加载器适配层的命令实现统一调用本类的静态方法生成回显文本，
 * 避免同一文案在 9 个适配层重复硬编码；语言随 {@link I18n} 切换。</p>
 */
public final class CommandText {

    private CommandText() {
    }

    private static String t(String key, Object... args) {
        return I18n.tr(key, args);
    }

    public static String runFailure(String message) {
        return t("mac.cmd.err.run", message);
    }

    public static String reload() {
        return t("mac.cmd.reload");
    }

    public static String save() {
        return t("mac.cmd.save");
    }

    public static String recheck() {
        return t("mac.cmd.recheck");
    }

    public static String enabled(boolean value) {
        return t("mac.cmd.enabled", value);
    }

    public static String dryrun(boolean value) {
        return t(value ? "mac.cmd.dryrun.on" : "mac.cmd.dryrun.off", value);
    }

    public static String mode(String key, boolean isSwitch) {
        return t("mac.cmd.mode", key) + (isSwitch ? t("mac.cmd.mode.switch_note") : "");
    }

    public static String active(String key) {
        return t("mac.cmd.active", key);
    }

    public static String errLearnMode() {
        return t("mac.cmd.err.learn_mode");
    }

    public static String errActiveMode() {
        return t("mac.cmd.err.active_mode");
    }

    public static String errSelfRequired() {
        return t("mac.cmd.err.self_required");
    }

    public static String recentEmpty() {
        return t("mac.cmd.recent.empty");
    }

    public static String recentHeader(int total) {
        return t("mac.cmd.recent.header", total);
    }

    public static String checkNotFound(String name) {
        return t("mac.cmd.check.not_found", name);
    }

    public static String auditEmpty(String name) {
        return t("mac.cmd.audit.empty", name);
    }

    public static String auditHeader(String name) {
        return t("mac.cmd.audit.header", name);
    }

    public static String exemptEmpty() {
        return t("mac.cmd.exempt.empty");
    }

    public static String exemptHeader(int size, boolean ops) {
        return t("mac.cmd.exempt.header", size, ops ? t("mac.cmd.exempt.ops") : "");
    }

    public static String exemptExists(String player) {
        return t("mac.cmd.exempt.exists", player);
    }

    public static String exemptAdded(String player) {
        return t("mac.cmd.exempt.added", player);
    }

    public static String exemptNotFound(String player) {
        return t("mac.cmd.exempt.not_found", player);
    }

    public static String exemptRemoved(String player) {
        return t("mac.cmd.exempt.removed", player);
    }

    public static String allowedMacEmpty() {
        return t("mac.cmd.allowedmac.empty");
    }

    public static String allowedMacHeader(int size) {
        return t("mac.cmd.allowedmac.header", size);
    }

    public static String allowedMacExists(String version) {
        return t("mac.cmd.allowedmac.exists", version);
    }

    public static String allowedMacCleared() {
        return t("mac.cmd.allowedmac.cleared");
    }

    public static String allowedMacAdded(String version) {
        return t("mac.cmd.allowedmac.added", version);
    }

    public static String allowedMacNotFound(String version) {
        return t("mac.cmd.allowedmac.not_found", version);
    }

    public static String allowedMacRemoved(String version) {
        return t("mac.cmd.allowedmac.removed", version);
    }

    public static String requiredEmpty(String mode) {
        return t("mac.cmd.required.empty", mode);
    }

    public static String requiredHeader(String mode) {
        return t("mac.cmd.required.header", mode);
    }

    public static String requiredAdded(String rule) {
        return t("mac.cmd.required.added", rule);
    }

    public static String requiredRemoved(String id) {
        return t("mac.cmd.required.removed", id);
    }

    public static String requiredNotFound(String id) {
        return t("mac.cmd.required.not_found", id);
    }

    public static String listName(boolean whitelist) {
        return t(whitelist ? "mac.cmd.list.name.whitelist" : "mac.cmd.list.name.blacklist");
    }

    public static String listEmpty(String name) {
        return t("mac.cmd.list.empty", name);
    }

    public static String listHeader(String name, int size) {
        return t("mac.cmd.list.header", name, size);
    }

    public static String listExists(String id, String name) {
        return t("mac.cmd.list.exists", id, name);
    }

    public static String listAdded(String name, String entry) {
        return t("mac.cmd.list.added", name, entry);
    }

    public static String listNotFound(String name, String id) {
        return t("mac.cmd.list.not_found", name, id);
    }

    public static String listRemoved(String name, String id) {
        return t("mac.cmd.list.removed", name, id);
    }

    // ---------------- 用法提示（pre-Brigadier 适配层需要手工提示参数缺失）

    public static String usageCheck() {
        return t("mac.cmd.usage.check");
    }

    public static String usageAudit() {
        return t("mac.cmd.usage.audit");
    }

    public static String usageLearn() {
        return t("mac.cmd.usage.learn");
    }

    public static String usageMode() {
        return t("mac.cmd.usage.mode");
    }

    public static String usageActive() {
        return t("mac.cmd.usage.active");
    }

    public static String usageRequired() {
        return t("mac.cmd.usage.required");
    }

    public static String usageList(String sub) {
        return t("mac.cmd.usage.list", sub);
    }

    public static String usagePolicy(String sub) {
        return t("mac.cmd.usage.policy", sub);
    }

    public static String usageBool() {
        return t("mac.cmd.usage.bool");
    }

    public static String errBool(String value) {
        return t("mac.cmd.err.bool", value);
    }

    public static String errUnknownSub(String sub, String usage) {
        return t("mac.cmd.err.unknown", sub, usage);
    }

    static void register() {
        put("mac.cmd.err.run", "[MAC] 命令执行失败: {}", "[MAC] Command failed: {}");
        put("mac.cmd.reload",
                "已从配置文件重新加载。若需用最新规则复检在线玩家，请执行 /mac recheck",
                "Reloaded from config file. Run /mac recheck to re-verify online players");
        put("mac.cmd.save", "已保存当前配置到文件。", "Saved current config to file.");
        put("mac.cmd.recheck", "已按当前规则对在线玩家执行一次完整复检。",
                "Completed a full recheck of online players with current rules.");
        put("mac.cmd.enabled", "总开关已设为: {}（若关闭，客户端将不再被强制校验）",
                "Master switch set to: {} (when off, clients are not verified)");
        put("mac.cmd.dryrun.on", "试运行模式已设为: true（违规只记录，不实际踢出玩家）",
                "Dry-run set to: true (violations are only logged, no kicks)");
        put("mac.cmd.dryrun.off", "试运行模式已设为: false", "Dry-run set to: false");
        put("mac.cmd.mode", "策略模式已设为: {}", "Policy mode set to: {}");
        put("mac.cmd.mode.switch_note", " 提示：当前生效策略请用 /mac active <whitelist|blacklist> 指定。",
                " Tip: set the active policy with /mac active <whitelist|blacklist>.");
        put("mac.cmd.active", "当前生效策略已设为: {}", "Active policy set to: {}");
        put("mac.cmd.err.learn_mode", "learn 只能用于 whitelist 或 blacklist。",
                "learn only accepts whitelist or blacklist.");
        put("mac.cmd.err.active_mode", "active 只能为 whitelist 或 blacklist。",
                "active only accepts whitelist or blacklist.");
        put("mac.cmd.err.self_required", "不能把本模组加入必需清单。",
                "Cannot require this mod itself.");
        put("mac.cmd.recent.empty", "暂无违规记录。", "No violations recorded.");
        put("mac.cmd.recent.header", "最近违规记录（最新在前，共 {} 条）：",
                "Recent violations (newest first, {} total):");
        put("mac.cmd.check.not_found", "[MAC] 找不到玩家: {}", "[MAC] Player not found: {}");
        put("mac.cmd.audit.empty", "没有 {} 的历史 Mod 记录。", "No mod history for {}.");
        put("mac.cmd.audit.header", "{} 的历史 Mod 记录（最新在前）：",
                "Mod history for {} (newest first):");
        put("mac.cmd.exempt.empty", "豁免名单为空（且未豁免 OP）。",
                "No exemptions (OPs are not exempt either).");
        put("mac.cmd.exempt.header", "豁免配置（{} 条{}）：", "Exemptions ({}{}):");
        put("mac.cmd.exempt.ops", "，同时豁免 OP", ", OPs exempt");
        put("mac.cmd.exempt.exists", "{} 已在豁免名单中。", "{} is already exempt.");
        put("mac.cmd.exempt.added", "已加入豁免名单: {}", "Added to exemptions: {}");
        put("mac.cmd.exempt.not_found", "豁免名单中不存在: {}", "Not in exemptions: {}");
        put("mac.cmd.exempt.removed", "已移出豁免名单: {}", "Removed from exemptions: {}");
        put("mac.cmd.allowedmac.empty", "允许的本模组版本列表为空（放行任意版本）。",
                "Allowed mod versions empty (any version allowed).");
        put("mac.cmd.allowedmac.header", "允许接入的本模组版本（{} 项）：",
                "Allowed mod versions ({}):");
        put("mac.cmd.allowedmac.exists", "{} 已在允许列表中。", "{} is already allowed.");
        put("mac.cmd.allowedmac.cleared", "已清空限制，放行任意本模组版本。",
                "Cleared; any mod version allowed.");
        put("mac.cmd.allowedmac.added", "已加入允许版本: {}", "Allowed version added: {}");
        put("mac.cmd.allowedmac.not_found", "允许列表中不存在: {}", "Not in allowed list: {}");
        put("mac.cmd.allowedmac.removed", "已移出允许版本: {}", "Allowed version removed: {}");
        put("mac.cmd.required.empty", "必需 Mod 清单为空（校验模式: {}）。",
                "No required mods (check mode: {}).");
        put("mac.cmd.required.header", "必需 Mod（校验模式: {}）：", "Required mods (check mode: {}):");
        put("mac.cmd.required.added", "已添加必需 Mod: {}", "Required mod added: {}");
        put("mac.cmd.required.removed", "已移除必需 Mod: {}", "Required mod removed: {}");
        put("mac.cmd.required.not_found", "清单中不存在: {}", "Not in required list: {}");
        put("mac.cmd.list.name.whitelist", "白名单", "whitelist");
        put("mac.cmd.list.name.blacklist", "黑名单", "blacklist");
        put("mac.cmd.list.empty", "{}为空。", "{} is empty.");
        put("mac.cmd.list.header", "{}（{} 项）：", "{} ({} entries):");
        put("mac.cmd.list.exists", "{} 已存在于{}。", "{} is already in {}.");
        put("mac.cmd.list.added", "已向{}加入: {}", "Added to {}: {}");
        put("mac.cmd.list.not_found", "{}中不存在: {}", "Not in {}: {}");
        put("mac.cmd.list.removed", "已从{}移除: {}", "Removed from {}: {}");
        put("mac.cmd.usage.check", "用法: /mac check <玩家>", "Usage: /mac check <player>");
        put("mac.cmd.usage.audit", "用法: /mac audit <玩家>", "Usage: /mac audit <player>");
        put("mac.cmd.usage.learn", "用法: /mac learn <玩家> <whitelist|blacklist>",
                "Usage: /mac learn <player> <whitelist|blacklist>");
        put("mac.cmd.usage.mode", "用法: /mac mode <whitelist|blacklist|switch>",
                "Usage: /mac mode <whitelist|blacklist|switch>");
        put("mac.cmd.usage.active", "用法: /mac active <whitelist|blacklist>",
                "Usage: /mac active <whitelist|blacklist>");
        put("mac.cmd.usage.required", "用法: /mac required list | add <id> [版本约束] | remove <id>",
                "Usage: /mac required list | add <id> [version bounds] | remove <id>");
        put("mac.cmd.usage.list", "用法: /mac {} list | add <id> | remove <id>",
                "Usage: /mac {} list | add <id> | remove <id>");
        put("mac.cmd.usage.policy", "用法: /mac {} list | add <id> [版本约束] | remove <id>",
                "Usage: /mac {} list | add <id> [version bounds] | remove <id>");
        put("mac.cmd.usage.bool", "用法: 需要 <true|false> 参数", "Usage: a <true|false> argument is required");
        put("mac.cmd.err.bool", "参数必须是 true 或 false: {}", "Argument must be true or false: {}");
        put("mac.cmd.err.unknown", "未知子命令: {}。{}", "Unknown subcommand: {}. {}");
        put("mac.help.title", "== [MAC] 帮助 ({}/{})(n = 数字参数, s = 文本参数) ==",
                "== [MAC] Help ({}/{}) (n = number arg, s = text arg) ==");
        put("mac.help.page_of", "第 {}/{} 页，/mac help <页码> 翻页",
                "Page {}/{}, use /mac help <page> to browse");
    }

    private static void put(String key, String zh, String en) {
        I18n.putOverride(key, zh, en);
    }
}
