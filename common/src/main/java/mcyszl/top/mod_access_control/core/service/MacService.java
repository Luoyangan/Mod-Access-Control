// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.service;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.config.ConfigManager;
import mcyszl.top.mod_access_control.core.feedback.DisconnectReason;
import mcyszl.top.mod_access_control.core.feedback.FeedbackText;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.i18n.I18n;
import mcyszl.top.mod_access_control.core.model.CheckMode;
import mcyszl.top.mod_access_control.core.model.MacConfig;
import mcyszl.top.mod_access_control.core.model.PolicyEntry;
import mcyszl.top.mod_access_control.core.model.PolicyMode;
import mcyszl.top.mod_access_control.core.model.RequiredModRule;
import mcyszl.top.mod_access_control.core.network.Json;
import mcyszl.top.mod_access_control.core.network.MacPackets;
import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Request;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage1Response;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage2Request;
import mcyszl.top.mod_access_control.core.network.MacPackets.Stage2Response;
import mcyszl.top.mod_access_control.core.platform.PlatformBridge;
import mcyszl.top.mod_access_control.core.session.Session;
import mcyszl.top.mod_access_control.core.session.Session.Phase;
import mcyszl.top.mod_access_control.core.session.ViolationRecord;
import mcyszl.top.mod_access_control.core.storage.ModHistoryStore;
import mcyszl.top.mod_access_control.core.storage.ModRecord;
import mcyszl.top.mod_access_control.core.validate.Problem;
import mcyszl.top.mod_access_control.core.validate.RuleEngine;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 模组准入控制核心服务（仅运行于服务端逻辑侧）。
 *
 * <p>职责：
 * <ol>
 *   <li>玩家加入 -> 启动两阶段握手（登录阶段最小信息 / 进入游戏阶段完整列表）；</li>
 *   <li>阶段超时与策略定期复检（按服务器刻驱动）；</li>
 *   <li>调用 {@link RuleEngine} 做规则校验，违规即通过 {@link PlatformBridge} 踢出并记录；</li>
 *   <li>为管理命令提供状态与违规记录查询。</li>
 * </ol>
 */
public final class MacService {

    private final PlatformBridge bridge;
    private final ConfigManager configManager;
    private final Map<String, Session> sessions = new HashMap<>();
    private final Deque<ViolationRecord> violations = new ArrayDeque<>();
    private final ModHistoryStore history;
    /** 供其他 mod 注册的违规监听器（公共 API 回调）。 */
    private final List<mcyszl.top.mod_access_control.api.MacApi.ViolationListener> listeners =
            new CopyOnWriteArrayList<>();
    private long tick;

    public MacService(PlatformBridge bridge) {
        this.bridge = bridge;
        this.configManager = new ConfigManager(bridge.configFile());
        // 玩家 Mod 清单历史记录与配置文件同目录（*.jsonl），纯文件追加式存储。
        this.history = new ModHistoryStore(
                bridge.configFile().resolveSibling("mod_access_control_history.jsonl"));
    }

    // ------------------------------------------------------------------ 生命周期

    public void onServerStarted() {
        tick = 0;
        configManager.load();
        I18n.refresh(configManager.current().getLanguage());
        sessions.clear();
        violations.clear();
        Mac.logger().info(I18n.tr("mac.server.started"),
                bridge.loaderType(), bridge.modVersion());
    }

    public void onServerStopping() {
        configManager.save();
        sessions.clear();
        Mac.logger().info(I18n.tr("mac.server.stopped"));
    }

    public ConfigManager configManager() {
        return configManager;
    }

    // ------------------------------------------------------------------ 服务器事件入口

    /**
     * 玩家加入：开启登录阶段握手。
     *
     * <p>注意：仅当配置要求客户端必须安装本模组（requireClientMod=true）时才发起
     * 握手；否则不建立会话、不发送任何网络消息（避免向无法解析本模组 payload 的
     * 客户端发送请求导致其被原版网络层踢出）。语义为“未强制安装 = 不校验，直接放行”。</p>
     */
    public void onPlayerJoin(String uuid, String playerName) {
        if (!shouldEnforce()) {
            return;
        }
        MacConfig cfg = configManager.current();
        if (!cfg.isRequireClientMod()) {
            Mac.logger().info(I18n.tr("mac.server.no_require_pass"), playerName);
            return;
        }
        sessions.remove(uuid); // 防御：历史残留
        Session s = new Session(uuid, playerName, tick);
        sessions.put(uuid, s);
        armDeadline(s);
        sendStage1Request(s);
    }

    /**
     * 适配层 JOIN 事件统一入口：把“预拦截”判断（总开关 / 豁免 / 未装本模组 / 试运行）
     * 收敛到核心，避免三种加载器重复实现且语义不一致。
     *
     * @param hasChannel 客户端是否注册了本模组的网络通道（未装本模组为 false）
     * @param isOp       该玩家是否为服务端 OP（用于 {@code exemptOps}）
     */
    public void handleLoginAttempt(String uuid, String playerName, boolean hasChannel, boolean isOp) {
        MacConfig cfg = configManager.current();
        if (!shouldEnforce()) {
            return;
        }
        if (isExempt(uuid, playerName, isOp)) {
            Mac.logger().info(I18n.tr("mac.server.exempt_hit"), playerName);
            return;
        }
        if (!cfg.isRequireClientMod()) {
            Mac.logger().info(I18n.tr("mac.server.no_require_pass"), playerName);
            return;
        }
        if (!hasChannel) {
            if (cfg.isDryRun()) {
                String summary = I18n.tr("mac.server.precheck.violation_only");
                logViolationOnly(uuid, playerName, summary);
                bridge.notifyOps(I18n.tr("mac.server.notify.noclientmod_dryrun", playerName));
                return;
            }
            // 客户端未装本模组：无会话可用，按平台桥能读到的客户端语言渲染纯文本。
            rejectImmediate(uuid, playerName,
                    KickMessage.simple(DisconnectReason.NO_CLIENT_MOD));
            return;
        }
        onPlayerJoin(uuid, playerName);
    }

    /** 该玩家是否命中豁免（exemptOps 或豁免名单条目）。 */
    public boolean isExempt(String uuid, String playerName, boolean isOp) {
        MacConfig cfg = configManager.current();
        if (cfg.isExemptOps() && isOp) {
            return true;
        }
        if (cfg.getExemptPlayers() != null) {
            for (String entry : cfg.getExemptPlayers()) {
                if (entry == null) {
                    continue;
                }
                String e = entry.trim();
                if (e.isEmpty()) {
                    continue;
                }
                if (e.regionMatches(true, 0, "uuid:", 0, 5)) {
                    if (uuid != null && e.substring(5).equalsIgnoreCase(uuid)) {
                        return true;
                    }
                } else if (e.equalsIgnoreCase(playerName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 玩家离开。 */
    public void onPlayerLeave(String uuid) {
        Session old = sessions.remove(uuid);
        if (old != null) {
            Mac.logger().info(I18n.tr("mac.server.session_end"), old.name(), old.phase());
        }
    }

    /** 服务器刻驱动：处理超时 + 定期复检。 */
    public void onServerTick() {
        tick++;
        if (sessions.isEmpty()) {
            return;
        }
        MacConfig cfg = configManager.current();
        long intervalTicks = cfg.getPolicy().getRecheckIntervalSeconds() * Mac.TICKS_PER_SECOND;
        boolean wantRecheck = cfg.isEnabled() && cfg.getPolicy().mode() == PolicyMode.SWITCH
                && intervalTicks > 0;
        // SWITCH 模式之外，若管理员显式配置了间隔，也允许复检（超集能力）。
        if (intervalTicks > 0 && cfg.isEnabled()) {
            wantRecheck = true;
        }
        List<Session> snapshot = new ArrayList<>(sessions.values());
        for (Session s : snapshot) {
            if (!sessions.containsKey(s.uuid())) {
                continue;
            }
            if (s.phase() == Phase.WAIT_STAGE1 || s.phase() == Phase.WAIT_STAGE2) {
                if (s.deadlineTick() > 0 && tick > s.deadlineTick()) {
                    handleTimeout(s);
                }
            } else if (wantRecheck && s.phase() == Phase.VERIFIED) {
                if (tick - s.lastRecheckTick() >= intervalTicks) {
                    s.lastRecheckTick(tick);
                    recheck(s);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 握手消息入口

    /** 收到登录阶段响应。 */
    public void receiveStage1(String uuid, Stage1Response resp) {
        Session s = sessions.get(uuid);
        if (s == null || s.phase() != Phase.WAIT_STAGE1) {
            Mac.logger().warn("[MAC] 忽略异常次序的 stage1 响应: {}", uuid);
            return;
        }
        MacConfig cfg = configManager.current();
        s.macVersion(resp.macVersion);
        s.loaderType(resp.loaderType);
        s.loaderVersion(resp.loaderVersion);
        s.clientLanguage(resp.language);
        if (resp.modVersions != null) {
            s.requiredReport().putAll(resp.modVersions);
        }
        List<Problem> problems = new ArrayList<>();
        if (resp.protocol != Mac.PROTOCOL_VERSION) {
            deny(s, KickMessage.simple(DisconnectReason.PROTOCOL_MISMATCH,
                    String.valueOf(Mac.PROTOCOL_VERSION), String.valueOf(resp.protocol)));
            return;
        }
        // 服务器限制本模组版本：仅在客户端上报的 macVersion 命中允许列表时才放行。
        List<String> allowedMac = cfg.getAllowedMacVersions();
        if (allowedMac != null && !allowedMac.isEmpty()
                && allowedMac.stream().noneMatch(v -> v.equalsIgnoreCase(s.macVersion()))) {
            deny(s, KickMessage.simple(DisconnectReason.MAC_VERSION_NOT_ALLOWED,
                    String.join(", ", allowedMac),
                    s.macVersion() == null || s.macVersion().isEmpty() ? "未知" : s.macVersion()));
            return;
        }
        boolean strict = cfg.requiredMode() == CheckMode.STRICT || cfg.isStrictLoader();
        problems.addAll(RuleEngine.checkLoader(strict, bridge.loaderType(), resp.loaderType));
        problems.addAll(RuleEngine.checkRequired(cfg.getRequiredMods(), resp.modVersions, cfg.requiredMode()));
        if (!problems.isEmpty()) {
            deny(s, KickMessage.violation(problems));
            return;
        }
        // 进入游戏阶段
        s.phase(Phase.WAIT_STAGE2);
        armDeadline(s);
        sendStage2Request(s);
        Mac.logger().info(I18n.tr("mac.server.pass_stage1"), s.name());
    }

    /** 收到进入游戏阶段响应（完整 Mod 列表）。 */
    public void receiveStage2(String uuid, Stage2Response resp) {
        Session s = sessions.get(uuid);
        if (s == null || s.phase() != Phase.WAIT_STAGE2) {
            Mac.logger().warn("[MAC] 忽略异常次序的 stage2 响应: {}", uuid);
            return;
        }
        List<ClientMod> mods = resp.mods == null ? Collections.<ClientMod>emptyList() : resp.mods;
        s.fullList().clear();
        s.fullList().addAll(mods);
        List<Problem> problems = runFullCheck(s);
        if (!problems.isEmpty()) {
            deny(s, KickMessage.violation(problems));
            return;
        }
        s.phase(Phase.VERIFIED);
        s.deadlineTick(0);
        s.lastRecheckTick(tick);
        recordHistory(s, ModRecord.RESULT_VERIFIED);
        Mac.logger().info(I18n.tr("mac.server.verified"), s.name(), s.fullList().size());
    }

    // ------------------------------------------------------------------ 复检 / 强制重查

    /** 用“最新规则”重新校验所有已通过玩家（管理命令 /mac recheck）。 */
    public void forceRecheckAll() {
        List<Session> snapshot = new ArrayList<>(sessions.values());
        int n = 0;
        for (Session s : snapshot) {
            if (s.phase() == Phase.VERIFIED) {
                s.lastRecheckTick(tick);
                recheck(s);
                n++;
            }
        }
        Mac.logger().info(I18n.tr("mac.server.recheck_done"), n);
    }

    /** 按配置间隔定期复检单个会话（使用其上报的完整列表，无需再次传输）。 */
    private void recheck(Session s) {
        List<Problem> problems = runFullCheck(s);
        if (problems.isEmpty()) {
            return;
        }
        Mac.logger().info(I18n.tr("mac.server.recheck_kick"), s.name());
        deny(s, KickMessage.violation(problems));
    }

    /** 依据当前配置对会话执行一次完整校验。 */
    private List<Problem> runFullCheck(Session s) {
        MacConfig cfg = configManager.current();
        List<Problem> problems = new ArrayList<>();
        List<ClientMod> mods = s.fullList();
        // 必需 Mod：以完整列表重新核验（可发现 stage1/stage2 不一致的伪造客户端）。
        Map<String, String> verByList = new LinkedHashMap<>();
        for (ClientMod m : mods) {
            if (m.id != null) {
                verByList.put(m.id, m.version);
            }
        }
        problems.addAll(RuleEngine.checkRequired(cfg.getRequiredMods(), verByList, cfg.requiredMode()));
        // 白名单 / 黑名单策略（SWITCH 时取当前生效策略）。
        PolicyMode active = cfg.getPolicy().activeMode();
        problems.addAll(RuleEngine.checkPolicy(mods, active,
                cfg.getPolicy().getWhitelist(), cfg.getPolicy().getBlacklist(), ignoredIds(cfg)));
        // 加载器（严格）。
        boolean strict = cfg.requiredMode() == CheckMode.STRICT || cfg.isStrictLoader();
        problems.addAll(RuleEngine.checkLoader(strict, bridge.loaderType(), s.loaderType()));
        return problems;
    }

    // ------------------------------------------------------------------ 命令辅助（只读查询）

    /**
     * 玩家客户端语言（如 {@code zh_cn}）：优先取平台桥（Forge / NeoForge 可直接从服务端
     * 读取玩家语言），否则回退到登录阶段客户端自报的语言（Fabric 端原版无语言接口，
     * 依赖客户端在 stage1 应答里上报）。两者都没有时返回 {@code null}，由调用方使用服务端语言。
     */
    public String clientLanguage(String uuid) {
        String lang = bridge.clientLanguage(uuid);
        if (lang != null && !lang.isEmpty()) {
            return lang;
        }
        Session s = sessions.get(uuid);
        return s == null ? null : s.clientLanguage();
    }

    /**
     * 供适配层在“握手尚未建立会话”前做预拦截时调用（如客户端未安装本模组）：
     * 记录违规、通知管理员并断开玩家，不建立会话。试运行模式下不实际踢出。
     */
    public void rejectImmediate(String uuid, String playerName, KickMessage msg) {
        MacConfig cfg = configManager.current();
        String clientLanguage = clientLanguage(uuid);
        msg = decorate(msg, clientLanguage).withClientLanguage(clientLanguage);
        if (cfg.isDryRun()) {
            logViolationOnly(uuid, playerName, I18n.tr("mac.server.precheck.dryrun", msg.summary()));
            bridge.notifyOps(I18n.tr("mac.server.notify.dryrun", playerName, msg.summary()));
            return;
        }
        violations.addFirst(new ViolationRecord(Instant.now().toEpochMilli(),
                playerName, uuid, msg.summary()));
        while (violations.size() > Mac.MAX_VIOLATION_LOG) {
            violations.removeLast();
        }
        fireListeners(uuid, playerName, msg, true);
        if (cfg.isLogViolations()) {
            Mac.logger().info(I18n.tr("mac.server.precheck.blocked"), playerName, msg.summary());
        }
        bridge.notifyOps(I18n.tr("mac.server.notify.violation", playerName,
                cfg.showAdminDetails() ? msg.summary() : msg.shortSummary()));
        bridge.disconnectPlayer(uuid, msg);
    }

    /** 状态概要（管理命令 /mac status 使用）。 */
    public List<String> statusLines() {
        MacConfig c = configManager.current();
        List<String> out = new ArrayList<>();
        out.add(I18n.tr("mac.server.status.title"));
        out.add(I18n.tr("mac.server.status.enabled", c.isEnabled()));
        out.add(I18n.tr("mac.server.status.scope", c.isEnforceIntegratedServer()
                ? I18n.tr("mac.server.status.scope.both")
                : I18n.tr("mac.server.status.scope.dedicated")));
        out.add(I18n.tr("mac.server.status.required_mode", c.requiredMode().key()));
        out.add(I18n.tr("mac.server.status.required_count", c.getRequiredMods().size()));
        out.add(I18n.tr("mac.server.status.policy_mode", c.getPolicy().mode().key()));
        if (c.getPolicy().mode() == PolicyMode.SWITCH) {
            out.add(I18n.tr("mac.server.status.active_mode", c.getPolicy().activeMode().key()));
            out.add(I18n.tr("mac.server.status.recheck_interval",
                    c.getPolicy().getRecheckIntervalSeconds()));
        }
        out.add(I18n.tr("mac.server.status.whitelist_count", c.getPolicy().getWhitelist().size()));
        out.add(I18n.tr("mac.server.status.blacklist_count", c.getPolicy().getBlacklist().size()));
        out.add(I18n.tr("mac.server.status.ignored", c.getIgnoredModIds()));
        out.add(I18n.tr("mac.server.status.dryrun", c.isDryRun()));
        out.add(I18n.tr("mac.server.status.exempt", c.getExemptPlayers().size(),
                c.isExemptOps() ? I18n.tr("mac.server.status.exempt.ops") : ""));
        out.add(I18n.tr("mac.server.status.language", c.getLanguage()));
        out.add(I18n.tr("mac.server.status.kick_details", c.showKickDetails()));
        out.add(I18n.tr("mac.server.status.admin_details", c.showAdminDetails()));
        out.add(I18n.tr("mac.server.status.allowed_mac", c.getAllowedMacVersions().isEmpty()
                ? I18n.tr("mac.server.status.allowed_mac.all") : c.getAllowedMacVersions()));
        int verified = 0;
        int pending = 0;
        for (Session s : sessions.values()) {
            if (s.phase() == Phase.VERIFIED) {
                verified++;
            } else {
                pending++;
            }
        }
        out.add(I18n.tr("mac.server.status.sessions", verified, pending));
        return out;
    }

    /** 最近违规记录（最新的在前）。 */
    public List<ViolationRecord> recentViolations() {
        return new ArrayList<>(violations);
    }

    /** 查询单个玩家的会话概要；不存在返回 null。 */
    public String sessionStatus(String nameOrUuid) {
        for (Session s : sessions.values()) {
            if (s.uuid().equals(nameOrUuid) || s.name().equalsIgnoreCase(nameOrUuid)) {
                return I18n.tr("mac.server.check.line", s.name(), s.phase(),
                        s.loaderType() == null ? "?" : s.loaderType(),
                        s.fullList().size(), s.joinTick());
            }
        }
        return null;
    }

    /** 查询单个玩家的会话对象（按 uuid 或玩家名，忽略大小写）；不存在返回 null。 */
    public Session findSession(String nameOrUuid) {
        if (nameOrUuid == null) {
            return null;
        }
        for (Session s : sessions.values()) {
            if (s.uuid().equalsIgnoreCase(nameOrUuid) || s.name().equalsIgnoreCase(nameOrUuid)) {
                return s;
            }
        }
        return null;
    }

    /** 查询玩家（在线会话）的完整 mod 清单版本映射；无会话返回空映射。 */
    public Map<String, String> sessionMods(String nameOrUuid) {
        Session s = findSession(nameOrUuid);
        Map<String, String> out = new LinkedHashMap<>();
        if (s != null) {
            for (ClientMod m : s.fullList()) {
                if (m != null && m.id != null) {
                    out.put(m.id, m.version);
                }
            }
        }
        return out;
    }

    /** 查看玩家历史 Mod 记录（管理命令 /mac audit）；无记录返回空列表。 */
    public List<String> auditLines(String nameOrUuid) {
        List<ModRecord> recs = history.forPlayer(nameOrUuid);
        if (recs.isEmpty()) {
            return Collections.emptyList();
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        List<String> out = new ArrayList<>();
        int shown = 0;
        for (ModRecord r : recs) {
            if (shown++ >= 10) {
                break;
            }
            List<ClientMod> mods = r.mods == null ? Collections.<ClientMod>emptyList() : r.mods;
            StringBuilder sb = new StringBuilder();
            sb.append(fmt.format(Instant.ofEpochMilli(r.t))).append(' ')
                    .append('[').append(r.result).append("] ")
                    .append(r.name == null ? "?" : r.name)
                    .append(" | ").append(r.loader == null ? "?" : r.loader)
                    .append('/').append(r.loaderVersion == null ? "?" : r.loaderVersion)
                    .append(" | ").append(mods.size()).append(I18n.tr("mac.server.audit.count"));
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < mods.size() && i < 12; i++) {
                ClientMod m = mods.get(i);
                parts.add(m.id + (m.version == null || m.version.trim().isEmpty() ? "" : "@" + m.version));
            }
            if (mods.size() > 12) {
                parts.add("…");
            }
            sb.append(String.join(", ", parts));
            out.add(sb.toString());
        }
        return out;
    }

    /**
     * 把玩家最近一次 Mod 清单（在线会话优先，否则最近历史记录）整体加入
     * 白名单或黑名单（跳过恒忽略项；条目为纯 id、无版本约束）。
     * 返回命令回显文本。
     */
    public String learn(String nameOrUuid, boolean toWhitelist) {
        MacConfig cfg = configManager.current();
        String targetName = I18n.tr(toWhitelist
                ? "mac.server.learn.list.whitelist" : "mac.server.learn.list.blacklist");
        List<ClientMod> src;
        String sourceDesc;
        Session live = null;
        for (Session s : sessions.values()) {
            if (s.phase() == Phase.VERIFIED
                    && (s.uuid().equalsIgnoreCase(nameOrUuid) || s.name().equalsIgnoreCase(nameOrUuid))) {
                live = s;
                break;
            }
        }
        if (live != null) {
            src = live.fullList();
            sourceDesc = I18n.tr("mac.server.learn.source.live");
        } else {
            ModRecord rec = history.latestFor(nameOrUuid);
            if (rec == null) {
                return I18n.tr("mac.server.learn.no_record", nameOrUuid);
            }
            src = rec.mods;
            sourceDesc = I18n.tr("mac.server.learn.source.history");
        }
        Set<String> ignore = ignoredIds(cfg);
        List<PolicyEntry> target = toWhitelist
                ? cfg.getPolicy().getWhitelist() : cfg.getPolicy().getBlacklist();
        List<String> added = new ArrayList<>();
        int dup = 0;
        int skip = 0;
        if (src != null) {
            for (ClientMod m : src) {
                if (m == null || m.id == null) {
                    continue;
                }
                if (ignore.contains(m.id)) {
                    skip++;
                    continue;
                }
                if (target.stream().anyMatch(e -> e != null && e.getId() != null
                        && e.getId().equalsIgnoreCase(m.id))) {
                    dup++;
                    continue;
                }
                target.add(new PolicyEntry(m.id));
                added.add(m.id);
            }
        }
        configManager().save();
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.tr("mac.server.learn.done", nameOrUuid, sourceDesc, targetName,
                added.size(), dup, skip));
        if (!added.isEmpty()) {
            int n = Math.min(added.size(), 8);
            sb.append(I18n.tr("mac.server.learn.sample",
                    String.join(", ", added.subList(0, n))));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 内部实现

    /** 为断开消息附加配置的“底部提示区”文案（默认提示行随玩家客户端语言）。 */
    private KickMessage decorate(KickMessage msg, String clientLanguage) {
        return msg.withFooter(FeedbackText.footer(msg.reason(), configManager.current(), clientLanguage));
    }

    /** 只记录一条违规（试运行等不实际断线场景），并受 logViolations 控制日志。 */
    private void logViolationOnly(String uuid, String playerName, String summary) {
        violations.addFirst(new ViolationRecord(Instant.now().toEpochMilli(),
                playerName, uuid, summary));
        while (violations.size() > Mac.MAX_VIOLATION_LOG) {
            violations.removeLast();
        }
        if (configManager.current().isLogViolations()) {
            Mac.logger().info("[MAC] {}", summary);
        }
    }

    /** 若会话已有完整 Mod 清单则写入历史记录（防止重复记录空清单）。 */
    private void recordHistory(Session s, String result) {
        if (s.fullList().isEmpty()) {
            return;
        }
        history.append(new ModRecord(Instant.now().toEpochMilli(), s.uuid(), s.name(),
                s.loaderType(), s.loaderVersion(), s.macVersion(), result, s.fullList()));
    }

    /** 是否对当前服务器连接启用准入强制（供适配层在加入瞬间做预拦截判断）。 */
    public boolean shouldEnforce() {
        MacConfig c = configManager.current();
        if (!c.isEnabled()) {
            return false;
        }
        // 集成服务器默认不强制执行（可配置）。
        if (!bridge.dedicatedServer() && !c.isEnforceIntegratedServer()) {
            return false;
        }
        return true;
    }

    /** 当前是否需要客户端安装本模组。 */
    public boolean requireClientMod() {
        return configManager.current().isRequireClientMod();
    }

    private Set<String> ignoredIds(MacConfig cfg) {
        Set<String> set = new HashSet<>();
        set.add(Mac.MOD_ID);
        set.addAll(Arrays.asList(Mac.ALWAYS_IGNORED_MODS));
        set.add(bridge.loaderType());
        // 各加载器自身的“基础设施”mod：不属于可被玩家控制的游戏内容，恒不参与黑白名单。
        String loader = bridge.loaderType().toLowerCase();
        if (Mac.LOADER_FORGE.equals(loader)) {
            set.add("forge");
            set.add("fmlonly");
            set.add("javafml");
        } else if (Mac.LOADER_FABRIC.equals(loader)) {
            set.add("fabricloader");
            set.add("fabric-api");
            set.add("fabric");
        } else if (Mac.LOADER_NEOFORGE.equals(loader)) {
            set.add("neoforge");
            set.add("javafml");
        }
        if (cfg.getIgnoredModIds() != null) {
            set.addAll(cfg.getIgnoredModIds());
        }
        return set;
    }

    private void armDeadline(Session s) {
        long timeoutTicks = (long) configManager.current().getHandshakeTimeoutSeconds() * Mac.TICKS_PER_SECOND;
        s.deadlineTick(tick + timeoutTicks);
    }

    private void sendStage1Request(Session s) {
        Stage1Request req = new Stage1Request();
        req.protocol = Mac.PROTOCOL_VERSION;
        req.checkMode = configManager.current().requiredMode().key();
        for (RequiredModRule r : configManager.current().getRequiredMods()) {
            req.requiredIds.add(r.getId());
        }
        bridge.sendToClient(s.uuid(), MacPackets.KIND_STAGE1_REQUEST, Json.toJson(req));
    }

    private void sendStage2Request(Session s) {
        bridge.sendToClient(s.uuid(), MacPackets.KIND_STAGE2_REQUEST,
                Json.toJson(new Stage2Request()));
    }

    private void handleTimeout(Session s) {
        MacConfig cfg = configManager.current();
        if (!cfg.isRequireClientMod()) {
            // 允许未安装本模组的客户端进入（仅记录），不做任何列表校验。
            s.phase(Phase.VERIFIED);
            s.deadlineTick(0);
            Mac.logger().warn("[MAC] 玩家 {} 未完成校验但配置允许放行(requireClientMod=false)", s.name());
            return;
        }
        if (s.phase() == Phase.WAIT_STAGE1) {
            deny(s, KickMessage.simple(DisconnectReason.NO_CLIENT_MOD));
        } else {
            deny(s, KickMessage.simple(DisconnectReason.HANDSHAKE_TIMEOUT));
        }
    }

    private void deny(Session s, KickMessage msg) {
        MacConfig cfg = configManager.current();
        // 记录被踢玩家的客户端语言：适配层据此在服务端渲染本地化纯文本，
        // 客户端界面与服务端/控制台日志显示一致（不再走客户端翻译键路径）。
        String clientLanguage = clientLanguage(s.uuid());
        msg = decorate(msg, clientLanguage).withClientLanguage(clientLanguage);
        // 明细开关：关闭时踢出消息不带逐条违规行。
        if (!cfg.showKickDetails()) {
            msg = msg.withoutDetails();
        }
        String adminDetail = cfg.showAdminDetails() ? msg.summary() : msg.shortSummary();
        if (cfg.isDryRun()) {
            // 试运行：只记录 / 告警，不踢出；会话置 VERIFIED 避免同阶段反复告警，
            // 后续定期复检仍会按间隔再次触发（用于观察规则效果）。
            recordViolation(s, msg);
            recordHistory(s, ModRecord.RESULT_REJECTED);
            if (cfg.isLogViolations()) {
                Mac.logger().info(I18n.tr("mac.server.deny_dryrun"), s.name(), msg.summary());
            }
            bridge.notifyOps(I18n.tr("mac.server.notify.dryrun", s.name(), adminDetail));
            s.phase(Phase.VERIFIED);
            s.deadlineTick(0);
            s.lastRecheckTick(tick);
            return;
        }
        s.phase(Phase.FAILED);
        recordViolation(s, msg);
        recordHistory(s, ModRecord.RESULT_REJECTED);
        fireListeners(s.uuid(), s.name(), msg, true);
        if (cfg.isLogViolations()) {
            Mac.logger().info(I18n.tr("mac.server.deny"), s.name(), msg.summary());
        }
        bridge.notifyOps(I18n.tr("mac.server.notify.violation", s.name(), adminDetail));
        bridge.disconnectPlayer(s.uuid(), msg);
        sessions.remove(s.uuid());
    }

    /** 通知全部公共 API 违规监听器（单监听器异常不影响整体）。 */
    private void fireListeners(String uuid, String name, KickMessage msg, boolean kicked) {
        if (listeners.isEmpty()) {
            return;
        }
        for (mcyszl.top.mod_access_control.api.MacApi.ViolationListener l : listeners) {
            try {
                l.onViolation(uuid, name, msg.reason().name(), msg.summary(), kicked);
            } catch (Throwable t) {
                Mac.logger().warn("[MAC] violation listener error: {}", t.toString());
            }
        }
    }

    /** 注册违规监听器（公共 API 入口；重复注册同一实例忽略）。 */
    public boolean addViolationListener(mcyszl.top.mod_access_control.api.MacApi.ViolationListener l) {
        if (l == null || listeners.contains(l)) {
            return false;
        }
        return listeners.add(l);
    }

    /** 指定玩家（uuid 或玩家名）当前会话是否已通过全部校验。 */
    public boolean isVerified(String nameOrUuid) {
        Session s = findSession(nameOrUuid);
        return s != null && s.phase() == Phase.VERIFIED;
    }

    /** 公共 API：新增豁免条目（玩家名或 uuid: 前缀 UUID）；已存在返回 false。 */
    public boolean addExemptEntry(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return false;
        }
        String e = entry.trim();
        MacConfig cfg = configManager.current();
        for (String ex : cfg.getExemptPlayers()) {
            if (ex != null && ex.equalsIgnoreCase(e)) {
                return false;
            }
        }
        cfg.getExemptPlayers().add(e);
        configManager.save();
        return true;
    }

    /** 公共 API：移除豁免条目（忽略大小写）；不存在返回 false。 */
    public boolean removeExemptEntry(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return false;
        }
        String e = entry.trim();
        MacConfig cfg = configManager.current();
        boolean removed = cfg.getExemptPlayers().removeIf(ex -> ex != null && ex.equalsIgnoreCase(e));
        if (removed) {
            configManager.save();
        }
        return removed;
    }

    private void recordViolation(Session s, KickMessage msg) {
        violations.addFirst(new ViolationRecord(Instant.now().toEpochMilli(),
                s.name(), s.uuid(), msg.summary()));
        while (violations.size() > Mac.MAX_VIOLATION_LOG) {
            violations.removeLast();
        }
    }
}
