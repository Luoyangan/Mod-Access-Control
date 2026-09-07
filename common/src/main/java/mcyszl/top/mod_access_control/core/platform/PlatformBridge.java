package mcyszl.top.mod_access_control.core.platform;

import mcyszl.top.mod_access_control.core.feedback.KickMessage;

import java.nio.file.Path;

/**
 * 加载器适配层需实现的桥接接口（服务端核心与加载器能力的边界）。
 *
 * <p>核心逻辑（配置/校验/状态机/复检）全部位于 common 中，仅通过本接口
 * 获取加载器信息与执行“发送网络包 / 断开玩家”等平台动作。</p>
 */
public interface PlatformBridge {

    /** 加载器标识：forge / fabric / neoforge。 */
    String loaderType();

    /** 加载器版本字符串。 */
    String loaderVersion();

    /** 本模组版本字符串。 */
    String modVersion();

    /** 配置文件路径（不存在时由核心负责写入默认值）。 */
    Path configFile();

    /** 当前是否专用（dedicated）服务器；false 表示单机/客户端环境或集成服务器。 */
    boolean dedicatedServer();

    /** 向指定客户端发送一条 C→S 方向之外的服务端消息（kind + JSON 载荷）。 */
    void sendToClient(String playerUuid, String kind, String payloadJson);

    /** 断开指定玩家（发送理由消息）。若玩家不在线则忽略。 */
    void disconnectPlayer(String playerUuid, KickMessage message);

    /** 向在线管理员广播一条纯文本提示（聊天 + 日志）。 */
    default void notifyOps(String text) {
    }
}
