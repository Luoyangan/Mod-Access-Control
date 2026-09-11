package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.Mac;
import org.apache.logging.log4j.Logger;

/**
 * Forge 日志桥：把 common 核心的日志委托给 Forge 的 log4j（占位符同为 {}）。
 *
 * <p>1.12.2 的 Logger 实例来自 {@code FMLPreInitializationEvent#getModLog()}。</p>
 */
public final class ForgeLog implements Mac.Log {

    private final Logger log;

    public ForgeLog(Logger log) {
        this.log = log;
    }

    @Override
    public void info(String format, Object... args) {
        log.info(Mac.format(format, args));
    }

    @Override
    public void warn(String format, Object... args) {
        log.warn(Mac.format(format, args));
    }

    @Override
    public void error(String format, Object... args) {
        log.error(Mac.format(format, args));
    }
}
