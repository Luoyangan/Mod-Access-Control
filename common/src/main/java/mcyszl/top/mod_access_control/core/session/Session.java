// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.session;

import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端视角下的单个玩家接入会话（分阶段握手状态）。
 */
public final class Session {

    public enum Phase {
        /** 等待登录阶段响应（协议 / 加载器 / 必需 Mod）。 */
        WAIT_STAGE1,
        /** 等待进入游戏阶段响应（完整 Mod 列表）。 */
        WAIT_STAGE2,
        /** 全部校验通过。 */
        VERIFIED,
        /** 校验失败（将被踢出或已失效）。 */
        FAILED
    }

    private final String uuid;
    private final String name;
    private final long joinTick;
    private Phase phase;
    /** 当前阶段的最后期限（tick）；<=0 表示无。 */
    private long deadlineTick;

    private String loaderType;
    private String loaderVersion;
    private String macVersion;
    /** 登录阶段客户端上报的界面语言（旧版客户端可能不报，为 null）。 */
    private String clientLanguage;
    /** 登录阶段客户端上报：必需 mod id -> 版本。 */
    private final Map<String, String> requiredReport = new LinkedHashMap<>();
    /** 进入游戏阶段客户端上报的完整列表。 */
    private final List<ClientMod> fullList = new ArrayList<>();
    /** 上次完整复检的 tick。 */
    private long lastRecheckTick;

    public Session(String uuid, String name, long joinTick) {
        this.uuid = uuid;
        this.name = name;
        this.joinTick = joinTick;
        this.phase = Phase.WAIT_STAGE1;
        this.lastRecheckTick = joinTick;
    }

    public String uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public long joinTick() {
        return joinTick;
    }

    public Phase phase() {
        return phase;
    }

    public void phase(Phase phase) {
        this.phase = phase;
    }

    public long deadlineTick() {
        return deadlineTick;
    }

    public void deadlineTick(long deadlineTick) {
        this.deadlineTick = deadlineTick;
    }

    public String loaderType() {
        return loaderType;
    }

    public void loaderType(String loaderType) {
        this.loaderType = loaderType;
    }

    public String loaderVersion() {
        return loaderVersion;
    }

    public void loaderVersion(String loaderVersion) {
        this.loaderVersion = loaderVersion;
    }

    public String macVersion() {
        return macVersion;
    }

    public void macVersion(String macVersion) {
        this.macVersion = macVersion;
    }

    public String clientLanguage() {
        return clientLanguage;
    }

    public void clientLanguage(String clientLanguage) {
        this.clientLanguage = clientLanguage;
    }

    public Map<String, String> requiredReport() {
        return requiredReport;
    }

    public List<ClientMod> fullList() {
        return fullList;
    }

    public long lastRecheckTick() {
        return lastRecheckTick;
    }

    public void lastRecheckTick(long lastRecheckTick) {
        this.lastRecheckTick = lastRecheckTick;
    }
}
