package mcyszl.top.mod_access_control.core.feedback;

import mcyszl.top.mod_access_control.core.validate.Problem;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条“踢出 / 拒绝加入”消息。
 *
 * <p>标题（reason header）带 {@code headerArgs}，主体为逐条 {@link Problem}
 * 违规行；由各加载器适配层翻译为玩家可见的 Component。</p>
 */
public final class KickMessage {

    private final DisconnectReason reason;
    private final String[] headerArgs;
    private final List<Problem> problems;

    public KickMessage(DisconnectReason reason, String[] headerArgs, List<Problem> problems) {
        this.reason = reason;
        this.headerArgs = headerArgs == null ? new String[0] : headerArgs;
        this.problems = problems == null ? new ArrayList<>() : problems;
    }

    public static KickMessage simple(DisconnectReason reason, String... args) {
        return new KickMessage(reason, args, List.of());
    }

    public static KickMessage violation(List<Problem> problems) {
        return new KickMessage(DisconnectReason.POLICY_VIOLATION, new String[0], problems);
    }

    public DisconnectReason reason() {
        return reason;
    }

    public String[] headerArgs() {
        return headerArgs;
    }

    public List<Problem> problems() {
        return problems;
    }

    /** 日志摘要（中文）。 */
    public String summary() {
        StringBuilder sb = new StringBuilder(reason.name());
        if (headerArgs.length > 0) {
            sb.append(" args=").append(String.join(",", headerArgs));
        }
        if (!problems.isEmpty()) {
            sb.append(" [");
            for (int i = 0; i < problems.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(problems.get(i).summary());
            }
            sb.append(']');
        }
        return sb.toString();
    }
}
