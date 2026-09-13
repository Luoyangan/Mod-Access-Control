// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.i18n.I18n;
import mcyszl.top.mod_access_control.core.validate.Problem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketDisconnect;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * 玩家可见反馈渲染（踢出原因）。
 *
 * <p>v1.1.1 起统一为单一路径：由服务端按被踢玩家的客户端语言
 * （{@link KickMessage#clientLanguage()}，未知时回退服务端语言）渲染为纯文本
 * 后再下发，客户端界面与服务端日志/控制台显示完全一致。</p>
 *
 * <p>旧实现区分“翻译键路径 / legacy 纯文本路径”：翻译键交由客户端解析，
 * 但服务端日志仍按服务端语言渲染同一组件，会出现同一条消息中英混杂，
 * 故不再使用。</p>
 *
 * <p>1.12.2 没有 ChatFormatting/MutableComponent 链式 API，改用
 * {@link TextComponentString} + {@link TextFormatting}。</p>
 */
public final class Feedback {

    private Feedback() {
    }

    /** 由 KickMessage 渲染为可发送的断开原因组件（服务端按玩家客户端语言本地化）。 */
    public static ITextComponent disconnect(KickMessage m) {
        String lang = m.clientLanguage();
        TextComponentString c = new TextComponentString("");
        TextComponentString head = new TextComponentString(I18n.trTo(lang, m.headerKey(), m.headerArgs()));
        head.getStyle().setColor(TextFormatting.RED);
        head.getStyle().setBold(true);
        c.appendSibling(head);
        List<Problem> problems = m.problems();
        if (problems != null && !problems.isEmpty()) {
            for (Problem p : problems) {
                TextComponentString line = new TextComponentString(
                        "\n  - " + I18n.trTo(lang, p.lineKey(), p.args()));
                line.getStyle().setColor(TextFormatting.YELLOW);
                c.appendSibling(line);
            }
        }
        for (String line : m.footer()) {
            TextComponentString f = new TextComponentString("\n" + line);
            f.getStyle().setColor(TextFormatting.GRAY);
            c.appendSibling(f);
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
}
