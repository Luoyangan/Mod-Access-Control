// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.validate;

import mcyszl.top.mod_access_control.core.feedback.Keys;
import mcyszl.top.mod_access_control.core.i18n.I18n;

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

    /** 当前服务端语言的摘要（管理日志 / 违规记录用）。 */
    public String summary() {
        return I18n.tr(lineKey(), args());
    }

    /** 该问题对应的翻译键（服务端 I18n 字典与 lang 文件共用键名）。 */
    public String lineKey() {
        switch (type) {
            case MISSING_REQUIRED:
                return Keys.LINE_MISSING_REQUIRED;
            case VERSION_MISMATCH:
                return Keys.LINE_VERSION_MISMATCH;
            case BLACKLISTED:
                return Keys.LINE_BLACKLISTED;
            case BLACKLISTED_VERSION:
                return Keys.LINE_BLACKLISTED_VERSION;
            case NOT_WHITELISTED:
                return Keys.LINE_NOT_WHITELISTED;
            case NOT_WHITELISTED_VERSION:
                return Keys.LINE_NOT_WHITELISTED_VERSION;
            case LOADER_MISMATCH:
            default:
                return Keys.LINE_LOADER;
        }
    }

    /** 摘要行占位参数（顺序与 lineKey 文案一致）。 */
    public Object[] args() {
        String id = modId == null ? "?" : modId;
        String act = actual == null ? "?" : actual;
        switch (type) {
            case MISSING_REQUIRED:
                return new Object[]{id, expected == null ? "*" : expected};
            case VERSION_MISMATCH:
                return new Object[]{id, expected == null ? "*" : expected,
                        actual == null ? I18n.tr("mac.server.audit.unknown_version") : actual};
            case BLACKLISTED:
            case NOT_WHITELISTED:
                return new Object[]{id, actual == null ? "?" : actual};
            case BLACKLISTED_VERSION:
            case NOT_WHITELISTED_VERSION:
                return new Object[]{id, expected == null ? "*" : expected, actual == null ? "?" : actual};
            case LOADER_MISMATCH:
            default:
                return new Object[]{expected == null ? "?" : expected, act};
        }
    }

    @Override
    public String toString() {
        return summary();
    }
}
