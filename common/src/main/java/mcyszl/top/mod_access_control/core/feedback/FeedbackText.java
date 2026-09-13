// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.feedback;

import mcyszl.top.mod_access_control.core.i18n.I18n;
import mcyszl.top.mod_access_control.core.model.MacConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 跨加载器统一的“踢出信息底部提示区”文案生成。
 *
 * <p>v1.1 起，断开消息最下方的灰色提示由核心统一生成并随 {@link KickMessage}
 * 携带给各适配层渲染，行为与文案在三种加载器上完全一致；是否显示由配置
 * {@code kickFooterEnabled}（默认 true）控制，可在其后追加自定义行
 * {@code kickFooterLines}。</p>
 */
public final class FeedbackText {

    private FeedbackText() {
    }

    /**
     * 根据原因生成默认提示行；无对应提示返回 {@code null}。
     * 注意：仅负责“提示区”，标题与逐条原因仍由适配层渲染。
     * 文案随当前服务端语言（默认场景；玩家可见场景请用带客户端语言的重载）。
     */
    public static String hintFor(DisconnectReason reason) {
        return I18n.trTo(null, hintKeyFor(reason));
    }

    /** 按断开原因取底部提示区的翻译键。 */
    public static String hintKeyFor(DisconnectReason reason) {
        switch (reason) {
            case HANDSHAKE_TIMEOUT:
                return "mac.kick.hint.timeout";
            case POLICY_VIOLATION:
                return "mac.kick.hint.violation";
            case MAC_VERSION_NOT_ALLOWED:
                return "mac.kick.hint.mac_version";
            case NO_CLIENT_MOD:
            case PROTOCOL_MISMATCH:
            case LOADER_MISMATCH:
            default:
                return "mac.kick.hint.generic";
        }
    }

    /**
     * 汇总底部提示区逐行文案：默认提示行（若存在）+ 配置的自定义行。
     * 关闭 {@code kickFooterEnabled} 时返回空列表（整体隐藏）。
     *
     * <p>默认提示行按服务端语言生成（日志 / 未知客户端语言场景）。</p>
     */
    public static List<String> footer(DisconnectReason reason, MacConfig cfg) {
        return footer(reason, cfg, null);
    }

    /**
     * 汇总底部提示区逐行文案（默认提示行随被踢玩家的客户端语言）。
     *
     * @param clientLanguage 被踢玩家的客户端语言（如 {@code zh_cn}）；
     *                       无法识别时回退服务端语言
     */
    public static List<String> footer(DisconnectReason reason, MacConfig cfg, String clientLanguage) {
        if (cfg == null || !cfg.isKickFooterEnabled()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        out.add(I18n.trTo(clientLanguage, hintKeyFor(reason)));
        if (cfg.getKickFooterLines() != null) {
            for (String line : cfg.getKickFooterLines()) {
                if (line != null && !line.trim().isEmpty()) {
                    out.add(line.trim());
                }
            }
        }
        return out;
    }
}
