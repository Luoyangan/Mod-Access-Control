package mcyszl.top.mod_access_control.core.validate;

/**
 * 单个违规问题的结构化描述（供玩家提示 / 管理日志 / 违规记录共用）。
 */
public final class Problem {

    private final ProblemType type;
    /** 相关 mod id。 */
    private final String modId;
    /** 规则期望（如 ">=1.2.0"）。 */
    private final String expected;
    /** 客户端实际版本。 */
    private final String actual;

    public Problem(ProblemType type, String modId, String expected, String actual) {
        this.type = type;
        this.modId = modId;
        this.expected = expected;
        this.actual = actual;
    }

    public ProblemType type() {
        return type;
    }

    public String modId() {
        return modId;
    }

    public String expected() {
        return expected;
    }

    public String actual() {
        return actual;
    }

    /** 简体中文日志摘要。 */
    public String summary() {
        switch (type) {
            case MISSING_REQUIRED:
                return "缺少必需Mod[" + modId + "] 要求" + expected;
            case VERSION_MISMATCH:
                return "Mod[" + modId + "]版本不符 要求" + expected + " 实际" + (actual == null ? "无" : actual);
            case BLACKLISTED:
                return "命中黑名单Mod[" + modId + "] 版本" + (actual == null ? "?" : actual);
            case NOT_WHITELISTED:
                return "安装了白名单外Mod[" + modId + "] 版本" + (actual == null ? "?" : actual);
            case LOADER_MISMATCH:
            default:
                return "加载器不兼容 期望" + expected + " 实际" + actual;
        }
    }

    @Override
    public String toString() {
        return summary();
    }
}
