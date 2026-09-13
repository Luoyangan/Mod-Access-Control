// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.neoforge;

import mcyszl.top.mod_access_control.api.MacApi;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.service.MacService;

import java.util.Map;

/**
 * {@link MacApi} 的 NeoForge 平台实现：把公共 API 门面接到本加载器的服务单例上。
 * 由 {@code NeoMacMod} 在初始化时通过 {@link MacApi#install} 注入。
 */
public final class MacApiImpl implements MacApi.Impl {

    private final MacService service;

    public MacApiImpl(MacService service) {
        this.service = service;
    }

    @Override
    public boolean isVerified(String uuidOrName) {
        return service.isVerified(uuidOrName);
    }

    @Override
    public Map<String, String> getSessionMods(String uuidOrName) {
        return service.sessionMods(uuidOrName);
    }

    @Override
    public boolean isExempt(String uuid, String playerName, boolean op) {
        return service.isExempt(uuid, playerName, op);
    }

    @Override
    public boolean addExempt(String entry) {
        return service.addExemptEntry(entry);
    }

    @Override
    public boolean removeExempt(String entry) {
        return service.removeExemptEntry(entry);
    }

    @Override
    public boolean addViolationListener(MacApi.ViolationListener listener) {
        return service.addViolationListener(listener);
    }

    @Override
    public String modVersion() {
        return Holder.bridge().modVersion();
    }

    @Override
    public int protocolVersion() {
        return Mac.PROTOCOL_VERSION;
    }

    @Override
    public String loaderType() {
        return Mac.LOADER_NEOFORGE;
    }
}
