// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.model;

/**
 * 必需 Mod 校验模式（对应需求中的 a/b/c 三种模式）。
 */
public enum CheckMode {
    /** 基础存在性检查：只验证客户端是否安装清单中的 Mod。 */
    PRESENCE("presence"),
    /** 版本范围检查：按 min/max/exact 校验每个必需 Mod 的版本。 */
    VERSION_RANGE("version_range"),
    /** 严格匹配检查：必须 Mod + 版本范围 + 加载器兼容性全部符合。 */
    STRICT("strict");

    private final String key;

    CheckMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static CheckMode byKey(String key) {
        if (key == null) {
            return PRESENCE;
        }
        for (CheckMode m : values()) {
            if (m.key.equalsIgnoreCase(key.trim())) {
                return m;
            }
        }
        return PRESENCE;
    }
}
