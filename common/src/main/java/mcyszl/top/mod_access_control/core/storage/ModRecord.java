// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.storage;

import mcyszl.top.mod_access_control.core.network.MacPackets.ClientMod;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条“玩家客户端 Mod 清单”历史记录（服务端在收到进入游戏阶段完整列表后写入）。
 */
public final class ModRecord {

    public static final String RESULT_VERIFIED = "VERIFIED";
    public static final String RESULT_REJECTED = "REJECTED";

    /** 事件时间（epoch millis）。 */
    public long t;
    public String uuid;
    public String name;
    public String loader;
    public String loaderVersion;
    public String macVersion;
    /** VERIFIED / REJECTED。 */
    public String result;
    /** 客户端完整 Mod 清单。 */
    public List<ClientMod> mods = new ArrayList<>();

    public ModRecord() {
    }

    public ModRecord(long t, String uuid, String name, String loader, String loaderVersion,
                     String macVersion, String result, List<ClientMod> mods) {
        this.t = t;
        this.uuid = uuid;
        this.name = name;
        this.loader = loader;
        this.loaderVersion = loaderVersion;
        this.macVersion = macVersion;
        this.result = result;
        this.mods = mods == null ? new ArrayList<>() : mods;
    }

    /** 匹配玩家：name 忽略大小写，或与 uuid 相同。 */
    public boolean matches(String nameOrUuid) {
        if (nameOrUuid == null) {
            return false;
        }
        return nameOrUuid.equalsIgnoreCase(name)
                || (uuid != null && nameOrUuid.equalsIgnoreCase(uuid));
    }
}
