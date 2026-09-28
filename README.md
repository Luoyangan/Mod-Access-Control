# Mod Access Control（模组准入控制）

[English](en_us.md)

一个**服务端** Minecraft 模组，让服主精确控制玩家客户端允许（或禁止）安装哪些 Mod。在 **Forge**、**Fabric** 和 **NeoForge** 三种加载器上行为完全一致，共用同一份配置文件和同一套 `/mac` 命令。

> **注意：** 客户端也必须安装本模组才能完成握手校验；未安装的客户端会在加入服务器时被拦截（可通过 `requireClientMod` 关闭该强制）。

## 特性

- **两阶段握手** —— 登录阶段仅交换协议/加载器等最小信息，进入世界前复核完整 Mod 列表，之后使用上报的列表按间隔定期复检（无需重复传输）。
- **必需 Mod 校验** —— 支持存在性（`presence`）、版本范围（`version_range`）、严格匹配（`strict`）三种模式。
- **策略系统** —— `whitelist`（白名单）/ `blacklist`（黑名单）/ `switch`（双模式，可随时切换生效策略并定期复检）。
- **游戏内管理规则** —— 通过 `/mac` 命令实时查看、增删、重载、保存白/黑名单与必需 Mod 清单，全部带 Tab 补全；可用 `/mac learn` 根据任意玩家的 Mod 清单一键生成名单。
- **服务端多语言** —— 踢出界面、管理员通知、命令回显等全部文案由服务端配置决定，支持 `zh_cn`、`zh_tw`、`en_us`、`ja_jp`、`ru_ru`（`/mac lang <语言>`，语言文件为 JSON 目录，便于扩展新语言）。
- **清晰的玩家反馈** —— 被拦截玩家的断开界面会明确列出缺失的必需 Mod、版本不符详情、命中黑名单或白名单外的 Mod，底部提示区支持自定义文案。
- **豁免机制** —— 可按玩家名或 UUID 豁免指定玩家，也可一键豁免全部 OP。
- **试运行模式** —— 只记录和通知违规、不实际踢出，方便上线前验证规则。
- **Mod 历史与审计** —— 每位玩家的客户端 Mod 清单持久化保存（JSONL），可用 `/mac audit` 审计、`/mac recent` 查看最近违规。
- **管理员提醒** —— 发生拦截时，在线管理员会收到聊天栏 + ActionBar + 音效三重提醒。
- **健壮的错误处理** —— 配置损坏时自动备份并重置；全流程异常捕获与日志记录，不会导致服务器崩溃。

## 配置

配置文件：`<服务器目录>/config/mod_access_control.json`（首次启动自动生成）

常用配置项：

- `enabled` —— 总开关
- `requireClientMod` —— 是否强制客户端安装本模组（默认 `true`）
- `strictLoader` —— 是否要求客户端加载器与服务端一致
- `requiredCheckMode` —— 必需 Mod 校验模式：`presence` / `version_range` / `strict`
- `policy.mode` —— 策略：`whitelist` / `blacklist` / `switch`
- `language` —— 服务端文案语言：`zh_cn` / `zh_tw` / `en_us` / `ja_jp` / `ru_ru`
- `exemptPlayers` / `exemptOps` —— 豁免玩家 / 豁免 OP
- `dryRun` —— 试运行模式（只记录不踢出）
- `allowedMacVersions` —— 允许接入的本模组版本列表（空 = 任意版本）

## 命令

`/mac` 命令一览：`status`、`recent`、`check`、`audit`、`learn`、`reload`、`save`、`recheck`、`enabled`、`dryrun`、`mode`、`active`、`exempt`、`allowedmac`、`required`、`whitelist`、`blacklist`、`lang`。

游戏内输入 `/mac help` 可查看分页帮助与完整用法。

QQ群：812500721