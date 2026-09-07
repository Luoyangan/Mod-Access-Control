package mcyszl.top.mod_access_control.core;

/**
 * Mod Access Control 全局常量与日志入口。
 *
 * <p>本类属于“跨加载器统一核心”，不依赖任何加载器 / Minecraft / slf4j 的类，
 * 被 Forge / Fabric / NeoForge 适配层共同编译进各自的 jar。
 * 日志通过 {@link Log} 接口抽象：核心内置“控制台直出”实现保证任意环境可用，
 * 各加载器适配层可在初始化时用 {@link #setLogger(Log)} 桥接到平台日志系统。</p>
 */
public final class Mac {

    /** 统一 mod id（与三个加载器的元数据文件保持一致）。 */
    public static final String MOD_ID = "mod_access_control";

    /** 中文名 / 显示名。 */
    public static final String MOD_NAME = "Mod Access Control";

    /** 逻辑握手协议版本：不匹配则视为协议不一致（登录阶段校验）。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 网络通道 resource path（命名空间为 MOD_ID）。 */
    public static final String CHANNEL_PATH = "main";

    /** 加载器标识（协议中使用）。 */
    public static final String LOADER_FORGE = "forge";
    public static final String LOADER_FABRIC = "fabric";
    public static final String LOADER_NEOFORGE = "neoforge";

    /** 默认忽略的“基础设施” mod id（不参与白名单/黑名单/必需校验）。 */
    public static final String[] ALWAYS_IGNORED_MODS = {"minecraft"};

    /** 违规记录最多保留条数。 */
    public static final int MAX_VIOLATION_LOG = 200;

    /** 服务端每个握手阶段最多等待的刻数换算系数：秒 -> tick。 */
    public static final int TICKS_PER_SECOND = 20;

    /** 最小日志抽象：支持 {@code {}} 占位符，语义与 slf4j/log4j 一致。 */
    public interface Log {
        void info(String format, Object... args);

        void warn(String format, Object... args);

        void error(String format, Object... args);
    }

    private static volatile Log LOGGER = new ConsoleLog();

    private Mac() {
    }

    /** 返回当前日志实现。 */
    public static Log logger() {
        return LOGGER;
    }

    /** 由适配层桥接到平台日志（如 log4j）；传入 null 恢复默认控制台实现。 */
    public static void setLogger(Log log) {
        LOGGER = log == null ? new ConsoleLog() : log;
    }

    /** 把 {@code {}} 占位符格式化成普通文本。 */
    public static String format(String format, Object... args) {
        if (format == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return format;
        }
        StringBuilder sb = new StringBuilder(format.length() + 32);
        int ai = 0;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '{' && i + 1 < format.length() && format.charAt(i + 1) == '}') {
                if (ai < args.length) {
                    sb.append(args[ai++]);
                } else {
                    sb.append("{}");
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 默认实现：直接打印到标准输出（任何无日志框架环境可用）。 */
    private static final class ConsoleLog implements Log {
        private synchronized void print(String level, String format, Object... args) {
            System.out.println("[MAC][" + level + "] " + Mac.format(format, args));
        }

        @Override
        public void info(String format, Object... args) {
            print("INFO", format, args);
        }

        @Override
        public void warn(String format, Object... args) {
            print("WARN", format, args);
        }

        @Override
        public void error(String format, Object... args) {
            print("ERROR", format, args);
        }
    }
}
