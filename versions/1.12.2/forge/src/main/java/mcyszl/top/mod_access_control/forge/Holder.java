package mcyszl.top.mod_access_control.forge;

import mcyszl.top.mod_access_control.core.service.MacService;

/**
 * 运行期单例持有者：在 mod preInit 阶段初始化，供事件 / 网络 / 命令层取用。
 */
public final class Holder {

    private static MacForgeBridge bridge;
    private static MacService service;

    private Holder() {
    }

    public static synchronized void init() {
        bridge = new MacForgeBridge();
        service = new MacService(bridge);
    }

    public static MacService service() {
        return service;
    }

    public static MacForgeBridge bridge() {
        return bridge;
    }
}
