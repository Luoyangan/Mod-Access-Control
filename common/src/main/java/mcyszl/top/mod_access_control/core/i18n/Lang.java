// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.i18n;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import mcyszl.top.mod_access_control.core.Mac;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端语言（i18n）工具：以英文原文为 key 的键值目录方案。
 *
 * <p>本模组的所有玩家可见文案均由服务端生成（踢出界面、管理员通知、命令回显），
 * 语言在服务端配置文件 {@code language} 字段统一设置，支持：
 * {@code zh_cn}（简体中文，默认）、{@code zh_tw}（繁體中文）、{@code en_us}（English）、
 * {@code ja_jp}（日本語）与 {@code ru_ru}（Русский）。</p>
 *
 * <p>文案调用形如 {@code Lang.tr("English text {}", arg)}：英文原文（可含 {@code {}}
 * 占位符）即为 key；{@code en_us} 直接返回原文本身，其余语言从
 * {@code /assets/mod_access_control/lang/<lang>.json} 的目录
 * {@code {"英文原文": "翻译"}} 中查找，缺失时回退为英文原文。</p>
 *
 * <p>语言值经 {@link #normalize} 归一化，未知值回退为 {@code zh_cn}。
 * 目录按语言缓存，加载失败（文件缺失/解析异常）记一次 warning 后使用空目录。</p>
 */
public final class Lang {

    /** 简体中文（默认）。 */
    public static final String ZH_CN = "zh_cn";
    /** 繁体中文（台湾用语）。 */
    public static final String ZH_TW = "zh_tw";
    /** 英语。 */
    public static final String EN_US = "en_us";
    /** 日语。 */
    public static final String JA_JP = "ja_jp";
    /** 俄语。 */
    public static final String RU_RU = "ru_ru";

    private static final Gson GSON = new Gson();
    private static final TypeToken<Map<String, String>> TYPE =
            new TypeToken<Map<String, String>>() {
            };
    /** 已加载的语言目录缓存（含加载失败的空目录，保证 warning 只记一次）。 */
    private static final Map<String, Map<String, String>> CATALOGS = new ConcurrentHashMap<>();

    private static volatile String current = ZH_CN;

    private Lang() {
    }

    /** 全部支持的语言键（小写）。 */
    private static final Set<String> SUPPORTED = new HashSet<>(
            Arrays.asList(ZH_CN, ZH_TW, EN_US, JA_JP, RU_RU));

    /** 归一化语言键：识别 en_us/zh_tw/ja_jp/ru_ru/zh_cn（忽略大小写与首尾空格），其余一律回退 zh_cn。 */
    public static String normalize(String key) {
        if (key != null) {
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            if (SUPPORTED.contains(normalized)) {
                return normalized;
            }
        }
        return ZH_CN;
    }

    /** 按归一化后的语言键切换运行时语言并加载目录（配置加载 / 修改时调用）。 */
    public static void setLanguage(String key) {
        current = normalize(key);
        if (!EN_US.equals(current)) {
            catalog(current);
        }
    }

    /**
     * 按当前服务端语言取文案：以英文原文（模板）为 key 查目录，
     * 缺失时回退 key 本身；{@code args} 依序替换 {@code {}} 占位符。
     */
    public static String tr(String key, Object... args) {
        String template = key;
        String lang = current;
        if (!EN_US.equals(lang)) {
            String translated = catalog(lang).get(key);
            if (translated != null) {
                template = translated;
            }
        }
        return Mac.format(template, args);
    }

    /** 取指定语言的目录（带缓存；加载失败回退为空目录）。 */
    private static Map<String, String> catalog(String lang) {
        Map<String, String> cached = CATALOGS.get(lang);
        if (cached != null) {
            return cached;
        }
        Map<String, String> loaded = loadCatalog(lang);
        CATALOGS.put(lang, loaded);
        return loaded;
    }

    /** 从 classpath 读取 {@code /assets/mod_access_control/lang/<lang>.json}。 */
    private static Map<String, String> loadCatalog(String lang) {
        String path = "/assets/" + Mac.MOD_ID + "/lang/" + lang + ".json";
        InputStream in = Lang.class.getResourceAsStream(path);
        if (in == null) {
            Mac.logger().warn("Language catalog not found: {}, falling back to English text", path);
            return new ConcurrentHashMap<String, String>();
        }
        try {
            Map<String, String> map = GSON.fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), TYPE.getType());
            if (map == null) {
                return new ConcurrentHashMap<String, String>();
            }
            return new ConcurrentHashMap<String, String>(map);
        } catch (Exception e) {
            Mac.logger().warn("Failed to load language catalog {}: {}", path, e.toString());
            return new ConcurrentHashMap<String, String>();
        } finally {
            try {
                in.close();
            } catch (Exception ignore) {
                // 忽略关闭失败
            }
        }
    }
}
