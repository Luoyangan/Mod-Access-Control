# 内部文档：构建与验证

> 本文件为**内部开发文档**，不对外公开（`docs/internal/` 整体在 `.gitignore` 中）。

## 构建命令

各自在对应目录执行（Gradle wrapper 已内置）：

```powershell
.\gradlew.bat build
```

| 工程 | Gradle | JDK（运行 Gradle） | 字节码 |
| --- | --- | --- | --- |
| 1.21.1 forge | 8.8 | 21（系统默认） | 21 |
| 1.21.1 fabric | 8.10.2 | 21 | 21 |
| 1.21.1 neoforge | 8.8 | 21 | 21 |
| 1.20.1 forge | 8.8 | 21（FG6 编译 release 17） | 17 |
| 1.20.1 fabric | 8.10.2 | 21 | 21 |
| 1.20.1 neoforge | 8.8 | 21 | 17 |
| 1.16.5 forge | 6.9 | 8（`org.gradle.java.home` 已指定） | 8 |
| 1.16.5 fabric | 8.10.2 | 21（`options.release = 8`） | 8 |
| 1.12.2 forge | 7.6.4 | 8（`org.gradle.java.home` 已指定） | 8 |

产物输出到各工程 `build/libs/`，命名 `mod-access-control-<loader>-<mc>-<version>.jar`。

## 已知坑（务必记住）

- **1.12.2**：只有 forge 1.12.2-14.23.5.2847 在 maven.minecraftforge.net 有 userdev
  classifier（2855+ 是 universal-only，2859 不存在）；SPacketCustomPayload 通道名上限
  20 字符 → 用裸 mod id（18 字符）做通道名；`player.connection.disconnect(String)` 把
  文本当翻译键 → 踢出走 SPacketDisconnect + closeChannel；.lang 值不支持 `\n` 转义。
- **1.16.5 Forge**：FG 4.1.16 的 `mappings` 必须用位置参数
  （`mappings channel, version`）；事件类在 `net.minecraftforge.fml.event.server.*`。
- **1.16.5 Fabric**：Fabric API 坐标是 `net.fabricmc.fabric-api:fabric-api`，1.16 线可用
  版本 0.41.3+1.16（0.42.0+1.16 已从 maven 移除）；容器 mod id 是 `fabric`（不是
  `fabric-api`），`fabric.mod.json` depends 写 `"fabric": "*"`；客户端 Netty 线程回包前
  必须 `client.execute(...)` 切主线程；`readUtf()` 必须带 int 参数（无参重载专用服务器
  jar 没有）；JOIN 事件早于玩家注册进 playersByUUID，握手要直接记 `handler.player`。
- **1.20.1 / 1.21.1 NeoForge maven** 拉取慢时用 IPv6 路由：
  `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Addresses=true`。
- **Gradle wrapper 下载**：services.gradle.org 可能停滞，可从
  `mirrors.cloud.tencent.com/gradle/` 手动下载 zip 放入
  `~/.gradle/wrapper/dists/...`。

## v1.1.0 构建验证记录

- 版本号：9 端 `mod_version=1.1.0`。
- 全部 9 端 `gradle build` 通过（见 git 提交记录）。
- lang 资源：common 的 `lang/*.json` 经各端 sourceSets 的 resources srcDir 打包；
  1.12.2 额外自带 `.lang`（单行值）。
