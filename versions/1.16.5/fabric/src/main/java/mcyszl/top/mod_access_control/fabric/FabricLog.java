// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.Mac;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fabric 日志桥（MC 1.16.5）：把 common 核心的日志委托给游戏自带的 log4j2（占位符同为 {}）。
 *
 * <p>1.16.5 的运行时与 dev classpath 均只提供 log4j2（slf4j 由更高版本 MC / Loader 引入），
 * 因此本模块直接使用 log4j2 API。</p>
 */
public final class FabricLog implements Mac.Log {

    private static final Logger LOG = LogManager.getLogger("ModAccessControl");

    @Override
    public void info(String format, Object... args) {
        LOG.info("[MAC] " + format, args);
    }

    @Override
    public void warn(String format, Object... args) {
        LOG.warn("[MAC] " + format, args);
    }

    @Override
    public void error(String format, Object... args) {
        LOG.error("[MAC] " + format, args);
    }
}
