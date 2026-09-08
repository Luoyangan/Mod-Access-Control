package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.Mac;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Forge 日志桥：把 common 核心的日志委托给 Forge 的 log4j（占位符同为 {}）。
 */
public final class ForgeLog implements Mac.Log {

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
