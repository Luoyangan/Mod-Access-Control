package mcyszl.top.mod_access_control.core.session;

/**
 * 单条违规记录（内存环形缓冲，最新在前）。
 */
public final class ViolationRecord {

    private final long timeMs;
    private final String playerName;
    private final String playerUuid;
    private final String summary;

    public ViolationRecord(long timeMs, String playerName, String playerUuid, String summary) {
        this.timeMs = timeMs;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.summary = summary;
    }

    public long timeMs() {
        return timeMs;
    }

    public String playerName() {
        return playerName;
    }

    public String playerUuid() {
        return playerUuid;
    }

    public String summary() {
        return summary;
    }

    /** 便于管理界面展示的格式化文本（默认 locale 日期）。 */
    public String displayText() {
        return java.time.Instant.ofEpochMilli(timeMs) + " " + playerName + " " + summary;
    }
}
