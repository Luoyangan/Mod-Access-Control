// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.feedback.DisconnectReason;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.i18n.Lang;
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
 * 直接构造纯文本（按服务端语言渲染）组件发送；无需客户端拥有语言资源。
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
                return Lang.tr("Connection denied: the client does not have \"Mod Access Control\" installed.\n"
                        + "This server requires all clients to install this mod for admission checks.");
            case PROTOCOL_MISMATCH:
                return Lang.tr("Admission protocol version mismatch (server={}, client={}).\n"
                        + "Please update the mod from the channel specified by the server and rejoin.",
                        arg(args, 0), arg(args, 1));
            case LOADER_MISMATCH:
                return Lang.tr("Incompatible mod loader: the server requires {}, your client is {}.\n"
                        + "Please use the same mod loader as the server.",
                        arg(args, 0), arg(args, 1));
            case HANDSHAKE_TIMEOUT:
                return Lang.tr("Admission check timed out. If you already have this mod installed, "
                        + "restart the game and retry; contact server staff if it still fails.");
            case POLICY_VIOLATION:
                return Lang.tr("Admission check failed. The client mod list has the following issues:");
            case MAC_VERSION_NOT_ALLOWED:
            default:
                return Lang.tr("Connection denied: this mod version is not in the server's allow list.\n"
                        + "Server requires version: {}; your version: {}.",
                        arg(args, 0), arg(args, 1));
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
                return Lang.tr("Missing required mod: ")
                        + id + Lang.tr(" (required ") + exp + "）";
            case VERSION_MISMATCH:
                return Lang.tr("Mod version mismatch: ") + id
                        + Lang.tr(" (required ") + exp
                        + Lang.tr(", yours ") + (act == null ? Lang.tr("unknown") : act) + "）";
            case BLACKLISTED:
                return Lang.tr("Blacklisted mod installed: ") + id
                        + Lang.tr(" (version ") + (act == null ? "?" : act) + "）";
            case NOT_WHITELISTED:
                return Lang.tr("Non-whitelisted mod installed: ") + id
                        + Lang.tr(" (version ") + (act == null ? "?" : act) + "）";
            case LOADER_MISMATCH:
            default:
                return Lang.tr("Loader mismatch (expected ") + exp
                        + Lang.tr(", actual ") + act + "）";
        }
    }
}
