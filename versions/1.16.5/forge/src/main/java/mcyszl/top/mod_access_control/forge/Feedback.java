// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.feedback.DisconnectReason;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.i18n.Lang;
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
                return Lang.tr("Connection denied: this mod version is not in the server's allow list.\n"
                        + "Server requires version: {}; your version: {}.",
                        arg(args, 0), arg(args, 1));
            default:
                return Lang.tr("Admission check failed. The client mod list has the following issues:");
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
                return Lang.tr("Loader mismatch (expected ") + exp
                        + Lang.tr(", actual ") + act + "）";
            default:
                return Lang.tr("unknown") + ": " + id;
        }
    }
}
