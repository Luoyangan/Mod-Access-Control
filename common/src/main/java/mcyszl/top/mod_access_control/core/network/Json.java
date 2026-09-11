// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 跨加载器统一网络消息的 JSON 编解码工具。
 *
 * <p>所有逻辑消息先序列化为 JSON 字符串，再被各加载器的适配层包装成各自的
 * 网络包（仅传输字符串，保持三种加载器协议完全一致）。</p>
 */
public final class Json {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private Json() {
    }

    public static String toJson(Object o) {
        return GSON.toJson(o);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    public static Gson gson() {
        return GSON;
    }
}
