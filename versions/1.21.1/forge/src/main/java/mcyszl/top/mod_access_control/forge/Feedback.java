// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.feedback.DisconnectReason;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.i18n.Lang;
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
        List<String> footer = m.footer();
        if (footer != null) {
            for (String line : footer) {
                c.append(Component.literal("\n" + line).withStyle(ChatFormatting.GRAY));
            }
        }
        return c;
    }

    private static String headerOf(DisconnectReason r, String[] args) {
        return switch (r) {
            case NO_CLIENT_MOD ->
                    Lang.tr("Connection denied: the client does not have \"Mod Access Control\" installed.\n"
                            + "This server requires all clients to install this mod for admission checks.");
            case PROTOCOL_MISMATCH ->
                    Lang.tr("Admission protocol version mismatch (server={}, client={}).\n"
                            + "Please update the mod from the channel specified by the server and rejoin.",
                            arg(args, 0), arg(args, 1));
            case LOADER_MISMATCH ->
                    Lang.tr("Incompatible mod loader: the server requires {}, your client is {}.\n"
                            + "Please use the same mod loader as the server.",
                            arg(args, 0), arg(args, 1));
            case HANDSHAKE_TIMEOUT ->
                    Lang.tr("Admission check timed out. If you already have this mod installed, "
                            + "restart the game and retry; contact server staff if it still fails.");
            case POLICY_VIOLATION ->
                    Lang.tr("Admission check failed. The client mod list has the following issues:");
            case MAC_VERSION_NOT_ALLOWED ->
                    Lang.tr("Connection denied: this mod version is not in the server's allow list.\n"
                            + "Server requires version: {}; your version: {}.",
                            arg(args, 0), arg(args, 1));
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
            case MISSING_REQUIRED ->
                    Lang.tr("Missing required mod: ")
                            + id + Lang.tr(" (required ") + exp + "）";
            case VERSION_MISMATCH ->
                    Lang.tr("Mod version mismatch: ") + id
                            + Lang.tr(" (required ") + exp
                            + Lang.tr(", yours ") + (act == null ? Lang.tr("unknown") : act) + "）";
            case BLACKLISTED ->
                    Lang.tr("Blacklisted mod installed: ") + id
                            + Lang.tr(" (version ") + (act == null ? "?" : act) + "）";
            case NOT_WHITELISTED ->
                    Lang.tr("Non-whitelisted mod installed: ") + id
                            + Lang.tr(" (version ") + (act == null ? "?" : act) + "）";
            case LOADER_MISMATCH ->
                    Lang.tr("Loader mismatch (expected ") + exp
                            + Lang.tr(", actual ") + act + "）";
        };
    }
}
