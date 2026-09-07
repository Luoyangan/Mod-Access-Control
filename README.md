# Mod Access Control（模组准入控制）

面向 **Minecraft 1.21.1** 的跨平台 **服务端** Mod：通过统一协议、服务端规则配置与
**两阶段握手校验**，对客户端的 Mod 组合实施安全准入控制。同一份规则与同一套协议，
在 **Forge / Fabric / NeoForge** 三种加载器上行为一致。

- Mod id（三端一致）：`mod_access_control`
- 协议版本：`1`
- 加载器标识：`forge` / `fabric` / `neoforge`（握手阶段上报，供严格模式比对）

> 提示：**客户端也必须安装本模组** 才能完成握手；未安装的客户端会在加入瞬间被拦截
> （`requireClientMod` 可关闭该强制，见配置）。

---

## 一、功能总览

| 需求 | 实现 |
| --- | --- |
| 两阶段握手 | 登录阶段（最小传输：协议/加载器/必需 Mod）+ 进入游戏阶段（完整 Mod 列表复核） |
| 必需 Mod 校验 | `presence` 存在性 / `version_range` 版本范围 / `strict` 严格匹配 |
| 策略系统 | `whitelist` / `blacklist` / `switch`（双模式 + 按间隔定期复检） |
| 规则管理 | 启动读配置 + 游戏内 `/mac` 命令即时修改 / 查看 / 保存 |
| 玩家反馈 | 纯文本断开原因（缺失 / 版本不符 / 违规 Mod 明细）+ 提示语 |
| 管理辅助 | 在线管理员收到违规广播（聊天 + ActionBar + 音效），`/mac status`、`/mac recent` |
| 错误处理 | 解析失败自动备份（`*.invalid.bak`）并重置默认；全流程 try/catch + 日志 |

---

## 二、目录结构

```
common/     # 跨加载器统一核心（纯 Java，不含任何加载器 / Minecraft 类）
forge/      # Forge 适配层（Gradle 工程，已实测编译通过，Forge 52.0.50）
fabric/     # Fabric 适配层（Fabric Loom / Loader 0.19.5 / Fabric API 0.116.17+1.21.1）
neoforge/   # NeoForge 适配层（NeoForge 21.1.250，ModDevGradle）
```

三个加载器产物使用完全相同的：
- `common` 核心逻辑（规则模型 / 校验引擎 / 会话状态机 / 协议 DTO）；
- 配置文件格式（`config/mod_access_control.json`）与字段语义；
- 网络协议（4 个逻辑消息：`stage1_req / stage1_resp / stage2_req / stage2_resp`，负载为 JSON 字符串）；
- `/mac` 命令体系。

仅以下平台差异由各适配层提供：Mod 列表读取接口、网络收发（通道注册 / 线程切换）、
服务器事件接线、配置文件路径与日志桥。

> 说明：Forge 产物经真实 `gradle build` 验证；Fabric 与 NeoForge 采用同源复制模式提供，
> 通过 javap 对照官方 API 编写，尚未在本机做端到端编译验证（构建脚本与版本均已锁定向官方
> maven 元数据）。

---

## 三、构建

各自在对应目录执行（JDK 21，Gradle 8.8 wrapper 已内置）：

```powershell
# Forge（已实测通过）
cd forge
.\gradlew.bat build        # 产物：forge/build/libs/mod_access_control-1.0.0.jar

# Fabric
cd ..\fabric
.\gradlew.bat build

# NeoForge
cd ..\neoforge
.\gradlew.bat build
```

调试运行（`client` / `server` run 任务）分别见各模块 `build.gradle`。

---

## 四、安装

1. 服务端：把与所用加载器对应的 jar 放入服务端 `mods/`；
2. 客户端：**每个玩家客户端**的 `mods/` 也需放入同一 jar（用于自动应答握手）；
3. 服务端第一次启动会自动生成默认配置 `config/mod_access_control.json`；
4. 加载器需与服务器一致（Forge 服配 Forge 客户端，Fabric 服配 Fabric 客户端，
   NeoForge 服配 NeoForge 客户端）；跨加载器握手会被“加载器兼容性”校验拦截。

---

## 五、配置文件

路径：`<服务器目录>/config/mod_access_control.json`（三个加载器完全一致）。

默认值（首次启动自动生成）：

| 字段 | 默认 | 含义 |
| --- | --- | --- |
| `configVersion` | `1` | 配置结构版本 |
| `enabled` | `true` | 总开关 |
| `enforceIntegratedServer` | `false` | 是否在单人存档 / 局域网（内置服务器）中也强制校验 |
| `requireClientMod` | `true` | 客户端必须安装本模组；未安装者加入时被拦截 |
| `strictLoader` | `true` | 客户端加载器标识必须与服务端一致 |
| `handshakeTimeoutSeconds` | `10` | 每个握手阶段的超时秒数 |
| `requiredCheckMode` | `"presence"` | 必需 Mod 校验模式：`presence` / `version_range` / `strict` |
| `requiredMods[]` | `[]` | 必需 Mod 清单（`id` + 可选 `minVersion`/`maxVersion`/`exactVersion`） |
| `policy.mode` | `"blacklist"` | 策略：`whitelist` / `blacklist` / `switch` |
| `policy.activeMode` | `"whitelist"` | `switch` 模式下当前生效的策略 |
| `policy.recheckIntervalSeconds` | `60` | 定期复检间隔（秒），`0` = 不复检 |
| `policy.whitelist[]` | `[]` | 白名单（mod id） |
| `policy.blacklist[]` | `[]` | 黑名单（mod id） |
| `ignoredModIds[]` | `[]` | 额外忽略的 mod id（不参与任何校验） |
| `logViolations` | `true` | 违规事件是否写入服务端日志 |

**必需 Mod 约束写法**（`version_range` 模式生效；`strict` 模式下上述规则 + 加载器一致性都须满足）：

```json
"requiredMods": [
  { "id": "sodium" },                                   // 任意版本，仅需存在
  { "id": "fabric-api", "minVersion": "0.100.0" },      // 下限
  { "id": "some_mod",   "maxVersion": "2.0.0" },        // 上限
  { "id": "map_mod",    "exactVersion": "1.2.3" }       // 精确版本（优先级最高）
]
```

完整示例见 [config-example/mod_access_control.json](config-example/mod_access_control.json)。

---

## 六、策略模式

| 模式 | 行为 |
| --- | --- |
| `whitelist` | 客户端只能安装白名单内 Mod；白名单之外一律拒绝 |
| `blacklist` | 客户端可装任意 Mod；检测到黑名单内 Mod 即拒绝 |
| `switch` | 加入时完整检查；运行时可在黑白名单间切换（`activeMode`），并按 `recheckIntervalSeconds` 对已通过玩家定期复检 |

> 忽略机制：本模组自身、`minecraft`、当前加载器及其基础设施（如 `forge`/`fabricloader`/
> `fabric-api`/`neoforge` 等）恒不参与白名单 / 黑名单 / 必需校验，避免误伤。
> 注意：黑白名单校验的是**游戏内容 Mod 的 id**，并非文件层面的完整性防作弊手段。

---

## 七、命令 `/mac`（权限等级 2）

| 命令 | 作用 |
| --- | --- |
| `/mac` 或 `/mac status` | 查看当前策略、清单数量、在线会话统计 |
| `/mac recent` | 最近违规记录（最新在前，最多 20 条展示） |
| `/mac check <玩家>` | 查看指定玩家会话状态 |
| `/mac reload` | 从配置文件重新加载规则 |
| `/mac save` | 把当前内存配置保存到文件 |
| `/mac recheck` | 用最新规则对在线玩家立即复检一次 |
| `/mac enabled <true/false>` | 总开关 |
| `/mac mode <whitelist/blacklist/switch>` | 设置策略模式 |
| `/mac active <whitelist/blacklist>` | 设置 switch 模式下生效的策略 |
| `/mac required list` | 列出必需 Mod |
| `/mac required add <id> [--min x] [--max y] [--exact z]` | 新增必需 Mod（可附裸参 `1.0~2.0` / `=1.2.3`） |
| `/mac required remove <id>` | 移除必需 Mod |
| `/mac whitelist list\|add <id>\|remove <id>` | 管理白名单 |
| `/mac blacklist list\|add <id>\|remove <id>` | 管理黑名单 |

所有修改会即时写盘并影响下一名玩家 / 下一次复检。

---

## 八、两阶段握手流程（三加载器一致）

```
玩家加入
  ├─ 预拦截：requireClientMod 且服务端启用时，若对方未注册本模组通道 → 立即踢出(NO_CLIENT_MOD)
  └─ 建立会话，发送 stage1_req（仅含：协议版本、必需Mod校验模式、必需Mod id 列表）
       │
       ▼  客户端应答 stage1_resp（最小数据：协议、加载器标识/版本、本模组版本、必需Mod的版本映射）
    服务端登录阶段校验：协议版本 → 加载器兼容(严格) → 必需Mod(presence/version_range/strict)
       │
       ├─ 失败 → 踢出并记录（缺失/版本不符等明细展示给玩家）
       └─ 通过 → 发送 stage2_req
                    │
                    ▼  客户端应答 stage2_resp（完整 Mod id+版本 列表）
                 服务端进入游戏阶段校验：必需Mod复核 + 白名单/黑名单 + 加载器(严格)
                    │
                    ├─ 失败 → 踢出
                    └─ 通过 → 状态 VERIFIED，允许进入世界；之后按间隔定期复检
```

- **登录阶段**只交换“必要信息”，避免传输全量列表；
- **进入游戏阶段**拿到全量列表后才放行进世界，并保留该列表供定期复检（无需再次传输）；
- 任一步骤超时按 `NO_CLIENT_MOD`（stage1）或 `HANDSHAKE_TIMEOUT`（stage2）处理；
- 握手全部在主线程驱动 / 处理，网络线程仅做编解码（Fabric 经 `execute`、NeoForge 经
  `enqueueWork` 切回主线程），对服务器 TPS 影响极小。

---

## 九、玩家反馈与管理端提示

- **被拒玩家**：断开原因组件由服务端直接构造纯文本（无翻译键依赖），逐条列出
  “缺少必需 Mod / 版本不符 / 被禁 Mod / 白名单外 Mod”，附解决提示。
- **管理员**：每次拦截通过聊天 + ActionBar + 铁砧音效广播（`[MAC] 违规拦截: 玩家 -> 原因`），
  无在线管理员时降级为服务端日志；历史记录可用 `/mac recent` 查看。

---

## 十、日志与错误处理

- 日志统一前缀 `[MAC]`，包含握手进度、放行 / 拒绝、配置加载与保存结果；
- 配置文件损坏时自动备份为 `mod_access_control.json.invalid.bak` 并重建默认；
- 全部网络与规则执行均包裹 try/catch，单个 mod 读取失败不影响整体判断；
- 违规记录为内存环形缓冲（上限 200 条），服务器重启清空。

## 十一、License / 免责

本项目以 GNU GPL 3.0 发布；准入控制校验的是 mod 标识与版本组合，供服务器管理者表达
**“允许/禁止安装哪些 Mod”** 的规则，不构成对文件级改动的防作弊保证。
