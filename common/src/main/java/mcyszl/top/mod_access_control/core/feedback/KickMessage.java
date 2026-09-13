// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.feedback;

import mcyszl.top.mod_access_control.core.validate.Problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一条“踢出 / 拒绝加入”消息。
 *
 * <p>标题（reason header）带 {@code headerArgs}，主体为逐条 {@link Problem}
 * 违规行；由各加载器适配层翻译为玩家可见的 Component。</p>
 *
 * <p>v1.1.1 起不再区分“翻译键 / legacy”两条渲染路径：适配层统一按
 * {@link #clientLanguage()} 在服务端渲染纯文本，避免客户端解析与服务端日志
 * 使用不同语言导致同一条消息中英混杂。</p>
 */
public final class KickMessage {

    private final DisconnectReason reason;
    private final String[] headerArgs;
    private final List<Problem> problems;
    /** 断开界面底部“提示区”的逐行文案（服务端按配置生成；渲染层原样追加）。 */
    private final List<String> footer;
    /** 被踢玩家的客户端语言（如 {@code zh_cn}）；未知为 null（回退服务端语言）。 */
    private final String clientLanguage;

    public KickMessage(DisconnectReason reason, String[] headerArgs, List<Problem> problems) {
        this(reason, headerArgs, problems, Collections.emptyList(), null);
    }

    public KickMessage(DisconnectReason reason, String[] headerArgs, List<Problem> problems,
                       List<String> footer) {
        this(reason, headerArgs, problems, footer, null);
    }

    public KickMessage(DisconnectReason reason, String[] headerArgs, List<Problem> problems,
                       List<String> footer, String clientLanguage) {
        this.reason = reason;
        this.headerArgs = headerArgs == null ? new String[0] : headerArgs;
        this.problems = problems == null ? new ArrayList<>() : problems;
        this.footer = footer == null ? Collections.emptyList() : footer;
        this.clientLanguage = clientLanguage;
    }

    public static KickMessage simple(DisconnectReason reason, String... args) {
        return new KickMessage(reason, args, Collections.emptyList());
    }

    public static KickMessage violation(List<Problem> problems) {
        return new KickMessage(DisconnectReason.POLICY_VIOLATION, new String[0], problems);
    }

    /** 复制一份并携带底部提示行。 */
    public KickMessage withFooter(List<String> footer) {
        return new KickMessage(reason, headerArgs, problems, footer, clientLanguage);
    }

    /** 复制一份并携带被踢玩家的客户端语言（供服务端按玩家语言渲染纯文本）。 */
    public KickMessage withClientLanguage(String language) {
        return new KickMessage(reason, headerArgs, problems, footer, language);
    }

    /** 复制一份并移除逐条违规明细（保留标题 / footer），受 kickShowDetails 控制。 */
    public KickMessage withoutDetails() {
        return new KickMessage(reason, headerArgs, Collections.<Problem>emptyList(), footer,
                clientLanguage);
    }

    public DisconnectReason reason() {
        return reason;
    }

    public String[] headerArgs() {
        return headerArgs;
    }

    public List<Problem> problems() {
        return problems;
    }

    public List<String> footer() {
        return footer;
    }

    /** 被踢玩家的客户端语言（如 {@code zh_cn}）；未知返回 null（回退服务端语言）。 */
    public String clientLanguage() {
        return clientLanguage;
    }

    /** 标题翻译键（与服务端 I18n 字典 / lang 文件共用键名）。 */
    public String headerKey() {
        switch (reason) {
            case NO_CLIENT_MOD:
                return Keys.KICK_HEADER_NO_CLIENT_MOD;
            case PROTOCOL_MISMATCH:
                return Keys.KICK_HEADER_PROTOCOL;
            case LOADER_MISMATCH:
                return Keys.KICK_HEADER_LOADER;
            case HANDSHAKE_TIMEOUT:
                return Keys.KICK_HEADER_TIMEOUT;
            case MAC_VERSION_NOT_ALLOWED:
                return Keys.KICK_HEADER_MAC_VERSION;
            case POLICY_VIOLATION:
            default:
                return Keys.KICK_HEADER_VIOLATION;
        }
    }

    /** 底部提示区默认行的翻译键（无对应键返回 null）。 */
    public String footerHintKey() {
        return FeedbackText.hintKeyFor(reason);
    }

    /** 日志摘要（当前服务端语言；违规行逐条列出）。 */
    public String summary() {
        StringBuilder sb = new StringBuilder(reason.name());
        if (headerArgs.length > 0) {
            sb.append(" args=").append(String.join(",", headerArgs));
        }
        if (!problems.isEmpty()) {
            sb.append(" [");
            for (int i = 0; i < problems.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(problems.get(i).summary());
            }
            sb.append(']');
        }
        return sb.toString();
    }

    /** 管理广播精简摘要（adminShowDetails=false 时用）：仅类型 + 违规条数。 */
    public String shortSummary() {
        return reason.name() + " count=" + problems.size();
    }
}
