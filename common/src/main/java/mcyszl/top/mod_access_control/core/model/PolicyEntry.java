// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 白名单 / 黑名单条目：mod id（支持 {@code *} 通配符）+ 可选版本约束。
 *
 * <p>版本约束语义（v1.1）：未设置任何约束 = 匹配全部版本；
 * 设置了约束（多条之间“且”）则客户端版本须全部满足。</p>
 *
 * <p>约束写法与必需 Mod 一致，由 {@link #applySpec(String)} 解析
 * （如 {@code >=1.0.0}、{@code 1.0~2.0}、{@code --exact 1.2.3}）。</p>
 */
public class PolicyEntry {

    private String id;
    /** 可选版本操作符约束集合；空 = 任意版本。 */
    private List<RequiredModRule.Bound> bounds = new ArrayList<>();

    public PolicyEntry() {
    }

    public PolicyEntry(String id) {
        this.id = id;
    }

    public PolicyEntry(String id, List<RequiredModRule.Bound> bounds) {
        this.id = id;
        this.bounds = bounds == null ? new ArrayList<>() : bounds;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<RequiredModRule.Bound> getBounds() {
        return bounds;
    }

    public void setBounds(List<RequiredModRule.Bound> bounds) {
        this.bounds = bounds == null ? new ArrayList<>() : bounds;
    }

    /** 是否配置了版本约束。 */
    public boolean hasBounds() {
        return bounds != null && !bounds.isEmpty();
    }

    /** 用命令的约束表达式写法解析版本约束（复用 RequiredModRule 的解析器）。 */
    public void applySpec(String spec) {
        RequiredModRule tmp = new RequiredModRule();
        tmp.applySpec(spec);
        this.bounds = tmp.getBounds();
    }

    /** 人类可读的约束描述；无约束返回 {@code *}（全部版本）。 */
    public String constraintText() {
        if (!hasBounds()) {
            return "*";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bounds.size(); i++) {
            if (i > 0) {
                sb.append(" 且 ");
            }
            RequiredModRule.Bound b = bounds.get(i);
            sb.append(b.getOp()).append(b.getVersion());
        }
        return sb.toString();
    }

    /**
     * mod id 是否命中本条目（忽略大小写；{@code *} 通配符匹配任意字符序列）。
     */
    public boolean matchesId(String modId) {
        if (id == null || modId == null) {
            return false;
        }
        if (id.contains("*")) {
            return wildcardPattern(id).matcher(modId).matches();
        }
        return id.equalsIgnoreCase(modId);
    }

    /** 版本是否满足本条目约束；无约束 = 全部版本通过。 */
    public boolean matchesVersion(String actualVersion) {
        if (!hasBounds()) {
            return true;
        }
        return RequiredModRule.matchesBounds(bounds, actualVersion);
    }

    /** 补齐缺省（旧文件可能缺字段；条目可能为 null）。 */
    public void normalize() {
        if (id != null) {
            id = id.trim();
        }
        if (bounds == null) {
            bounds = new ArrayList<>();
        }
        bounds.removeIf(b -> b == null || b.getVersion() == null || b.getVersion().trim().isEmpty());
        for (RequiredModRule.Bound b : bounds) {
            b.setVersion(b.getVersion().trim());
            b.setOp(b.getOp());
        }
    }

    @Override
    public String toString() {
        return id + "(" + constraintText() + ")";
    }

    /** 把 {@code foo*bar} 形态的通配符编译成整串匹配的正则（忽略大小写）。 */
    static Pattern wildcardPattern(String idWithWildcard) {
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < idWithWildcard.length(); i++) {
            char c = idWithWildcard.charAt(i);
            if (c == '*') {
                re.append(".*");
            } else if (c == '\\' || c == '.' || c == '+' || c == '?' || c == '^'
                    || c == '$' || c == '(' || c == ')' || c == '[' || c == ']'
                    || c == '{' || c == '}' || c == '|') {
                re.append('\\').append(c);
            } else {
                re.append(c);
            }
        }
        return Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE);
    }
}
