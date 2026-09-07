package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.feedback.DisconnectReason;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.validate.Problem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * 玩家可见反馈渲染（踢出原因）。为避免“未安装本模组”的客户端无法解析翻译键，
 * 直接构造纯文本（中文）组件发送；无需客户端拥有语言资源。
 */
public final class Feedback {

    private Feedback() {
    }

    /** 由 KickMessage 渲染为可发送的断开原因组件。 */
    public static Component disconnect(KickMessage m) {
        MutableComponent c = Component.literal("");
        String header = headerOf(m.reason(), m.headerArgs());
        MutableComponent head = Component.literal("[MAC] " + header)
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        c.append(head);
        List<Problem> problems = m.problems();
        if (problems != null && !problems.isEmpty()) {
            for (Problem p : problems) {
                c.append(Component.literal("\n  - " + problemOf(p)).withStyle(ChatFormatting.YELLOW));
            }
        }
        c.append(Component.literal("\n" + hintOf(m.reason())).withStyle(ChatFormatting.GRAY));
        return c;
    }

    private static String headerOf(DisconnectReason r, String[] args) {
        return switch (r) {
            case NO_CLIENT_MOD ->
                    "连接被拒绝：客户端未安装「Mod Access Control / 模组准入控制」。\n"
                            + "本服务器要求所有客户端安装该模组以完成准入校验。";
            case PROTOCOL_MISMATCH ->
                    "准入协议版本不兼容（服务器=" + arg(args, 0) + "，客户端=" + arg(args, 1) + "）。\n"
                            + "请从服务器指定的渠道更新模组后再加入。";
            case LOADER_MISMATCH ->
                    "加载器不兼容：服务器要求 " + arg(args, 0) + "，你的客户端为 " + arg(args, 1) + "。\n"
                            + "请使用与服务器一致的 Mod 加载器。";
            case HANDSHAKE_TIMEOUT ->
                    "准入校验超时。若你已安装本模组，请重启游戏后重试；"
                            + "若仍未解决，请联系服务器管理员。";
            case POLICY_VIOLATION -> "准入校验未通过，客户端 Mod 列表存在以下问题：";
        };
    }

    private static String hintOf(DisconnectReason r) {
        return switch (r) {
            case NO_CLIENT_MOD, PROTOCOL_MISMATCH, LOADER_MISMATCH ->
                    "提示：请按服务器要求安装正确的模组版本后重新加入。";
            case HANDSHAKE_TIMEOUT -> "提示：如多次失败，请尝试更新游戏/模组版本后重连。";
            case POLICY_VIOLATION -> "提示：请移除或补齐上述 Mod 后重新加入；如有疑问请联系服务器管理。";
        };
    }

    private static String arg(String[] args, int i) {
        return args != null && i < args.length && args[i] != null ? args[i] : "?";
    }

    private static String problemOf(Problem p) {
        String id = p.modId() == null ? "?" : p.modId();
        String exp = p.expected();
        String act = p.actual();
        return switch (p.type()) {
            case MISSING_REQUIRED -> "缺少必需 Mod: " + id + "（要求 " + exp + "）";
            case VERSION_MISMATCH ->
                    "Mod 版本不符: " + id + "（要求 " + exp + "，你的是 " + (act == null ? "未知" : act) + "）";
            case BLACKLISTED -> "安装了被禁止的 Mod: " + id + "（版本 " + (act == null ? "?" : act) + "）";
            case NOT_WHITELISTED -> "安装了白名单之外的 Mod: " + id + "（版本 " + (act == null ? "?" : act) + "）";
            case LOADER_MISMATCH -> "加载器不兼容（期望 " + exp + "，实际 " + act + "）";
        };
    }
}
