// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.validate;

import mcyszl.top.mod_access_control.core.model.CheckMode;
import mcyszl.top.mod_access_control.core.model.PolicyEntry;
import mcyszl.top.mod_access_control.core.model.PolicyMode;
import mcyszl.top.mod_access_control.core.model.RequiredModRule;
import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则校验引擎（纯逻辑、无副作用）。
 *
 * <p>负责：
 * <ul>
 *   <li>必需 Mod 存在性 / 版本范围 / 严格匹配（三种 {@link CheckMode}）；</li>
 *   <li>白名单 / 黑名单（条目支持 {@code *} 通配符与可选版本约束，
 *       无约束 = 全部版本；含 SWITCH 下当前生效的策略）；</li>
 *   <li>加载器兼容性（STRICT / 严格场景）。</li>
 * </ul>
 */
public final class RuleEngine {

    private RuleEngine() {
    }

    /**
     * 校验必需 Mod 集合。
     *
     * @param clientVersions 客户端上报的“必需 id -> 版本”（缺失项可能根本没有 key）
     * @param mode           校验模式
     * @return 违规问题列表（空表示通过）
     */
    public static List<Problem> checkRequired(List<RequiredModRule> rules,
                                              Map<String, String> clientVersions,
                                              CheckMode mode) {
        List<Problem> problems = new ArrayList<>();
        if (rules == null || rules.isEmpty()) {
            return problems;
        }
        Map<String, String> cv = clientVersions == null ? Collections.emptyMap() : clientVersions;
        for (RequiredModRule rule : rules) {
            String actual = cv.get(rule.getId());
            boolean present = actual != null && !actual.isEmpty();
            if (!present) {
                problems.add(new Problem(ProblemType.MISSING_REQUIRED,
                        rule.getId(), rule.constraintText(), null));
                continue;
            }
            // PRESENCE 只要求存在。
            if (mode == CheckMode.PRESENCE) {
                continue;
            }
            // VERSION_RANGE / STRICT：校验版本约束（无约束 = 任意版本通过）。
            if (!RequiredModRule.matchesBounds(rule.effectiveBounds(), actual)) {
                problems.add(new Problem(ProblemType.VERSION_MISMATCH,
                        rule.getId(), rule.constraintText(), actual));
            }
        }
        return problems;
    }

    /**
     * 校验客户端完整 Mod 列表是否命中白名单 / 黑名单。
     *
     * <p>条目匹配语义：id 命中（通配符/忽略大小写）且版本满足条目约束
     * （无约束 = 全部版本）才视作命中名单。</p>
     *
     * @param activePolicy 当前生效策略（SWITCH 时已由调用方解析为实际策略）
     */
    public static List<Problem> checkPolicy(List<ClientMod> clientMods,
                                            PolicyMode activePolicy,
                                            List<PolicyEntry> whitelist,
                                            List<PolicyEntry> blacklist,
                                            Set<String> ignoredIds) {
        List<Problem> problems = new ArrayList<>();
        if (clientMods == null || activePolicy == null || activePolicy == PolicyMode.SWITCH) {
            return problems;
        }
        Set<String> ignore = ignoredIds == null ? Collections.emptySet() : ignoredIds;
        for (ClientMod mod : clientMods) {
            if (mod.id == null || ignore.contains(mod.id)) {
                continue;
            }
            if (activePolicy == PolicyMode.WHITELIST) {
                PolicyEntry hit = firstHit(whitelist, mod);
                if (hit == null) {
                    // id 不在名单内，或 id 在但版本不满足条目约束。
                    boolean idListed = anyIdMatch(whitelist, mod.id);
                    problems.add(new Problem(
                            idListed ? ProblemType.NOT_WHITELISTED_VERSION : ProblemType.NOT_WHITELISTED,
                            mod.id,
                            idListed ? constraintSummary(whitelist, mod.id) : null,
                            mod.version));
                }
            } else if (activePolicy == PolicyMode.BLACKLIST) {
                PolicyEntry hit = firstHit(blacklist, mod);
                if (hit != null) {
                    // 命中且带版本约束 → 版本型黑名单违规；否则普通黑名单违规。
                    ProblemType type = hit.hasBounds()
                            ? ProblemType.BLACKLISTED_VERSION : ProblemType.BLACKLISTED;
                    problems.add(new Problem(type, mod.id,
                            hit.hasBounds() ? hit.constraintText() : null, mod.version));
                }
            }
        }
        return problems;
    }

    /**
     * 加载器兼容性检查（STRICT 场景）。
     */
    public static List<Problem> checkLoader(boolean strict,
                                            String serverLoader,
                                            String clientLoader) {
        List<Problem> problems = new ArrayList<>();
        if (strict && clientLoader != null && !clientLoader.equalsIgnoreCase(serverLoader)) {
            problems.add(new Problem(ProblemType.LOADER_MISMATCH,
                    "loader", serverLoader, clientLoader));
        }
        return problems;
    }

    /** 白名单判定：任一条目完整命中（id + 版本约束）即在名单内。 */
    private static boolean entryHit(List<PolicyEntry> entries, ClientMod mod) {
        return firstHit(entries, mod) != null;
    }

    /** 是否存在 id 命中（不考虑版本）的条目。 */
    private static boolean anyIdMatch(List<PolicyEntry> entries, String modId) {
        if (entries == null) {
            return false;
        }
        for (PolicyEntry e : entries) {
            if (e != null && e.matchesId(modId)) {
                return true;
            }
        }
        return false;
    }

    /** 汇总所有 id 命中条目的约束描述（“ 或 ”连接；无约束条目返回 *）。 */
    private static String constraintSummary(List<PolicyEntry> entries, String modId) {
        StringBuilder sb = new StringBuilder();
        if (entries != null) {
            for (PolicyEntry e : entries) {
                if (e != null && e.matchesId(modId)) {
                    if (sb.length() > 0) {
                        sb.append(" 或 ");
                    }
                    sb.append(e.constraintText());
                }
            }
        }
        return sb.length() == 0 ? "*" : sb.toString();
    }

    /** 返回第一个完整命中（id 命中且版本满足约束）的条目；无则 null。 */
    private static PolicyEntry firstHit(List<PolicyEntry> entries, ClientMod mod) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        for (PolicyEntry e : entries) {
            if (e != null && e.matchesId(mod.id) && e.matchesVersion(mod.version)) {
                return e;
            }
        }
        return null;
    }
}
