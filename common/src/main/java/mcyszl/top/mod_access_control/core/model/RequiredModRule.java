package mcyszl.top.mod_access_control.core.model;

/**
 * 单条“必需 Mod”规则：id + 可选版本约束（最低 / 最高 / 精确）。
 *
 * <p>版本约束优先级：{@code exactVersion} &gt; ({@code minVersion} + {@code maxVersion})。
 * 全部为空表示“任意版本，仅需存在”。</p>
 */
public class RequiredModRule {

    private String id;
    private String minVersion;
    private String maxVersion;
    private String exactVersion;

    public RequiredModRule() {
    }

    public RequiredModRule(String id) {
        this.id = id;
    }

    public RequiredModRule(String id, String minVersion, String maxVersion, String exactVersion) {
        this.id = id;
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
        this.exactVersion = exactVersion;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMinVersion() {
        return minVersion;
    }

    public void setMinVersion(String minVersion) {
        this.minVersion = blankToNull(minVersion);
    }

    public String getMaxVersion() {
        return maxVersion;
    }

    public void setMaxVersion(String maxVersion) {
        this.maxVersion = blankToNull(maxVersion);
    }

    public String getExactVersion() {
        return exactVersion;
    }

    public void setExactVersion(String exactVersion) {
        this.exactVersion = blankToNull(exactVersion);
    }

    /** 是否没有任何版本约束。 */
    public boolean anyVersionAllowed() {
        return exactVersion == null && minVersion == null && maxVersion == null;
    }

    /** 人类可读的约束描述（用于提示信息）。 */
    public String constraintText() {
        if (exactVersion != null) {
            return "=" + exactVersion;
        }
        if (minVersion != null && maxVersion != null) {
            return "[" + minVersion + " ~ " + maxVersion + "]";
        }
        if (minVersion != null) {
            return ">=" + minVersion;
        }
        if (maxVersion != null) {
            return "<=" + maxVersion;
        }
        return "*";
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() || "*".equals(t) ? null : t;
    }

    @Override
    public String toString() {
        return id + "(" + constraintText() + ")";
    }
}
