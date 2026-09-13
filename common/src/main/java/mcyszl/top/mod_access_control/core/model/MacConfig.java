// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端主配置文件（JSON）的完整数据模型。
 *
 * <p>字段均使用小写开头的 camelCase，文件位于
 * {@code <config>/mod_access_control.json}，由三个加载器共用同一格式。</p>
 */
public class MacConfig {

    /** 配置文件结构版本。 */
    private int configVersion = 1;

    /** 总开关。 */
    private boolean enabled = true;

    /** 是否在“内置服务器”（单人存档/局域网）中也强制执行；默认只对专用服务器生效。 */
    private boolean enforceIntegratedServer = false;

    /** 客户端必须安装本模组；未安装者在握手超时后被拒绝。 */
    private boolean requireClientMod = true;

    /** 严格模式下要求客户端加载器标识与服务端一致。 */
    private boolean strictLoader = true;

    /** 每个握手阶段的等待秒数（登录阶段/完整列表阶段）。 */
    private int handshakeTimeoutSeconds = 10;

    /** 必需 Mod 的校验模式。 */
    private String requiredCheckMode = "presence";

    /** 必需 Mod 清单。 */
    private List<RequiredModRule> requiredMods = new ArrayList<>();

    /** 白名单 / 黑名单策略配置。 */
    private PolicyConfig policy = new PolicyConfig();

    /** 额外忽略的 mod id（本模组自身与 minecraft 恒被忽略）。 */
    private List<String> ignoredModIds = new ArrayList<>();

    /** 是否把违规事件写入服务端日志。 */
    private boolean logViolations = true;

    /**
     * 豁免名单：条目为玩家名（忽略大小写）或以 {@code uuid:} 开头的 UUID。
     * 被豁免的玩家不做握手、不发任何网络消息、不参与任何校验。
     */
    private List<String> exemptPlayers = new ArrayList<>();

    /** 是否默认豁免服务端 OP（不参与任何校验）。 */
    private boolean exemptOps = false;

    /**
     * 试运行模式：违规（含未安装本模组 / 规则不通过）只记日志、记 recent 并
     * 通知管理员，不实际踢出玩家。用于上线前验证规则是否误伤。
     */
    private boolean dryRun = false;

    /** 踢出消息底部“提示区”是否显示（原因明细行不受此开关影响）。默认开启。 */
    private boolean kickFooterEnabled = true;

    /** 踢出消息底部“提示区”之后追加的自定义行（逐行展示），可为空。 */
    private List<String> kickFooterLines = new ArrayList<>();

    /**
     * 允许接入的本模组（Mod Access Control）版本白名单，精确匹配客户端上报的 macVersion。
     * 空列表 = 放行任意版本（默认）。条目为大小写不敏感的版本字符串。
     */
    private List<String> allowedMacVersions = new ArrayList<>();

    /** 服务端语言（命令 / 日志 / 管理广播文案）：auto | zh_cn | en_us。 */
    private String language = "auto";

    /**
     * 踢出消息是否展示逐条违规明细（缺失 / 版本不符 / 违规 Mod 行）。
     * 使用包装类型：旧配置缺字段时为 null，按“开启”处理（默认 true）。
     */
    private Boolean kickShowDetails = Boolean.TRUE;

    /**
     * 管理员广播是否展示逐条违规明细；关闭时只显示违规类型与数量。
     * 旧配置缺字段时为 null，按“开启”处理（默认 true）。
     */
    private Boolean adminShowDetails = Boolean.TRUE;

    public int getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion = configVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnforceIntegratedServer() {
        return enforceIntegratedServer;
    }

    public void setEnforceIntegratedServer(boolean enforceIntegratedServer) {
        this.enforceIntegratedServer = enforceIntegratedServer;
    }

    public boolean isRequireClientMod() {
        return requireClientMod;
    }

    public void setRequireClientMod(boolean requireClientMod) {
        this.requireClientMod = requireClientMod;
    }

    public boolean isStrictLoader() {
        return strictLoader;
    }

    public void setStrictLoader(boolean strictLoader) {
        this.strictLoader = strictLoader;
    }

    public int getHandshakeTimeoutSeconds() {
        return handshakeTimeoutSeconds;
    }

    public void setHandshakeTimeoutSeconds(int handshakeTimeoutSeconds) {
        this.handshakeTimeoutSeconds = Math.max(1, handshakeTimeoutSeconds);
    }

    public CheckMode requiredMode() {
        return CheckMode.byKey(requiredCheckMode);
    }

    public String getRequiredCheckMode() {
        return requiredCheckMode;
    }

    public void setRequiredCheckMode(String requiredCheckMode) {
        this.requiredCheckMode = CheckMode.byKey(requiredCheckMode).key();
    }

    public List<RequiredModRule> getRequiredMods() {
        return requiredMods;
    }

    public void setRequiredMods(List<RequiredModRule> requiredMods) {
        this.requiredMods = requiredMods == null ? new ArrayList<>() : requiredMods;
    }

    public PolicyConfig getPolicy() {
        return policy;
    }

    public void setPolicy(PolicyConfig policy) {
        this.policy = policy == null ? new PolicyConfig() : policy;
    }

    public List<String> getIgnoredModIds() {
        return ignoredModIds;
    }

    public void setIgnoredModIds(List<String> ignoredModIds) {
        this.ignoredModIds = ignoredModIds == null ? new ArrayList<>() : ignoredModIds;
    }

    public boolean isLogViolations() {
        return logViolations;
    }

    public void setLogViolations(boolean logViolations) {
        this.logViolations = logViolations;
    }

    public List<String> getExemptPlayers() {
        return exemptPlayers;
    }

    public void setExemptPlayers(List<String> exemptPlayers) {
        this.exemptPlayers = exemptPlayers == null ? new ArrayList<>() : exemptPlayers;
    }

    public boolean isExemptOps() {
        return exemptOps;
    }

    public void setExemptOps(boolean exemptOps) {
        this.exemptOps = exemptOps;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isKickFooterEnabled() {
        return kickFooterEnabled;
    }

    public void setKickFooterEnabled(boolean kickFooterEnabled) {
        this.kickFooterEnabled = kickFooterEnabled;
    }

    public List<String> getKickFooterLines() {
        return kickFooterLines;
    }

    public void setKickFooterLines(List<String> kickFooterLines) {
        this.kickFooterLines = kickFooterLines == null ? new ArrayList<>() : kickFooterLines;
    }

    public List<String> getAllowedMacVersions() {
        return allowedMacVersions;
    }

    public void setAllowedMacVersions(List<String> allowedMacVersions) {
        this.allowedMacVersions = allowedMacVersions == null ? new ArrayList<>() : allowedMacVersions;
    }

    public String getLanguage() {
        return language == null ? "auto" : language;
    }

    public void setLanguage(String language) {
        this.language = language == null || language.trim().isEmpty() ? "auto" : language.trim();
    }

    /** 是否向被拒玩家展示逐条违规明细（旧配置缺字段时默认开启）。 */
    public boolean showKickDetails() {
        return kickShowDetails == null || kickShowDetails;
    }

    public Boolean getKickShowDetails() {
        return kickShowDetails;
    }

    public void setKickShowDetails(Boolean kickShowDetails) {
        this.kickShowDetails = kickShowDetails;
    }

    /** 是否向管理员广播展示逐条违规明细（旧配置缺字段时默认开启）。 */
    public boolean showAdminDetails() {
        return adminShowDetails == null || adminShowDetails;
    }

    public Boolean getAdminShowDetails() {
        return adminShowDetails;
    }

    public void setAdminShowDetails(Boolean adminShowDetails) {
        this.adminShowDetails = adminShowDetails;
    }

    /**
     * 把反序列化后可能缺失的字段补齐默认值，保证旧版配置文件（缺少 v1.1 新增字段）
     * 加载后所有集合非空、枚举字段为合法键。
     */
    public void normalize() {
        if (requiredMods == null) {
            requiredMods = new ArrayList<>();
        }
        if (ignoredModIds == null) {
            ignoredModIds = new ArrayList<>();
        }
        if (exemptPlayers == null) {
            exemptPlayers = new ArrayList<>();
        }
        if (kickFooterLines == null) {
            kickFooterLines = new ArrayList<>();
        }
        if (allowedMacVersions == null) {
            allowedMacVersions = new ArrayList<>();
        }
        if (policy == null) {
            policy = new PolicyConfig();
        }
        policy.normalize();
        for (RequiredModRule r : requiredMods) {
            if (r != null) {
                r.normalize();
            }
        }
        language = getLanguage();
    }

    /**
     * 策略配置（白名单 / 黑名单 / 双模式+复检）。
     */
    public static class PolicyConfig {

        /** 策略模式：whitelist | blacklist | switch。默认黑名单（开箱即用不误伤）。 */
        private String mode = "blacklist";

        /**
         * 双模式(SWITCH)下当前生效的策略；在 whitelist/blacklist 模式下忽略。
         * 使用小写字符串，便于命令直接读写。
         */
        private String activeMode = "whitelist";

        /**
         * 定期复检间隔（秒）；0 表示不复检。
         * SWITCH 模式建议开启；其他模式若大于 0 也会生效。
         */
        private int recheckIntervalSeconds = 60;

        /** 白名单（v1.1：条目为 mod id，支持 {@code *} 通配符与可选版本约束）。 */
        private List<PolicyEntry> whitelist = new ArrayList<>();

        /** 黑名单（v1.1：条目为 mod id，支持 {@code *} 通配符与可选版本约束）。 */
        private List<PolicyEntry> blacklist = new ArrayList<>();

        public PolicyMode mode() {
            return PolicyMode.byKey(mode);
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = PolicyMode.byKey(mode).key();
        }

        public PolicyMode activeMode() {
            PolicyMode base = mode();
            return PolicyMode.byKey(base == PolicyMode.SWITCH ? activeMode : base.key());
        }

        public String getActiveMode() {
            return activeMode;
        }

        public void setActiveMode(String activeMode) {
            this.activeMode = PolicyMode.byKey(activeMode).key();
        }

        public int getRecheckIntervalSeconds() {
            return recheckIntervalSeconds;
        }

        public void setRecheckIntervalSeconds(int recheckIntervalSeconds) {
            this.recheckIntervalSeconds = Math.max(0, recheckIntervalSeconds);
        }

        public List<PolicyEntry> getWhitelist() {
            return whitelist;
        }

        public void setWhitelist(List<PolicyEntry> whitelist) {
            this.whitelist = whitelist == null ? new ArrayList<>() : whitelist;
        }

        public List<PolicyEntry> getBlacklist() {
            return blacklist;
        }

        public void setBlacklist(List<PolicyEntry> blacklist) {
            this.blacklist = blacklist == null ? new ArrayList<>() : blacklist;
        }

        /** 补齐默认值（旧配置文件可能缺字段）。 */
        public void normalize() {
            if (whitelist == null) {
                whitelist = new ArrayList<>();
            }
            if (blacklist == null) {
                blacklist = new ArrayList<>();
            }
            mode = PolicyMode.byKey(mode).key();
            activeMode = PolicyMode.byKey(activeMode).key();
        }
    }

    /** 返回一份全新的默认配置。 */
    public static MacConfig defaults() {
        MacConfig cfg = new MacConfig();
        cfg.getRequiredMods().clear();
        cfg.getPolicy().getWhitelist().clear();
        cfg.getPolicy().getBlacklist().clear();
        return cfg;
    }
}
