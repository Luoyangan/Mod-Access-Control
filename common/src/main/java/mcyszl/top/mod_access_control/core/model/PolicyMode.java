package mcyszl.top.mod_access_control.core.model;

/**
 * 白名单 / 黑名单策略模式（对应需求中的三种策略）。
 */
public enum PolicyMode {
    /** 白名单：客户端只能安装服务端白名单中的 Mod。 */
    WHITELIST("whitelist"),
    /** 黑名单：可安装任意 Mod，检测到黑名单中的 Mod 则拒绝。 */
    BLACKLIST("blacklist"),
    /** 双模式 + 定期复检：可运行时在黑白名单间切换，并按间隔复检。 */
    SWITCH("switch");

    private final String key;

    PolicyMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static PolicyMode byKey(String key) {
        if (key == null) {
            return WHITELIST;
        }
        for (PolicyMode m : values()) {
            if (m.key.equalsIgnoreCase(key.trim())) {
                return m;
            }
        }
        return WHITELIST;
    }
}
