package mcyszl.top.mod_access_control.core.feedback;

/**
 * 拒绝玩家接入 / 踢出的原因类别（映射到玩家可见的标题翻译键）。
 */
public enum DisconnectReason {

    /** 客户端没有安装本模组（未在限定时间内完成阶段握手）。 */
    NO_CLIENT_MOD(Keys.KICK_HEADER_NO_CLIENT_MOD),
    /** 协议版本不一致。 */
    PROTOCOL_MISMATCH(Keys.KICK_HEADER_PROTOCOL),
    /** 加载器不兼容（严格模式）。 */
    LOADER_MISMATCH(Keys.KICK_HEADER_LOADER),
    /** 校验阶段超时。 */
    HANDSHAKE_TIMEOUT(Keys.KICK_HEADER_TIMEOUT),
    /** 规则校验未通过（缺失必需 Mod / 版本不符 / 黑白名单等）。 */
    POLICY_VIOLATION(Keys.KICK_HEADER_VIOLATION),
    /** 本模组版本不在服务器允许的接入版本列表中。 */
    MAC_VERSION_NOT_ALLOWED(Keys.KICK_HEADER_MAC_VERSION);

    private final String headerKey;

    DisconnectReason(String headerKey) {
        this.headerKey = headerKey;
    }

    public String headerKey() {
        return headerKey;
    }
}
