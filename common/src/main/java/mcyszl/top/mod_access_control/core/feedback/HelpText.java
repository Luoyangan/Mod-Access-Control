// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.feedback;

import mcyszl.top.mod_access_control.core.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /mac help [页码]} 分页帮助（v1.1）。
 *
 * <p>文案单一来源在核心（走 I18n，服务端语言），各适配层只负责把返回的
 * 逐行文本按自身方式发送给命令执行者；三加载器 / 全版本内容一致。</p>
 */
public final class HelpText {

    /** 每页正文行数（不含标题与页码行）。 */
    private static final int LINES_PER_PAGE = 10;

    private HelpText() {
    }

    /** 子命令总数对应的总页数。 */
    public static int totalPages() {
        return (commandCount() + LINES_PER_PAGE - 1) / LINES_PER_PAGE;
    }

    /** 规范化页码（1-based；越界回退第 1 页）。 */
    public static int normalizePage(int page) {
        int total = totalPages();
        if (page < 1 || page > total) {
            return 1;
        }
        return page;
    }

    /**
     * 取指定页的逐行文本（首页含标题，尾行含页码指示）。
     *
     * @param page 页码（1-based；越界自动回退第 1 页）
     */
    public static List<String> page(int page) {
        int total = totalPages();
        int p = normalizePage(page);
        List<String> out = new ArrayList<>();
        out.add(I18n.tr("mac.help.title", p, total));
        int start = (p - 1) * LINES_PER_PAGE;
        int end = Math.min(start + LINES_PER_PAGE, commandCount());
        for (int i = start; i < end; i++) {
            out.add(I18n.tr("mac.help.cmd." + COMMAND_KEYS[i]));
        }
        out.add(I18n.tr("mac.help.page_of", p, total));
        return out;
    }

    /** 帮助条目数（保持与 COMMAND_KEYS 同步）。 */
    private static int commandCount() {
        return COMMAND_KEYS.length;
    }

    /**
     * 帮助条目键后缀（对应 {@code mac.help.cmd.<key>}）；每条完整文案形如
     * “/mac status — 查看状态概要”。新增子命令时在数组中按展示顺序追加。
     */
    private static final String[] COMMAND_KEYS = {
            "status", "help", "recent", "check", "audit", "learn", "reload", "save",
            "recheck", "enabled", "dryrun", "mode", "active", "exempt", "allowedmac",
            "required", "whitelist", "blacklist"
    };

    static {
        // 文案注册（zh / en），键名与 COMMAND_KEYS 一一对应。
        help("status", "/mac status — 查看状态概要", "/mac status — show status summary");
        help("help", "/mac help [页码] — 分页查看本帮助", "/mac help [page] — this help, paged");
        help("recent", "/mac recent — 最近违规记录（最新在前）", "/mac recent — recent violations (newest first)");
        help("check", "/mac check <玩家> — 查看玩家会话状态", "/mac check <player> — inspect a player's session");
        help("audit", "/mac audit <玩家> — 查看玩家历史 Mod 记录", "/mac audit <player> — view player's mod history");
        help("learn", "/mac learn <玩家> <whitelist|blacklist> — 用其清单一键建名单",
                "/mac learn <player> <whitelist|blacklist> — build list from their mods");
        help("reload", "/mac reload — 从配置文件重新加载规则", "/mac reload — reload rules from config file");
        help("save", "/mac save — 把当前配置保存到文件", "/mac save — save current config to file");
        help("recheck", "/mac recheck — 对在线玩家立即复检", "/mac recheck — re-verify all online players now");
        help("enabled", "/mac enabled <true|false> — 总开关", "/mac enabled <true|false> — master switch");
        help("dryrun", "/mac dryrun <true|false> — 试运行开关（违规不踢出）", "/mac dryrun <true|false> — dry-run toggle (no kicks)");
        help("mode", "/mac mode <whitelist|blacklist|switch> — 策略模式", "/mac mode <whitelist|blacklist|switch> — policy mode");
        help("active", "/mac active <whitelist|blacklist> — switch 模式下生效策略",
                "/mac active <whitelist|blacklist> — active policy in switch mode");
        help("exempt", "/mac exempt list|add|remove — 豁免名单管理", "/mac exempt list|add|remove — manage exemptions");
        help("allowedmac", "/mac allowedmac list|add|remove — 允许的本模组版本",
                "/mac allowedmac list|add|remove — allowed versions of this mod");
        help("required", "/mac required list|add|remove — 必需 Mod 管理（支持版本约束）",
                "/mac required list|add|remove — required mods (version bounds supported)");
        help("whitelist", "/mac whitelist list|add|remove — 白名单管理（id 支持 * 通配符，可选版本约束如 >=1.0）",
                "/mac whitelist list|add|remove — manage whitelist (ids allow *, optional bounds like >=1.0)");
        help("blacklist", "/mac blacklist list|add|remove — 黑名单管理（id 支持 * 通配符，可选版本约束如 >=1.0）",
                "/mac blacklist list|add|remove — manage blacklist (ids allow *, optional bounds like >=1.0)");
    }

    private static void help(String key, String zh, String en) {
        I18n.putOverride("mac.help.cmd." + key, zh, en);
    }
}
