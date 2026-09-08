package mcyszl.top.mod_access_control.neoforge;

import mcyszl.top.mod_access_control.core.Mac;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NeoForge 日志桥：把 common 核心的日志委托给 NeoForge 的 log4j（占位符同为 {}）。
 */
public final class NeoLog implements Mac.Log {

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
