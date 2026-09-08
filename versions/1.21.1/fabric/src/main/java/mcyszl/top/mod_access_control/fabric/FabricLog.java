package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.Mac;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 日志桥：把 common 核心的日志委托给 Fabric Loader 自带的 slf4j（占位符同为 {}）。
 */
public final class FabricLog implements Mac.Log {

    private static final Logger LOG = LoggerFactory.getLogger("ModAccessControl");

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
