// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.i18n.I18n;
import mcyszl.top.mod_access_control.core.validate.Problem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
 */
public final class Feedback {

    private Feedback() {
    }

    /** 由 KickMessage 渲染为可发送的断开原因组件（服务端按玩家客户端语言本地化）。 */
    public static Component disconnect(KickMessage m) {
        String lang = m.clientLanguage();
        MutableComponent c = Component.literal(I18n.trTo(lang, m.headerKey(), m.headerArgs()))
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        List<Problem> problems = m.problems();
        if (problems != null && !problems.isEmpty()) {
            for (Problem p : problems) {
                c.append(Component.literal("\n  - " + I18n.trTo(lang, p.lineKey(), p.args()))
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        for (String line : m.footer()) {
            c.append(Component.literal("\n" + line).withStyle(ChatFormatting.GRAY));
        }
        return c;
    }
}
