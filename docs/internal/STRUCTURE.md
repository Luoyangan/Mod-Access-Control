# 内部文档：目录结构与工程组织

> 本文件为**内部开发文档**，不对外公开（`docs/internal/` 整体在 `.gitignore` 中）。

## 顶层结构

```
common/                       # 跨加载器统一核心（纯 Java，不含任何加载器 / Minecraft 类）
versions/
  1.21.1/
    forge/                    # Forge 52.0.50（ForgeGradle 6 / Gradle 8.8，JDK 21）
    fabric/                   # Loader 0.19.5 / Fabric API 0.116.17+1.21.1（Loom，Gradle 8.10.2）
    neoforge/                 # NeoForge 21.1.250（ModDevGradle，Gradle 8.8）
  1.20.1/
    forge/                    # Forge 47.3.0（ForgeGradle 6，Gradle 8.8，JDK 17）
    fabric/                   # Loader 0.15.11 / Fabric API 0.92.3+1.20.1（Loom，Gradle 8.10.2）
    neoforge/                 # NeoForge 47.1.106（NeoGradle userdev，Gradle 8.8）
  1.16.5/
    forge/                    # Forge 36.2.42（ForgeGradle 4.1，Gradle 6.9，JDK 8）
    fabric/                   # Loader 0.15.11 / Fabric API 0.41.3+1.16（Loom 1.7.4，Gradle 8.10.2，--release 8）
  1.12.2/
    forge/                    # Forge 14.23.5.2847（anatawa12 ForgeGradle 2.3-1.0.8，Gradle 7.6.4，JDK 8）
config-example/               # 对外公开的完整配置示例
wiki/                         # 站点文档（不进 git）
```

每个 `versions/<mc>/<loader>/` 是**独立 Gradle 工程**（自带 wrapper / settings.gradle /
gradle.properties），通过 `sourceSets` 直接把 `common/src/main/java` 与
`common/src/main/resources` 并入 main sourceSet，编译进同一个 jar。

## 工程约定

- 产物命名：`mod-access-control-<loader>-<mc>-<版本>.jar`（`archives_base_name` + loader + mc）。
- `gradle.properties` 每端独立维护 `mod_version`，升级版本时 9 端同步改。
- 许可证：Apache-2.0（`LICENSE` / `NOTICE` / 元数据 `mod_license`）；每个 `.java` 顶部
  两行 ASCII 头（SPDX + 版权），头部写入保持幂等。
- 配置文件格式三端一致：`config/mod_access_control.json`。
- 网络协议：4 个逻辑消息（stage1_req / stage1_resp / stage2_req / stage2_resp），
  负载为 JSON 字符串；协议版本常量 `Mac.PROTOCOL_VERSION`。

## 各适配层文件职责（模式统一）

| 文件 | 职责 |
| --- | --- |
| `<Loader>MacMod` / `Mod` 入口 | 初始化顺序：日志桥 → Holder → 网络 → 事件 → MacApi 注入 |
| `Holder` | 运行期单例（bridge / service） |
| `Mac<Loader>Bridge` | PlatformBridge 实现：加载器信息 / 发包 / 断线 / 通知管理员 |
| `net/<Loader>Net` | 通道注册、消息编解码、线程切换 |
| `<Loader>Events` | 玩家进出 / 刻驱动 / 命令注册 / 管理广播 |
| `command/MacCommand` | `/mac` 命令（Brigadier 或 1.12.2 ICommand） |
| `Feedback` | 踢出组件渲染（翻译键 / legacy 纯文本双路径） |
| `MacApiImpl` | 公共 API 门面的平台实现 |
| `<Loader>Log` | 日志桥（占位符统一 `{}`） |

## i18n 组织

- 服务端文案：`core/i18n/I18n`（内置 zh_cn / en_us 字典，`language: auto|zh_cn|en_us`）；
- 命令回显集中在 `core/i18n/CommandText`，分页帮助在 `core/feedback/HelpText`；
- 客户端踢出界面：非 legacy 走翻译键 + lang 文件（`common/resources/assets/.../lang/*.json`，
  1.12.2 用 `.lang` 且**不支持 `\n` 转义，值必须单行**）；legacy 走服务端 I18n 纯文本。
