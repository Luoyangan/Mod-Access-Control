// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.feedback;

/**
 * 翻译键常量。所有键在三个加载器的 {@code assets/mod_access_control/lang/*.json}
 * 中维护 en_us / zh_cn（及其他语言）翻译。
 */
public final class Keys {

    private Keys() {
    }

    public static final String KICK_HEADER_NO_CLIENT_MOD = "mac.kick.header.no_client_mod";
    public static final String KICK_HEADER_PROTOCOL = "mac.kick.header.protocol";
    public static final String KICK_HEADER_LOADER = "mac.kick.header.loader";
    public static final String KICK_HEADER_TIMEOUT = "mac.kick.header.timeout";
    public static final String KICK_HEADER_VIOLATION = "mac.kick.header.violation";
    public static final String KICK_HEADER_MAC_VERSION = "mac.kick.header.mac_version";

    /** 底部提示区（按原因取键；见 FeedbackText.hintKeyFor）。 */
    public static final String KICK_HINT_GENERIC = "mac.kick.hint.generic";
    public static final String KICK_HINT_TIMEOUT = "mac.kick.hint.timeout";
    public static final String KICK_HINT_VIOLATION = "mac.kick.hint.violation";
    public static final String KICK_HINT_MAC_VERSION = "mac.kick.hint.mac_version";

    public static final String LINE_MISSING_REQUIRED = "mac.line.missing_required";
    public static final String LINE_VERSION_MISMATCH = "mac.line.version_mismatch";
    public static final String LINE_NOT_WHITELISTED = "mac.line.not_whitelisted";
    public static final String LINE_NOT_WHITELISTED_VERSION = "mac.line.not_whitelisted_version";
    public static final String LINE_BLACKLISTED = "mac.line.blacklisted";
    public static final String LINE_BLACKLISTED_VERSION = "mac.line.blacklisted_version";
    public static final String LINE_LOADER = "mac.line.loader";
}
