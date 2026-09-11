package mcyszl.top.mod_access_control.core.service;

import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.config.ConfigManager;
import mcyszl.top.mod_access_control.core.feedback.DisconnectReason;
import mcyszl.top.mod_access_control.core.feedback.FeedbackText;
import mcyszl.top.mod_access_control.core.feedback.KickMessage;
import mcyszl.top.mod_access_control.core.model.CheckMode;
import mcyszl.top.mod_access_control.core.model.MacConfig;
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
        sessions.clear();
        violations.clear();
        Mac.logger().info("[MAC] Mod Access Control 已启动 (loader={}, version={})",
                bridge.loaderType(), bridge.modVersion());
    }

    public void onServerStopping() {
        configManager.save();
        sessions.clear();
        Mac.logger().info("[MAC] Mod Access Control 已停止。");
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
            Mac.logger().info("[MAC] 客户端准入未强制开启(requireClientMod=false)，放行玩家 {}", playerName);
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
            Mac.logger().info("[MAC] 玩家 {} 命中豁免名单，跳过准入校验", playerName);
            return;
        }
        if (!cfg.isRequireClientMod()) {
            Mac.logger().info("[MAC] 客户端准入未强制开启(requireClientMod=false)，放行玩家 {}", playerName);
            return;
        }
        if (!hasChannel) {
            if (cfg.isDryRun()) {
                logViolationOnly(uuid, playerName, "试运行(NO_CLIENT_MOD 未拦截): 客户端未安装本模组");
                bridge.notifyOps("试运行: " + playerName + " 客户端未安装本模组（本将拦截，未实际踢出）");
                return;
            }
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
            Mac.logger().info("[MAC] 玩家 {} 离开，会话结束(phase={})", old.name(), old.phase());
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
        Mac.logger().info("[MAC] 玩家 {} 通过登录阶段校验，请求完整 Mod 列表", s.name());
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
        Mac.logger().info("[MAC] 玩家 {} 通过全部准入校验，允许进入游戏（加载 {} 个客户端 Mod）",
                s.name(), s.fullList().size());
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
        Mac.logger().info("[MAC] 强制复检完成，共扫描 {} 名在线玩家", n);
    }

    /** 按配置间隔定期复检单个会话（使用其上报的完整列表，无需再次传输）。 */
    private void recheck(Session s) {
        List<Problem> problems = runFullCheck(s);
        if (problems.isEmpty()) {
            return;
        }
        Mac.logger().info("[MAC] 定期复检发现玩家 {} 违规，正在执行踢出", s.name());
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
     * 供适配层在“握手尚未建立会话”前做预拦截时调用（如客户端未安装本模组）：
     * 记录违规、通知管理员并断开玩家，不建立会话。试运行模式下不实际踢出。
     */
    public void rejectImmediate(String uuid, String playerName, KickMessage msg) {
        MacConfig cfg = configManager.current();
        msg = decorate(msg);
        if (cfg.isDryRun()) {
            logViolationOnly(uuid, playerName, "试运行(未拦截): " + msg.summary());
            bridge.notifyOps("试运行: " + playerName + " -> " + msg.summary() + "（未实际踢出）");
            return;
        }
        violations.addFirst(new ViolationRecord(Instant.now().toEpochMilli(),
                playerName, uuid, msg.summary()));
        while (violations.size() > Mac.MAX_VIOLATION_LOG) {
            violations.removeLast();
        }
        if (cfg.isLogViolations()) {
            Mac.logger().info("[MAC] 预拦截玩家 {}: {}", playerName, msg.summary());
        }
        bridge.notifyOps("违规拦截: " + playerName + " -> " + msg.summary());
        bridge.disconnectPlayer(uuid, msg);
    }

    /** 状态概要（管理命令 /mac status 使用）。 */
    public List<String> statusLines() {
        MacConfig c = configManager.current();
        List<String> out = new ArrayList<>();
        out.add("== Mod Access Control ==");
        out.add("启用: " + c.isEnabled());
        out.add("强制范围: " + (c.isEnforceIntegratedServer() ? "专用服务器+集成服务器" : "仅专用服务器"));
        out.add("必需Mod校验模式: " + c.requiredMode().key());
        out.add("必需Mod数量: " + c.getRequiredMods().size());
        out.add("策略模式: " + c.getPolicy().mode().key());
        if (c.getPolicy().mode() == PolicyMode.SWITCH) {
            out.add("当前生效策略: " + c.getPolicy().activeMode().key());
            out.add("复检间隔(秒): " + c.getPolicy().getRecheckIntervalSeconds());
        }
        out.add("白名单数量: " + c.getPolicy().getWhitelist().size());
        out.add("黑名单数量: " + c.getPolicy().getBlacklist().size());
        out.add("忽略列表: " + c.getIgnoredModIds());
        out.add("试运行(不踢): " + c.isDryRun());
        out.add("豁免: " + c.getExemptPlayers().size() + " 条"
                + (c.isExemptOps() ? "（默认豁免 OP）" : ""));
        out.add("允许的本模组版本: "
                + (c.getAllowedMacVersions().isEmpty() ? "全部" : c.getAllowedMacVersions()));
        int verified = 0;
        int pending = 0;
        for (Session s : sessions.values()) {
            if (s.phase() == Phase.VERIFIED) {
                verified++;
            } else {
                pending++;
            }
        }
        out.add("在线会话: 已通过 " + verified + " / 校验中 " + pending);
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
                return "玩家 " + s.name() + " 状态: " + s.phase()
                        + " | 加载器: " + (s.loaderType() == null ? "?" : s.loaderType())
                        + " | 客户端Mod数: " + s.fullList().size()
                        + " | 加入于 tick " + s.joinTick();
            }
        }
        return null;
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
                    .append(" | ").append(mods.size()).append(" 个Mod: ");
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
     * 白名单或黑名单（跳过恒忽略项）。返回命令回显文本。
     */
    public String learn(String nameOrUuid, boolean toWhitelist) {
        MacConfig cfg = configManager.current();
        String targetName = toWhitelist ? "白名单" : "黑名单";
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
            sourceDesc = "在线完整清单";
        } else {
            ModRecord rec = history.latestFor(nameOrUuid);
            if (rec == null) {
                return "找不到玩家 " + nameOrUuid + " 的在线会话或历史 Mod 记录。";
            }
            src = rec.mods;
            sourceDesc = "最近一次记录";
        }
        Set<String> ignore = ignoredIds(cfg);
        List<String> target = toWhitelist
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
                if (target.stream().anyMatch(i -> i.equalsIgnoreCase(m.id))) {
                    dup++;
                    continue;
                }
                target.add(m.id);
                added.add(m.id);
            }
        }
        configManager().save();
        StringBuilder sb = new StringBuilder();
        sb.append("已用玩家 ").append(nameOrUuid).append(" 的").append(sourceDesc)
                .append("更新").append(targetName).append("：新增 ").append(added.size())
                .append("，已存在 ").append(dup).append("，跳过 ").append(skip);
        if (!added.isEmpty()) {
            int n = Math.min(added.size(), 8);
            sb.append("；示例: ").append(String.join(", ", added.subList(0, n)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 内部实现

    /** 为断开消息附加配置的“底部提示区”文案。 */
    private KickMessage decorate(KickMessage msg) {
        return msg.withFooter(FeedbackText.footer(msg.reason(), configManager.current()));
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
        msg = decorate(msg);
        if (cfg.isDryRun()) {
            // 试运行：只记录 / 告警，不踢出；会话置 VERIFIED 避免同阶段反复告警，
            // 后续定期复检仍会按间隔再次触发（用于观察规则效果）。
            recordViolation(s, msg);
            recordHistory(s, ModRecord.RESULT_REJECTED);
            if (cfg.isLogViolations()) {
                Mac.logger().info("[MAC][试运行] 本将拒绝玩家 {}: {}", s.name(), msg.summary());
            }
            bridge.notifyOps("试运行: " + s.name() + " -> " + msg.summary() + "（未实际踢出）");
            s.phase(Phase.VERIFIED);
            s.deadlineTick(0);
            s.lastRecheckTick(tick);
            return;
        }
        s.phase(Phase.FAILED);
        recordViolation(s, msg);
        recordHistory(s, ModRecord.RESULT_REJECTED);
        if (cfg.isLogViolations()) {
            Mac.logger().info("[MAC] 拒绝玩家 {} 进入: {}", s.name(), msg.summary());
        }
        bridge.notifyOps("违规拦截: " + s.name() + " -> " + msg.summary());
        bridge.disconnectPlayer(s.uuid(), msg);
        sessions.remove(s.uuid());
    }

    private void recordViolation(Session s, KickMessage msg) {
        violations.addFirst(new ViolationRecord(Instant.now().toEpochMilli(),
                s.name(), s.uuid(), msg.summary()));
        while (violations.size() > Mac.MAX_VIOLATION_LOG) {
            violations.removeLast();
        }
    }
}
