// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.model;

import mcyszl.top.mod_access_control.core.version.SemVer;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条“必需 Mod”规则：id + 版本约束。
 *
 * <p>版本约束有两种写法（二选一即可）：
 * <ul>
 *   <li><b>操作符集合 {@code bounds}</b>（v1.1+）：{@code op} ∈ {@code = != > >= < <=}，
 *       多条之间为“且”关系；</li>
 *   <li><b>旧版字段</b> {@code minVersion / maxVersion / exactVersion}（v1.0 兼容，
 *       校验时按 {@code exact} 或 {@code [min,max]} 语义处理）。</li>
 * </ul>
 * 全部为空表示“任意版本，仅需存在”。</p>
 */
public class RequiredModRule {

    private String id;
    private String minVersion;
    private String maxVersion;
    private String exactVersion;
    /** v1.1：版本操作符约束集合；非空时优先于旧版字段参与校验。 */
    private List<Bound> bounds = new ArrayList<>();

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

    public List<Bound> getBounds() {
        return bounds;
    }

    public void setBounds(List<Bound> bounds) {
        this.bounds = bounds == null ? new ArrayList<>() : bounds;
    }

    /** 是否配置了操作符约束（bounds）。 */
    public boolean hasBounds() {
        return bounds != null && !bounds.isEmpty();
    }

    /** 是否没有任何版本约束。 */
    public boolean anyVersionAllowed() {
        return !hasBounds() && exactVersion == null && minVersion == null && maxVersion == null;
    }

    /** 补齐缺省（旧文件可能没有 bounds；条目可能为 null）。 */
    public void normalize() {
        if (bounds == null) {
            bounds = new ArrayList<>();
        }
        minVersion = blankToNull(minVersion);
        maxVersion = blankToNull(maxVersion);
        exactVersion = blankToNull(exactVersion);
        bounds.removeIf(b -> b == null || b.version == null || b.version.trim().isEmpty());
        for (Bound b : bounds) {
            b.version = b.version.trim();
            b.op = Bound.canonicalOp(b.op);
        }
    }

    /** 供命令使用的约束表达式解析：把 ">=1.0 <2.0"、"=1.2.3"、"1.0~2.0"、"--min v" 等写入 bounds。 */
    public void applySpec(String spec) {
        bounds = new ArrayList<>();
        minVersion = null;
        maxVersion = null;
        exactVersion = null;
        if (spec == null || spec.trim().isEmpty()) {
            return;
        }
        String[] toks = spec.trim().split("\\s+");
        for (int i = 0; i < toks.length; i++) {
            String t = toks[i].trim();
            if (t.isEmpty()) {
                continue;
            }
            // 命名词法 --min/--max/--exact
            if (i + 1 < toks.length) {
                if ("--min".equalsIgnoreCase(t)) {
                    bounds.add(new Bound(">=", toks[++i]));
                    continue;
                }
                if ("--max".equalsIgnoreCase(t)) {
                    bounds.add(new Bound("<=", toks[++i]));
                    continue;
                }
                if ("--exact".equalsIgnoreCase(t)) {
                    bounds.add(new Bound("=", toks[++i]));
                    continue;
                }
            }
            // 区间 a~b（两端可省略）
            if (t.contains("~")) {
                String[] range = t.split("~", 2);
                if (!range[0].trim().isEmpty()) {
                    bounds.add(new Bound(">=", range[0]));
                }
                if (range.length > 1 && !range[1].trim().isEmpty()) {
                    bounds.add(new Bound("<=", range[1]));
                }
                continue;
            }
            // 操作符前缀
            String op = null;
            String ver = null;
            if (t.startsWith(">=") || t.startsWith("<=") || t.startsWith("!=")) {
                op = t.substring(0, 2);
                ver = t.substring(2);
            } else if (t.startsWith(">") || t.startsWith("<") || t.startsWith("=")) {
                op = t.substring(0, 1);
                ver = t.substring(1);
            }
            if (op != null && !ver.trim().isEmpty()) {
                bounds.add(new Bound(op, ver));
            }
            // 其余裸 token 忽略（避免误把无法解析的内容当约束）
        }
        bounds.removeIf(b -> b.version == null || b.version.trim().isEmpty());
    }

    /** 人类可读的约束描述（用于提示信息 / 违规文案）。 */
    public String constraintText() {
        List<Bound> eff = effectiveBounds();
        if (eff.isEmpty()) {
            return "*";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < eff.size(); i++) {
            if (i > 0) {
                sb.append(" 且 ");
            }
            sb.append(eff.get(i).op).append(eff.get(i).version);
        }
        return sb.toString();
    }

    /**
     * 实际参与校验的约束集合：优先 bounds；否则由旧版字段合成
     * （exact 优先于 min+max）。
     */
    public List<Bound> effectiveBounds() {
        if (hasBounds()) {
            return bounds;
        }
        List<Bound> eff = new ArrayList<>();
        if (exactVersion != null) {
            eff.add(new Bound("=", exactVersion));
        } else {
            if (minVersion != null) {
                eff.add(new Bound(">=", minVersion));
            }
            if (maxVersion != null) {
                eff.add(new Bound("<=", maxVersion));
            }
        }
        return eff;
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() || "*".equals(t) ? null : t;
    }

    /**
     * 静态版本比对：实际版本是否满足全部约束（无约束 = 全部版本通过）。
     * 供必需 Mod 与黑白名单条目共用（版本约束语义一致：无约束 = 任意版本）。
     */
    public static boolean matchesBounds(List<Bound> bounds, String actualVersion) {
        if (bounds == null || bounds.isEmpty()) {
            return true;
        }
        SemVer actual = SemVer.parse(actualVersion);
        for (Bound b : bounds) {
            if (!matchBound(b, actual)) {
                return false;
            }
        }
        return true;
    }

    /** 用一条操作符约束比对客户端实际版本。 */
    static boolean matchBound(Bound bound, SemVer actual) {
        SemVer want = SemVer.parse(bound.getVersion());
        if (want == null) {
            // 规则里配置的版本无法解析：当作无约束，避免误伤。
            return true;
        }
        if (actual == null) {
            // 客户端版本无法解析：仅 “!=” 可视为“确非该版本”成立。
            return "!=".equals(bound.getOp());
        }
        int c = actual.compareTo(want);
        String op = bound.getOp();
        if ("=".equals(op)) {
            return c == 0;
        }
        if ("!=".equals(op)) {
            return c != 0;
        }
        if (">".equals(op)) {
            return c > 0;
        }
        if (">=".equals(op)) {
            return c >= 0;
        }
        if ("<".equals(op)) {
            return c < 0;
        }
        if ("<=".equals(op)) {
            return c <= 0;
        }
        return true;
    }

    @Override
    public String toString() {
        return id + "(" + constraintText() + ")";
    }

    /** 单条版本约束：{@code op + version}。 */
    public static class Bound {
        private String op;
        private String version;

        public Bound() {
        }

        public Bound(String op, String version) {
            this.op = canonicalOp(op);
            this.version = version == null ? null : version.trim();
        }

        public String getOp() {
            return op;
        }

        public void setOp(String op) {
            this.op = canonicalOp(op);
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version == null ? null : version.trim();
        }

        /** 统一操作符写法："==" 归一为 "="。 */
        static String canonicalOp(String op) {
            if (op == null) {
                return "=";
            }
            String t = op.trim();
            if ("==".equals(t)) {
                return "=";
            }
            if (t.isEmpty()) {
                return "=";
            }
            return t;
        }
    }
}
