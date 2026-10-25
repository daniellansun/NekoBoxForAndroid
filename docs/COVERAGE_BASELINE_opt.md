# 第一方代码覆盖率（分支 `opt`）

数字来自 **ossDebug JVM 单元测试 + JaCoCo 0.8.12**，只统计本仓库手写的 app 类。第三方 Maven/JitPack 依赖、ViewBinding、Room 生成类、AIDL Stub、`libcore` gomobile 桩都不进入分子或分母。

## 基线（本批之前）

测量提交：`a4c5072040329a68cc139d8823d016f8aeba6094`（`opt` 顶端，`bump version`）。当时唯一测试 `CoverageHarnessTest` 不调用产品代码。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 |
| --- | ---: | ---: | ---: | ---: |
| 行（LINE） | 0 | 16144 | 16144 | **0.00%** |
| 指令（INSTRUCTION） | 0 | 112159 | 112159 | **0.00%** |
| 分支（BRANCH） | 0 | 9512 | 9512 | **0.00%** |
| 方法（METHOD） | 0 | 3746 | 3746 | **0.00%** |
| 类（CLASS） | 0 | 771 | 771 | **0.00%** |

JaCoCo 扫到 876 个第一方 `.class`（771 个 JaCoCo 类、207 个有可执行行的源文件）。

## 第一批（分享链接解析 + 纯函数）

26 个 JVM 测试，0 失败。`./gradlew :app:jacocoFirstPartyReport`（`SKIP_HEV=1`）通过。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对基线 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 2591 | 13555 | 16146 | **16.05%** | +2591 行；分母 +2 |
| 指令（INSTRUCTION） | 16814 | 95363 | 112177 | **14.99%** | +16814；分母 +18 |
| 分支（BRANCH） | 1556 | 7956 | 9512 | **16.36%** | +1556；分母不变 |
| 方法（METHOD） | 338 | 3412 | 3750 | **9.01%** | +338；分母 +4 |
| 类（CLASS） | 99 | 672 | 771 | **12.84%** | +99；分母不变 |

分母多出的 2 行、4 个方法和 18 条指令来自 `Utils.kt`：`Socket.getFileDescriptor$` 与 `FileDescriptor.getInt$` 改为 `by lazy`。原先在文件初始化时反射，JVM 上会抛 `ExceptionInInitializerError`，整份 `UtilsKt`（含 `urlSafe`、`blankAsNull`、`readableMessage`）都加载不了。Android 上仍是第一次使用才查找，查找的方法没变。

协议解析包（`io.nekohasekai.sagernet.fmt` 及其子包，加上 `moe.matsuri.nb4a.proxy.**`）合计 **2137 / 3655 行（58.47%）**。`fmt` 根包里的 `ConfigBuilder.kt`（698 行）仍是 0，所以这个合计被配置构建拉低；分享链接文件本身见下表。

有覆盖的源文件 53 个（原先 0）。class 文件数仍是 876。

## 第二批（配置构建、订阅原文、TLS 出站）

42 个 JVM 测试，0 失败（本批新增 16 个）。`./gradlew :app:jacocoFirstPartyReport`（`SKIP_HEV=1`）通过。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第一批 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 4265 | 11919 | 16184 | **26.35%** | +1674 行；分母 +38 |
| 指令（INSTRUCTION） | 29069 | 83235 | 112304 | **25.88%** | +12255；分母 +127 |
| 分支（BRANCH） | 2941 | 6571 | 9512 | **30.92%** | +1385；分母不变 |
| 方法（METHOD） | 524 | 3265 | 3789 | **13.83%** | +186；分母 +39 |
| 类（CLASS） | 149 | 624 | 773 | **19.28%** | +50；分母 +2 |

分母多出的 38 行、39 个方法和 2 个类来自新文件 `ConfigSource.kt`（接口 + `ProductionConfigSource`）。JaCoCo 扫到 879 个第一方 `.class`（773 个 JaCoCo 类）。有覆盖的源文件 65 个。

协议包（`fmt` 及其子包，加上 `nb4a.proxy.**`）合计 **2994 / 3693 行（81.07%）**。分母相对第一批 +38，即 `ConfigSource.kt`。

本批主要文件：

| 源文件 | 第一批 | 第二批 |
| --- | ---: | ---: |
| `fmt/ConfigBuilder.kt` | 0/698 | **600/698（86.0%）** |
| `group/RawUpdater.kt` | 0/646 | **487/646（75.4%）** |
| `nb4a/SingBoxOptions.java` | 27/171 | **82/171（48.0%）** |
| `nb4a/SingBoxOptionsUtil.kt` | 0/131 | **123/131（93.9%）** |
| `fmt/v2ray/V2RayFmt.kt` | 493/569 | 528/569 |
| `fmt/hysteria/HysteriaFmt.kt` | 189/243 | 239/243 |
| `fmt/tuic/TuicFmt.kt` | 44/66 | 66/66 |
| `fmt/juicity/JuicityFmt.kt` | 35/52 | 52/52 |
| `fmt/naive/NaiveFmt.kt` | 34/53 | 52/53 |
| `fmt/trojan_go/TrojanGoFmt.kt` | 58/104 | 92/104 |
| `ktx/Formats.kt` | 123/176 | 126/176 |
| `nb4a/utils/JavaUtil.java` | 32/91 | 35/91 |
| `fmt/KryoConverters.java` | 15/60 | 19/60 |
| `ktx/Utils.kt` | 11/158 | 23/158 |
| `fmt/ConfigSource.kt` | （新文件） | 2/38 |

`RawUpdater.kt` 未覆盖的 159 行里，约 149 行在 `doUpdate`（会走 `libcore` HTTP 和 `DataStore`），`parseRaw` 及其解析助手只剩 10 行。`ConfigSource.kt` 几乎未覆盖是故意的：`ProductionConfigSource` 的 getter 一调用就会打开 Room，单元测试换成内存实现，不执行这层委托。

### 可测缝

`buildConfig` 和 TLS 出站原先直接读 `DataStore` / `SagerDatabase`。`DataStore` 的对象初始化会构造 `RoomPreferenceDataStore`，所以不能在普通 JVM 测试里加载它。

`ConfigSource` 把这些读取收成调用时才取值的接口。默认实现 `ProductionConfigSource` 仍在用到时读 `DataStore`、`SagerDatabase` 和 `SingBoxOptionsUtil.domainStrategy`。`@Volatile var configSource` 默认就是这个实现。单元测试在 `withConfig { }` 里换成 `MemoryConfigSource`，结束时恢复。生产路径的读取时机和读取对象没有改。测试不把 Room 放进单元测试 classpath。

替换点：`ConfigBuilder.kt` 里原来的 `DataStore.*`、`SagerDatabase.*Dao`、`SingBoxOptionsUtil.domainStrategy`；以及 `V2RayFmt.buildSingBoxOutboundTLS`、Hysteria / TUIC / Juicity 的 sing-box TLS、`buildNaiveConfig`、`buildTrojanGoConfig` 里的 `globalAllowInsecure` / `logLevel` / `ipv6Mode`。


## 第三批（插件映射、订阅更新、选项构造）

52 个 JVM 测试，0 失败（本批新增 10 个）。`./gradlew :app:jacocoFirstPartyReport`（`SKIP_HEV=1`）通过。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第二批 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 5272 | 10945 | 16217 | **32.51%** | +1007 行；分母 +33 |
| 指令（INSTRUCTION） | 35054 | 77371 | 112425 | **31.18%** | +5985；分母 +121 |
| 分支（BRANCH） | 3452 | 6062 | 9514 | **36.28%** | +511；分母 +2 |
| 方法（METHOD） | 755 | 3060 | 3815 | **19.79%** | +231；分母 +26 |
| 类（CLASS） | 253 | 524 | 777 | **32.56%** | +104；分母 +4 |

分母多出的行来自 `ConfigSource` 上新的插件 / 包名 / 警告方法，以及 `SubscriptionAccess.kt`（订阅 HTTP 与资料库写入的生产委托）。JaCoCo 扫到 885 个第一方 `.class`（777 个 JaCoCo 类）。

| 源文件 | 第二批 | 第三批 |
| --- | ---: | ---: |
| `fmt/ConfigBuilder.kt` | 600/698 | **686/690** |
| `group/RawUpdater.kt` | 487/646 | **619/631** |
| `group/GroupUpdater.kt` | 0/85 | **57/85** |
| `nb4a/SingBoxOptions.java` | 82/171 | **169/171** |
| `database/ProxyEntity.kt` | 96/365 | **314/365** |
| `database/ProxyGroup.kt` | — | **52/55** |
| `database/preference/KeyValuePair.kt` | 0/71 | **54/71** |
| `fmt/KryoConverters.java` | 19/60 | **58/60** |
| `ktx/Formats.kt` | 126/176 | **165/176** |
| `ktx/Utils.kt` | 23/158 | **36/158** |
| `database` 包 | 131/961 | **489/961** |
| `database/DataStore.kt` | 0/206 | 0/206 |

`ConfigBuilder` 剩下 4 行，是当前协议集合到不了的 `else` 分支。`RawUpdater` 剩下 12 行，多数是 `parseRaw` 里没喂到的分支。`doUpdate` 的插入、改名、去重、过滤、更新、重排、删除、content URI 和 `forceResolve` 都在 JVM 上跑过。

### 本批的缝

- `ConfigSource` 增加 `usesMatsuriExe`、`hasExternalPlugin`、`packageUid`、`ensurePackagesLoaded`、两条警告，以及 `serviceStarted`。生产实现仍在调用时走 `Plugins`、`PackageCache`、`Toast` 和 `DataStore.serviceState`。测试用内存实现，不弹 Toast、不开 Room。
- `SubscriptionClient` / `ProfileStore` 把 `doUpdate` 的 libcore HTTP、content URI 和 `SagerDatabase` 写入收成调用时委托。`ProductionSubscriptionClient` 仍用原来的 `Libcore.newHttpClient()`、`DataStore` 端口和 `allowInsecure`。`ProductionProfileStore` 仍调用原来的 DAO。测试替换这两个 `@Volatile` 变量。
- `GroupUpdater.forceResolve` 改读 `configSource` 的 IPv6、fake DNS 和服务状态，不再在这条路径上碰 `DataStore`。假 DNS 走系统网络的分支仍要 `SagerNet.underlyingNetwork` 非空才会进。

## 第四批（DataStore、生产委托、订阅确认）

58 个 JVM 测试，0 失败（本批新增 6 个）。`./gradlew :app:jacocoFirstPartyReport`（`SKIP_HEV=1`）通过。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第三批 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 5756 | 10524 | 16280 | **35.36%** | +484 行；分母 +63 |
| 指令（INSTRUCTION） | 43424 | 69631 | 113055 | **38.41%** | +8370；分母 +630 |
| 分支（BRANCH） | 3656 | 5880 | 9536 | **38.34%** | +204；分母 +22 |
| 方法（METHOD） | 1253 | 2614 | 3867 | **32.40%** | +498；分母 +52 |
| 类（CLASS） | 265 | 514 | 779 | **34.02%** | +12；分母 +2 |

分母增加来自 `GroupDirectory` / `RoomGroupDirectory`、`DataStore` 里延迟打开 Room 的 getter，以及 `Preferences.kt` 里按次取 store 的委托。`syncFirstPartyClasses` 复制 1029 个 class 文件（第三批记录为 885）；多出来的主要是各 preference 默认值 lambda。JaCoCo CLASS 计数 779。有可执行行的源文件 209 个。

| 源文件 | 第三批 | 第四批 |
| --- | ---: | ---: |
| `database/DataStore.kt` | 0/206 | **216/219** |
| `database/preference/RoomPreferenceDataStore.kt` | 0 | **53/53** |
| `database` 包 | 489/961 | **706/974** |
| `database.preference` 包 | 54/151 | **109/151** |
| `fmt/ConfigSource.kt` | 2/56 | **34/56** |
| `group/SubscriptionAccess.kt` | 8/38 | **26/38** |
| `group/GroupUpdater.kt` | 57/85 | **79/87** |
| `group/RawUpdater.kt` | 619/631 | **629/631** |
| `fmt/ConfigBuilder.kt` | 686/690 | **689/690** |
| `nb4a/proxy/PreferenceBinding.kt` | 0/62 包内 | **43/44** |
| `ktx/Preferences.kt` | — | **69/85** |
| `database/ProxyEntity.kt` | 314/365 | 315/365 |
| `nb4a/SingBoxOptions.java` | 169/171 | 169/171 |
| `ktx/Utils.kt` | 36/158 | 36/158 |
| `nb4a/utils/JavaUtil.java` | 37/91 | 37/91 |

`DataStore` 剩下 3 行是 `RoomGroupDirectory`，里面直接叫 `SagerDatabase.groupDao`。`ConfigBuilder` 剩下的 1 行是协议 `when` 的 `else -> throw IllegalStateException("can't reach")`。`RawUpdater` 剩下 2 行：clash 的 `proxies` 不是列表时 `app.getString`，以及 vmess/vless/trojan 类型分支里到不了的 `else`。`SingBoxOptions` 仍是 2 行（静态 gson 与 serializer 里返回 null 的标记）。

### 本批的缝

- `configurationStore` / `profileCacheStore` 改成 getter。默认仍是 `RoomPreferenceDataStore(PublicDatabase.kvPairDao)` 和 `TempDatabase.profileCacheDao`，但是 `by lazy`，对象加载时不打开 Room。每个 preference 委托在 get/set 时才调用 `configPrefs()` / `cachePrefs()`。单元测试先把 `configurationStoreOverride` 设成内存 `KeyValuePair.Dao` 上的 `RoomPreferenceDataStore`。生产上第一次真正读偏好时才建库；只写 `serviceState` 不再建库。键、默认值和读写的类型没有改。
- `userIndex` 生产仍是 `Binder.getCallingUserHandle().hashCode()`，懒计算。测试可设 `userIndexOverride`，避免 `mixedPort` 在 JVM 上碰 Binder。
- `currentGroupId` / `currentGroup` / `selectedGroupForImport` 改走 `groupDirectory`。默认 `RoomGroupDirectory` 仍调用原来的 `SagerDatabase.groupDao`。测试换成内存列表。
- `GroupUpdater.executeUpdate` 仍读 `DataStore.serviceState.connected`（现在这样读不会打开 Room）。断开时的确认文案收成 `subscriptionUpdateWarning`，默认还是 `app.getString(R.string.update_subscription_warning)`。测试换成固定字符串。
- `ProductionConfigSource` 里读 `DataStore` 的 getter，以及 `SingBoxOptionsUtil.domainStrategy`，在内存 store 上跑过。`ProductionSubscriptionClient.fetch` 用 libcore 桩的空 HTTP 客户端跑过（含 `restrictedTLS` 和 `allowInsecure`）。DAO、`Plugins`、`PackageCache`、`Toast`、`readContent`、`ProductionProfileStore` 没有调用，它们要 Room 或 `SagerNet.application`。

## 第五批（档案、组、WebView 锁路径、hev 配置）

73 个 JVM 测试，0 失败（本批新增 15 个）。`./gradlew :app:jacocoFirstPartyReport`（`SKIP_HEV=1`）通过。

**验收基线是 Test 干净复跑的行 6203/16314（38.02%）**。本仓库同一次命令的 XML 是 6205/16314。差 2 行，允许 ±2，标题用 Test 的 6203。下表指令、分支、方法、类来自本仓库那份 XML（当时行数是 6205）。Test 没有逐项重报这四项，只说指令大约 46736–46737。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第四批 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 6203 | 10111 | 16314 | **38.02%** | +447 行；分母 +34 |
| 指令（INSTRUCTION） | 46743 | 66472 | 113215 | **41.29%** | +3319；分母 +160 |
| 分支（BRANCH） | 3827 | 5725 | 9552 | **40.06%** | +171；分母 +16 |
| 方法（METHOD） | 1429 | 2467 | 3896 | **36.68%** | +176；分母 +29 |
| 类（CLASS） | 304 | 475 | 779 | **39.02%** | +39；分母不变 |

`syncFirstPartyClasses` 仍复制 1029 个 class 文件。有可执行行的源文件仍是 209 个。分母增加来自 DAO 覆盖口、默认规则文案、`settingsActivity`、WebView 锁路径和 `noProxiesFoundInFile`。

本仓库在 `12ece1f` 的干净构建（`SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport --offline`，73 个测试、0 失败）XML 根计数是：行 6205/10109/16314，指令 46743/66472/113215，分支 3827/5725/9552，方法 1429/2467/3896，类 304/475/779。HTML 页脚写的是未覆盖/合计：指令 66,472 of 113,215，分支 5,725 of 9,552，行 10,109 of 16,314，方法 2,467 of 3,896，类 475 of 779。行覆盖 = 16314 − 10109 = 6205。同一份 CSV 的指令合计与 XML 相同（46743 与 66472）。把 CSV 的 `LINE_*` 加起来是 6253/10318（合计 16571），高于 XML，因为内部类把同一行算了多次。报告标题用 XML/HTML 的行，不用 CSV 行合计。

Test 的干净复跑是行 **6203/16314**、指令大约 46736–46737，分母在 113215 与 113223 之间；分支仍是 3827/9552。这是第五批的验收基线。本仓库没有复现 6203，差 2 行，允许 ±2。差来自两条跟机器状态有关的路径：`Executable.killAll` 扫 `/proc`，某条 cmdline 读失败才走进 `IOException` 的 `continue`；`LocalResolverImpl.lookup` 把结果丢到 `Dispatchers.IO`，测试只 `delay(1500)`，解析变慢时成功或 NXDOMAIN 那几行赶不上这次 exec。指令分母从增量编译的 113223 变成干净编译的 113215（少 8），行合计仍是 16314。少掉的 8 条指令集中在三个包：`ui` 32797→32788，`group` 6020/7021→6018/7019，`database` 10921/11762→10924/11765。这是 Kotlin 增量编译和全量编译的字节码差异。以后各批都允许覆盖行数相对另一台干净机器 ±2，分母以当次 XML 为准。

| 源文件 | 第四批 | 第五批 |
| --- | ---: | ---: |
| `database/ProfileManager.kt` | 0/142 | **144/146** |
| `database/GroupManager.kt` | 10/50 | **50/50** |
| `database/DataStore.kt` | 216/219 | **219/219** |
| `database` 包 | 706/974 | **939/985** |
| `database/ProxyEntity.kt` | 315/365 | **344/366** |
| `fmt/ConfigSource.kt` | 34/56 | **39/56** |
| `group/SubscriptionAccess.kt` | 26/38 | **46/49** |
| `group/GroupUpdater.kt` | 79/87 | **82/86** |
| `group/RawUpdater.kt` | 629/631 | **630/631** |
| `fmt/ConfigBuilder.kt` | 689/690 | 689/690 |
| `nb4a/SingBoxOptions.java` | 169/171 | 169/171 |
| `nb4a/utils/JavaUtil.java` | 37/91 | **71/97** |
| `ktx/Utils.kt` | 36/158 | 36/158 |
| `bg/proto/TrafficUpdater.kt` | 0 | **50/50** |
| `nb4a/hevtun/HevTunRuntime.kt` | 0/45 | **31/44** |
| `nb4a/plugin/Plugins.kt` | 0/53 | **15/53** |
| `fmt/gson/GsonConverters.java` | 0/8 | **7/8** |
| `bg` 包 | 6/803 | **16/808** |
| `bg.proto` 包 | 0/419 | **50/419** |

`ProfileManager` 剩下 2 行是默认规则文案 lambda（测试换成了字面量）和一处监听转发。`GroupManager` 和 `DataStore`（含 `RoomGroupDirectory`）已铺满。`ConfigBuilder` 剩下的 1 行仍是到不了的 `else`。`RawUpdater` 剩下 1 行是 vmess/vless/trojan 里到不了的 `else`；clash 的 `proxies` 不是列表已经走到 `noProxiesFoundInFile`。`SingBoxOptions` 仍是 2 行：静态 gson 附近的一行，以及 serializer 里作为 marker 返回 null、Gson 不会调用的 `create`。

### 本批的缝

- `SagerDatabase.groupDao` / `proxyDao` / `rulesDao` 在覆盖口为 null 时仍打开 Room。测试在读之前放上内存 DAO。`ProfileManager`、`GroupManager`、`RoomGroupDirectory`、`ProductionConfigSource` 的 DAO 方法和 `ProductionProfileStore` 都走这条路径。生产不设覆盖口。
- `ProfileManager` 的默认规则名收成 `ruleTextProvider`。默认 lambda 仍是 `app.getString`。测试换成 `"s$resId"`。美国和中国 locale 都跑过；非中国会多出 ir/ru 规则。
- `SubscriptionUpdater.reconfigureUpdater` 在 `reconfigureOverride` 非空时直接返回。默认仍走 `RemoteWorkManager`。订阅组的创建、更新、删除都会调到它。
- `ProxyEntity.chainName` 在 `chainNameOverride` 为空时仍读 `app.getString`。`settingIntent` 的协议分支抽成 `settingsActivity()`，`Intent` 的构造没有改。测试只调用 `settingsActivity()`，避免在 JVM 上构造 `Context`。
- `GroupUpdater.startUpdate` 把原来丢掉的 `Job` 返回给调用方。调用方仍然忽略返回值。测试 `join` 这条 Job。
- `SubscriptionClient` 增加 `noProxiesFoundInFile`。生产实现转到 `noProxiesInFileText`，默认仍是 `app.getString`。测试替换 lambda 后再调用生产方法。
- `JavaUtil.handleWebviewDir` 的路径拼装抽成 `webviewLockPaths`。`WebView.setDataDirectorySuffix` 仍留在 `SDK_INT >= 28` 的分支里，路径字符串与原来的拼接一致。`tryLockOrRecreateFile` / `createFile` 改为包内可见，便于同一包的测试锁临时文件。`SDK_INT` 桩仍是 0，所以 `handleWebviewDir` 本体在 `< 28` 处返回。
- `HevTunRuntime.isRunning` 在 `.so` 加载失败被包成 `ExceptionInInitializerError` 时返回 false。设备上库加载成功时不会进这个分支。`buildConfig` 改为 `internal`，yaml 内容没有改。

## 复现

需要 JDK 17+（本次为 21）、Android SDK `platforms;android-35` 与 `build-tools;35.0.1`。`local.properties` 的 `sdk.dir` 或 `ANDROID_HOME` 指向 SDK。

```bash
export ANDROID_HOME="$HOME/android-sdk"   # 或写入 local.properties 的 sdk.dir
export SKIP_HEV=1                         # 单元测试不需要 libhev-socks5-tunnel.so / NDK
./gradlew :app:jacocoFirstPartyReport
```

报告：

- HTML：`app/build/reports/jacoco/firstParty/html/index.html`
- XML：`app/build/reports/jacoco/firstParty/jacocoFirstParty.xml`
- CSV：`app/build/reports/jacoco/firstParty/jacocoFirstParty.csv`
- 执行数据：`app/build/jacoco/testOssDebugUnitTest.exec`

`SKIP_HEV` 未设置时，`preBuild` 仍会按原逻辑调用 `buildHevTun`。四个产品风味共享 `src/main`；本报告固定 **ossDebug**。`preview` 只改已排除的 `BuildConfig.PRE_VERSION_NAME`。

`app/libs/libcore.aar`（`libcore/build.sh` 的 gomobile 产物，被 gitignore）存在时，打包使用该 AAR。不存在时，`compileOnly` / `testImplementation` 使用 `app/src/libcoreStub/java` 打出的桩 jar，**不打进 APK**，也 **不计入** 覆盖率。桩只提供 `libcore.*` 与 `go.Seq` 的空实现，让 JVM 测试能够通过类型检查。

JVM 单元测试不能把真 AAR 留在 classpath 上。`implementation(fileTree("libs"))` 会把它带进 `testOssDebugUnitTest`。gomobile 类的 JNI 初始化在普通 JVM 上抛 `UnsatisfiedLinkError`，`ProfileManagerJvmTest` 会一直卡住。只挪走 `app/libs/libcore.aar` 不够：`libcore/build.sh` 还在 `libcore/libcore.aar` 留一份，`libcore/build/outputs/aar/` 里也可能有一份。

单元测试或 JaCoCo 任务（任务名含 `unittest` / `jacoco`，或就是 `test` / `check`）在配置阶段把这三处现有的 `*.aar` 改名为旁边的 `*.aar.coverage-aside`，构建结束（含失败）改回。`assemble*`、`bundle*`、`install*`、`package*`、`build` 不动文件。同一次 Gradle 里既打包又跑单元测试时也不挪，并打出警告。`app/libs` 的 `fileTree` 排除 `*.coverage-aside`，备份文件不会被当成依赖。上次跑测试时进程被杀掉、只剩下备份的话，下一次任何配置会先把备份改回原名。`SKIP_LIBCORE_ASIDE=1` 关掉自动挪动。

手动画法（自动逻辑没跑、或要跟打包任务放在同一次 Gradle 里时）：

```bash
aside() { [ -f "$1" ] && mv "$1" "$1.coverage-aside"; }
restore() { [ -f "$1.coverage-aside" ] && mv "$1.coverage-aside" "$1"; }
aside app/libs/libcore.aar
aside libcore/libcore.aar
for f in libcore/build/outputs/aar/*.aar; do aside "$f"; done
SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport
status=$?
restore app/libs/libcore.aar
restore libcore/libcore.aar
for f in libcore/build/outputs/aar/*.aar.coverage-aside; do
  [ -f "$f" ] && mv "$f" "${f%.coverage-aside}"
done
exit $status
```

单元测试还有两件只存在于测试 classpath 的替身，同样不进 APK、不进覆盖率分母：

- `app/src/testAndroidBase64` 打成 javaagent，在 bootstrap class loader 上提供真正的 `android.util.Base64`。`android.jar` 的桩会让 `encode` / `decode` 返回 null，vmess / ss / ssr 链接无法往返。
- 可解析配置 `unitTestOrgJson` 把 `org.json:json:20240303` 放在 `android.jar` 前面。这不是 app 的 `implementation` 依赖，也没有改现有版本。`android.jar` 里的 `JSONObject.opt` / `get` 桩返回 null。

`unitTests.isReturnDefaultValues = true` 仍在。`TextUtils.isEmpty` 因此恒为 false，`Os.inet_pton` 返回 null。`Subnet` 的成功路径用 `InetAddress.getByAddress` 覆盖。

## 计入与排除

源码根（已按目录核对）：

| 包前缀 | 位置 | 处理 |
| --- | --- | --- |
| `io.nekohasekai.sagernet.**` | `app/src/main/java` | 计入 |
| `moe.matsuri.nb4a.**` | `app/src/main/java` | 计入 |
| `com.github.shadowsocks.plugin.**` | `app/src/main/java`（树内维护的插件胶水，不是 Gradle 依赖） | 计入 |

排除项：

| 内容 | 原因 |
| --- | --- |
| Maven / JitPack / `app/libs` 里的 AAR | 第三方依赖，不在 app 编译类目录的分子分母里 |
| `**/R.class`、`**/R$*.class`、`**/BuildConfig.class`、`**/BR.class`、`**/Manifest*.class` | Android 生成代码 |
| `**/databinding/**` | ViewBinding 生成类。不过滤名字里带 `Binding` 的手写类（例如 `PreferenceBinding`） |
| `**/*_Impl.class`、`**/*_Impl$*.class` | Room / Roomigrant 生成实现（如 `SagerDatabase_Impl`、`*_Dao_Impl`、`*_AutoMigration_*_Impl`） |
| `ISagerNetService`、`ISagerNetServiceCallback` 及其内部类 | `src/main/aidl` 生成的 Stub。同包下的手写 `TrafficData.kt` 等仍计入 |
| `libcore` 桩、`go.Seq` 桩 | 仅用于缺 AAR 时编译，不是本仓库业务实现 |
| `libcore/stun/**` | 第三方拷贝，来自 [ccding/go-stun](https://github.com/ccding/go-stun) `877ebaff`，见 `libcore/stun/README` |
| `hev-socks5-tunnel` | 第三方子模块，原生库，不在 JaCoCo 范围 |
| `sing-box`、`libneko` 以及 `go.mod` 里的其它 module | 仓库外依赖。`go.mod` 用 `replace` 指向 `../../sing-box`、`../../libneko` |

`SnellBuildConfig.kt` 是手写源码，保留。过滤器用的是精确的 `BuildConfig.class`，不会误伤它。

JaCoCo 行数低于源文件行数：只统计字节码里的可执行行。`SingBoxOptions.java` 约 4846 行源码，绝大多数是字段声明，JaCoCo 只记到 171 行。

## 分包表（第七批之后）

行 / 指令 / 分支 / 方法 / 类的写法都是 `已覆盖/合计`。数字来自第七批这次干净构建的 XML（行 10734/16314）。`ui.profile` 975/1272，`ui` 1672/5183，`bg` 478/808，`widget` 271/518，`bg.proto` 292/419，`nb4a.ui` 180/307。分母相对第六批没有变。Test 复跑允许再少 0–2 行。

| 包 | 行 | 指令 | 分支 | 方法 | 类 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `io.nekohasekai.sagernet.ui` | 1672/5183 | 9450/32791 | 352/2568 | 368/1072 | 87/250 |
| `io.nekohasekai.sagernet.ui.profile` | 975/1272 | 5963/7943 | 140/339 | 164/255 | 32/42 |
| `io.nekohasekai.sagernet.bg` | 478/808 | 3008/5537 | 158/394 | 127/218 | 26/46 |
| `io.nekohasekai.sagernet.widget` | 271/518 | 1536/3095 | 116/315 | 78/140 | 19/26 |
| `io.nekohasekai.sagernet.bg.proto` | 292/419 | 2072/3149 | 103/206 | 72/101 | 13/17 |
| `moe.matsuri.nb4a.ui` | 180/307 | 1079/2032 | 13/72 | 49/101 | 15/18 |
| `io.nekohasekai.sagernet.ktx` | 431/561 | 3367/4403 | 168/230 | 149/220 | 18/25 |
| `io.nekohasekai.sagernet.utils` | 301/407 | 1933/2752 | 196/303 | 63/91 | 12/22 |
| `io.nekohasekai.sagernet.fmt.v2ray` | 838/939 | 5434/6096 | 551/817 | 64/67 | 6/6 |
| `moe.matsuri.nb4a.utils` | 281/362 | 1816/2402 | 127/208 | 45/59 | 5/6 |
| `moe.matsuri.nb4a.net` | 16/83 | 90/346 | 7/28 | 4/11 | 2/4 |
| `io.nekohasekai.sagernet.fmt` | 887/952 | 5618/6148 | 547/639 | 112/140 | 11/14 |
| `moe.matsuri.nb4a` | 312/374 | 1968/2703 | 222/384 | 184/206 | 148/152 |
| `io.nekohasekai.sagernet.group` | 766/818 | 6091/7019 | 694/1011 | 61/79 | 13/22 |
| `io.nekohasekai.sagernet` | 132/175 | 643/919 | 32/76 | 43/62 | 7/8 |
| `io.nekohasekai.sagernet.plugin` | 6/46 | 34/283 | 5/32 | 2/12 | 1/3 |
| `io.nekohasekai.sagernet.database` | 965/985 | 11191/11765 | 414/485 | 574/633 | 34/37 |
| `moe.matsuri.nb4a.proxy.config` | 41/51 | 183/252 | 15/30 | 15/18 | 3/3 |
| `io.nekohasekai.sagernet.database.preference` | 124/151 | 817/952 | 63/73 | 60/68 | 7/10 |
| `moe.matsuri.nb4a.proxy.anytls` | 141/145 | 898/930 | 67/92 | 15/17 | 4/4 |
| `moe.matsuri.nb4a.proxy.shadowtls` | 54/56 | 332/339 | 4/4 | 13/15 | 4/4 |
| `com.github.shadowsocks.plugin.fragment` | 17/21 | 129/181 | 4/6 | 10/14 | 1/2 |
| `moe.matsuri.nb4a.proxy` | 62/62 | 342/376 | 24/30 | 19/27 | 2/2 |
| `moe.matsuri.nb4a.hevtun` | 35/47 | 336/372 | 7/10 | 6/6 | 2/2 |
| `io.nekohasekai.sagernet.fmt.trojan_go` | 130/144 | 889/1002 | 78/120 | 11/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt.hysteria` | 309/321 | 2094/2202 | 244/300 | 19/22 | 3/3 |
| `moe.matsuri.nb4a.plugin` | 42/53 | 261/376 | 32/50 | 9/10 | 1/1 |
| `io.nekohasekai.sagernet.fmt.internal` | 23/33 | 82/132 | 8/12 | 9/15 | 3/3 |
| `io.nekohasekai.sagernet.fmt.trojan` | 24/33 | 146/194 | 14/24 | 8/10 | 3/3 |
| `moe.matsuri.nb4a.proxy.neko` | 24/32 | 83/110 | 4/4 | 9/14 | 2/2 |
| `io.nekohasekai.sagernet.fmt.socks` | 72/80 | 503/529 | 50/66 | 14/17 | 3/3 |
| `io.nekohasekai.sagernet.fmt.ssh` | 44/51 | 195/222 | 24/30 | 7/10 | 3/3 |
| `io.nekohasekai.sagernet.fmt.wireguard` | 54/59 | 250/263 | 18/20 | 9/12 | 3/3 |
| `io.nekohasekai.sagernet.fmt.shadowsocks` | 130/135 | 794/868 | 63/82 | 12/14 | 3/3 |
| `io.nekohasekai.sagernet.fmt.snell` | 115/119 | 930/1023 | 124/178 | 13/15 | 4/4 |
| `io.nekohasekai.sagernet.fmt.tuic` | 121/125 | 750/776 | 76/96 | 10/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt.mieru` | 50/54 | 292/309 | 10/12 | 8/10 | 3/3 |
| `io.nekohasekai.sagernet.fmt.http` | 39/42 | 261/282 | 23/32 | 8/11 | 3/3 |
| `io.nekohasekai.sagernet.fmt.juicity` | 78/81 | 524/561 | 46/70 | 11/14 | 3/3 |
| `io.nekohasekai.sagernet.aidl` | 17/19 | 122/253 | 0/2 | 13/31 | 3/3 |
| `io.nekohasekai.sagernet.fmt.shadowsocksr` | 85/87 | 589/614 | 16/20 | 11/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt.naive` | 92/94 | 652/664 | 61/76 | 11/13 | 3/3 |
| `com.github.shadowsocks.plugin` | 1/2 | 3/12 | 0/0 | 1/3 | 1/1 |
| `io.nekohasekai.sagernet.fmt.gson` | 7/8 | 39/42 | 8/8 | 3/4 | 1/1 |

## 第一批盖住的解析与工具

| 源文件 | 行 |
| --- | ---: |
| `fmt/v2ray/V2RayFmt.kt` | 493/569（86.6%） |
| `fmt/v2ray/XhttpExtraConverter.kt` | 167/172（97.1%） |
| `fmt/v2ray/StandardV2RayBean.java` | 125/186（67.2%） |
| `fmt/hysteria/HysteriaFmt.kt` | 189/243（77.8%） |
| `fmt/shadowsocks/ShadowsocksFmt.kt` | 73/76 |
| `fmt/shadowsocksr/ShadowsocksRFmt.kt` | 56/56 |
| `fmt/snell/SnellFmt.kt` | 49/51 |
| `fmt/tuic/TuicFmt.kt` | 44/66 |
| `fmt/socks/SOCKSFmt.kt` | 38/40 |
| `fmt/juicity/JuicityFmt.kt` | 35/52 |
| `fmt/naive/NaiveFmt.kt` | 34/53 |
| `fmt/mieru/MieruFmt.kt` | 27/27 |
| `fmt/wireguard/WireGuardFmt.kt` | 25/27 |
| `fmt/http/HttpFmt.kt` | 23/23 |
| `fmt/ssh/SSHFmt.kt` | 13/13 |
| `fmt/trojan/TrojanFmt.kt` | 7/8 |
| `fmt/trojan_go/TrojanGoFmt.kt` | 58/104（55.8%） |
| `proxy/anytls/AnyTLSFmt.kt` | 76/76 |
| `proxy/shadowtls/ShadowTLSFmt.kt` | 8/8 |
| `ktx/Formats.kt` | 123/176（69.9%） |
| `ktx/Nets.kt` | 19/26 |
| `utils/Commandline.kt` | 53/60 |
| `utils/Subnet.kt` | 34/51 |
| `nb4a/utils/Util.kt` | 79/94 |
| `nb4a/utils/NGUtil.kt` | 58/95 |
| `nb4a/utils/JavaUtil.java` | 32/91 |
| `nb4a/SingBoxOptions.java` | 27/171 |

测试调用真实的 `parse*` / `toUri` / `buildSingBoxOutbound*` / `XhttpExtraConverter` / `parseProxies`，并在 `initializeDefaultValues()` 之后用 Kryo `clone()` 做相等比较。

第一批刻意躲开 `DataStore`（静态初始化会碰 Room 的 `PublicDatabase.kvPairDao`）。第二批用 `ConfigSource` 把这些路径打开了，见上一节。第一批当时的限制是：

- `buildSingBoxOutboundTLS` 只在 `security != "tls"` 时调用，此时它在读 `DataStore` 之前返回 null。
- Hysteria 的 sing-box 构建用会在读 `DataStore` 之前 `error(...)` 的协议版本。
- TUIC v4 在读 `DataStore` 之前抛异常。
- 不调用 `buildNaiveConfig`、`buildTrojanGoConfig`，以及 hysteria / tuic / juicity 里会组装 sing-box TLS 的路径。

测试锁定的现有行为（本批没有改产品逻辑）：

- `isJsonObjectValid("[1]")` 为 false。`JSONArray(j)` 在 `j: Any` 上选了 `JSONArray(Object)`，字符串会被拒绝。`JSONObject` / `JSONArray` 实例以及 JSON 对象字符串为 true。
- vmess 少见密码格式先把 `bean.type` 设成带 `+tls` 的用户名，再剥协议名。`ws+tls:...` 解析后 `type` 是 `ws+tls`，`security` 是 `tls`。
- `buildSingBoxOutboundAnyTLSBean` 里 `fingerprint?.let { utls = OutboundUTLSOptions().apply { fingerprint = it } }` 把赋值解析到外层局部变量。`utls.enabled` 为 true，`utls.fingerprint` 保持 null。reality 公钥仍会把局部 fingerprint 默认成 `"chrome"`，但该字符串没有写进 UTLS 字段。

## 仍未覆盖最多的源文件

第七批干净 XML。未覆盖行按源文件合计，不把内部类再加一遍。

| 源文件 | 未覆盖行 | 已覆盖 |
| --- | ---: | ---: |
| `io.nekohasekai.sagernet.ui.ConfigurationFragment.kt` | 1181 | 406/1587 |
| `io.nekohasekai.sagernet.ui.BackupFragment.kt` | 501 | 5/506 |
| `io.nekohasekai.sagernet.ui.GroupFragment.kt` | 229 | 93/322 |
| `io.nekohasekai.sagernet.ui.MainActivity.kt` | 181 | 142/323 |
| `io.nekohasekai.sagernet.ui.SettingsPreferenceFragment.kt` | 163 | 104/267 |
| `io.nekohasekai.sagernet.ui.AssetsActivity.kt` | 160 | 85/245 |
| `io.nekohasekai.sagernet.ui.AppManagerActivity.kt` | 153 | 75/228 |
| `io.nekohasekai.sagernet.ui.GroupSettingsActivity.kt` | 147 | 169/316 |
| `io.nekohasekai.sagernet.ui.RouteSettingsActivity.kt` | 146 | 82/228 |
| `io.nekohasekai.sagernet.ui.RouteFragment.kt` | 111 | 83/194 |
| `io.nekohasekai.sagernet.ui.AppListActivity.kt` | 105 | 67/172 |
| `io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity.kt` | 99 | 55/154 |
| `io.nekohasekai.sagernet.bg.proto.BoxInstance.kt` | 86 | 32/118 |
| `io.nekohasekai.sagernet.bg.GuardedProcessPool.kt` | 69 | 6/75 |
| `io.nekohasekai.sagernet.bg.BaseService.kt` | 61 | 185/246 |
| `io.nekohasekai.sagernet.bg.VpnService.kt` | 23 | 104/127 |

`ConfigBuilder.kt` 689/690。`RawUpdater.kt` 630/631。`SingBoxOptions.java` 169/171。`JavaUtil.java` 93/97。`DataStore.kt` 219/219。`ConfigSource.kt` 56/56。`SubscriptionAccess.kt` 49/49。`ProfileManager.kt` 146/146。`ProxyEntity.kt` 364/366。`GroupUpdater.kt` 85/86。`Theme.kt` 70/70。`Utils.kt` 114/158。`TrafficLooper.kt` 180/186。`SocksSettingsActivity.kt` 33/33。

## 第六批（UI、服务、WebView 锁）

75 个 JVM 测试，0 失败（本批新增 2 个 Robolectric 测试）。`SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport --offline` 通过。`syncFirstPartyClasses` 仍复制 1029 个 class 文件。有可执行行的源文件仍是 209 个。

**验收基线是 Test 在 `0186a71` 的干净复跑：行 8196/16314（50.24%），指令 58102/113219（51.32%），分支 4335/9554（45.37%）。** 相对 Test 的第五批 6203/16314，行 +1993，分母仍是 16314。本仓库同提交的干净 XML 是行 8198、指令 58108，比 Test 多 2 行、6 条指令，落在允许的 ±2 行里。分支两边都是 4335/9554。方法、类只在本仓库 XML 里数过（1940/3896、421/779）。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 来源 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 8196 | 8118 | 16314 | **50.24%** | Test 干净复跑 |
| 指令（INSTRUCTION） | 58102 | 55117 | 113219 | **51.32%** | Test 干净复跑 |
| 分支（BRANCH） | 4335 | 5219 | 9554 | **45.37%** | Test，与本仓库 XML 相同 |
| 方法（METHOD） | 1940 | 1956 | 3896 | **49.79%** | 本仓库 XML（行 8198 那次） |
| 类（CLASS） | 421 | 358 | 779 | **54.04%** | 本仓库 XML（行 8198 那次） |

本仓库 HTML 页脚是未覆盖/合计：指令 55,111 of 113,219，分支 5,219 of 9,554，行 8,116 of 16,314（覆盖 = 16314 − 8116 = 8198），方法 1,956 of 3,896，类 358 of 779。指令分母比第五批本仓库 XML 多 4，来自 `SagerNet.process` 的空进程名回退，不是测试抖动。行合计仍是 16314。下面源文件比例来自这份 8198 行的 XML，和 Test 的 8196 可以差 1–2 行。

| 源文件 | 第五批干净构建 | 第六批 |
| --- | ---: | ---: |
| `ui` 包 | 0/5183 | **822/5183** |
| `ui.profile` 包 | 0/1272 | 0/1272 |
| `widget` 包 | 0/518 | **178/518** |
| `nb4a.ui` 包 | 0/307 | **127/307** |
| `bg` 包 | 16/808 | **303/808** |
| `bg.proto` 包 | 50/419 | **106/419** |
| `ui/MainActivity.kt` | 0/323 | **138/323** |
| `bg/BaseService.kt` | 6/246 | **144/246** |
| `bg/VpnService.kt` | 0/127 | **25/127** |
| `bg.proto/TrafficLooper.kt` | 0/186 | **56/186** |
| `ktx/Utils.kt` | 36/158 | **81/158** |
| `nb4a/utils/JavaUtil.java` | 71/97 | **87/97** |
| `utils/Theme.kt` | — | **70/70** |
| `fmt/ConfigSource.kt` | 39/56 | **56/56** |
| `group/SubscriptionAccess.kt` | 46/49 | **49/49** |
| `database/ProfileManager.kt` | 144/146 | **146/146** |
| `SagerNet.kt` | — | **113/120** |

### 本批的做法

- `org.robolectric:robolectric:4.14.1` 只在 `testImplementation`。`unitTests.isIncludeAndroidResources = true` 只给单元测试读资源。release 的 `implementation` 没有加 Robolectric，APK 不打包它。
- 测试里的 `CoverageApplication` 继承 `SagerNet`。`SagerNet` 改为 `open`，清单里的应用类仍是 `SagerNet`。子类在 `super.onCreate()` 之前放上内存 preference 和内存 DAO，所以 `onCreate` 里的 `DataStore.logBufSize`、`Theme`、`handleWebviewDir`、`PackageCache`、`AppLocale` 会跑，但不会打开 Room。生产不设这些覆盖口。
- `process` 在 `Application.getProcessName()` 返回 null 时用 `BuildConfig.APPLICATION_ID`。设备上这个 API 返回进程名，主进程 / `:bg` 的判断不变。Robolectric 构造 Application 时进程名还是 null，不回退的话类初始化会 NPE。
- 只有 `@RunWith(RobolectricTestRunner)` 的测试进沙箱（这次是 SDK 29）。其余 JUnit 仍用 `android.jar` 桩，`SDK_INT` 仍是 0，`TextUtils.isEmpty` 仍恒为 false。
- Robolectric 的 `bindService` 不调用 `Service.onBind`，默认把 null binder 交给 `SagerConnection`。测试先建好 `VpnService`，再用 `setComponentNameAndServiceForBindService` 把 `data.binder` 交出去。`MainActivity` 因此走完 `onCreate`、配置页，并切到分组、路由、设置、工具、日志、关于和流量页。
- `GroupSettingsActivity` 的 `isValidServerDns` 改为 `internal`。判断没有改。
- `VpnService.establishTun` 在 `Builder.addAddress` 调用隐藏的 `InetAddress.parseNumericAddress`，JVM 上是 `NoSuchMethodError`。调用点之前的几行跑过，路由和按应用代理的后半段没有跑到。
- 订阅文案的默认 lambda 在沙箱里走 `app.getString` / `contentResolver`。`ConfigSource` 的 Toast、`PackageCache` 和非空插件 id 也在沙箱里跑过。`ProfileManager.getRules` 用默认的 `app.getString`。
- `libcore` Go 仍没有接进 JaCoCo。没有克隆 `../../sing-box` 和 `../../libneko`。

## 第七批（档案设置页、VPN 组网、流量循环）

77 个 JVM 测试，0 失败（本批新增 2 个 Robolectric 测试）。`SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport --offline` 通过，约 44 秒。`syncFirstPartyClasses` 仍复制 1029 个 class 文件。有可执行行的源文件仍是 209 个。分母相对第六批不变。`compileOssReleaseKotlin --offline` 通过。

这次干净 XML / HTML 就是下表。Test 复跑允许比 10734 少 0–2 行。相对 Test 验收的第六批 8196/16314，行 **+2538**。相对本仓库第六批 XML 的 8198，是 +2536。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第六批 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 10734 | 5580 | 16314 | **65.80%** | +2538 行（相对 Test 的 8196）；分母不变 |
| 指令（INSTRUCTION） | 73789 | 39430 | 113219 | **65.17%** | +15687（相对 Test 的 58102）；分母不变 |
| 分支（BRANCH） | 4928 | 4626 | 9554 | **51.58%** | +593；分母不变 |
| 方法（METHOD） | 2493 | 1403 | 3896 | **63.99%** | +553（相对本仓库 XML 的 1940） |
| 类（CLASS） | 526 | 253 | 779 | **67.52%** | +105（相对本仓库 XML 的 421） |

指令若改按本仓库第六批 XML 的 58108 相减是 +15681，少 6。这和允许的 ±2 行是同一类机器差，分母没有变。方法、类 Test 没有单报。

HTML 页脚是未覆盖/合计：指令 39,430 of 113,219，分支 4,626 of 9,554，复杂度未覆盖 4,568、合计 8,871，行 5,580 of 16,314（覆盖 = 16314 − 5580 = 10734），方法 1,403 of 3,896，类 253 of 779。

| 源文件 | 第六批本仓库 XML | 第七批 |
| --- | ---: | ---: |
| `ui` 包 | 822/5183 | **1672/5183** |
| `ui.profile` 包 | 0/1272 | **975/1272** |
| `widget` 包 | 178/518 | **271/518** |
| `nb4a.ui` 包 | 127/307 | **180/307** |
| `bg` 包 | 303/808 | **478/808** |
| `bg.proto` 包 | 106/419 | **292/419** |
| `bg/BaseService.kt` | 144/246 | **185/246** |
| `bg/VpnService.kt` | 25/127 | **104/127** |
| `bg.proto/TrafficLooper.kt` | 56/186 | **180/186** |
| `bg.proto/BoxInstance.kt` | 0/118 | **32/118** |
| `ktx/Utils.kt` | 81/158 | **114/158** |
| `nb4a/utils/JavaUtil.java` | 87/97 | **93/97** |
| `database/ProxyEntity.kt` | 344/366 | **364/366** |
| `group/GroupUpdater.kt` | 82/86 | **85/86** |
| `ui/profile/SocksSettingsActivity.kt` | 0/33 | **33/33** |

### 本批的做法

- 档案设置页在现有 Robolectric 沙箱里 `setup()` 后 `saveAndExit`。SOCKS 还点了协议和密码摘要。已有档案走删除、快捷方式、自定义 JSON、移动和返回时的未保存对话框。`UnsavedChangesDialogFragment` 必须先 `key()` 再 `show`，否则销毁时 `requireArguments` 会抛。
- `VpnService.establishTun` 的产品逻辑没改。`Builder.addAddress` / `addRoute` / `addDnsServer` 仍会调用隐藏的 `InetAddress.parseNumericAddress`。只在 `Batch7RobolectricTest` 上注册三个测试影子：`ShadowTunBuilder` 让这三步和 `establish` 在沙箱里返回；`ShadowOsAddress` 提供 `Os.inet_pton`（绕过 LAN 的 `Subnet.fromString` 需要它；Robolectric 自带的 `ShadowOs` 是 final，这个影子只在这个测试类上替换它，并保留 `sysconf`）；`ShadowResolvingNetwork` 让 `Network.getAllByName` 返回 `127.0.0.1`。真 AAR 和 release 编译看不到这些类。
- `establish` 返回 null 时仍抛 `NullConnectionException`。按应用代理的缺失包走 `NameNotFoundException`。`needBypassRootUid`（hysteria faketcp）这条没有造数据。
- SOCKS 档案经 `ProxyService.onStartCommand` 走完 `BaseService` 的选中档案路径。`TrafficLooper` 在 `speedInterval > 0` 时启动。桩上的 `libcore.BoxInstance.queryStats` 改为读 `public static volatile long stats`，默认 0，所以第五批断言流量增量为 0 的测试仍然成立。只有这一轮服务测试把它设成 8。gomobile 的 `libcore.aar` 没有这个字段。
- 假 DNS：`forceResolve` 在 `enableFakeDns` 且 VPN 模式下走 `underlyingNetwork.getAllByName`。影子网络把它解析成 `127.0.0.1`，`sni` 回填主机名。
- `JavaUtil`：非默认进程名 `coveragebg` 走到 `setDataDirectorySuffix`；同一 JVM 里已持有的文件锁抛 `OverlappingFileLockException`（不是 `tryLock == null`）；锁路径被换成目录时走重建。API 27 的测试走反射版 `getProcessName`。`parseNumericAddress` 在这个测试类上因 `inet_pton` 影子可以成功。
- 外部插件（trojan-go、mieru、naive、hysteria）的 `PluginManager.init` 仍抛 `PluginNotFoundException`。没有启动插件进程。

## 第八批

1. **`ConfigurationFragment` 仍缺 1181 行**，`BackupFragment` 仍是 5/506。列表点击、导入导出、搜索、测速 UI 没走。`GroupFragment` 93/322，`RouteFragment` 83/194，`SettingsPreferenceFragment` 104/267，`AboutFragment` 94/190，这几页相对第六批没有增加。
2. **`MainActivity` 142/323**。导入订阅、菜单和要 `connection.service` 的测速成功路径还在。`AssetsActivity` 85/245，`AppManagerActivity` 75/228，`AppListActivity` 67/172，`RouteSettingsActivity` 82/228，`WebDAVSettingsActivity` 40/136，`ScannerActivity` 38/116：`onCreate` 跑过，点击和适配器大多没有。
3. **`BoxInstance` 32/118**，`GuardedProcessPool` 6/75。外部插件要 `PluginManager.init` 返回可执行路径，`processes.start` 会拉起二进制。不要在 JVM 上 exec 插件。
4. **`VpnService` 104/127**。剩下的主要是 `needBypassRootUid`（hysteria faketcp，且 `proxyApps == false` 时把全部应用塞进 individual）以及 hev 原生 `start` 成功。`establishTun` 的产品分支不要为了覆盖率改写。
5. **`BaseService` 185/246**。`preInit` 在链路属性为 null 时跳过 `also`；`canReloadSelector` 在 `selectorGroupId < 0` 时直接 false。`UnknownHostException`、`PluginNotFoundException` 的部分文案还在。
6. **`TrafficLooper` 180/186**，`ProxyInstance` 30/36。`Utils.kt` 114/158。不要调用 `triggerFullRestart`（`Runtime.exit`）。`JavaUtil` 93/97：类初始化那一行、`handleWebviewDir` 的外层 `catch`（92–93）、`tryLock == null` 的分支（133，同一 JVM 的 `tryLock` 抛 `OverlappingFileLockException` 而不是返回 null）。
7. **`GroupUpdater` 85/86**。剩下的第 109 行是 `is TrojanBean -> sni = serverAddress`。`TrojanBean` 继承 `StandardV2RayBean`，父分支先匹配，这行到不了。不要调换 `when` 顺序：父分支只在 `security == "tls"` 时写 `sni`，Trojan 分支会无条件写，调换会改行为。`ProxyEntity` 364/366，剩下第 250 行（`requireBean` 的空 bean elvis，空 SOCKS 在拼错误信息时先 NPE）和第 543 行（`putBean` 的 `else -> error`，现有类型都进了前面的分支）。
8. **`TileService` 0/49**，`SagerConnection` 49/99，`SubscriptionUpdater` 5/54，`LocalResolverImpl` 16/83，`GroupInterfaceAdapter` 1/51。`HevTunRuntime` 34/44：原生 `TProxyStartService` 成功之后的 `running = true`，以及 `stop()` 在 `running` 时的路径，JVM 上到不了。`libcore` Go 仍无 JaCoCo 入口，也没有 `_test.go`。

## `libcore` Go

`go test -count=0 ./...`（工具链按 `go.mod` 下载了 go 1.24.9）结果：

- 主包 `libcore`、`libcore/ech`、`libcore/protocol/juicity`：**setup failed**。`replace github.com/matsuridayo/libneko => ../../libneko` 与 `replace github.com/sagernet/sing-box => ../../sing-box` 的目录不在本仓库。锁定提交在 `buildScript/lib/core/get_source_env.sh`：sing-box `0b7a2fc2f2c33f754e5fe5a1bb3e0a4f610b6aa9`，libneko `6a85c185d62435a5293ef70ac3b638ae3ee1efa7`。
- `libcore/device`、`libcore/procfs`：编译通过，输出 `? [no test files]`。
- `libcore/stun`：同样无测试；这是第三方拷贝，不计入第一方分母。

没有 `_test.go`，因此即使依赖齐了，第一方 Go 覆盖率也会从 0% 起算。主包 `libcore` 是一个 package，混有 sing-box import，不能只编译其中几个文件。

第一方 Go 非空非注释行（不含 `stun/`）：

| 目录 | 文件 | 代码行 | 本次 `go test` |
| --- | ---: | ---: | --- |
| `libcore`（根 package，含 `stun.go` 包装） | 19 | 1554 | 失败，缺 `../../sing-box` 与 `../../libneko` |
| `libcore/protocol/juicity` | 1 | 156 | 失败，同样缺 sing-box |
| `libcore/procfs` | 1 | 116 | 通过编译，无测试 |
| `libcore/ech` | 1 | 71 | 失败，缺 sing-box |
| `libcore/device` | 2 | 30 | 通过编译，无测试 |
| 合计 | 24 | 1927 | 无覆盖率百分比 |

排除的 `libcore/stun`：12 个文件，约 892 行代码。

第五批没有把上述提交克隆到 `../../sing-box` 和 `../../libneko`。现有第一方报告是 JaCoCo 的 JVM exec，没有 Go 覆盖率入口；这两个目录也没有 `_test.go`。

## 本次没有做的事

- 没有升级已有的 app 依赖，没有改 release 签名。`org.json:json:20240303` 仍只在单元测试 classpath。Robolectric 4.14.1 只在 `testImplementation`，不进 release APK。
- 没有跑仪器测试。libcore 桩仍是 `compileOnly` / `testImplementation`，不进 APK、不进覆盖率。JVM 测试任务会把 `app/libs/libcore.aar`、`libcore/libcore.aar`、`libcore/build/outputs/aar/*.aar` 临时改名，构建结束改回；`assemble*` 不改名。
- 没有把 `libcore.aar` 或 JaCoCo HTML 提交进 git（`build/`、`app/libs/` 仍被忽略）。
- 没有改 AnyTLS 的 fingerprint 赋值。测试按当前字节码断言 `utls.fingerprint == null`。
- 没有把 Room 数据库放进单元测试。DAO 覆盖口为 null 时仍打开 Room。Robolectric 测试在 `super.onCreate()` 之前放上内存 DAO。
- 没有改 WireGuard `mtu` 为空时 `clone()` 会 NPE 的现有行为。
- 没有改 `handleWebviewDir` 在 API 28 以下直接返回的行为，也没有改锁文件路径的拼接。API 29 的沙箱跑过函数本体。
- 没有改 `VpnService.establishTun` 的组网逻辑。地址、路由、DNS 和 `establish` 能在第七批跑完，是因为测试影子替换了 `VpnService.Builder` 和 `Os.inet_pton`，只挂在 `Batch7RobolectricTest` 上。
- 没有把 `TrojanBean` 的 `sni` 分支调到 `StandardV2RayBean` 前面。那一行在现有类型层次上到不了，调换会改变 `security != "tls"` 时的赋值。
- 没有克隆 `../../sing-box` 和 `../../libneko`，没有把 Go 覆盖率并进 JaCoCo。
- 没有调用 `triggerFullRestart`。那条路径会 `Runtime.exit`。
