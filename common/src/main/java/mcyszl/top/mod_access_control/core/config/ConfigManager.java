// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2026 mcyszl.top (Luoyangan)

package mcyszl.top.mod_access_control.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import mcyszl.top.mod_access_control.core.i18n.Lang;
import mcyszl.top.mod_access_control.core.Mac;
import mcyszl.top.mod_access_control.core.model.MacConfig;
import mcyszl.top.mod_access_control.core.network.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * 服务端规则配置管理器：负责从配置文件加载、向文件保存、以及校验失败时的
 * 兜底与备份。所有修改均通过内存中单例进行，避免读写竞态。
 */
public final class ConfigManager {

    private final Path file;
    private volatile MacConfig current;

    public ConfigManager(Path file) {
        this.file = file;
    }

    /** 首次加载；文件缺失时创建默认配置，解析失败时备份原文件后重建。 */
    public void load() {
        if (!Files.exists(file)) {
            this.current = MacConfig.defaults();
            save();
            Mac.logger().info(Lang.tr("Config file not found, generated default config: {}"), file);
            return;
        }
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            MacConfig parsed = Json.gson().fromJson(text, MacConfig.class);
            if (parsed == null) {
                throw new JsonSyntaxException("empty config");
            }
            parsed.normalize();
            this.current = parsed;
            Lang.setLanguage(parsed.getLanguage());
            Mac.logger().info(Lang.tr("Config file loaded: {}"), file);
        } catch (Exception e) {
            Mac.logger().warn(Lang.tr("Config parse failed ({}), backed up and reset to defaults: {}"),
                    e.getMessage(), file);
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".invalid.bak"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
                // 忽略备份失败
            }
            this.current = MacConfig.defaults();
            save();
        }
    }

    /** 修改内存配置并立即写盘。 */
    public synchronized void updateAndSave(Consumer<MacConfig> change) {
        if (current == null) {
            current = MacConfig.defaults();
        }
        change.accept(current);
        Lang.setLanguage(current.getLanguage());
        save();
    }

    /** 写盘。 */
    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Gson pretty = Json.prettyGson();
            Files.write(file, pretty.toJson(current).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Mac.logger().warn(Lang.tr("Config save failed: {}"), e.getMessage());
        }
    }

    public MacConfig current() {
        if (current == null) {
            current = MacConfig.defaults();
            Lang.setLanguage(current.getLanguage());
        }
        return current;
    }

    /** 仅用于外部校验 JSON 语法。 */
    public static boolean isJson(String text) {
        try {
            // Gson 2.8.0（MC 1.12.2 自带）无静态 parseString，使用实例方法保持全版本兼容
            new JsonParser().parse(text);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }
}
