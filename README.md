# Mod Access Control（模组准入控制）

面向 **Minecraft** 的 **服务端** Mod：通过统一协议、服务端规则配置与
**两阶段握手校验**，对客户端的 Mod 组合实施安全准入控制。同一份规则与同一套协议，
在 **Forge / Fabric / NeoForge** 三种加载器上行为一致。

- 当前版本：`1.1.1`（支持 MC 1.21.1 / 1.20.1 / 1.16.5 / 1.12.2）
- Mod id（各端一致）：`mod_access_control`
- 握手协议版本：`1`
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
| 黑白名单进阶（v1.1） | 条目支持 `*` 通配符与**可选版本约束**（如 `sodium >=1.0`）；未写约束 = 匹配全部版本 |
| 多语言（v1.1） | 服务端文案（命令/日志/管理广播）随 `language` 配置切换 zh_cn / en_us；踢出消息统一由服务端按**客户端语言**渲染为纯文本下发，客户端界面与服务端日志显示一致（语言未知时回退服务端语言） |
| 反馈明细开关（v1.1） | `kickShowDetails` 控制踢出消息是否逐条列出违规 Mod；`adminShowDetails` 控制管理员广播明细粒度 |
| 公共 API（v1.1） | 其他 Mod 可把本模组声明为前置，调用 `MacApi` 查询玩家校验状态 / Mod 清单 / 管理豁免 / 订阅违规事件 |
| 规则管理 | 启动读配置 + 游戏内 `/mac` 命令即时修改 / 查看 / 保存（枚举参数带自动补全，`/mac help [页码]` 分页帮助） |
| 玩家反馈 | 双语断开原因（缺失 / 版本不符 / 违规 Mod 明细）+ 底部提示区（可用自定义行） |
| 豁免 | `exemptPlayers` 玩家名 / `uuid:` 列表 + `exemptOps`，豁免者跳过全部校验与握手 |
| 试运行 | `dryRun`：违规只记录 / 通知，不实际踢出，用于上线前验证规则避免误伤 |
| Mod 历史 | 玩家客户端 Mod 清单持久记录（JSONL），支持 `/mac audit` 审计、`/mac learn` 一键建名单 |
| 管理辅助 | 在线管理员收到违规广播（聊天 + ActionBar + 音效），`/mac status`、`/mac recent` |
| 错误处理 | 解析失败自动备份（`*.invalid.bak`）并重置默认；全流程 try/catch + 日志 |

---

## 二、安装

1. 服务端：把与所用加载器对应的 jar 放入服务端 `mods/`；
2. 客户端：**每个玩家客户端**的 `mods/` 也需放入同一 jar（用于自动应答握手）；
3. 服务端第一次启动会自动生成默认配置 `config/mod_access_control.json`；
4. 加载器需与服务器一致（Forge 服配 Forge 客户端，Fabric 服配 Fabric 客户端，
   NeoForge 服配 NeoForge 客户端）；跨加载器握手会被“加载器兼容性”校验拦截。

---

## 三、配置文件

路径：`<服务器目录>/config/mod_access_control.json`（所有加载器完全一致）。

默认值（首次启动自动生成）：

| 字段 | 默认 | 含义 |
| --- | --- | --- |
| `configVersion` | `1` | 配置结构版本 |
| `enabled` | `true` | 总开关 |
| `enforceIntegratedServer` | `false` | 是否在单人存档 / 局域网（内置服务器）中也强制校验 |
| `requireClientMod` | `true` | 客户端必须安装本模组；未安装者加入时被拦截 |
| `strictLoader` | `true` | 客户端加载器标识必须与服务端一致 |
| `handshakeTimeoutSeconds` | `10` | 每个握手阶段的超时秒数 |
| `language` | `"auto"` | 服务端文案语言（v1.1）：`auto` / `zh_cn` / `en_us`；`auto` 跟随服务器系统语言 |
| `requiredCheckMode` | `"presence"` | 必需 Mod 校验模式：`presence` / `version_range` / `strict` |
| `requiredMods[]` | `[]` | 必需 Mod 清单（`id` + 可选 `bounds[]` 操作符约束，或旧版 `minVersion`/`maxVersion`/`exactVersion`） |
| `policy.mode` | `"blacklist"` | 策略：`whitelist` / `blacklist` / `switch` |
| `policy.activeMode` | `"whitelist"` | `switch` 模式下当前生效的策略 |
| `policy.recheckIntervalSeconds` | `60` | 定期复检间隔（秒），`0` = 不复检 |
| `policy.whitelist[]` | `[]` | 白名单（v1.1：字符串或 `{id, bounds}` 对象，见下文） |
| `policy.blacklist[]` | `[]` | 黑名单（同上） |
| `ignoredModIds[]` | `[]` | 额外忽略的 mod id（不参与任何校验） |
| `logViolations` | `true` | 违规事件是否写入服务端日志 |
| `exemptPlayers[]` | `[]` | 豁免玩家：玩家名（忽略大小写）或 `uuid:` 前缀的 UUID |
| `exemptOps` | `false` | 是否默认豁免服务端 OP（不参与任何校验） |
| `dryRun` | `false` | 试运行：违规只记录 / 通知，不实际踢出（验证规则用） |
| `allowedMacVersions[]` | `[]` | 允许接入的本模组（Mod Access Control）版本列表，精确匹配客户端 macVersion；空 = 放行任意版本 |
| `kickShowDetails` | `true` | 踢出消息是否逐条列出违规明细（v1.1；旧配置缺字段按开启处理） |
| `adminShowDetails` | `true` | 管理员广播是否展示逐条违规明细（v1.1；关闭时只报类型与条数） |
| `kickFooterEnabled` | `true` | 踢出消息底部“提示区”是否显示 |
| `kickFooterLines[]` | `[]` | 底部提示区之后追加的自定义行 |

### 黑白名单条目（v1.1）

条目支持两种写法，**旧配置（纯字符串）无需修改即可继续使用**：

```json
"whitelist": [
  "jei",                                            // 纯字符串：匹配该 id 的任意版本
  { "id": "sodium", "bounds": [ { "op": ">=", "version": "0.5.0" } ] },
  { "id": "optifine*", "bounds": [ { "op": "=", "version": "1.20.1" } ] }
]
```

- `id` 支持 `*` 通配符（如 `sodium*` 匹配所有以 sodium 开头的 id）；
- 未设置 `bounds` = 匹配**全部版本**；设置了则版本也须满足；
- 黑名单同理（`"blacklist": [{ "id": "hacked*", "bounds": [...] }]`）。

**必需 Mod 约束写法**（`version_range` 模式生效；`strict` 模式下上述规则 + 加载器一致性都须满足）：

```json
"requiredMods": [
  { "id": "sodium" },                                                  // 任意版本，仅需存在
  { "id": "fabric-api", "bounds": [ { "op": ">=", "version": "0.100.0" } ] }, // 下限
  { "id": "some_mod",   "bounds": [ { "op": "<", "version": "2.0.0" } ] },    // 上限
  { "id": "map_mod",    "bounds": [ { "op": "=", "version": "1.2.3" } ] }     // 精确版本
]
```

操作符支持：`=`（等于）、`!=`（不等于）、`>` / `>=`、`<` / `<=`；多条 `bounds` 之间为
“且”关系。旧版字段 `minVersion` / `maxVersion` / `exactVersion` 仍受支持并自动按
`[min,max]` / `exact` 语义参与校验。

完整示例见 [config-example/mod_access_control.json](config-example/mod_access_control.json)。

---

## 四、策略模式

| 模式 | 行为 |
| --- | --- |
| `whitelist` | 客户端只能安装白名单内 Mod（含版本约束）；白名单之外一律拒绝 |
| `blacklist` | 客户端可装任意 Mod；检测到黑名单内 Mod（或命中版本约束）即拒绝 |
| `switch` | 加入时完整检查；运行时可在黑白名单间切换（`activeMode`），并按 `recheckIntervalSeconds` 对已通过玩家定期复检 |

> 忽略机制：本模组自身、`minecraft`、当前加载器及其基础设施（如 `forge`/`fabricloader`/
> `fabric-api`/`neoforge` 等）恒不参与白名单 / 黑名单 / 必需校验，避免误伤。
> 注意：黑白名单校验的是**游戏内容 Mod 的 id 与版本**，并非文件层面的完整性防作弊手段。

---

## 五、命令 `/mac`（权限等级 2）

| 命令 | 作用 |
| --- | --- |
| `/mac help [页码]` | 分页帮助（v1.1；`/mac help 2` 查看下一页） |
| `/mac` 或 `/mac status` | 查看当前策略、清单数量、豁免 / 试运行状态、在线会话统计 |
| `/mac recent` | 最近违规记录（最新在前，最多 20 条展示） |
| `/mac check <玩家>` | 查看指定玩家会话状态（玩家参数可 Tab 补全） |
| `/mac audit <玩家>` | 查看指定玩家最近的历史 Mod 记录（最多 10 条，来自 JSONL 持久记录） |
| `/mac learn <玩家> <whitelist/blacklist>` | 用该玩家在线/最近一次 Mod 清单一键写入白名单或黑名单 |
| `/mac reload` | 从配置文件重新加载规则 |
| `/mac save` | 把当前内存配置保存到文件 |
| `/mac recheck` | 用最新规则对在线玩家立即复检一次 |
| `/mac enabled <true/false>` | 总开关 |
| `/mac dryrun <true/false>` | 试运行开关（违规不实际踢出） |
| `/mac mode <whitelist/blacklist/switch>` | 设置策略模式（Tab 可补全） |
| `/mac active <whitelist/blacklist>` | 设置 switch 模式下生效的策略（Tab 可补全） |
| `/mac exempt list\|add <玩家>\|remove <玩家>` | 管理豁免名单（移除项 Tab 可补全） |
| `/mac allowedmac list\|add <版本>\|remove <版本>` | 管理允许接入的本模组版本（空 = 放行任意；`add *` 清空限制） |
| `/mac required list` | 列出必需 Mod |
| `/mac required add <id> [操作符写法]` | 新增必需 Mod（如 `>=1.0.0`、`1.0~2.0`、`--exact 1.2.3`） |
| `/mac required remove <id>` | 移除必需 Mod（Tab 可补全） |
| `/mac whitelist list\|add <id> [版本约束]\|remove <id>` | 管理白名单（v1.1：add 可附版本约束，如 `add sodium >=1.0`） |
| `/mac blacklist list\|add <id> [版本约束]\|remove <id>` | 管理黑名单（同上） |

> 自动补全：`mode` / `active` / `learn`、`required/whitelist/blacklist remove`、以及
> `check/audit/learn/exempt add` 的玩家参数均提供在线候选，降低误输。
> 所有修改会即时写盘并影响下一名玩家 / 下一次复检。

---

## 六、多语言（v1.1）

- **服务端文案**（命令回显 / 日志 / 管理广播）：由配置 `language` 决定，
  `auto` 跟随服务器操作系统语言；内置 `zh_cn` 与 `en_us` 两套文案。
- **玩家踢出消息**：由服务端按**玩家的客户端语言**渲染为纯文本后下发，
  因此客户端界面与服务端日志 / 控制台显示完全一致；无法获取客户端语言时
  回退到服务端语言（`language` 配置）。

---

## 七、作为其他 Mod 的前置（公共 API，v1.1）

其他 Mod 可把本模组声明为前置（Forge/NeoForge 在 `mods.toml` 加依赖项，Fabric 在
`fabric.mod.json` 的 `depends` 中加入 `"mod_access_control": "*"`），然后调用静态门面
`mcyszl.top.mod_access_control.api.MacApi`：

| 方法 | 作用 |
| --- | --- |
| `MacApi.available()` | 核心是否已就绪 |
| `MacApi.isVerified(uuidOrName)` | 玩家当前会话是否已通过全部准入校验 |
| `MacApi.getSessionMods(uuidOrName)` | 读取该玩家在线会话的完整 Mod 清单（id → 版本） |
| `MacApi.isExempt(uuid, name, op)` / `addExempt` / `removeExempt` | 查询 / 增删豁免条目 |
| `MacApi.addViolationListener(listener)` | 订阅违规事件（含试运行中“未实际踢出”的违规） |
| `MacApi.modVersion()` / `protocolVersion()` / `loaderType()` | 版本与加载器信息 |

所有方法在核心未就绪时安全返回默认值，不会抛异常。

---

## 八、两阶段握手流程（各加载器一致）

```
玩家加入
  ├─ 统一预检(handleLoginAttempt)：总开关关闭 → 直接放行
  │    ├─ 命中豁免（exemptPlayers / exemptOps）→ 直接放行，不发任何包、不建会话
  │    ├─ 未装本模组 且 requireClientMod=true → 立即踢出(NO_CLIENT_MOD)；dryRun 下只记录
  │    └─ 通过 → 建立会话，发送 stage1_req（仅含：协议版本、必需Mod校验模式、必需Mod id 列表）
       │
       ▼  客户端应答 stage1_resp（最小数据：协议、加载器标识/版本、本模组版本、必需Mod的版本映射）
    服务端登录阶段校验：协议版本 → 加载器兼容(严格) → 必需Mod(presence/version_range/strict)
       │
       ├─ 失败 → 记录违规；dryRun 下置 VERIFIED 不踢出，否则踢出并记录（明细展示给玩家）
       └─ 通过 → 发送 stage2_req
                    │
                    ▼  客户端应答 stage2_resp（完整 Mod id+版本 列表）
                 服务端进入游戏阶段校验：必需Mod复核 + 白名单/黑名单 + 加载器(严格)
                    │
                    ├─ 失败 → 同上（dryRun 记录 / 正常踢出）
                    └─ 通过 → 状态 VERIFIED、写入 Mod 历史记录，允许进入世界；之后按间隔定期复检
```

- **登录阶段**只交换“必要信息”，避免传输全量列表；
- **进入游戏阶段**拿到全量列表后才放行进世界，并保留该列表供定期复检（无需再次传输）；
- 任一步骤超时按 `NO_CLIENT_MOD`（stage1）或 `HANDSHAKE_TIMEOUT`（stage2）处理；
- 握手全部在主线程驱动 / 处理，网络线程仅做编解码（Fabric 经 `execute`、NeoForge 经
  `enqueueWork` 切回主线程），对服务器 TPS 影响极小。

---

## 九、玩家反馈与管理端提示

- **被拒玩家**：断开原因随客户端语言显示（详见“多语言”），逐条列出
  “缺少必需 Mod / 版本不符 / 被禁 Mod / 白名单外 Mod”；`kickShowDetails=false`
  时隐藏逐条明细只保留标题与提示区。下方附加底部提示区（默认提示 +
  配置的自定义行，`kickFooterEnabled` 可整体关闭）。
- **豁免者**：不发送任何网络消息、不建立会话、不参与校验，直接放行。
- **试运行（dryRun）**：所有本应踢出的违规划转为“记录违规 + 通知管理员（标注未实际踢出）”，
  用于上线前验证规则是否误伤。
- **Mod 历史**：每个通过/被拒玩家在握手结束时把完整 Mod 清单写入
  `config/mod_access_control_history.jsonl`（最长保留 2 万条 / 8MB），可用 `/mac audit` 审计，
  或 `/mac learn` 一键把它写入白名单 / 黑名单。
- **管理员**：每次拦截通过聊天 + ActionBar + 铁砧音效广播（`[MAC] 违规拦截: 玩家 -> 原因`），
  无在线管理员时降级为服务端日志；`adminShowDetails=false` 时只报违规类型与条数；
  历史记录可用 `/mac recent` 查看。

---

## 十、日志与错误处理

- 日志统一前缀 `[MAC]`，包含握手进度、放行 / 拒绝、配置加载与保存结果；
- 配置文件损坏时自动备份为 `mod_access_control.json.invalid.bak` 并重建默认；
- 全部网络与规则执行均包裹 try/catch，单个 mod 读取失败不影响整体判断；
- 违规记录为内存环形缓冲（上限 200 条），服务器重启清空；
- 玩家 Mod 历史为持久 JSONL 记录（`mod_access_control_history.jsonl`），重启不丢失，
  自动裁剪（最多 2 万条 / 8MB），供 `/mac audit` 与 `/mac learn` 使用。

## 十一、License / 免责

本项目以 Apache License 2.0 发布（许可证全文见 `LICENSE`，版权署名与随附第三方组件见 `NOTICE`）；
准入控制校验的是 mod 标识与版本组合，供服务器管理者表达
**“允许/禁止安装哪些 Mod”** 的规则，不构成对文件级改动的防作弊保证。

## 十二、版权

@Copyright 2024-2026 原生之旅 | mcyszl.top Luoyangan
