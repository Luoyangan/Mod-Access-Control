package mcyszl.top.mod_access_control.fabric;

import mcyszl.top.mod_access_control.core.service.MacService;

/**
 * 运行期单例持有者：在入口点初始化阶段创建，供事件 / 网络 / 命令层取用。
 */
public final class Holder {

    private static MacFabricBridge bridge;
    private static MacService service;

    private Holder() {
    }

    public static synchronized void init() {
        bridge = new MacFabricBridge();
        service = new MacService(bridge);
    }

    public static MacService service() {
        return service;
    }

    public static MacFabricBridge bridge() {
        return bridge;
    }
}
