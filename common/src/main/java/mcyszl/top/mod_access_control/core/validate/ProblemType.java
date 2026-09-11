// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.validate;

/**
 * 违规问题类型。
 */
public enum ProblemType {
    /** 缺少必需 Mod。 */
    MISSING_REQUIRED,
    /** 必需 Mod 存在但版本不符合范围。 */
    VERSION_MISMATCH,
    /** 客户端安装了黑名单 Mod。 */
    BLACKLISTED,
    /** 白名单模式下客户端安装了名单之外的 Mod。 */
    NOT_WHITELISTED,
    /** 加载器不兼容（STRICT）。 */
    LOADER_MISMATCH
}
