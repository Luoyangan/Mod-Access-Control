package mcyszl.top.mod_access_control.neoforge;

import mcyszl.top.mod_access_control.core.service.MacService;

/**
 * 运行期单例持有者：在 {@code @Mod} 构造阶段初始化，供事件 / 网络 / 命令层取用。
 */
public final class Holder {

    private static MacNeoBridge bridge;
    private static MacService service;

    private Holder() {
    }

    public static synchronized void init() {
        bridge = new MacNeoBridge();
        service = new MacService(bridge);
    }

    public static MacService service() {
        return service;
    }

    public static MacNeoBridge bridge() {
        return bridge;
    }
}
