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
    /** 客户端安装了黑名单 Mod 但版本不在允许/禁止约束内（v1.1：带版本约束的黑名单条目）。 */
    BLACKLISTED_VERSION,
    /** 白名单模式下客户端安装了名单之外的 Mod。 */
    NOT_WHITELISTED,
    /** 白名单模式：mod id 在名单内但版本不满足条目约束（v1.1）。 */
    NOT_WHITELISTED_VERSION,
    /** 加载器不兼容（STRICT）。 */
    LOADER_MISMATCH
}
