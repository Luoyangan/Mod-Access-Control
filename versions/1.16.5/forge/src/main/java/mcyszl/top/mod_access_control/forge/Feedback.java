package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.feedback.DisconnectReason;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.validate.Problem;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * 玩家可见反馈渲染（踢出原因）。为避免“未安装本模组”的客户端无法解析翻译键，
 * 直接构造纯文本（中文）组件发送；无需客户端拥有语言资源。
 */
public final class Feedback {

    private Feedback() {
    }

    /** 由 KickMessage 渲染为可发送的断开原因组件。 */
    public static ITextComponent disconnect(KickMessage m) {
        IFormattableTextComponent c = new StringTextComponent("");
        String header = headerOf(m.reason(), m.headerArgs());
        IFormattableTextComponent head = new StringTextComponent("[MAC] " + header)
                .withStyle(TextFormatting.RED, TextFormatting.BOLD);
        c.append(head);
        List<Problem> problems = m.problems();
        if (problems != null && !problems.isEmpty()) {
            for (Problem p : problems) {
                c.append(new StringTextComponent("\n  - " + problemOf(p)).withStyle(TextFormatting.YELLOW));
            }
        }
        List<String> footer = m.footer();
        if (footer != null) {
            for (String line : footer) {
                c.append(new StringTextComponent("\n" + line).withStyle(TextFormatting.GRAY));
            }
        }
        return c;
    }

    private static String headerOf(DisconnectReason r, String[] args) {
        switch (r) {
            case NO_CLIENT_MOD:
                return "连接被拒绝：客户端未安装「Mod Access Control / 模组准入控制」。\n"
                        + "本服务器要求所有客户端安装该模组以完成准入校验。";
            case PROTOCOL_MISMATCH:
                return "准入协议版本不兼容（服务器=" + arg(args, 0) + "，客户端=" + arg(args, 1) + "）。\n"
                        + "请从服务器指定的渠道更新模组后再加入。";
            case LOADER_MISMATCH:
                return "加载器不兼容：服务器要求 " + arg(args, 0) + "，你的客户端为 " + arg(args, 1) + "。\n"
                        + "请使用与服务器一致的 Mod 加载器。";
            case HANDSHAKE_TIMEOUT:
                return "准入校验超时。若你已安装本模组，请重启游戏后重试；"
                        + "若仍未解决，请联系服务器管理员。";
            case POLICY_VIOLATION:
                return "准入校验未通过，客户端 Mod 列表存在以下问题：";
            case MAC_VERSION_NOT_ALLOWED:
                return "连接被拒绝：本模组版本不在服务器允许列表内。\n"
                        + "服务器要求的版本: " + arg(args, 0) + "；你的版本: " + arg(args, 1) + "。";
            default:
                return "未知拒绝原因。";
        }
    }

    private static String arg(String[] args, int i) {
        return args != null && i < args.length && args[i] != null ? args[i] : "?";
    }

    private static String problemOf(Problem p) {
        String id = p.modId() == null ? "?" : p.modId();
        String exp = p.expected();
        String act = p.actual();
        switch (p.type()) {
            case MISSING_REQUIRED:
                return "缺少必需 Mod: " + id + "（要求 " + exp + "）";
            case VERSION_MISMATCH:
                return "Mod 版本不符: " + id + "（要求 " + exp + "，你的是 " + (act == null ? "未知" : act) + "）";
            case BLACKLISTED:
                return "安装了被禁止的 Mod: " + id + "（版本 " + (act == null ? "?" : act) + "）";
            case NOT_WHITELISTED:
                return "安装了白名单之外的 Mod: " + id + "（版本 " + (act == null ? "?" : act) + "）";
            case LOADER_MISMATCH:
                return "加载器不兼容（期望 " + exp + "，实际 " + act + "）";
            default:
                return "未知问题: " + id;
        }
    }
}
