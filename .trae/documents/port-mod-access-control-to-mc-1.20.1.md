# 将 Mod Access Control 移植到 Minecraft 1.20.1（Forge + Fabric）

## Context（背景）

当前项目仅支持 **Minecraft 1.21.1**（Forge / Fabric / NeoForge，Java 21，新版 payload 网络 API）。
用户希望扩展支持更多 MC 版本。经讨论确认：

* **首个目标 = 1.20.1**（目前模组玩家最广的版本，Java 17，旧版 SimpleChannel/PacketByteBuf 网络 API）。

* **只做 Forge + Fabric**；NeoForge 1.20.1 为早期 beta 形态、构建体系特殊，暂不做。

* **工程组织 = 仓库内版本目录**：新增 `versions/1.20.1/forge`、`versions/1.20.1/fabric`，与现有 1.21.1 三端共存于同一仓库；`common/` 核心全版本复用，不复制。

架构优势（已验证）：`common/` 是纯 Java、零 MC/加载器依赖，仅使用 Java 11–17 特性（无 Java 21 专属 API），**一行都不用改**，只需把三个工程的 toolchain 从 21 降到 17。

### 关键版本事实（已核实）

* Forge 1.20.1 = **47.x**，选 `47.3.0`（RB3；47.4.0 为可选更新）。

* Forge 1.20.1 网络 API：`NetworkRegistry.newSimpleChannel(ResourceLocation, Supplier<String>, Predicate<String>, Predicate<String>)`，**无** **`channel.build()`**；`SimpleChannel` 包名为 `net.minecraftforge.network.simple.SimpleChannel`；`consumerMainThread` 的回调签名为 `BiConsumer<MSG, Supplier<NetworkEvent.Context>>`；`NetworkEvent.Context` 无 `isClientSide()/isServerSide()`，需用 `ctx.getDirection().getReceptionSide()`；连接对象用 `ctx.getNetworkManager()`（非 `getConnection()`）；**无** **`ResourceLocation.fromNamespaceAndPath`**（用 `new ResourceLocation(ns, path)`）。

* Fabric 1.20.1：无 `CustomPacketPayload`/`StreamCodec`/`PayloadTypeRegistry`（1.20.5+ 才有）；用旧 API：`ServerPlayNetworking.registerGlobalReceiver(ResourceLocation, PlayChannelHandler)`（5 参回调）、`PacketByteBufs.create()`、`ServerPlayNetworking.send(player, id, buf)`、`canSend(player, id)`、`PacketSender.sendPacket(id, buf)`。

* 版本钉：Loom `1.7.4`（1.8+ 需 Java 21，必须 ≤1.7.x）、Fabric Loader `0.15.11`、Fabric API `0.92.3+1.20.1`。

* `pack.mcmeta` 的 `pack_format` 34 → **15**。

* 机器已装 JDK 17（`C:\Program Files\Java\jdk-17`）；Gradle 8.8（forge）/ 8.10.2（fabric）wrapper 均可运行。

***

## 目标目录结构

```
Mod Access Control\
├── common\            # 不变，全版本共享
├── forge\ fabric\ neoforge\   # 不变（1.21.1）
└── versions\
    └── 1.20.1\
        ├── forge\     # 新工程：gradlew*、gradle\wrapper\、settings.gradle、build.gradle、gradle.properties、src\main\**
        └── fabric\    # 新工程（同上）
```

复制规则：只复制 `src`、`build.gradle`、`gradle.properties`、`settings.gradle`、`gradle/wrapper/` + `gradlew*`。
**不要复制**：`build/`、`.gradle/`、`run-*/`、`.idea/`、根目录遗留的 `src/`、`bin/`、`*.LCK*.java~`、`*~` 等。

产物命名（保持约定 `模组名称-模组加载器-mc版本-模组版本`，由 `archivesName` 自动生成）：

* `versions/1.20.1/forge/build/libs/mod-access-control-forge-1.20.1-1.0.0.jar`

* `versions/1.20.1/fabric/build/libs/mod-access-control-fabric-1.20.1-1.0.0.jar`

***

## 实施步骤

### 1. 创建目录并复制骨架

用 Shell 把 1.21.1 对应工程的骨架复制到 `versions/1.20.1/{forge,fabric}`（按上面复制规则），保留 `gradle/wrapper` 与 `gradlew*`。

### 2. 两个工程修改 `gradle.properties`

**`versions/1.20.1/forge/gradle.properties`**（与 1.21.1 相比仅改版本相关字段，mod 元数据不变）：

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false

minecraft_version=1.20.1
minecraft_version_range=[1.20.1,1.21)
mapping_channel=official
mapping_version=1.20.1

forge_version=47.3.0
forge_version_range=[47,)
loader_version_range=[47,)

mod_id=mod_access_control
mod_name=Mod Access Control
archives_base_name=mod-access-control
mod_license=GNU GPL 3.0
mod_version=1.0.0
mod_group_id=mcyszl.top
mod_authors=Luoyangan
mod_description=Client mod admission control for Minecraft servers. A unified two-phase handshake verifies protocol, loader, required mods and whitelist/blacklist policies across Forge / Fabric / NeoForge. (模组准入控制)
```

**`versions/1.20.1/fabric/gradle.properties`**（字段名与 1.21.1 的 fabric/gradle.properties 一致）：

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false

minecraft_version=1.20.1
loom_version=1.7.4
loader_version=0.15.11
fabric_version=0.92.3+1.20.1

mod_id=mod_access_control
mod_name=Mod Access Control
mod_license=GNU GPL 3.0
mod_version=1.0.0
maven_group=mcyszl.top
archives_base_name=mod-access-control
mod_authors=Luoyangan
mod_description=Client mod admission control for Minecraft servers. A unified two-phase handshake verifies protocol, loader, required mods and whitelist/blacklist policies across Forge / Fabric / NeoForge. (模组准入控制)
```

### 3. 两个工程修改 `build.gradle`（各 2 处）

* toolchain：`JavaLanguageVersion.of(21)` → `of(17)`。

* `srcDir '../common/src/main/java'` → `'../../../common/src/main/java'`。

* 其余（plugins、minecraft/loom 块、processResources、jar manifest）原样保留。

### 4. `versions/1.20.1/fabric/settings.gradle`：补加 toolchain 解析器

Forge 的 settings.gradle 已有；fabric 的没有。复制到 fabric settings.gradle 的 plugins 块：

```gradle
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.7.0'
}
```

### 5. 重写 `versions/1.20.1/forge/src/main/java/.../net/ForgeNet.java`

其余 Forge 适配文件（ForgeMacMod / ForgeEvents / ForgeLog / Holder / MacForgeBridge / Feedback / command/MacCommand）**原样复制，零改动**（其使用的所有 API 在 47.x 均存在）。仅 `ForgeNet.java` 按下面映射重写：

| 1.21.1（现状）                                                                                                                                          | 1.20.1（改写）                                                                                                                                                          |
| --------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `import net.minecraftforge.event.network.CustomPayloadEvent.Context`                                                                                | `import net.minecraftforge.network.NetworkEvent`（处理回调用 `Supplier<NetworkEvent.Context>`）                                                                            |
| `import net.minecraftforge.network.ChannelBuilder`                                                                                                  | `import net.minecraftforge.network.NetworkRegistry` + `net.minecraftforge.network.simple.SimpleChannel`                                                             |
| `ChannelBuilder.named(...).networkProtocolVersion(...).clientAcceptedVersions(...).serverAcceptedVersions(...).simpleChannel()` + `channel.build()` | `channel = NetworkRegistry.newSimpleChannel(new ResourceLocation(Mac.MOD_ID, Mac.CHANNEL_PATH), () -> String.valueOf(Mac.PROTOCOL_VERSION), v -> true, v -> true);` |
| `ResourceLocation.fromNamespaceAndPath(ns, path)`                                                                                                   | `new ResourceLocation(ns, path)`                                                                                                                                    |
| 四个 `messageBuilder(..., NetworkDirection.PLAY_TO_CLIENT/SERVER).encoder(...).decoder(...).consumerMainThread(X::handle).add()`                      | 结构不变；**handle 签名改为** **`(MSG m, Supplier<NetworkEvent.Context> sup)`**，方法体 `NetworkEvent.Context ctx = sup.get(); ctx.setPacketHandled(true);`                      |
| `ctx.isClientSide()` / `ctx.isServerSide()`                                                                                                         | `ctx.getDirection().getReceptionSide().isClient()` / `.isServer()`                                                                                                  |
| `ctx.getConnection()`                                                                                                                               | `ctx.getNetworkManager()`                                                                                                                                           |
| `channel.send(msg, PacketDistributor.PLAYER.with(player))`                                                                                          | **参数顺序反转**：`channel.send(PacketDistributor.PLAYER.with(player), msg)`                                                                                               |
| 客户端应答 `channel.send(msg, c)`                                                                                                                        | `channel.sendToServer(msg)`（客户端向服务器发）                                                                                                                               |
| `channel.isRemotePresent(connection)`                                                                                                               | 不变（47.x 同签名）                                                                                                                                                        |
| `localVersions()` / `localVersion()`（ModList/IModInfo）                                                                                              | 不变                                                                                                                                                                  |

注意：`FriendlyByteBuf.writeUtf/readUtf` 在 1.20.1 默认上限 32767，与 1.21.1 用法一致，保持默认即可。

### 6. 重写 Fabric 网络层（3 个文件）

* `FabricNet.java`：删除 4 个 `record ... implements CustomPacketPayload` 与 `StreamCodec`、`PayloadTypeRegistry`；改为 4 个通道常量：

  ```java
  public static final ResourceLocation CH_S1_REQ  = new ResourceLocation(Mac.MOD_ID, "s1_req");
  public static final ResourceLocation CH_S1_RESP = new ResourceLocation(Mac.MOD_ID, "s1_resp");
  public static final ResourceLocation CH_S2_REQ  = new ResourceLocation(Mac.MOD_ID, "s2_req");
  public static final ResourceLocation CH_S2_RESP = new ResourceLocation(Mac.MOD_ID, "s2_resp");
  ```

  * `init()`：只注册服务端 C2S 接收器：`ServerPlayNetworking.registerGlobalReceiver(CH_S1_RESP, FabricNet::onStage1Response)`（stage2 同理）。

  * `sendToPlayer`：保留 `canSend` 守卫；`FriendlyByteBuf buf = PacketByteBufs.create(); buf.writeUtf(payloadJson); ServerPlayNetworking.send(player, CH_S1_REQ, buf);`

  * 服务端回调（5 参，网络线程）：`(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf, PacketSender responseSender)`；读取 `buf.readUtf(32767)` 后 **`server.execute(() -> ...)`** **切回主线程**再调 `Holder.service().receiveStage1/2(...)`。

  * 客户端应答构建：`respondStage1(String json, PacketSender responseSender)` / `respondStage2(...)`，内部 `PacketByteBufs.create()` + `writeUtf` + `responseSender.sendPacket(CH_S1_RESP, buf)`。

  * `localVersions()/localVersion()` 不变（`FabricLoader.getAllMods()/getModContainer` 在 0.15.x 一致）。

* `FabricClientMod.java`：旧式 5 参回调注册：

  ```java
  ClientPlayNetworking.registerGlobalReceiver(FabricNet.CH_S1_REQ,
          (client, handler, buf, responseSender) -> FabricNet.respondStage1(buf.readUtf(32767), responseSender));
  ClientPlayNetworking.registerGlobalReceiver(FabricNet.CH_S2_REQ,
          (client, handler, buf, responseSender) -> FabricNet.respondStage2(buf.readUtf(32767), responseSender));
  ```

* `FabricEvents.java`：**仅 1 行**——`ServerPlayNetworking.canSend(sp, FabricNet.Stage1RequestPayload.TYPE)` → `ServerPlayNetworking.canSend(sp, FabricNet.CH_S1_REQ)`。其余事件 API（ServerLifecycleEvents / ServerTickEvents / ServerPlayConnectionEvents / CommandRegistrationCallback）在 0.92.x 均不变。

其余 Fabric 适配文件（FabricMacMod / FabricLog / Holder / MacFabricBridge / Feedback / command/MacCommand）**原样复制，零改动**。

### 7. 资源文件

* `forge/src/main/resources/META-INF/mods.toml`：占位符由 gradle.properties 展开，无需手动改（loaderVersion/forge\_version\_range→`[47,)`、minecraft\_version\_range→`[1.20.1,1.21)`）。

* `fabric/src/main/resources/fabric.mod.json`：`"minecraft": "~1.20.1"`，`"java": ">=17"`（fabricloader `>=0.14.21` 与 fabric-api `*` 保持）。

* 两个工程的 `pack.mcmeta`：`"pack_format": 34` → `15`。

### 8. 更新根 `README.md`

* 首行版本描述改为同时覆盖 1.21.1（Forge/Fabric/NeoForge）与 1.20.1（Forge/Fabric）。

* 目录结构节增加 `versions/1.20.1/forge|fabric` 说明（共享 `common/`，相对 srcDir 引入）。

* 构建节增加 1.20.1 的构建命令并注明 **JDK 17**。

### 9. `.gitignore`

无需修改（`build/`、`**/build/`、`.gradle`、`run-*/` 均为通用模式，已覆盖新目录）。

***

## 验证

```powershell
# 1.20.1 Forge（toolchain 17 自动解析本机 C:\Program Files\Java\jdk-17）
cd "d:\java xm\Mod Access Control\versions\1.20.1\forge"
.\gradlew.bat build        # 期望：build\libs\mod-access-control-forge-1.20.1-1.0.0.jar

# 1.20.1 Fabric
cd "d:\java xm\Mod Access Control\versions\1.20.1\fabric"
.\gradlew.bat build        # 期望：build\libs\mod-access-control-fabric-1.20.1-1.0.0.jar

# 回归：1.21.1 三端不应受影响
cd "d:\java xm\Mod Access Control\forge" ; .\gradlew.bat build
cd "d:\java xm\Mod Access Control\fabric" ; .\gradlew.bat build
cd "d:\java xm\Mod Access Control\neoforge" ; .\gradlew.bat build
```

编译通过后，运行期验证清单（在 1.20.1 服务器放 jar）：

1. 日志出现 `MAC Forge/Fabric 网络通道已注册`。
2. 装了本模组的客户端加入 → 两阶段握手通过，`/mac status` 显示通过。
3. 纯原版客户端加入 → 被以 `NO_CLIENT_MOD` 纯文本理由踢出。
4. `/mac whitelist add <id>` 后复检/重进生效；`/mac mode`、`/mac active` 等 Tab 补全正常。

***

## 风险与回退

| 风险                                            | 回退                                                                                                                                 |
| --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `NetworkEvent.Context` 具体方法签名与预期不符            | 用通用写法 `ctx.getDirection().getReceptionSide()`；`sendToServer(msg)` 与 `send(PacketDistributor.PLAYER.with(p), msg)` 为 47.x 确定存在的 API |
| ForgeGradle 6.0.x 与 Gradle 8.8 不兼容            | 该工程 wrapper 降级到 `gradle-8.1.1-bin.zip`（1.20.1 官方 MDK 版本）                                                                           |
| Loom 1.7.4 与 Gradle 8.10.2 不匹配                | wrapper 改 `gradle-8.11-bin.zip`（仍兼容 Loom 1.7 / Java 17）                                                                            |
| JDK 17 未被 Gradle 自动发现                         | 已装 `C:\Program Files\Java\jdk-17`，foojay resolver 兜底自动下载                                                                           |
| Fabric 客户端 `canSend` 在 JOIN 时为 false 误判“未装模组” | 现有逻辑本就按“无通道=未装模组”踢出；若误伤再改为延迟到下一 tick 检查                                                                                            |
| `readUtf` 32767 上限对大 Mod 列表不足                 | 1.20.1 上限即 32767，够常规整合包（约 300 mod / 20KB）；极端超长列表再议协议分片                                                                             |
| 47.3.0 有回归                                    | `forge_version` 升 47.4.0，其余不动                                                                                                      |

