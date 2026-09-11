// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.feedback.DisconnectReason;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.validate.Problem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketDisconnect;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * 玩家可见反馈渲染（踢出原因）。为避免“未安装本模组”的客户端无法解析翻译键，
 * 直接构造纯文本（中文）组件发送；无需客户端拥有语言资源。
 *
 * <p>1.12.2 没有 ChatFormatting/MutableComponent 链式 API，改用
 * {@link TextComponentString} + {@link Style} + {@link TextFormatting}。</p>
 */
public final class Feedback {

    private Feedback() {
    }

    /** 由 KickMessage 渲染为可发送的断开原因组件。 */
    public static ITextComponent disconnect(KickMessage m) {
        TextComponentString c = new TextComponentString("");
        TextComponentString head = new TextComponentString("[MAC] " + headerOf(m.reason(), m.headerArgs()));
        head.getStyle().setColor(TextFormatting.RED);
        head.getStyle().setBold(true);
        c.appendSibling(head);
        List<Problem> problems = m.problems();
        if (problems != null && !problems.isEmpty()) {
            for (Problem p : problems) {
                TextComponentString line = new TextComponentString("\n  - " + problemOf(p));
                line.getStyle().setColor(TextFormatting.YELLOW);
                c.appendSibling(line);
            }
        }
        List<String> footer = m.footer();
        if (footer != null) {
            for (String line : footer) {
                TextComponentString f = new TextComponentString("\n" + line);
                f.getStyle().setColor(TextFormatting.GRAY);
                c.appendSibling(f);
            }
        }
        return c;
    }

    /**
     * 实际踢出。1.12.2 的 {@code player.connection.disconnect(String)} 只接受
     * 翻译键字符串，会把纯文本当 key 显示成原文乱码，因此仿照 FML 的
     * kickWithMessage：先发 SPacketDisconnect 再关闭通道。
     */
    public static void kick(EntityPlayerMP player, KickMessage message) {
        ITextComponent component = disconnect(message);
        NetworkManager manager = player.connection.netManager;
        manager.sendPacket(new SPacketDisconnect(component));
        manager.closeChannel(component);
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
            default:
                return "连接被拒绝：本模组版本不在服务器允许列表内。\n"
                        + "服务器要求的版本: " + arg(args, 0) + "；你的版本: " + arg(args, 1) + "。";
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
            default:
                return "加载器不兼容（期望 " + exp + "，实际 " + act + "）";
        }
    }
}
