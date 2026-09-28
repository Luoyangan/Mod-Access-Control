// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.validate;

import mcyszl.top.mod_access_control.core.i18n.Lang;

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
                return Lang.tr("Missing required mod [") + modId + "] "
                        + Lang.tr("required ") + expected;
            case VERSION_MISMATCH:
                return "Mod[" + modId + "]" + Lang.tr(" version mismatch, required ")
                        + expected + " " + Lang.tr("actual ")
                        + (actual == null ? Lang.tr("none") : actual);
            case BLACKLISTED:
                return Lang.tr("Blacklisted mod [") + modId + "] "
                        + Lang.tr("version ") + (actual == null ? "?" : actual);
            case NOT_WHITELISTED:
                return Lang.tr("Non-whitelisted mod [") + modId + "] "
                        + Lang.tr("version ") + (actual == null ? "?" : actual);
            case LOADER_MISMATCH:
            default:
                return Lang.tr("Loader mismatch, expected ") + expected
                        + " " + Lang.tr("actual ") + actual;
        }
    }

    @Override
    public String toString() {
        return summary();
    }
}
