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

Test 的干净复跑是行 **6203/16314**、指令大约 46736–46737，分母在 113215 与 113223 之间；分支仍是 3827/9552。这是第五批的验收基线。本仓库没有复现 6203，差 2 行，允许 ±2。差来自两条跟机器状态有关的路径：`Executable.killAll` 扫 `/proc`，某条 cmdline 读失败才走进 `IOException` 的 `continue`；`LocalResolverImpl.lookup` 把结果丢到 `Dispatchers.IO`，测试只 `delay(1500)`，解析变慢时成功或 NXDOMAIN 那几行赶不上这次 exec。指令分母从增量编译的 113223 变成干净编译的 113215（少 8），行合计仍是 16314。少掉的 8 条指令集中在三个包：`ui` 32797→32788，`group` 6020/7021→6018/7019，`database` 10921/11762→10924/11765。这是 Kotlin 增量编译和全量编译的字节码差异。第五到第八批按 ±2 行收。第九批实测跨度已经大于 ±2，见第九批。以后以当次干净 XML 页脚为准，分母也以当次 XML 为准。

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

引用覆盖率时只读 XML 报告级 `counter`，或 HTML 页脚的未覆盖/合计（覆盖 = 合计 − 未覆盖）。CSV 的 LINE 会把内部类再加一遍，不能当标题。`ConfigurationFragment` 第 209–210 行、`GuardedProcessPool.close()` 的异步收尾、`TrafficLooper` 的分支侧会让同一提交的合计挪动，幅度可以大于 ±2。`ServiceNotification` 只有在构造时 `showDirectSpeed` 为 false 才走简版速度文字。这些包还在动时，报数要写明。

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

## 分包表（第十五批之后）

行 / 指令 / 分支 / 方法 / 类的写法都是 `已覆盖/合计`。下表来自第十五批行数较低的那次本仓库 XML，页脚是行 **15152/16338**、指令 **102285/113365**、分支 **6915/9564**。另一次是行 **15154**、指令 **102271**、分支 **6915**。两次差 2 行。指令方向相反：行数较低的一次指令多 14。分支两次相同。只动 `ConfigurationFragment`、`GuardedProcessPool` 和 `ProfileSettingsActivity`。这张表用较低的行页脚。它相对第十四批行数较低的本仓库 XML（行 14819）合计多 333 行。相对 Test 验收的第十四批 14820，较低行页脚多 332 行。少掉的 1 行是那份本仓库 XML 比 Test 少出来的，不要把这张表当成 Test 的页脚。

**第十五批验收基线是 Test 在 `c072a61` 的 15158/16338（92.78%），指令 102300/113365（90.24%），分支 6917/9564（72.32%）。** 108 个测试，0 失败。相对 Test 的 14820，行 **+338**，指令 **+1827**，分支 **+142**。不要把第十五批写成 15152 或 15154。本仓库两次是 15152 和 15154。Test 比 15152 高 6 行，比 15154 高 4 行，落在这个 2 行跨度外面。方向和先前几批相反：先前 Test 落在两次之间，或比较低的一次再少 2 到 3 行。能对上的文件仍是 `ConfigurationFragment` 全局模式重载、`GuardedProcessPool` 和 `ProfileSettingsActivity` 未保存对话框。这三份的并集是 15157 行，Test 的 15158 还多 1 行，多出来的那一行不在本仓库两次的文件差里。**第十四批验收基线是 Test 在 `26c78d6` 的 14820/16338（90.71%），指令 100473/113365，分支 6775/9564。** 102 个测试，0 失败。不要把第十四批写成 14819 或 14821。**第十三批验收基线是 Test 在 `5f41659` 的 14537/16338（88.98%），指令 98732/113365，分支 6622/9564。** 97 个测试，0 失败。不要把第十三批写成 14539 或 14540。第十二批验收基线是 Test 在 `c6ad074` 的 **14224/16338（87.06%）**，指令 **96486/113365**，分支 **6454/9564**。93 个测试，0 失败。不要把第十二批写成 14227。第十一批验收基线是 Test 在 `b61ad54` 的 **13841/16338（84.72%）**，指令 **94165/113365**，分支 **6288/9564**。第十批验收基线是 Test 的 **13512/16338**。第九批验收基线是 Test 的 **12965/16333**。分母没有变。这次翻掉的是 `ConfigurationFragment` 全局模式重载的第 875–876、878–879、886 行，`GuardedProcessPool` 第 106 行，以及 `ProfileSettingsActivity` 未保存对话框确定按钮的第 56–57 行。

| 包 | 行 | 指令 | 分支 | 方法 | 类 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `io.nekohasekai.sagernet.ui` | 4739/5183 | 29091/32791 | 1748/2568 | 931/1072 | 238/250 |
| `io.nekohasekai.sagernet.widget` | 462/518 | 2539/3095 | 210/315 | 114/140 | 25/26 |
| `io.nekohasekai.sagernet.ui.profile` | 1184/1272 | 7222/7943 | 221/339 | 216/255 | 42/42 |
| `moe.matsuri.nb4a.ui` | 250/307 | 1457/2032 | 38/72 | 58/101 | 16/18 |
| `io.nekohasekai.sagernet.fmt.v2ray` | 838/939 | 5434/6096 | 551/817 | 64/67 | 6/6 |
| `moe.matsuri.nb4a.utils` | 294/365 | 1937/2413 | 136/210 | 48/60 | 5/6 |
| `io.nekohasekai.sagernet.ktx` | 492/561 | 3768/4403 | 182/230 | 169/220 | 22/25 |
| `io.nekohasekai.sagernet.utils` | 347/407 | 2340/2752 | 229/303 | 80/91 | 20/22 |
| `io.nekohasekai.sagernet.bg` | 785/816 | 5105/5570 | 299/398 | 203/222 | 45/47 |
| `io.nekohasekai.sagernet.database` | 967/985 | 11402/11765 | 426/485 | 595/633 | 35/37 |
| `io.nekohasekai.sagernet.database.preference` | 136/153 | 873/962 | 65/75 | 65/70 | 7/10 |
| `io.nekohasekai.sagernet.fmt.trojan_go` | 130/144 | 890/1002 | 81/120 | 11/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt` | 939/952 | 5890/6148 | 553/639 | 129/140 | 14/14 |
| `io.nekohasekai.sagernet.bg.proto` | 407/419 | 2930/3149 | 145/206 | 89/101 | 16/17 |
| `io.nekohasekai.sagernet.fmt.hysteria` | 310/321 | 2100/2202 | 252/300 | 20/22 | 3/3 |
| `moe.matsuri.nb4a.proxy.config` | 41/51 | 183/252 | 15/30 | 15/18 | 3/3 |
| `io.nekohasekai.sagernet.fmt.internal` | 24/33 | 93/132 | 9/12 | 9/15 | 3/3 |
| `io.nekohasekai.sagernet.fmt.trojan` | 24/33 | 146/194 | 14/24 | 8/10 | 3/3 |
| `moe.matsuri.nb4a.proxy.neko` | 24/32 | 83/110 | 4/4 | 9/14 | 2/2 |
| `moe.matsuri.nb4a.plugin` | 45/53 | 306/376 | 39/50 | 9/10 | 1/1 |
| `moe.matsuri.nb4a` | 367/374 | 2372/2703 | 251/384 | 202/206 | 152/152 |
| `io.nekohasekai.sagernet.fmt.ssh` | 44/51 | 195/222 | 24/30 | 7/10 | 3/3 |
| `io.nekohasekai.sagernet.fmt.socks` | 73/80 | 505/529 | 51/66 | 14/17 | 3/3 |
| `io.nekohasekai.sagernet.fmt.wireguard` | 54/59 | 250/263 | 18/20 | 9/12 | 3/3 |
| `io.nekohasekai.sagernet.fmt.shadowsocks` | 130/135 | 794/868 | 63/82 | 12/14 | 3/3 |
| `io.nekohasekai.sagernet` | 170/175 | 832/919 | 48/76 | 52/62 | 8/8 |
| `moe.matsuri.nb4a.proxy.anytls` | 141/145 | 898/930 | 67/92 | 15/17 | 4/4 |
| `moe.matsuri.nb4a.net` | 79/83 | 325/346 | 25/28 | 11/11 | 4/4 |
| `io.nekohasekai.sagernet.fmt.tuic` | 121/125 | 750/776 | 76/96 | 10/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt.snell` | 115/119 | 930/1023 | 124/178 | 13/15 | 4/4 |
| `io.nekohasekai.sagernet.fmt.mieru` | 50/54 | 292/309 | 10/12 | 8/10 | 3/3 |
| `com.github.shadowsocks.plugin.fragment` | 17/21 | 143/181 | 4/6 | 12/14 | 1/2 |
| `moe.matsuri.nb4a.hevtun` | 52/55 | 410/448 | 10/10 | 12/20 | 2/2 |
| `io.nekohasekai.sagernet.fmt.juicity` | 78/81 | 524/561 | 46/70 | 11/14 | 3/3 |
| `io.nekohasekai.sagernet.fmt.http` | 39/42 | 261/282 | 23/32 | 8/11 | 3/3 |
| `moe.matsuri.nb4a.proxy.shadowtls` | 54/56 | 332/339 | 4/4 | 13/15 | 4/4 |
| `io.nekohasekai.sagernet.group` | 816/818 | 6623/7019 | 714/1011 | 75/79 | 19/22 |
| `io.nekohasekai.sagernet.fmt.shadowsocksr` | 85/87 | 589/614 | 16/20 | 11/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt.naive` | 92/94 | 652/664 | 61/76 | 11/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt.gson` | 7/8 | 39/42 | 8/8 | 3/4 | 1/1 |
| `io.nekohasekai.sagernet.aidl` | 18/19 | 147/253 | 0/2 | 18/31 | 3/3 |
| `com.github.shadowsocks.plugin` | 1/2 | 3/12 | 0/0 | 1/3 | 1/1 |
| `moe.matsuri.nb4a.proxy` | 62/62 | 342/376 | 24/30 | 19/27 | 2/2 |
| `io.nekohasekai.sagernet.plugin` | 49/49 | 288/299 | 31/34 | 12/14 | 3/3 |

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

第十二批第二次干净 XML。未覆盖行按源文件合计，不把内部类再加一遍。`ConfigurationFragment.onRemoved` 第 1357、1358、1361 行这次算在未覆盖里；第一次干净 XML 里这三行是盖住的（1375/1587）。第 209–210 行这次两次都盖住了，以后仍可能单独翻。

| 源文件 | 未覆盖行 | 已覆盖 |
| --- | ---: | ---: |
| `io.nekohasekai.sagernet.ui.ConfigurationFragment.kt` | 215 | 1372/1587 |
| `io.nekohasekai.sagernet.ui.RouteSettingsActivity.kt` | 58 | 170/228 |
| `io.nekohasekai.sagernet.ui.GroupFragment.kt` | 56 | 266/322 |
| `io.nekohasekai.sagernet.bg.BaseService.kt` | 56 | 195/251 |
| `io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity.kt` | 55 | 178/233 |
| `io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean.java` | 54 | 132/186 |
| `io.nekohasekai.sagernet.ui.BackupFragment.kt` | 50 | 456/506 |
| `io.nekohasekai.sagernet.bg.SagerConnection.kt` | 47 | 52/99 |
| `io.nekohasekai.sagernet.widget.StatsBar.kt` | 45 | 152/197 |
| `io.nekohasekai.sagernet.ui.profile.ConfigEditActivity.kt` | 44 | 62/106 |
| `io.nekohasekai.sagernet.utils.DefaultNetworkListener.kt` | 43 | 33/76 |
| `io.nekohasekai.sagernet.ui.MainActivity.kt` | 43 | 280/323 |
| `moe.matsuri.nb4a.NativeInterface.kt` | 42 | 1/43 |
| `io.nekohasekai.sagernet.ui.RouteFragment.kt` | 41 | 153/194 |
| `io.nekohasekai.sagernet.fmt.v2ray.V2RayFmt.kt` | 40 | 529/569 |
| `io.nekohasekai.sagernet.ui.GroupSettingsActivity.kt` | 37 | 279/316 |

`AboutFragment.kt` 154/190。`SettingsPreferenceFragment.kt` 252/267。`LocalResolverImpl.kt` 73/83。`QuickToggleShortcut.kt` 0/36。`GroupInterfaceAdapter.kt` 15/51。`ConfigBuilder.kt` 689/690。`RawUpdater.kt` 630/631。`SingBoxOptions.java` 169/171。`JavaUtil.java` 100/100。`DataStore.kt` 219/219。`ConfigSource.kt` 56/56。`SubscriptionAccess.kt` 49/49。`ProfileManager.kt` 146/146。`ProxyEntity.kt` 364/366。`GroupUpdater.kt` 85/86。`Theme.kt` 70/70。`TrafficLooper.kt` 180/186。`BoxInstance.kt` 118/118。`TileService.kt` 49/49。`VpnService.kt` 127/127。`GuardedProcessPool.kt` 74/78。`PluginManager.kt` 49/49。`ServiceNotification.kt` 108/128。`SubscriptionUpdater.kt` 54/54。`ChainSettingsActivity.kt` 129/154。`WebDAVSettingsActivity.kt` 119/136。

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

77 个 JVM 测试，0 失败（本批新增 2 个 Robolectric 测试）。`SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport --offline` 通过。`syncFirstPartyClasses` 复制 1029 个 class 文件。有可执行行的源文件仍是 209 个。分母相对第六批不变。`compileOssReleaseKotlin --offline` 通过。提交 `a0d3937`。

**验收基线是 Test 在 `a0d3937` 的干净复跑：行 10732/16314（65.78%），指令 73783/113219，分支 4928/9554（与本仓库 XML 相同）。** 相对 Test 的第六批 8196/16314，行 **+2536**，分母仍是 16314。本仓库同提交的干净 XML 是行 10734、指令 73789，比 Test 多 2 行、6 条指令，落在当时允许的 ±2 行里。第九批起实测跨度更大，不再按 ±2 收。方法、类只在本仓库 XML 里数过（2493/3896、526/779），Test 没有单报。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 来源 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 10732 | 5582 | 16314 | **65.78%** | Test 干净复跑；相对 Test 的 8196 为 +2536 |
| 指令（INSTRUCTION） | 73783 | 39436 | 113219 | **65.17%** | Test 干净复跑；相对 Test 的 58102 为 +15681 |
| 分支（BRANCH） | 4928 | 4626 | 9554 | **51.58%** | Test，与本仓库 XML 相同 |
| 方法（METHOD） | 2493 | 1403 | 3896 | **63.99%** | 本仓库 XML（行 10734 那次） |
| 类（CLASS） | 526 | 253 | 779 | **67.52%** | 本仓库 XML（行 10734 那次） |

本仓库 HTML 页脚是未覆盖/合计：指令 39,430 of 113,219，分支 4,626 of 9,554，复杂度未覆盖 4,568、合计 8,871，行 5,580 of 16,314（覆盖 = 16314 − 5580 = 10734），方法 1,403 of 3,896，类 253 of 779。下面源文件比例来自这份 10734 行的 XML，和 Test 的 10732 可以差 1–2 行。

指令相对 Test 的 73783 少 6，和允许的 ±2 行是同一类机器差。按本仓库第六批 XML 的 58108 相减是 +15681。分母没有变。

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

## 第八批（配置页菜单、备份、外部插件、磁贴）

78 个 JVM 测试，0 失败（本批新增 1 个 Robolectric 测试）。`SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport --offline` 通过。`syncFirstPartyClasses` 复制 1030 个 class 文件。有可执行行的源文件仍是 209 个。`compileOssReleaseKotlin --offline` 通过。

**Test 在 `2a81735` 复跑后的验收基线是 11956/16330（73.21%）。** 指令 **81856/113332**，分支 **5401/9560**（与本仓库 XML 的分支覆盖数一致）。78 个测试，0 失败。相对 Test 验收的第七批 10732/16314，行 **+1224**。行分母 16314 → 16330（+16）已确认，来自本批加的覆盖口。本仓库干净 XML 是 11958/16330 行、81862/113332 指令，比 Test 多 2 行、多 6 条指令。允许再差 0–2 行。方法和类 Test 没有另报，下表这两行仍用本仓库 XML。后面的文件比例也来自这份 11958 的 XML，和 Test 可能差 1–2 行。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第七批验收 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 11956 | 4374 | 16330 | **73.21%** | +1224 行；分母 +16 |
| 指令（INSTRUCTION） | 81856 | 31476 | 113332 | **72.23%** | +8073（相对 Test 的 73783）；分母 +113 |
| 分支（BRANCH） | 5401 | 4159 | 9560 | **56.50%** | +473；分母 +6 |
| 方法（METHOD） | 2731 | 1184 | 3915 | **69.76%** | 本仓库 XML；Test 未另报 |
| 类（CLASS） | 585 | 194 | 779 | **75.10%** | 本仓库 XML；Test 未另报 |

本仓库那次 HTML 页脚（对应 11958 行，不是 Test 的 11956）：指令 31,470 of 113,332，分支 4,159 of 9,560，复杂度未覆盖 4,187、合计 8,893，行 4,372 of 16,330，方法 1,184 of 3,915，类 194 of 779。

| 源文件 | 第七批本仓库 XML | 第八批 |
| --- | ---: | ---: |
| `ui` 包 | 1672/5183 | **2555/5183** |
| `bg` 包 | 478/808 | **602/808** |
| `bg.proto` 包 | 292/419 | **384/419** |
| `widget` 包 | 271/518 | **311/518** |
| `nb4a.ui` 包 | 180/307 | **188/307** |
| `ui/ConfigurationFragment.kt` | 406/1587 | **763/1587** |
| `ui/BackupFragment.kt` | 5/506 | **251/506** |
| `ui/GroupFragment.kt` | 93/322 | **200/322** |
| `ui/RouteFragment.kt` | 83/194 | **106/194** |
| `ui/SettingsPreferenceFragment.kt` | 104/267 | **197/267** |
| `ui/AboutFragment.kt` | 94/190 | **115/190** |
| `ui/MainActivity.kt` | 142/323 | **172/323** |
| `bg.proto/BoxInstance.kt` | 32/118 | **118/118** |
| `bg/GuardedProcessPool.kt` | 6/75 | **61/75** |
| `bg/VpnService.kt` | 104/127 | **118/127** |
| `bg/TileService.kt` | 0/49 | **49/49** |
| `nb4a/utils/JavaUtil.java` | 93/97 | **100/100** |
| `nb4a/hevtun/HevTunRuntime.kt` | 34/44 | **51/52** |
| `group/GroupUpdater.kt` | 85/86 | **85/86** |
| `database/ProxyEntity.kt` | 364/366 | **364/366** |

`GuardedProcessPool` 的重启分支靠 `SystemClock.elapsedRealtime`。Robolectric 暂停的 looper 不跟 `Thread.sleep` 走，所以这一行在两台机器上可能差 1。这落在允许的 ±2 行里。

### 本批的做法

- `PluginManager.initOverride` 非 null 时，`init` 直接返回测试给的路径。生产保持 null，仍走原来的插件查找。
- `HevTunRuntime` 的 `runningNative` / `startNative` / `stopNative` 默认仍调用 `HevTunNative`。测试替换这三个 lambda，`start` 成功后 `running = true`，`stop` 会调 `stopNative`。设备上不吞 native 错误。
- `JavaUtil.lockProbe` 生产为 null，仍走 `FileChannel.tryLock()`。测试返回 null，走到“锁被别的进程占用”那一支。`new JavaUtil()` 盖住类那一行；`getDataDir` 抛异常盖住 `handleWebviewDir` 的外层 catch。
- `PublicDatabase.kvPairDaoOverride` 生产为 null，仍打开 Room。备份页 `settings = true` 时测试放上内存 DAO，避免在 JVM 上建库。
- 外部插件用 `/bin/true` 当可执行文件。trojan-go、mieru、naive、hysteria（wechat-video 和 faketcp）都 `init` 再 `launch`。faketcp 仍会在命令前加 `su -c`；这台机器没有 `su`，`start` 抛 `IOException`，命令组装那几行已经执行。没有 exec 真实插件二进制。`externalInstances` 里放一个空实例再 `launch` 一次。hysteria 的 `caText` 会把 CA 写进缓存文件。
- `GuardedProcessPool` 用 `/bin/true`（启动后马上退出）和 `sleep 0.3`。`ShadowSystemClock` 把 `elapsedRealtime` 拨过 1 秒，走到“退出不够快就重启”。SDK 24 以下的 `Os.kill(pid)` 没有走：桌面 JVM 没有 `java.lang.ProcessManager$ProcessImpl`，那条会变成 `ExceptionInInitializerError`。
- `VpnService.establishTun` 的产品分支没有改。hysteria faketcp 的 `trafficMap` 让 `needBypassRootUid` 为 true，分别跑了 `proxyApps == false`（全部应用进 individual）和 `proxyApps && bypass`（从全部应用里扣掉名单）。再跑了一次 IPv6 默认路由。`ShadowTunBuilder`、`ShadowOsAddress`、`ShadowResolvingNetwork` 现在挂在 `Batch7RobolectricTest` 和 `Batch8RobolectricTest` 上，release 看不到。
- `TileService` 用反射放上私有的 `mTile`，再用 `ISagerNetService.Stub` 走 `stateChanged`、`onClick`、锁屏点击和 `cbSelectorUpdate`。Robolectric 销毁这个服务时会把 `ShadowTileService` 错铸成 `ShadowService`，测试把这次 `destroy` 接住，服务状态已经走过。
- 配置页菜单、剪贴板导入、ping/url 测试、分组和路由的 holder、设置项（重置只点取消）、关于页检查更新、备份的导出/分享/导入和非法 WebDAV URL 都在同一个沙箱里跑。导入对话框点的是取消，没有 `triggerFullRestart`。夜间主题和语言会 `recreate`，放在测试末尾。
- `FileProvider` 会把第一份沙箱的路径根缓存住。备份分享之后清掉 `sCache`，否则后面的 `UiBgRobolectricTest` 发日志找不到 root。
- 没有把 `GroupUpdater` 第 109 行调成可达，也没有给 `ProxyEntity` 发明类型。`TrojanBean` 继承 `StandardV2RayBean`，父分支先匹配；调换 `when` 会在 `security != "tls"` 时改掉 `sni`。`ProxyEntity` 第 250 行的 elvis 在拼错误信息时先 NPE，第 543 行的 `else` 对现有类型到不了。

## 第九批（配置列表、WebDAV、应用列表、API 23 进程）

80 个 JVM 测试，0 失败（本批新增 2 个 Robolectric 测试）。`SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport --offline` 通过。`syncFirstPartyClasses` 复制 1030 个 class 文件。有可执行行的源文件仍是 209 个。`compileOssReleaseKotlin --offline` 通过。

**验收基线是 Test 在 `e138299` 的干净复跑：行 12965/16333（79.38%），指令 88553/113352，分支 5923/9562。** 80 个测试，0 失败。相对 Test 验收的第八批 11956/16330，行 **+1009**。行分母 16330 → 16333（+3）已确认，来自 `GuardedProcessPool.pidReader`。方法和类 Test 没有另报，下表这两行仍用本仓库 XML。允许差到下面实测的幅度，不再把第九批收成 ±2。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第八批验收 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 12965 | 3368 | 16333 | **79.38%** | +1009 行；分母 +3 |
| 指令（INSTRUCTION） | 88553 | 24799 | 113352 | **78.12%** | +6697（相对 Test 的 81856）；分母 +20 |
| 分支（BRANCH） | 5923 | 3639 | 9562 | **61.94%** | +522；分母 +2 |
| 方法（METHOD） | 2947 | 970 | 3917 | **75.24%** | +216（相对本仓库第八批 XML 的 2731）；分母 +2 |
| 类（CLASS） | 622 | 157 | 779 | **79.85%** | +37（相对本仓库 XML 的 585）；分母不变 |

本仓库在 `e138299` 上又跑了两次干净的 `SKIP_HEV=1 :app:clean :app:jacocoFirstPartyReport`。第一次行 **12969**、指令 **88566**、分支 **5928**。HTML 页脚：指令 24,786 of 113,352，分支 3,634 of 9,562，行 3,364 of 16,333。第二次行 **12972**、指令 **88625**、分支 **5928**。HTML 页脚：指令 24,727 of 113,352，分支 3,634 of 9,562，行 3,361 of 16,333。原先写的 12970/88611/5928 落在这两次指令之间。Test 的 12965/88553/5923 比这两次都低。三次机器加起来，行是 **12965–12972**（差 7），指令是 **88553–88625**（差 72），分支是 **5923–5928**（差 5）。

两次本机 XML 对过源文件，挪动的是这些：

- `ConfigurationFragment.kt` 第 209–210 行。档案状态协程在 `Dispatchers.IO` 里被取消时会重抛 `CancellationException`。一次盖住，一次没有。差 2 行、3 条指令。
- `ServiceNotification.kt` 第 77–84 行。`showDirectSpeed` 在构造时抄下来，只有当时为 false 才会走简版速度文字。`Batch7RobolectricTest` 先把它设成 false 再启动 VPN，流量循环赶不赶上这次 `postNotificationSpeedUpdate` 看调度。一次 102 行，一次 107 行。大约 60 条指令。两次总指令差 59，就是这里。
- `TrafficLooper.kt` 第 244、245、267 行只换了分支侧，行数仍是 180。
- `GuardedProcessPool.kt` 这两次都是 72/78，第 103 和 105 行（`destroyForcibly` 和随后的 `receive`）都盖住了。早先同一次提交的干净 XML 记过 70/78。`close()` 不等人，API 21 的 `withTimeout(500)` 和 API 29 的 `withTimeout(1000)` 可能在测试方法返回之后才跑完。Test 比本机低 4 行，和少掉第 209–210 行再少掉这两行的数量一致；分支少 5，本机两次都是 5928，没有复现。

后面的文件比例来自本仓库那份合计接近 12970 的 XML，和 Test 的 12965 可能差这几行。

| 源文件 | 第八批本仓库 XML | 第九批 |
| --- | ---: | ---: |
| `ui` 包 | 2555/5183 | **3501/5183** |
| `bg` 包 | 602/808 | **620/811** |
| `ui/ConfigurationFragment.kt` | 763/1587 | **1177/1587** |
| `ui/BackupFragment.kt` | 251/506 | **391/506** |
| `ui/AssetsActivity.kt` | 85/245 | **178/245** |
| `ui/AppManagerActivity.kt` | 75/228 | **217/228** |
| `ui/AppListActivity.kt` | 67/172 | **168/172** |
| `ui/ScannerActivity.kt` | 38/116 | **87/116** |
| `bg/GuardedProcessPool.kt` | 61/75 | **70/78** |
| `bg/VpnService.kt` | 118/127 | **127/127** |
| `nb4a/hevtun/HevTunRuntime.kt` | 51/52 | **51/52** |
| `group/GroupUpdater.kt` | 85/86 | **85/86** |
| `database/ProxyEntity.kt` | 364/366 | **364/366** |

`GuardedProcessPool` 的重启和 `withTimeout` 收尾，加上 `ServiceNotification` 的简版速度文字，会让第九批的合计跳出 ±2。验收用 Test 的 12965/16333。

### 本批的做法

- 配置页里的 `GroupFragment` 由 `GroupPagerAdapter.createFragment` 挂到单独的 `FrameLayout` 上。列表绑定、筛选、单列和双列拖动、排序菜单、分享菜单、删除确认和直接删除都走过。`select = true` 的配置页只绑定，不把 `MainActivity` 当成 `SelectCallback`。
- WebDAV 用本机 `ServerSocket`，没有新依赖。PROPFIND 404 后 MKCOL 再 PUT，目录已存在则直接 PUT，401 和 500 走失败。恢复列表分别匹配 `D:href`、`d:href`、`href`，zip 和 json 都下载过。导入对话框只点取消，没有 `ProcessPhoenix.triggerRebirth`。
- `AssetsActivity` 刷新规则库。`rulesProvider` 为 1 时走 GitHub 请求（桩上的 HTTP 客户端立刻返回空正文），为 4 时走自定义 URL。自定义 `.db` 走删除和撤销。
- `AppListActivity` 和 `AppManagerActivity` 在 `PackageCache.reload()` 之后打开。额外装了 `android` 和一个带 `INTERNET` 的包。搜索、系统应用开关、反选、清空、剪贴板和自动选择都点过。关闭代理的芯片会 `finish()`。
- `ScannerActivity` 直接调扫码回调：socks、空结果、无法解析的文本，以及 `sn://subscription`。没有点会 `triggerFullRestart` 的确认。
- `VpnService.startProcesses` 在 `enableHevTun` 为 true 时走 `establishTun` 再 `HevTunRuntime.start`。native hook 仍返回成功，不加载 `libhev-socks5-tunnel`。SOCKS 且 `bypass` 为 true 时走 `addDisallowedApplication`。hysteria faketcp 让 `allApps` 跑过 `"android"` 和 `INTERNET` 两个分支。`updateUnderlyingNetwork` 在有底层网络时调用。
- API 21 的测试给 `GuardedProcessPool.pidReader` 赋值，所以不会碰 `java.lang.ProcessManager$ProcessImpl`。`ShadowOsAddress.kill` 在这个测试类上替换 `Os.kill`：一次抛 `NoSuchFieldException`，一次 `ESRCH`，一次其它 errno，一次记录调用且不发信号。生产 `pidReader` 为 null，设备上仍是反射加真正的 `Os.kill`。
- 没有把 `GroupUpdater` 第 109 行调成可达，也没有给 `ProxyEntity` 发明类型。

## 第十批（导入回调、设置页、插件查找、进程重启）

84 个 JVM 测试，0 失败，0 跳过（本批新增 4 个 Robolectric 测试）。`SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport :app:compileOssReleaseKotlin --offline` 通过。`syncFirstPartyClasses` 复制 1031 个 class 文件。有可执行行的源文件仍是 209 个。

**验收基线是 Test 在 `b97bde7` 的干净复跑：行 13512/16338（82.70%），指令 92114/113365，分支 6122/9564。** 84 个测试，0 失败。相对 Test 验收的第九批 12965/16333，行 **+547**。方法和类 Test 没有另报，下表这两行仍用本仓库 XML。不按 ±2 收。不要把第十批写成 13514 或 13517。

分母多出来的 5 行、13 条指令、2 个分支、2 个方法、1 个类，都是 `BaseService.Binder` 的 `restartProcessOverride`。生产默认 null，`CONNECTION_ID_RESTART_BG` 仍调用 `Runtime.getRuntime().exit(0)`。`exit` 那一行这次未覆盖。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第九批验收 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 13512 | 2826 | 16338 | **82.70%** | +547 行；分母 +5 |
| 指令（INSTRUCTION） | 92114 | 21251 | 113365 | **81.25%** | +3561（相对 Test 的 88553）；分母 +13 |
| 分支（BRANCH） | 6122 | 3442 | 9564 | **64.01%** | +199（相对 Test 的 5923）；分母 +2 |
| 方法（METHOD） | 3066 | 853 | 3919 | **78.23%** | +119（相对第九批本仓库 XML 的 2947）；分母 +2 |
| 类（CLASS） | 656 | 124 | 780 | **84.10%** | +34（相对第九批本仓库 XML 的 622）；分母 +1 |

本仓库在 `b97bde7` 上跑了两次同样的干净命令。第一次行 **13514**、指令 **92122**、分支 **6123**。HTML 页脚：指令 21,243 of 113,365，分支 3,441 of 9,564，行 2,824 of 16,338。第二次行 **13517**、指令 **92151**、分支 **6123**。HTML 页脚：指令 21,214 of 113,365，分支 3,441 of 9,564，行 2,821 of 16,338。Test 的 13512/92114/6122 比这两次都低。三次合在一起：行 **13512–13517**（差 5），指令 **92114–92151**（差 37），分支 **6122–6123**（差 1）。84 个测试，0 失败。后面的文件比例来自第一次本仓库 XML，和 Test 的 13512 可能差这几行。验收用 Test 的 13512/16338。

挪动的只有两处：

- `GuardedProcessPool.kt` 第 44 行，流日志吞 `IOException` 的空 catch。第一次未覆盖（73/78），第二次盖住（74/78），1 条指令。第 31–32 行两次都未覆盖：桌面 JVM 没有 `ProcessManager$ProcessImpl`，一碰就是 `ExceptionInInitializerError`。第 106–107 行两次都未覆盖。第 39 行默认 `env`、第 103 和 105 行两次都盖住了。`close()` 不等人，第九批这几行还曾在 70/78 和 72/78 之间动过。
- `TrafficLooper.kt` 第 172 行（`proxy == null` 时 `delay` 之后的 `continue`）和第 271 行（循环末尾的 `delay`）。第一次 180/186，第二次 182/186，指令 1332 → 1360（+28）。分支两次都是 77/112。

`ConfigurationFragment.kt` 第 209–210 行两次都没盖住。那是档案状态协程被取消时重抛 `CancellationException`，大约 2 行、3 条指令，第九批曾单独翻转。`ServiceNotification.kt` 两次都是 **108/128**，第 77–84 行的简版速度文字都盖住了。第十批在 `showDirectSpeed` 为 false 和 true 时都直接调用 `postNotificationSpeedUpdate`。第九批那一块大约 5 行、60 条指令，是当时指令合计少 58 的主要来源。

第九批三次机器的跨度是行 7、指令 72、分支 5。第十批加上 Test 是行差 5、指令差 37、分支差 1。不要按 ±2 收。验收用 Test 的 13512/16338。

| 源文件 | 第九批本仓库 XML | 第十批 |
| --- | ---: | ---: |
| `ui` 包 | 3501/5183 | **3922/5183** |
| `bg` 包 | 620/811 | **631/816** |
| `ui/ConfigurationFragment.kt` | 1177/1587 | **1312/1587** |
| `ui/BackupFragment.kt` | 391/506 | **440/506** |
| `ui/MainActivity.kt` | 172/323 | **244/323** |
| `ui/GroupSettingsActivity.kt` | 169/316 | **213/316** |
| `ui/RouteSettingsActivity.kt` | 82/228 | **123/228** |
| `ui/AssetsActivity.kt` | 178/245 | **227/245** |
| `ui/ScannerActivity.kt` | 87/116 | **106/116** |
| `bg/GuardedProcessPool.kt` | 70/78 | **73/78** |
| `plugin/PluginManager.kt` | 12/49 | **37/49** |
| `bg/ServiceNotification.kt` | 102/128 或 107/128 | **108/128** |
| `bg/BaseService.kt` | 189/246 | **195/251** |
| `ktx/Utils.kt` | 118/158 | **131/158** |
| `bg/VpnService.kt` | 127/127 | **127/127** |
| `group/GroupUpdater.kt` | 85/86 | **85/86** |
| `database/ProxyEntity.kt` | 364/366 | **364/366** |

### 本批的做法

- 配置页先走导入文件回调（空、socks、空白、非法、zip、`sn://subscription` 后点取消），再回到配置页走剪贴板、`clash://install-config` 和新建档案菜单。`sn://` 会拆掉当前 fragment，所以回调之后重新 `displayFragmentWithId`。测速把 `connectionTestConcurrent` 设成 1。
- 本地备份导入的确定按钮走到 `triggerFullRestart`，再进 `registerCallback(CONNECTION_ID_RESTART_BG)`。测试把 `restartProcessOverride` 设成计数，生产仍是 null 然后 `exit`。WebDAV 恢复的确定按钮直接 `ProcessPhoenix.triggerRebirth`。`ShadowProcessPhoenix` 只在第十批，并且 `instrumentedPackages` 含 `com.jakewharton.processphoenix`，否则影子不会装上，`onCreate` 里的 `Runtime.exit(0)` 会把整个测试进程杀掉，Gradle 把当前用例记成 SKIPPED、构建仍是 SUCCESS，后面的测试类都不会跑。
- `GroupSettingsActivity` 走了剪贴板导入、非法 DNS、修剪后的 DNS、保存和删除。`RouteSettingsActivity` 走了保存，以及 `dirty == false` 时的空路由对话框。`MainActivity` 走了 `ACTION_VIEW`、扫码入口和导航。
- `AssetsActivity` 的 GitHub JSON 来自桩上的 `Libcore.httpBody`。空字符串仍返回空正文、`writeTo` 不写文件，和原来的桩一样。桩不进 APK，也不进 JaCoCo。相册导入在 API 29 走 `ImageDecoder`。API 28 以下的 `MediaStore.getBitmap` 和第 86–90 行的异常还在。
- `PluginManager` 在 `initOverride` 为 null 时走了内置 so（`applicationInfo == null`）和外部 provider。`initNativeFaster` 的失败、metadata 的 `Int` / 其它类型还在。
- `GuardedProcessPool` 用合成构造的 mask 位盖住默认 `env`。`/bin/sh -c "trap '' TERM; exec sleep 30"` 再 `idle` 主线程，让 `destroyForcibly` 跑到。没有加宽 pid 反射的 catch。
- 没有把 `GroupUpdater` 第 109 行或 `ProxyEntity` 第 250、543 行改成可达。`libcore` Go 仍无 JaCoCo 入口，也没有 `_test.go`。

## 第十一批（订阅任务、设置监听、链式档案、WebDAV 探测）

88 个 JVM 测试，0 失败，0 跳过（本批新增 4 个 Robolectric 测试）。两次 `SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport :app:compileOssReleaseKotlin --offline` 都通过。`syncFirstPartyClasses` 复制 1031 个 class 文件。有可执行行的源文件仍是 209 个。分母相对第十批没有变：行 16338，指令 113365，分支 9564，方法 3919，类 780。生产代码没有改。

**验收基线是 Test 在 `b61ad54` 的干净复跑：行 13841/16338（84.72%），指令 94165/113365，分支 6288/9564。** 88 个测试，0 失败。相对 Test 验收的第十批 13512/16338，行 **+329**。方法和类 Test 没有另报，下表这两行仍用本仓库 XML。不按 ±2 收。不要把第十一批写成 13844 或 13846。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第十批验收 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 13841 | 2497 | 16338 | **84.72%** | +329 行；分母不变 |
| 指令（INSTRUCTION） | 94165 | 19200 | 113365 | **83.06%** | +2051（相对 Test 的 92114） |
| 分支（BRANCH） | 6288 | 3276 | 9564 | **65.75%** | +166（相对 Test 的 6122） |
| 方法（METHOD） | 3119 | 800 | 3919 | **79.59%** | 本仓库 XML；Test 未另报 |
| 类（CLASS） | 672 | 108 | 780 | **86.15%** | 本仓库 XML；Test 未另报 |

第一次干净 XML：行 **13846/16338**，指令 **94193/113365**，分支 **6288/9564**。HTML 页脚：指令 19,172 of 113,365，分支 3,276 of 9,564，行 2,492 of 16,338（覆盖 = 16338 − 2492 = 13846）。第二次：行 **13844/16338**，指令 **94190/113365**，分支 **6288/9564**。HTML 页脚：指令 19,175 of 113,365，分支 3,276 of 9,564，行 2,494 of 16,338（覆盖 = 16338 − 2494 = 13844）。方法和类两次都是 3119/3919、672/780。

本仓库两次和 Test 合在一起：行 **13841–13846**（差 5），指令 **94165–94193**（差 28），分支都是 **6288**。两次本仓库 XML 之间只动了 `ConfigurationFragment.kt` 第 209–210 行。第一次盖住（1347/1587，指令 8491），第二次未覆盖（1345/1587，指令 8488）。差 2 行、3 条指令，分支 661/1062 没变。那是档案状态协程被取消时重抛 `CancellationException`。这解释不了 Test 再少的那 1 行和大约 25 条指令，所以不把 13844 收成验收。`GuardedProcessPool` 两次都是 74/78。`TrafficLooper` 两次都是 180/186，指令 1330，分支 76/112。`ServiceNotification` 两次都是 108/128。验收用 Test 的 13841/16338。

下面文件比例来自第十一批第二次本仓库 XML（行 13844），不是 Test 的 13841，也不是文首第十二批的表。和第十批第一次本仓库 XML（行 13514 那份，不是 Test 的 13512）比，行数变了的包是：

| 源文件 | 第十批第一次本仓库 XML | 第十一批第二次 |
| --- | ---: | ---: |
| `ui` 包 | 3922/5183 | **4114/5183**（+192；第一次 4116，因第 209–210 行盖住） |
| `ui.profile` 包 | 975/1272 | **1058/1272**（+83） |
| `bg` 包 | 631/816 | **681/816**（+50） |
| `plugin` 包 | 37/49 | **40/49**（+3） |
| `group` 包 | 779/818 | **780/818**（+1） |
| `ktx` 包 | 460/561 | **461/561**（+1） |
| `ui/ConfigurationFragment.kt` | 1312/1587 | **1345/1587**（第一次 1347） |
| `ui/GroupSettingsActivity.kt` | 213/316 | **261/316** |
| `ui/RouteSettingsActivity.kt` | 123/228 | **154/228** |
| `ui/profile/ChainSettingsActivity.kt` | 55/154 | **129/154** |
| `ui/WebDAVSettingsActivity.kt` | 40/136 | **119/136** |
| `ui/profile/ProfileSettingsActivity.kt` | 170/233 | **178/233** |
| `ui/BackupFragment.kt` | 440/506 | **441/506** |
| `bg/SubscriptionUpdater.kt` | 5/54 | **54/54** |
| `bg/GuardedProcessPool.kt` | 73/78 | **74/78** |
| `plugin/PluginManager.kt` | 37/49 | **40/49** |
| `group/GroupInterfaceAdapter.kt` | 14/51 | **15/51** |
| `bg/ServiceNotification.kt` | 108/128 | **108/128** |
| `bg.proto/TrafficLooper.kt` | 180/186 | **180/186** |
| `bg/VpnService.kt` | 127/127 | **127/127** |
| `group/GroupUpdater.kt` | 85/86 | **85/86** |
| `database/ProxyEntity.kt` | 364/366 | **364/366** |

`ui` 的 +192 就是配置页 +33、分组设置 +48、路由设置 +31、WebDAV 设置 +79、备份 +1。`bg` 的 +50 是 `SubscriptionUpdater` +49 和 `GuardedProcessPool` +1。`ui.profile` 的 +83 里，链式档案 +74，档案设置 +8，同包还有 1 行。`bg.proto` 行数仍是 404/419；指令 2916→2914、分支 143→142，是 `TrafficLooper` 的分支侧，两次第十一批之间没有再动。`nb4a.utils` 行数仍是 291/365，指令 1893→1895，分支 131→132。这些都不是行合计的来源。

### 本批的做法

- 配置页菜单走了清流量、清测速结果、删除不可用（点确定）、22 条重复档案的去重（点确定）、订阅组的更新确认、已连接时的全局模式和 snackbar 动作。zip 里放了一个目录项。导出回调写进注册过的输出流，也传了一次 null。剪贴板是不带名字的 `clash://install-config`，会进 `fetchAirportName`。`decodeProfileTitle` 和 `parseContentDisposition` 用反射调用，因为桩上的 `httpHeader` 分不出两个头名字。
- `GroupSettingsActivity` 走了类型、自动更新、间隔（非数字、小于 15、20）、过滤模式、前置和落地代理（含选择档案）、密码摘要、未保存返回（取消和放弃）和缺失的分组 id。`RouteSettingsActivity` 走了出站、应用列表、未保存返回、保存、删除（先取消再确定）和缺失的规则 id。
- `ChainSettingsActivity` 打开时带 `ProfileSettingsActivity.EXTRA_PROFILE_ID`。测了 `testProfileContains` / `testProfileAllowed`、`reload`、追加、替换、把自己加进去的对话框、移动和删除。`replacing` 只在列表非空时设成 1。detached holder 的位置是 −1，不能拿去当下标。
- `WebDAVSettingsActivity` 对上空地址、1 秒内的第二次点击、非法 URL，以及本机 `StatusServer` 的 401、200 加 MKCOL 405、500。然后 `onSupportNavigateUp`。备份页点了空服务器的备份和恢复、导出、分享（接住异常）、`ftp://bad` 和 401。
- `SubscriptionUpdater.reconfigureOverride` 在这一段设成 null，在 `Dispatchers.IO` 上调用 `reconfigureUpdater`，最多等 8 秒，没结束就取消。`UpdateTask` 手造 `WorkerParameters`。四个订阅组分别是关闭自动更新、仅在连接时更新、刚更新过、已过间隔。`doWork` 在停止和已连接两种状态下都跑过。测完把 override 放回空 lambda。生产默认仍是 null，仍走 `RemoteWorkManager`。
- `PluginManager.initOverride` 为 null 时读了 `PluginNotFoundException` 的本地化文案，装了一个不能执行的外部 so，并用 `Int` 和布尔 metadata 走了 `loadString`。失败路径仍未覆盖：第 42–44、57–58、65–70 行。
- `GuardedProcessPool` 第 31–32 行用反射碰过，API 21 上也在 `pidReader == null` 时 `close` 了一个 `sleep 30`。这两行仍未覆盖：`Class.forName` 在 JaCoCo 探针之前就抛 `ClassNotFoundException`。第 106–107 行仍未覆盖。第 44 行这次盖住了。没有加宽生产 catch。
- 没有把 `GroupUpdater` 第 109 行或 `ProxyEntity` 第 250、543 行改成可达。`libcore` Go 仍无 JaCoCo 入口。

## 第十二批（分组和路由列表、设置、本地 DNS）

93 个 JVM 测试，0 失败，0 跳过（本批新增 5 个 Robolectric 测试）。两次 `SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport :app:compileOssReleaseKotlin --offline` 都通过。`syncFirstPartyClasses` 复制 1031 个 class 文件。有可执行行的源文件仍是 209 个。分母相对第十一批没有变：行 16338，指令 113365，分支 9564，方法 3919，类 780。生产代码没有改。

**验收基线是 Test 在 `c6ad074` 的干净复跑：行 14224/16338（87.06%），指令 96486/113365，分支 6454/9564。** 93 个测试，0 失败。相对 Test 验收的第十一批 13841/16338，行 **+383**。方法和类 Test 没有另报，下表这两行仍用本仓库第二次 XML。不按 ±2 收。不要把第十二批写成 14227。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第十一批验收 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 14224 | 2114 | 16338 | **87.06%** | +383 行；分母不变 |
| 指令（INSTRUCTION） | 96486 | 16879 | 113365 | **85.11%** | +2321（相对 Test 的 94165） |
| 分支（BRANCH） | 6454 | 3110 | 9564 | **67.48%** | +166（相对 Test 的 6288） |
| 方法（METHOD） | 3184 | 735 | 3919 | **81.25%** | 本仓库第二次 XML；Test 未另报 |
| 类（CLASS） | 694 | 86 | 780 | **88.97%** | 本仓库 XML；Test 未另报 |

第一次本仓库干净 XML：行 **14227/16338（87.08%）**，指令 **96508/113365（85.13%）**，分支 **6454/9564（67.48%）**。HTML 页脚：指令 16,857 of 113,365，分支 3,110 of 9,564，行 2,111 of 16,338（覆盖 = 16338 − 2111 = 14227）。方法 3185/3919，类 694/780。第二次：行 **14224/16338（87.06%）**，指令 **96464/113365（85.09%）**，分支 **6447/9564（67.41%）**。HTML 页脚：指令 16,901 of 113,365，分支 3,117 of 9,564，行 2,114 of 16,338（覆盖 = 16338 − 2114 = 14224）。方法 3184/3919，类 694/780。Test 的行数是较低的 **14224**，指令是 **96486**（比第二次多 22，比第一次少 22），分支是 **6454**（和第一次相同）。

两次本仓库 XML 只动了 `ConfigurationFragment.kt` 的 `onRemoved`。第一次盖住第 1357、1358、1361 行（1375/1587，指令 8692，分支 692/1062）。第二次这三行未覆盖（1372/1587，指令 8648，分支 685/1062）。差 3 行、44 条指令、7 个分支、1 个方法。那是分组里最后一个未分组档案被删掉时重载列表，回调赶不赶上这次 exec。第 209–210 行这次两次都盖住了。`GuardedProcessPool` 两次都是 74/78。`TrafficLooper` 两次都是 180/186。`ServiceNotification` 两次都是 108/128。`PluginManager` 两次都是 49/49。`LocalResolverImpl` 两次都是 73/83。验收用 Test 的 14224/16338，不把 14227 收成验收。

和第十一批第二次本仓库 XML（行 13844 那份，不是 Test 的 13841）比，第二次这张表上行数变了的包是：

| 源文件 | 第十一批第二次 | 第十二批第二次 |
| --- | ---: | ---: |
| `ui` 包 | 4114/5183 | **4424/5183**（+310；第一次 4427，因 `onRemoved` 三行盖住） |
| `moe.matsuri.nb4a.net` | 16/83 | **73/83**（+57） |
| `plugin` 包 | 40/49 | **49/49**（+9） |
| `widget` 包 | 330/518 | **332/518**（+2） |
| `ktx` 包 | 461/561 | **463/561**（+2） |
| `ui/GroupFragment.kt` | 209/322 | **266/322** |
| `ui/SettingsPreferenceFragment.kt` | 197/267 | **252/267** |
| `ui/RouteFragment.kt` | 106/194 | **153/194** |
| `ui/AboutFragment.kt` | 115/190 | **154/190** |
| `ui/MainActivity.kt` | 244/323 | **280/323** |
| `ui/ConfigurationFragment.kt` | 1345/1587 | **1372/1587**（第一次 1375） |
| `ui/GroupSettingsActivity.kt` | 261/316 | **279/316** |
| `ui/RouteSettingsActivity.kt` | 154/228 | **170/228**（+16） |
| `ui/BackupFragment.kt` | 441/506 | **456/506** |
| `nb4a/net/LocalResolverImpl.kt` | 16/83 | **73/83** |
| `plugin/PluginManager.kt` | 40/49 | **49/49** |
| `bg/BaseService.kt` | 195/251 | **195/251** |
| `bg/GuardedProcessPool.kt` | 74/78 | **74/78** |

`ui` 的 +310 就是分组列表 +57、设置 +55、路由列表 +47、关于 +39、主界面 +36、配置页 +27、分组设置 +18、路由设置 +16、备份 +15。`nb4a.net` 的 +57 就是 `LocalResolverImpl`。`plugin` 的 +9 就是 `PluginManager` 从 40 到 49。

### 本批的做法

- 配置页把一组档案绑进列表，走了移动、撤销、流量刷新，以及 TCP ping 和 URL 测速。选中一条 socks 之后启动了 VPN。删除不可用走了确定。
- 分组页走了移动、提交顺序、订阅流量和 userinfo 两种绑定、导出文件回调、加入和删除。路由页走了移动、文档行、删除、撤销、清空和提交。分组设置和路由设置把选择档案的结果交回去；路由设置还交回了应用列表，并点了删除。
- 主界面走了已连接时的 FAB、统计栏、最近任务隐藏、选择器和流量回调、底栏和代理应用和模式的偏好变化、抽屉按键、返回、`sn://subscription` 和 `socks://` 导入。关于页走了更新检查（有更新、预览、同版本、坏 JSON）和忽略电池优化的取消。设置页走了绕过列表摘要、规则来源、hev-tun、Clash API、隐藏最近任务、本地代理端口对话框、清缓存，以及最后的夜间主题、语言和主题色。
- 备份页点了导出和分享，导出回调写进注册过的输出流，也传了一次打不开的 URI。`isRestoreInProgress` 为 true 时拆掉 fragment，走到取消恢复。
- `PluginManager` 在 `applicationInfo == null` 时初始化 hysteria，内置 so 路径抛异常，盖住第 57–58 行。只装一个指向缺失 so 的外部插件时，`check(canExecute())` 失败，盖住第 65–70 行。布尔 metadata 单独再装上，扫描时 `loadString` 抛错，盖住第 42–44 行。行已经 49/49。指令还有 285/299。
- `LocalResolverImpl` 在 API 29 上用 `ShadowDnsResolver` 把 `DnsResolver` 的回调拉回调用线程，四种结果各走一遍：成功、非 0 rcode、`ErrnoException`、其它错误。API 28 走 `InetAddress`。空白域名让 `ShadowResolvingNetwork.getAllByName` 抛 `UnknownHostException`，普通域名仍返回 `127.0.0.1`。剩下的是回调内部再抛、以及 `getAllByName` 返回 null 的那一支。
- `GuardedProcessPool` 第 31–32 行和第 106–107 行仍未覆盖。没有加宽生产 catch。`BaseService` 的 `exit` 仍只在 `restartProcessOverride == null` 时走到，测试里它不是 null。
- 没有把 `GroupUpdater` 第 109 行或 `ProxyEntity` 第 250、543 行改成可达。`libcore` Go 仍无 JaCoCo 入口。

## 第十三批（快捷方式、服务回调、备份导入）

97 个 JVM 测试，0 失败，0 跳过（本批新增 4 个 Robolectric 测试）。两次 `SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport :app:compileOssReleaseKotlin --offline` 都通过。`syncFirstPartyClasses` 复制 1031 个 class 文件。分母相对第十二批没有变：行 16338，指令 113365，分支 9564，方法 3919，类 780。生产代码没有改。`compileOssReleaseKotlin --offline` 通过。

**验收基线是 Test 在 `5f41659` 的干净复跑：行 14537/16338（88.98%），指令 98732/113365，分支 6622/9564。** 97 个测试，0 失败。相对 Test 验收的第十二批 14224/16338，行 **+313**。方法和类 Test 没有另报，下表这两行仍用本仓库第一次 XML。不按 ±2 收。不要把第十三批写成 14539 或 14540。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第十二批验收 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 14537 | 1801 | 16338 | **88.98%** | +313 行；分母不变 |
| 指令（INSTRUCTION） | 98732 | 14633 | 113365 | **87.09%** | +2246（相对 Test 的 96486） |
| 分支（BRANCH） | 6622 | 2942 | 9564 | **69.24%** | +168（相对 Test 的 6454） |
| 方法（METHOD） | 3270 | 649 | 3919 | **83.44%** | 本仓库 XML；Test 未另报 |
| 类（CLASS） | 723 | 57 | 780 | **92.69%** | 本仓库 XML；Test 未另报 |

第一次本仓库干净 XML：行 **14539/16338（88.99%）**，指令 **98750/113365（87.11%）**，分支 **6627/9564（69.29%）**。HTML 页脚：指令 14,615 of 113,365，分支 2,937 of 9,564，行 1,799 of 16,338（覆盖 = 16338 − 1799 = 14539）。方法 3270/3919，类 723/780。第二次：行 **14540/16338（88.99%）**，指令 **98749/113365（87.11%）**，分支 **6623/9564（69.25%）**。HTML 页脚：指令 14,616 of 113,365，分支 2,941 of 9,564，行 1,798 of 16,338（覆盖 = 16338 − 1798 = 14540）。方法 3270/3919，类 723/780。Test 的行数比第一次少 2，指令少 18，分支少 5。第二次行数比 Test 多 3。

两次本仓库 XML 只动了 `bg.proto` 里的 `TrafficLooper.kt`。第一次 180/186，指令 1330，分支 76/112。第二次 181/186，指令 1329，分支 72/112。差 1 行、1 条指令、4 个分支。那一行以前就会单独翻。`ConfigurationFragment.onRemoved` 这次两次都在 1388/1587 里，没有再把合计拉开。`GuardedProcessPool` 两次都是 75/78。`PluginManager` 两次都是 49/49。`ServiceNotification` 两次都是 108/128。验收用 Test 的 14537/16338，不把 14539 收成验收。

和第十二批第二次本仓库 XML（行 14224 那份，行数和 Test 相同，指令 96464、分支 6447 不是 Test 的页脚）比，行数变了的包是：

| 源文件 | 第十二批第二次 | 第十三批第一次 |
| --- | ---: | ---: |
| `ui` 包 | 4424/5183 | **4531/5183**（+107） |
| `io.nekohasekai.sagernet` | 133/175 | **170/175**（+37） |
| `bg` 包 | 681/816 | **718/816**（+37） |
| `group` 包 | 780/818 | **816/818**（+36） |
| `utils` 包 | 313/407 | **343/407**（+30） |
| `moe.matsuri.nb4a` | 326/374 | **366/374**（+40） |
| `ui.profile` 包 | 1058/1272 | **1076/1272**（+18） |
| `nb4a.net` | 73/83 | **79/83**（+6） |
| `bg.proto` | 404/419 | **407/419**（+3；第二次 408，因 `TrafficLooper`） |
| `aidl` | 17/19 | **18/19**（+1） |
| `ui/GroupFragment.kt` | 266/322 | **300/322** |
| `ui/RouteFragment.kt` | 153/194 | **190/194** |
| `ui/ConfigurationFragment.kt` | 1372/1587 | **1388/1587** |
| `ui/profile/ProfileSettingsActivity.kt` | 178/233 | **196/233** |
| `ui/MainActivity.kt` | 280/323 | **287/323** |
| `ui/RouteSettingsActivity.kt` | 170/228 | **177/228** |
| `ui/BackupFragment.kt` | 456/506 | **462/506** |
| `bg/SagerConnection.kt` | 52/99 | **85/99** |
| `bg/BaseService.kt` | 195/251 | **198/251** |
| `bg/GuardedProcessPool.kt` | 74/78 | **75/78** |
| `utils/DefaultNetworkListener.kt` | 33/76 | **63/76** |
| `group/GroupInterfaceAdapter.kt` | 15/51 | **51/51** |
| `QuickToggleShortcut.kt` | 0/36 | **35/36** |
| `nb4a/NativeInterface.kt` | 1/43 | **41/43** |
| `nb4a/net/LocalResolverImpl.kt` | 73/83 | **79/83** |

`ui` 的 +107 就是分组列表 +34、路由列表 +37、配置页 +16、主界面 +7、路由设置 +7、备份 +6。`group` 的 +36 就是 `GroupInterfaceAdapter` 从 15 到 51。`utils` 的 +30 就是 `DefaultNetworkListener`。`nb4a` 的 +40 就是 `NativeInterface`。根包的 +37 里，35 行是 `QuickToggleShortcut`。

### 本批的做法

- `QuickToggleShortcut` 走了创建快捷方式、已连接时停止、换成另一条档案再 reload、`Stopping` 时直接结束，以及 `Stopped` 时启动。`SagerConnection.serviceClass` 在非法 `serviceMode` 上抛了 `UnknownError`。
- `SagerConnection` 的回调 Stub 在监听器为空和不为空时都调用过。`updateConnectionId`、`onServiceDisconnected`、`binderDied`（含 `restartingApp`）和没有活动绑定时的 `disconnect` 都走过。`listenForDeath` 为 true。
- `NativeInterface` 走了 `protect`、空的 `vpnService`、`openTun`、`useProcFS`（API 29 为 false，API 28 为 true）、`findConnectionOwner`、uid 和包名互查、`wifiState`、`useOfficialAssets`，以及 `selector_OnProxySelected` 的非 `proxy` 标签。`proxy` 标签在 `buildConfigTmp` 成功时会进选择器。
- `DefaultNetworkListener` 喂了 `onAvailable`、`onCapabilitiesChanged`、`onLost`，并在 `fallback` 为 true 时调用 `get()`。`LocalResolverImpl` 的回调里，`success` 和 `errnoCode` 各抛过一次。桩上的 `ExchangeContext.successFailure` / `errnoFailure` 默认是 null，这时方法和以前一样是空操作。这两个字段只在单元测试用的 `libcore` 桩上，不进 APK，设备上的 gomobile 类没有它们。
- `BaseService` 的广播接收器走了关机、空闲模式开和关、重置上游连接，以及未知 action。`reload` 在 `Stopped`、`Connecting` 和 `Idle` 上各走一次。`urlTest` 在核心没启动时走了 `error`。`registerCallback` 碰到了重启 id。`GuardedProcessPool` 在 API 29 上关过一个 `sleep`，行从 74 到 75。第 31–32 行的 `Class.forName` 仍在探针前面，没有加宽 catch。
- 配置页在未分组档案数为 0 时直接调用了 `GroupPagerAdapter.onRemoved`，也调用了列表适配器的 `onRemoved`。holder 走了点击、状态、删除、分享菜单和排序菜单。分组页和路由页走了新建、更新、重置和触摸回调。档案设置走了应用、自定义 JSON、快捷方式、移动和删除。路由设置带了包名 extra。
- 备份页用 `doBackup` 的字节走了 `finishImport`，以及不是备份文件、坏 JSON、zip 和空 zip 的 `startImport`。确定导入时 `restartProcessOverride` 是计数，不是 `exit`。

## 第十四批（编辑器、快捷方式、备份错误）

102 个 JVM 测试，0 失败，0 跳过（本批新增 5 个 Robolectric 测试）。两次 `SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport :app:compileOssReleaseKotlin --offline` 都通过。`syncFirstPartyClasses` 复制 1031 个 class 文件。分母没有变：行 16338，指令 113365，分支 9564，方法 3919，类 780。生产代码没有改。`libcore` 测试桩多了 `urlTestFailure`，不进 APK。`compileOssReleaseKotlin --offline` 通过。

上一批验收基线是 Test 在 `5f41659` 的 **14537/16338（88.98%）**，指令 **98732/113365**，分支 **6622/9564**。97 个测试，0 失败。

**验收基线是 Test 在 `26c78d6` 的干净复跑：行 14820/16338（90.71%），指令 100473/113365，分支 6775/9564。** 102 个测试，0 失败。相对 Test 验收的第十三批 14537/16338，行 **+283**。方法和类 Test 没有另报，下表这两行仍用本仓库 XML。不按 ±2 收。不要把第十四批写成 14819 或 14821。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第十三批验收 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 14820 | 1518 | 16338 | **90.71%** | +283 行；分母不变 |
| 指令（INSTRUCTION） | 100473 | 12892 | 113365 | **88.63%** | +1741（相对 Test 的 98732） |
| 分支（BRANCH） | 6775 | 2789 | 9564 | **70.84%** | +153（相对 Test 的 6622） |
| 方法（METHOD） | 3344 | 575 | 3919 | **85.33%** | 本仓库 XML；Test 未另报 |
| 类（CLASS） | 736 | 44 | 780 | **94.36%** | 本仓库 XML；Test 未另报 |

行数较低的一次 XML：行 **14819/16338（90.70%）**，指令 **100496/113365（88.65%）**，分支 **6777/9564（70.86%）**。HTML 页脚：指令 12,869 of 113,365，分支 2,787 of 9,564，复杂度 3,022 of 8,899，行 1,519 of 16,338（覆盖 = 16338 − 1519 = 14819），方法 575 of 3,919，类 44 of 780。方法 3344/3919，类 736/780。相对 Test 的 14537，行 **+282**，指令 **+1764**，分支 **+155**。

另一次 XML：行 **14821/16338（90.71%）**，指令 **100474/113365（88.63%）**，分支 **6775/9564（70.84%）**。未覆盖行 1517，指令 12891，分支 2789。方法 3344/3919，类 736/780。那次 HTML 被下一次 `:app:clean` 清掉，页脚以 XML 为准。相对 Test 的 14537，行 **+284**，指令 **+1742**（100474 − 98732），分支 **+153**（6775 − 6622）。

Test 的 14820 在两次本仓库行数之间：比 14819 多 1，比 14821 少 1。指令 100473 比行数较高的那次少 1，比行数较低的那次少 23。分支 6775 和行数较高的那次相同，比行数较低的那次少 2。验收用 Test 的 14820/16338。

两次差 2 行。指令和分支方向相反：行数较低的一次指令多 22、分支多 2。只动了三个文件：

| 源文件 | 行数较高的一次 | 行数较低的一次 |
| --- | --- | --- |
| `GuardedProcessPool.kt` | 75/78，指令 669，分支 16/24。第 44 行盖住 | 74/78，指令 686，分支 16/24。第 44 行未盖住 |
| `ConfigurationFragment.kt` | 1399/1587，指令 8874，分支 730/1062。第 209–210 行盖住 | 1397/1587，指令 8871，分支 730/1062。第 209–210 行未盖住 |
| `MainActivity.kt` | 308/323，指令 1816，分支 143/191。第 526 行盖住，第 178–179 行未盖住 | 309/323，指令 1824，分支 145/191。第 178–179 行盖住，第 526 行未盖住 |

`bg` 包从 767/816、指令 4983、分支 295/398 到 766/816、指令 5000、分支 295/398。`ui` 包从 4659/5183、指令 28648、分支 1718/2568 到 4658/5183、指令 28653、分支 1720/2568。方法和类两次相同。`GuardedProcessPool` 第 31–32 行和第 107 行两次都没盖住。第 44 行是 `catch (_: IOException)`，会单独翻。没有加宽生产 catch。

和第十三批第一次本仓库 XML（行 14539、指令 98750、分支 6627，那份比 Test 的 14537 多 2 行）比，行数变了的包合计 **+280**。较低行页脚相对 Test 的 14537 是 **+282**。

| 包或源文件 | 第十三批第一次 | 第十四批行数较低的一次 |
| --- | ---: | ---: |
| `ui` 包 | 4531/5183 | **4658/5183**（+127） |
| `bg` 包 | 718/816 | **766/816**（+48） |
| `ui.profile` 包 | 1076/1272 | **1123/1272**（+47） |
| `ktx` 包 | 463/561 | **492/561**（+29） |
| `widget` 包 | 332/518 | **354/518**（+22） |
| `utils` 包 | 343/407 | **347/407**（+4） |
| `moe.matsuri.nb4a.ui` | 188/307 | **190/307**（+2） |
| `moe.matsuri.nb4a` | 366/374 | **367/374**（+1） |
| `ktx/Kryos.kt` | 2/31 | **31/31** |
| `ui/BackupFragment.kt` | 462/506 | **492/506** |
| `bg/BaseService.kt` | 198/251 | **226/251** |
| `ui/MainActivity.kt` | 287/323 | **309/323**（较高的一次 308/323） |
| `widget/ReselectableSpinner.kt` | 0/19 | **19/19** |
| `ui/GroupFragment.kt` | 300/322 | **316/322** |
| `ui/GroupSettingsActivity.kt` | 279/316 | **295/316** |
| `ui/QuickEnableShortcut.kt` | 0/16 | **15/16** |
| `ui/QuickDisableShortcut.kt` | 0/16 | **15/16** |
| `ui/profile/ConfigEditActivity.kt` | 62/106 | **97/106** |
| `ui/profile/ProfileSettingsActivity.kt` | 196/233 | **208/233** |
| `bg/SagerConnection.kt` | 85/99 | **97/99** |
| `ui/ConfigurationFragment.kt` | 1388/1587 | **1397/1587**（较高的一次 1399/1587） |
| `bg/ServiceNotification.kt` | 108/128 | **116/128** |
| `utils/DefaultNetworkListener.kt` | 63/76 | **67/76** |
| `ui/RouteSettingsActivity.kt` | 177/228 | **181/228** |
| `widget/StatsBar.kt` | 152/197 | **155/197** |
| `nb4a/ui/ExtendedKeyboard.kt` | 29/34 | **31/34** |
| `bg/ProxyService.kt` | 10/11 | **11/11** |
| `nb4a/Protocols.kt` | 17/20 | **18/20** |
| `bg/GuardedProcessPool.kt` | 75/78 | **74/78**（较高的一次仍是 75/78） |

`VpnService.kt` 和 `ProxyInstance.kt` 各多 3 条指令、1 个方法，行数没变。`AboutFragment.kt` 仍是 154/190。`PluginManager` 仍是 49/49。`GroupInterfaceAdapter` 仍是 51/51。

`ui` 的 +127 里，备份 +30、主界面 +22、分组列表 +16、分组设置 +16、两个快捷方式各 +15、配置页 +9。`ui.profile` 的 +47 是配置编辑 +35、档案设置 +12。`bg` 的 +48 是 `BaseService` +28、`SagerConnection` +12、`ServiceNotification` +8、`ProxyService` +1，再减去 `GuardedProcessPool` 的 1 行。`ktx` 的 +29 就是 `Kryos`。`widget` 的 +22 是 `ReselectableSpinner` +19、`StatsBar` +3。

### 本批的做法

- `Kryos` 走了空列表、非空列表、空集合、非空集合，以及 `marshall` / `unmarshall`。`ReselectableSpinner` 走了再次选择同一项和空适配器。两份源文件行都满了。
- `QuickEnableShortcut` 在 `Stopped` 时启动，在已连接时直接结束。`QuickDisableShortcut` 在 `canStop` 时停止，在 `Stopped` 时直接结束。API 24 和 API 29 都跑过。各剩第 52 行的空 `stateChanged`。
- `SagerConnection` 用没有覆盖默认方法的回调走了速度、流量、选择器和 `missingPlugin`。`registerCallback` 抛 `RemoteException` 的本地 Stub 进了 `updateConnectionId` 和 `onServiceConnected` 的 catch。没绑定时 `disconnect` 碰到 `unbind` 的 `IllegalArgumentException`，`listenForDeath` 为 true 时 `unlinkToDeath` 碰到 `NoSuchElementException`。`binderDied` 在 `restartingApp` 为 false 且回调非空时进了第 144 行。
- `DefaultNetworkListener` 在最后一个监听器上 `stop`，对已经 `onAvailable` 的同一个网络 `onLost`。`fallback` 为 true 且没有活动网络时，`get()` 抛 `UnknownHostException`。API 23 的 `NetworkRequest` 分支仍到不了：请求在类初始化时就建好了。
- `GuardedProcessPool` 起了 `/bin/true` 再 `close`。第 31–32 行的 `Class.forName` 仍在探针前面。第 107 行两次都没盖住。第 44 行只在行数较高的一次盖住。
- 选择器组里 `ProxyInstance.init()` 之后 `reload()` 调了 `selectOutbound`。`Libcore.urlTestFailure` 抛过一次再清掉。字段为 null 时 `urlTest` 仍返回 0。这个字段只在测试桩上。`preInit` 的链路名从 `wlan0` 换成 `rmnet0`。
- `ServiceNotification` 走了带分组名的标题、`postNotificationTitle`，以及亮屏、灭屏和未连接时的 `onReceive`。API 24 的 `destroy` 走的是 `>= N` 那一支。`StatsBar` 用主界面上的那一条走了外部滚动、`testConnection` 和隐藏。行只从 152 到 155。
- `ConfigEditActivity` 走了插入、撤销、重做、制表，空白和非法 JSON 的 `formatText`，未保存对话框的三个按钮，以及键盘和窗口 inset。`ExtendedKeyboard` 在 `submitList` 之后点过一项。`ProfileSettingsActivity` 走了未保存的保存和放弃、删除确认，以及自定义 JSON 的两个结果回调。`GroupSettingsActivity` 走了剪贴板导入、前后置代理的档案选择和缺 id、换订阅链接时清掉 `subscriptionUserinfo`。`RouteSettingsActivity` 走了未保存的放弃、删除确认，以及规则已经不在库里时的 `saveAndExit`。
- `BackupFragment` 在每个组的 `SubscriptionBean` 上先 `initializeDefaultValues()`，再 `doBackup`。导出写成功和 `IOException` 都走过。本地 `TinyHttp` 走了 PROPFIND 404、PUT 500、列出的备份 GET 500，以及条目不是 JSON 的 zip。`webdavServer = "http://%"` 进了 URL 解析失败。游标显示名 `from-cursor.json` 走了查询路径。坏 base64 的 JSON 进了 `finishImport` 的 `onFailure`。
- `MainActivity` 走了空 id 的 `displayFragmentWithId`、`onPostResume`、隐藏最近任务、方向键、已连接时的 `urlTest`、`getState` 抛 `RemoteException`、VPN 权限 snackbar、`PROXY_APPS` 的 reload、`SERVICE_MODE` 的 `onBinderDied`，以及坏的档案 URI 和订阅链接。API 33 的 `onCreate` 请求了 `POST_NOTIFICATIONS`。第 178–179 行和第 526 行两次各盖住一边。
- 配置页在 `canStop` 时点了全局模式的 snackbar，打开再关掉测速对话框。双列菜单用拆下来的 holder 绑定，没有对已有列表调用 `switchLayoutMode()`。分组页把较高的项移到较低的项上，再走导出失败。关于页点过列表并跑了 `checkUpdate`，行数仍是 154/190。

## 第十五批（设置控件、链式档案、服务错误）

108 个 JVM 测试，0 失败，0 跳过（本批新增 6 个 Robolectric 测试）。两次 `SKIP_HEV=1 ./gradlew :app:clean :app:jacocoFirstPartyReport :app:compileOssReleaseKotlin --offline` 都通过。`syncFirstPartyClasses` 复制 1031 个 class 文件。分母没有变：行 16338，指令 113365，分支 9564，方法 3919，类 780。生产代码没有改。`libcore` 测试桩多了 `stunResult`，不进 APK。`compileOssReleaseKotlin --offline` 通过。

上一批验收基线是 Test 在 `26c78d6` 的 **14820/16338（90.71%）**，指令 **100473/113365**，分支 **6775/9564**。102 个测试，0 失败。不要把第十四批写成 14819 或 14821。

**本批验收基线是 Test 在 `c072a61` 的 15158/16338（92.78%），指令 102300/113365（90.24%），分支 6917/9564（72.32%）。** 108 个测试，0 失败。相对 Test 的 14820，行 **+338**，指令 **+1827**，分支 **+142**。未覆盖按这个页脚算是行 1180、指令 11065、分支 2647。方法 3401/3919、类 747/780 仍是本仓库 XML，Test 没有另报。不要把第十五批写成 15152 或 15154。

本仓库两次干净跑是行 **15152** 和 **15154**。Test 比 15152 高 6 行，比 15154 高 4 行，落在这两次的外面。这和先前 ±2 / ±3 的方向相反：先前 Test 落在两次之间，或比较低的一次再少几行。两次只动了下面三个文件，并集是 15157 行。Test 的 15158 比并集多 1 行，这一行不在本仓库的文件差里。指令 Test 比 15152 多 15（102300 − 102285），比 15154 多 29（102300 − 102271）。分支两次本仓库都是 6915，Test 是 6917。下表较低的行页脚仍是本仓库记录，不是 Test 的页脚。

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 | 相对第十四批验收 |
| --- | ---: | ---: | ---: | ---: | --- |
| 行（LINE） | 15152 | 1186 | 16338 | **92.74%** | +332 行；分母不变 |
| 指令（INSTRUCTION） | 102285 | 11080 | 113365 | **90.23%** | +1812（相对 Test 的 100473） |
| 分支（BRANCH） | 6915 | 2649 | 9564 | **72.30%** | +140（相对 Test 的 6775） |
| 方法（METHOD） | 3401 | 518 | 3919 | **86.78%** | 本仓库 XML；Test 未另报 |
| 类（CLASS） | 747 | 33 | 780 | **95.77%** | 本仓库 XML；Test 未另报 |

行数较低的一次 XML：行 **15152/16338（92.74%）**，指令 **102285/113365（90.23%）**，分支 **6915/9564（72.30%）**。HTML 页脚：指令 11,080 of 113,365，分支 2,649 of 9,564，复杂度 2,894 of 8,899，行 1,186 of 16,338（覆盖 = 16338 − 1186 = 15152），方法 518 of 3,919，类 33 of 780。方法 3401/3919，类 747/780。相对 Test 的 14820，行 **+332**，指令 **+1812**，分支 **+140**。相对第十四批行数较低的本仓库 XML（14819），行 **+333**，指令 **+1789**（102285 − 100496），分支 **+138**（6915 − 6777）。

另一次 XML：行 **15154/16338（92.75%）**，指令 **102271/113365（90.21%）**，分支 **6915/9564（72.30%）**。未覆盖行 1184，指令 11094，分支 2649。方法 3401/3919，类 747/780。那次 HTML：指令 11,094 of 113,365，分支 2,649 of 9,564，复杂度 2,894 of 8,899，行 1,184 of 16,338（覆盖 = 15154），方法 518 of 3,919，类 33 of 780。相对 Test 的 14820，行 **+334**，指令 **+1798**，分支 **+140**。

两次差 2 行。指令方向相反：行数较低的一次指令多 14。分支相同。只动了三个文件：

| 源文件 | 行数较高的一次 | 行数较低的一次 |
| --- | ---: | ---: |
| `ConfigurationFragment.kt` | 1402/1587，指令 8881 | 1397/1587，指令 8858 |
| `GuardedProcessPool.kt` | 74/78，指令 666 | 75/78，指令 687 |
| `ProfileSettingsActivity.kt` | 217/233，指令 1276 | 219/233，指令 1292 |

`ConfigurationFragment` 多出来的 5 行是全局模式重载里的第 875–876、878–879、886 行，只在行数较高的一次盖住。第 887 行在行数较低的一次仍是部分覆盖。`GuardedProcessPool` 第 106 行只在行数较低的一次盖住；第 61 行从部分覆盖变成整行覆盖，所以那次指令更高。第 31–32 行和第 107 行两次都没盖住。第 44 行这次两次都盖住。`ProfileSettingsActivity` 第 56–57 行是未保存对话框的确定按钮，只在行数较低的一次盖住。

和第十四批行数较低的本仓库 XML（行 14819、指令 100496、分支 6777，那份比 Test 的 14820 少 1 行）比，行数变了的包合计 **+333**。较低行页脚相对 Test 的 14820 是 **+332**。

| 包或源文件 | 第十四批行数较低的一次 | 第十五批行数较低的一次 |
| --- | ---: | ---: |
| `widget` | 354/518 | **462/518**（+108） |
| `ui` | 4658/5183 | **4739/5183**（+81） |
| `ui.profile` | 1123/1272 | **1184/1272**（+61） |
| `nb4a.ui` | 190/307 | **250/307**（+60） |
| `bg` | 766/816 | **785/816**（+19） |
| `nb4a.utils` | 291/365 | **294/365**（+3） |
| `fmt.internal` | 23/33 | **24/33**（+1） |
| `StatsBar.kt` | 155/197 | **187/197** |
| `StandardV2RaySettingsActivity.kt` | 138/172 | **171/172** |
| `StunActivity.kt` | 12/40 | **40/40** |
| `ColorPickerPreference.kt` | 32/61 | **60/61** |
| `UrlTestPreference.kt` | 11/38 | **35/38** |
| `LinkOrContentPreference.kt` | 10/34 | **34/34** |
| `OutboundPreference.kt` | 13/40 | **33/40** |
| `LogcatFragment.kt` | 30/55 | **50/55** |
| `BaseService.kt` | 226/251 | **244/251** |
| `WebviewFragment.kt` | 14/35 | **31/35** |
| `ChainSettingsActivity.kt` | 129/154 | **146/154** |
| `AutoCollapseTextView.kt` | 0/17 | **15/17** |
| `RouteSettingsActivity.kt` | 181/228 | **195/228** |
| `ProfileSettingsActivity.kt` | 208/233 | **219/233**（另一次 217/233） |
| `ServiceButton.kt` | 68/85 | **77/85** |
| `QuickEnableShortcut.kt` / `QuickDisableShortcut.kt` | 15/16 | **16/16** |
| `GuardedProcessPool.kt` | 74/78 | **75/78**（另一次 74/78） |
| `ConfigurationFragment.kt` | 1397/1587 | **1397/1587**（另一次 1402/1587） |
| `AboutFragment.kt` | 154/190 | **154/190** |
| `Utils.kt` | 131/158 | **131/158**（指令 866→874，分支 35→40） |
| `MainActivity.kt` | 309/323 | **309/323** |

- `StunActivity` 走了失败对话框的确定和取消，以及 `stunResult` 成功时的结果文本。`Libcore.stunResult` 为 null 时仍返回空的失败结果。这个字段只在测试桩上。
- `QuickEnableShortcut` 和 `QuickDisableShortcut` 在 `idle` 之前调用了空的 `stateChanged`，两份快捷方式都到 16/16。
- `BaseService` 的启动协程盖住了 `UnknownHostException`、`PluginNotFoundException`，以及类名以 `proxyerror` 结尾和其它 `Throwable`。`CancellationException` 第 398 行和 `onFatal` 第 390–392 行仍缺。`exit` 第 122 行没有走。`restartProcessOverride` 为 null 时仍退出进程。
- `GuardedProcessPool` 起了 `/bin/true` 并等过 1200 毫秒，没有先 `close`。第 106 行只在行数较低的一次盖住。第 107 行两次都没盖住。第 31–32 行仍在探针前面。没有加宽 catch。
- `ProfileSettingsActivity` 走了未保存的放弃、返回、删除确认，以及缺 id 的 `saveAndExit`。确定按钮的第 56–57 行会单独翻。密码摘要第 371、375–379 行和 `createPreferences` 的 catch 仍缺。
- `ChainSettingsActivity` 的滑动和拖动方向盖住了。`onMove` 第 93–97 行和 `onSwiped` 第 102–103 行仍缺：列表上的 holder 没有绑上，回调没有跑到。递归的 `testProfileContains` 第 203 行仍缺。
- `RouteSettingsActivity` 从 181 到 195。删除对话框的第 206–214 行和第 360–363 行仍缺。
- `StatsBar` 从 155 到 187。`HideAfterStart` 的第 236–243 行和第 114 行仍缺。
- `AboutFragment` 仍是 **154/190**。反射拿到的关于列表没有把插件行、电池优化和第 263–265 行的更新按钮算进去。
- `ConfigurationFragment` 较低行页脚仍是 **1397/1587**。剪贴板、新建 socks 和扫码跑过。全局模式重载那 5 行只在另一次盖住。
- `Utils.kt` 行数仍是 131/158。`listenForPackageChanges` 的函数体盖住了，签名那一行没有。`forEachTry` 是 inline，定义处的行仍缺。API 24 的 `parseNumericAddress` 反射分支第 160–162 行仍缺。
- `AutoCollapseTextView` 剩第 30 和 36 行的 catch。`ColorPickerPreference` 剩构造参数那一行。`LinkOrContentPreference` 34/34。`StandardV2RaySettingsActivity` 剩第 23 行。

## 第十六批

上一批验收基线是 Test 在 `c072a61` 的 **15158/16338（92.78%）**，指令 **102300/113365（90.24%）**，分支 **6917/9564（72.32%）**。108 个测试，0 失败。不要把第十五批写成 15152 或 15154。本仓库那次的跨度是 15152–15154，Test 比 15152 高 6 行。本批的页脚还没有干净跑，下面的热点仍是第十五批行数较低的那次本仓库 XML。`ConfigurationFragment` 全局模式重载、`GuardedProcessPool` 第 106 与第 107 行、`ProfileSettingsActivity` 未保存对话框的确定按钮，本批改成每次都会走到，避免再单独翻。

1. **`ConfigurationFragment` 仍缺 190 行**（1397/1587；另一次 1402/1587）。
2. **`StandardV2RayBean` 132/186**，`V2RayFmt` 529/569，`NGUtil` 61/95。`AboutFragment` 仍是 154/190。
3. **`RouteSettingsActivity` 195/228**，`Utils.kt` 131/158，`GroupSettingsActivity` 295/316，`CrashHandler` 69/90。
4. **`AssetsActivity` 227/245**，`WebDAVSettingsActivity` 119/136，`VpnRequestActivity` 16/32，`Preferences.kt` 69/85，`BackupFragment` 492/506，`MainActivity` 309/323，`ProfileSettingsActivity` 219/233（另一次 217/233）。
5. **`StatsBar` 187/197** 仍缺 `HideAfterStart`。`ServiceNotification` 116/128，`DefaultNetworkListener` 67/76，`ChainSettingsActivity` 146/154 仍缺 `onMove` / `onSwiped`。
6. **`BaseService` 244/251**。剩下第 122 行的 `exit`、第 390–392 行的 `onFatal`，以及第 398 行的 `CancellationException`。
7. **`GuardedProcessPool` 75/78**（另一次 74/78）。第 31–32 行在桌面 JVM 上探针到不了，第 107 行两次都没盖住。不要加宽 catch。
8. **`GroupUpdater` 85/86 第 109 行**、`ProxyEntity` 364/366 第 250 和 543 行仍不可达。不要为了覆盖率改这些。
9. **`libcore` Go** 约 1927 行，没有 JaCoCo 入口，也没有 `_test.go`。`replace` 指向的 `../../sing-box` 和 `../../libneko` 不在本仓库。

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
- 没有改 `VpnService.establishTun` 的组网逻辑。地址、路由、DNS 和 `establish` 能跑完，是因为测试影子替换了 `VpnService.Builder` 和 `Os.inet_pton`。这两个影子加上 `ShadowResolvingNetwork` 挂在第七到第十六批的 Robolectric 测试上。空白域名会让这个影子抛 `UnknownHostException`，其它域名仍返回 `127.0.0.1`。`ShadowOsAddress.kill` 在第九到第十六批替换 `Os.kill`，避免桌面 JVM 向真实 pid 发信号。`ShadowProcessPhoenix` 在第十到第十六批，并且要 `instrumentedPackages` 含 `com.jakewharton.processphoenix`。`ShadowDnsResolver` 在第十二到第十六批，把 `DnsResolver` 的回调拉回调用线程。
- 没有把 `TrojanBean` 的 `sni` 分支调到 `StandardV2RayBean` 前面。那一行在现有类型层次上到不了，调换会改变 `security != "tls"` 时的赋值。
- 没有克隆 `../../sing-box` 和 `../../libneko`，没有把 Go 覆盖率并进 JaCoCo。
- 没有把 `PluginManager.initOverride`、`JavaUtil.lockProbe`、`PublicDatabase.kvPairDaoOverride`、`GuardedProcessPool.pidReader`、`BaseService.Binder.restartProcessOverride` 的生产默认值改掉，也没有把 `HevTunRuntime` 的 native hook 默认成空实现。这些口子为 null，或仍指向 `HevTunNative` 时，设备和以前一样。`Libcore.httpBody` / `httpHeader` 为空时，桩仍返回空正文且不写文件。`ExchangeContext.successFailure` / `errnoFailure` 为 null 时，桩上的 `success` 和 `errnoCode` 仍是空操作。`Libcore.urlTestFailure` 为 null 时，桩上的 `urlTest` 仍返回 0。`Libcore.stunResult` 为 null 时，桩上的 `stunTest` 仍返回空的失败结果。这些字段只在测试桩上。
- 本地导入的确定按钮会走到 `triggerFullRestart`。`restartProcessOverride` 为 null 时，那条仍 `Runtime.exit(0)`。测试里换成计数，所以第十到第十六批没有杀掉测试进程。WebDAV 恢复的确定按钮直接调用 `ProcessPhoenix.triggerRebirth`。
