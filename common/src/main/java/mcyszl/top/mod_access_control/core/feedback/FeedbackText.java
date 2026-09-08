package mcyszl.top.mod_access_control.core.feedback;

import mcyszl.top.mod_access_control.core.model.MacConfig;

import java.util.ArrayList;
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
     */
    public static String hintFor(DisconnectReason reason) {
        return switch (reason) {
            case NO_CLIENT_MOD, PROTOCOL_MISMATCH, LOADER_MISMATCH ->
                    "提示：请按服务器要求安装正确的模组版本后重新加入。";
            case HANDSHAKE_TIMEOUT -> "提示：如多次失败，请尝试更新游戏/模组版本后重连。";
            case POLICY_VIOLATION ->
                    "提示：请移除或补齐上述 Mod 后重新加入；如有疑问请联系服务器管理。";
            case MAC_VERSION_NOT_ALLOWED ->
                    "提示：请把 Mod Access Control 更新/切换到服务器要求的版本后重新加入。";
        };
    }

    /**
     * 汇总底部提示区逐行文案：默认提示行（若存在）+ 配置的自定义行。
     * 关闭 {@code kickFooterEnabled} 时返回空列表（整体隐藏）。
     */
    public static List<String> footer(DisconnectReason reason, MacConfig cfg) {
        if (cfg == null || !cfg.isKickFooterEnabled()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String hint = hintFor(reason);
        if (hint != null) {
            out.add(hint);
        }
        if (cfg.getKickFooterLines() != null) {
            for (String line : cfg.getKickFooterLines()) {
                if (line != null && !line.isBlank()) {
                    out.add(line.trim());
                }
            }
        }
        return out;
    }
}
