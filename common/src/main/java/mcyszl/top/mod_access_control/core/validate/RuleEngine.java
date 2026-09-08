package mcyszl.top.mod_access_control.core.validate;

import mcyszl.top.mod_access_control.core.model.CheckMode;
import mcyszl.top.mod_access_control.core.model.PolicyMode;
import mcyszl.top.mod_access_control.core.model.RequiredModRule;
import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;
import mcyszl.top.mod_access_control.core.version.SemVer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 规则校验引擎（纯逻辑、无副作用）。
 *
 * <p>负责：
 * <ul>
 *   <li>必需 Mod 存在性 / 版本范围 / 严格匹配（三种 {@link CheckMode}）；</li>
 *   <li>白名单 / 黑名单（含 SWITCH 下当前生效的策略）；</li>
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
        Map<String, String> cv = clientVersions == null ? Map.of() : clientVersions;
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
            // VERSION_RANGE / STRICT：校验版本约束。
            if (!versionAllowed(rule, actual)) {
                problems.add(new Problem(ProblemType.VERSION_MISMATCH,
                        rule.getId(), rule.constraintText(), actual));
            }
        }
        return problems;
    }

    /**
     * 校验客户端完整 Mod 列表是否命中白名单 / 黑名单。
     *
     * @param activePolicy 当前生效策略（SWITCH 时已由调用方解析为实际策略）
     */
    public static List<Problem> checkPolicy(List<ClientMod> clientMods,
                                            PolicyMode activePolicy,
                                            List<String> whitelist,
                                            List<String> blacklist,
                                            Set<String> ignoredIds) {
        List<Problem> problems = new ArrayList<>();
        if (clientMods == null || activePolicy == null || activePolicy == PolicyMode.SWITCH) {
            return problems;
        }
        Set<String> ignore = ignoredIds == null ? Set.of() : ignoredIds;
        for (ClientMod mod : clientMods) {
            if (mod.id == null || ignore.contains(mod.id)) {
                continue;
            }
            if (activePolicy == PolicyMode.WHITELIST
                    && (whitelist == null || !whitelist.contains(mod.id))) {
                problems.add(new Problem(ProblemType.NOT_WHITELISTED, mod.id, null, mod.version));
            } else if (activePolicy == PolicyMode.BLACKLIST
                    && blacklist != null && blacklist.contains(mod.id)) {
                problems.add(new Problem(ProblemType.BLACKLISTED, mod.id, null, mod.version));
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

    private static boolean versionAllowed(RequiredModRule rule, String actualVersion) {
        SemVer actual = SemVer.parse(actualVersion);
        List<RequiredModRule.Bound> bounds = rule.effectiveBounds();
        if (bounds.isEmpty()) {
            // 无版本约束：存在即可（缺失已在调用方处理）。
            return true;
        }
        for (RequiredModRule.Bound b : bounds) {
            if (!matchBound(b, actual)) {
                return false;
            }
        }
        return true;
    }

    /** 用一条操作符约束比对客户端实际版本。 */
    private static boolean matchBound(RequiredModRule.Bound bound, SemVer actual) {
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
        return switch (bound.getOp()) {
            case "=" -> c == 0;
            case "!=" -> c != 0;
            case ">" -> c > 0;
            case ">=" -> c >= 0;
            case "<" -> c < 0;
            case "<=" -> c <= 0;
            default -> true;
        };
    }
}
