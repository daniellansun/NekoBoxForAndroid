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

`app/libs/libcore.aar`（`libcore/build.sh` 的 gomobile 产物，被 gitignore）存在时，编译使用该 AAR。不存在时，`compileOnly` / `testImplementation` 使用 `app/src/libcoreStub/java` 打出的桩 jar，**不打进 APK**，也 **不计入** 覆盖率。桩只提供 `libcore.*` 与 `go.Seq` 的空实现，让 JVM 测试能够通过类型检查。

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

## 分包表（第二批之后）

行 / 指令 / 分支 / 方法 / 类的写法都是 `已覆盖/合计`。`fmt` 合计从 904 变为 942，即 `ConfigSource.kt`。`ktx` 的 513 行来自第一批把反射查找改成 `by lazy`。

| 包 | 行 | 指令 | 分支 | 方法 | 类 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `io.nekohasekai.sagernet.ui` | 0/5183 | 0/32782 | 0/2568 | 0/1072 | 0/250 |
| `io.nekohasekai.sagernet.ui.profile` | 0/1272 | 0/7943 | 0/339 | 0/255 | 0/42 |
| `io.nekohasekai.sagernet.database` | 131/961 | 1088/11447 | 48/467 | 55/601 | 7/35 |
| `io.nekohasekai.sagernet.fmt` | 669/942 | 4173/6102 | 423/637 | 42/133 | 8/14 |
| `io.nekohasekai.sagernet.fmt.v2ray` | 836/939 | 5427/6096 | 546/817 | 64/67 | 6/6 |
| `io.nekohasekai.sagernet.bg` | 0/803 | 0/5516 | 0/392 | 0/216 | 0/46 |
| `io.nekohasekai.sagernet.group` | 490/783 | 3907/6885 | 524/1011 | 8/44 | 2/18 |
| `io.nekohasekai.sagernet.widget` | 0/518 | 0/3095 | 0/315 | 0/140 | 0/26 |
| `io.nekohasekai.sagernet.ktx` | 178/513 | 1385/4033 | 105/214 | 39/190 | 6/25 |
| `io.nekohasekai.sagernet.bg.proto` | 0/419 | 0/3149 | 0/206 | 0/101 | 0/17 |
| `io.nekohasekai.sagernet.utils` | 87/407 | 615/2752 | 85/303 | 15/91 | 4/22 |
| `moe.matsuri.nb4a` | 205/374 | 1546/2703 | 199/384 | 88/206 | 57/152 |
| `moe.matsuri.nb4a.utils` | 179/356 | 1344/2390 | 99/206 | 34/58 | 4/6 |
| `io.nekohasekai.sagernet.fmt.hysteria` | 309/321 | 2094/2202 | 244/300 | 19/22 | 3/3 |
| `moe.matsuri.nb4a.ui` | 0/307 | 0/2032 | 0/72 | 0/101 | 0/18 |
| `io.nekohasekai.sagernet` | 0/175 | 0/918 | 0/74 | 0/62 | 0/8 |
| `io.nekohasekai.sagernet.database.preference` | 0/151 | 0/952 | 0/73 | 0/68 | 0/10 |
| `moe.matsuri.nb4a.proxy.anytls` | 115/145 | 653/930 | 66/92 | 10/17 | 3/4 |
| `io.nekohasekai.sagernet.fmt.trojan_go` | 130/144 | 875/1002 | 75/120 | 11/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt.shadowsocks` | 130/135 | 794/868 | 62/82 | 12/14 | 3/3 |
| `io.nekohasekai.sagernet.fmt.tuic` | 121/125 | 736/776 | 75/96 | 10/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt.snell` | 113/119 | 924/1023 | 124/178 | 11/15 | 4/4 |
| `io.nekohasekai.sagernet.fmt.naive` | 91/94 | 638/664 | 60/76 | 11/13 | 3/3 |
| `io.nekohasekai.sagernet.fmt.shadowsocksr` | 85/87 | 589/614 | 16/20 | 11/13 | 3/3 |
| `moe.matsuri.nb4a.net` | 0/83 | 0/346 | 0/28 | 0/11 | 0/4 |
| `io.nekohasekai.sagernet.fmt.juicity` | 78/81 | 510/561 | 45/70 | 11/14 | 3/3 |
| `io.nekohasekai.sagernet.fmt.socks` | 69/80 | 483/529 | 48/66 | 13/17 | 3/3 |
| `moe.matsuri.nb4a.proxy` | 0/62 | 0/376 | 0/30 | 0/27 | 0/2 |
| `io.nekohasekai.sagernet.fmt.wireguard` | 54/59 | 250/263 | 18/20 | 9/12 | 3/3 |
| `moe.matsuri.nb4a.proxy.shadowtls` | 26/56 | 108/339 | 4/4 | 8/15 | 3/4 |
| `io.nekohasekai.sagernet.fmt.mieru` | 50/54 | 283/309 | 7/12 | 8/10 | 3/3 |
| `moe.matsuri.nb4a.plugin` | 0/53 | 0/376 | 0/50 | 0/10 | 0/1 |
| `moe.matsuri.nb4a.proxy.config` | 13/51 | 50/252 | 5/30 | 6/18 | 2/3 |
| `io.nekohasekai.sagernet.fmt.ssh` | 33/51 | 161/222 | 22/30 | 6/10 | 3/3 |
| `io.nekohasekai.sagernet.plugin` | 0/46 | 0/283 | 0/32 | 0/12 | 0/3 |
| `moe.matsuri.nb4a.hevtun` | 0/45 | 0/368 | 0/10 | 0/6 | 0/2 |
| `io.nekohasekai.sagernet.fmt.http` | 34/42 | 246/282 | 23/32 | 7/11 | 3/3 |
| `io.nekohasekai.sagernet.fmt.internal` | 15/33 | 55/132 | 5/12 | 7/15 | 3/3 |
| `io.nekohasekai.sagernet.fmt.trojan` | 23/33 | 132/194 | 13/24 | 8/10 | 3/3 |
| `moe.matsuri.nb4a.proxy.neko` | 0/32 | 0/110 | 0/4 | 0/14 | 0/2 |
| `com.github.shadowsocks.plugin.fragment` | 0/21 | 0/181 | 0/6 | 0/14 | 0/2 |
| `io.nekohasekai.sagernet.aidl` | 0/19 | 0/253 | 0/2 | 0/31 | 0/3 |
| `io.nekohasekai.sagernet.fmt.gson` | 0/8 | 0/42 | 0/8 | 0/4 | 0/1 |
| `com.github.shadowsocks.plugin` | 1/2 | 3/12 | 0/0 | 1/3 | 1/1 |

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

| 源文件 | 未覆盖行 | 已覆盖 |
| --- | ---: | ---: |
| `io.nekohasekai.sagernet.ui.ConfigurationFragment.kt` | 1587 | 0/1587 |
| `io.nekohasekai.sagernet.ui.BackupFragment.kt` | 506 | 0/506 |
| `io.nekohasekai.sagernet.ui.MainActivity.kt` | 323 | 0/323 |
| `io.nekohasekai.sagernet.ui.GroupFragment.kt` | 322 | 0/322 |
| `io.nekohasekai.sagernet.ui.GroupSettingsActivity.kt` | 316 | 0/316 |
| `io.nekohasekai.sagernet.database.ProxyEntity.kt` | 269 | 96/365 |
| `io.nekohasekai.sagernet.ui.SettingsPreferenceFragment.kt` | 267 | 0/267 |
| `io.nekohasekai.sagernet.bg.BaseService.kt` | 246 | 0/246 |
| `io.nekohasekai.sagernet.ui.AssetsActivity.kt` | 245 | 0/245 |
| `io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity.kt` | 233 | 0/233 |
| `io.nekohasekai.sagernet.ui.RouteSettingsActivity.kt` | 228 | 0/228 |
| `io.nekohasekai.sagernet.ui.AppManagerActivity.kt` | 228 | 0/228 |
| `io.nekohasekai.sagernet.database.DataStore.kt` | 206 | 0/206 |
| `io.nekohasekai.sagernet.widget.StatsBar.kt` | 197 | 0/197 |
| `io.nekohasekai.sagernet.group.RawUpdater.kt` | 159 | 487/646 |
| `io.nekohasekai.sagernet.ktx.Utils.kt` | 135 | 23/158 |
| `io.nekohasekai.sagernet.fmt.ConfigBuilder.kt` | 98 | 600/698 |

`ConfigBuilder.kt` 剩下的 98 行主要是：外部插件映射（`Plugins`）、按包名选 UID（`PackageCache` / `Toast`）、部分 DNS ruleset 分支、以及 `ConfigBean` JSON 解析失败的分支。`RawUpdater.kt` 剩下的 159 行里约 149 行是 `doUpdate`。

工具剩余：`JavaUtil.java` 56 行、`Formats.kt` 50 行、`KryoConverters.java` 41 行（各协议的 `*Deserialize`）、`Utils.kt` 135 行（其余多为 Android / 网络）。`SingBoxOptions.java` 还剩 89 行可执行代码，多半是还没被 `asMap` 碰到的内部类构造。`V2RayFmt.kt` 剩 41，`StandardV2RayBean.java` 剩 55，`HysteriaFmt.kt` 剩 4，`TrojanGoFmt.kt` 剩 12。`TuicFmt.kt` 与 `JuicityFmt.kt` 的可执行行已经盖满。

## 第三批

1. **`ConfigBuilder.kt` 剩下的 98 行**。外部插件、包名规则和 Toast 需要 Android / `PackageManager` 替身，不能再靠内存 `ConfigSource`。无效 `ConfigBean` JSON 可以在 JVM 上补一条失败路径。
2. **`RawUpdater.doUpdate`（约 149 行）**。会发 HTTP 并读 `DataStore`。`parseRaw` 本身只剩约 10 行。
3. **`SingBoxOptions` 内部类**。可执行行 82/171。继续用 `asMap` / 序列化样例盖构造函数，不必逐字段写。
4. **工具剩余**。`JavaUtil` 的进程名反射失败以外的分支、`Formats.kt` 各协议 `parse*` 的失败分支、`KryoConverters` 各类型反序列化、`Utils.kt` 里不碰 Android 的片段。
5. **数据库与后台**。`database` 现为 131/961，其中 `DataStore.kt` 仍是 0/206（一加载就开 Room）。`preference` 0/151，`bg` 0/803，`bg.proto` 0/419。`ProductionConfigSource`（`ConfigSource.kt` 2/38）同样只有在真的读 `DataStore` 时才会执行。
6. **UI**。`ui` 与 `ui.profile` 合计仍是 0/6455。用仪器测试或 Robolectric。`isReturnDefaultValues` 不能代替界面测试。
7. **`libcore` Go**。见下一节。补上兄弟仓库后再跑 `go test -cover`。`device`、`procfs` 已经能单独编译。

仍基本为 0：`fmt.gson`（0/8）、`nb4a.proxy`（0/62）、`nb4a.proxy.neko`（0/32）。`fmt.internal` 15/33，`nb4a.proxy.config` 13/51。

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

## 本次没有做的事

- 没有升级 app 依赖，没有改 release 签名。`org.json:json:20240303` 只挂在单元测试 classpath。
- 没有跑仪器测试或打包 APK。`assemble` 的 release 依赖没有改；`libcore.aar` 存在时仍走原来的 `compileOnly`。
- 没有把 `libcore.aar` 或 JaCoCo HTML 提交进 git（`build/`、`app/libs/` 仍被忽略）。
- 没有改 AnyTLS 的 fingerprint 赋值。测试按当前字节码断言 `utls.fingerprint == null`。
- 没有把 Room 放进单元测试。`ProductionConfigSource` 仍是生产默认值，getter 在调用时才读 `DataStore`。
- 没有改 WireGuard `mtu` 为空时 `clone()` 会 NPE 的现有行为。测试夹具写上 `MTU`。
- 没有调用 `RawUpdater.doUpdate`、`GroupUpdater.forceResolve`，也没有覆盖 UI。
