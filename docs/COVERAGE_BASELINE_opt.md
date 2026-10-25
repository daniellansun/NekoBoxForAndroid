# 第一方代码覆盖率基线（分支 `opt`）

测量提交：`a4c5072040329a68cc139d8823d016f8aeba6094`（`opt` 顶端，`bump version`）。

数字来自 **ossDebug JVM 单元测试 + JaCoCo 0.8.12**，只统计本仓库手写的 app 类。第三方 Maven/JitPack 依赖、ViewBinding、Room 生成类、AIDL Stub、`libcore` gomobile 桩都不进入分子或分母。

## 结论

| 指标 | 覆盖 | 未覆盖 | 合计 | 比例 |
| --- | ---: | ---: | ---: | ---: |
| 行（LINE） | 0 | 16144 | 16144 | **0.00%** |
| 指令（INSTRUCTION） | 0 | 112159 | 112159 | **0.00%** |
| 分支（BRANCH） | 0 | 9512 | 9512 | **0.00%** |
| 方法（METHOD） | 0 | 3746 | 3746 | **0.00%** |
| 类（CLASS） | 0 | 771 | 771 | **0.00%** |

这是真实基线，不是空报告。JaCoCo 扫到 876 个第一方 `.class`（771 个 JaCoCo 类、207 个有可执行行的源文件）。当前唯一测试是 `CoverageHarnessTest.harnessDoesNotTouchProductCode`，它不断言产品行为，所以已覆盖计数为 0。

仓库里原先没有任何 `src/test` 或 `src/androidTest`。仪器测试（`connectedOssDebugAndroidTest`）需要设备和模拟器，本次没有跑。

`libcore/` 的 Go 覆盖率没有并进上表，原因见文末。第一方 Go 约 1927 行非空非注释代码，现有 `_test.go` 数量为 0。

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

## 分包表（全部为 0%）

行 / 指令 / 分支 / 方法 / 类的写法都是 `已覆盖/合计`。

| 包 | 行 | 指令 | 分支 | 方法 | 类 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `io.nekohasekai.sagernet.ui` | 0/5183 | 0/32782 | 0/2568 | 0/1072 | 0/250 |
| `io.nekohasekai.sagernet.ui.profile` | 0/1272 | 0/7943 | 0/339 | 0/255 | 0/42 |
| `io.nekohasekai.sagernet.database` | 0/961 | 0/11447 | 0/467 | 0/601 | 0/35 |
| `io.nekohasekai.sagernet.fmt.v2ray` | 0/939 | 0/6096 | 0/817 | 0/67 | 0/6 |
| `io.nekohasekai.sagernet.fmt` | 0/904 | 0/5975 | 0/637 | 0/94 | 0/12 |
| `io.nekohasekai.sagernet.bg` | 0/803 | 0/5516 | 0/392 | 0/216 | 0/46 |
| `io.nekohasekai.sagernet.group` | 0/783 | 0/6885 | 0/1011 | 0/44 | 0/18 |
| `io.nekohasekai.sagernet.widget` | 0/518 | 0/3095 | 0/315 | 0/140 | 0/26 |
| `io.nekohasekai.sagernet.ktx` | 0/511 | 0/4015 | 0/214 | 0/186 | 0/25 |
| `io.nekohasekai.sagernet.bg.proto` | 0/419 | 0/3149 | 0/206 | 0/101 | 0/17 |
| `io.nekohasekai.sagernet.utils` | 0/407 | 0/2752 | 0/303 | 0/91 | 0/22 |
| `moe.matsuri.nb4a` | 0/374 | 0/2703 | 0/384 | 0/206 | 0/152 |
| `moe.matsuri.nb4a.utils` | 0/356 | 0/2390 | 0/206 | 0/58 | 0/6 |
| `io.nekohasekai.sagernet.fmt.hysteria` | 0/321 | 0/2202 | 0/300 | 0/22 | 0/3 |
| `moe.matsuri.nb4a.ui` | 0/307 | 0/2032 | 0/72 | 0/101 | 0/18 |
| `io.nekohasekai.sagernet` | 0/175 | 0/918 | 0/74 | 0/62 | 0/8 |
| `io.nekohasekai.sagernet.database.preference` | 0/151 | 0/952 | 0/73 | 0/68 | 0/10 |
| `moe.matsuri.nb4a.proxy.anytls` | 0/145 | 0/930 | 0/92 | 0/17 | 0/4 |
| `io.nekohasekai.sagernet.fmt.trojan_go` | 0/144 | 0/1002 | 0/120 | 0/13 | 0/3 |
| `io.nekohasekai.sagernet.fmt.shadowsocks` | 0/135 | 0/868 | 0/82 | 0/14 | 0/3 |
| `io.nekohasekai.sagernet.fmt.tuic` | 0/125 | 0/776 | 0/96 | 0/13 | 0/3 |
| `io.nekohasekai.sagernet.fmt.snell` | 0/119 | 0/1023 | 0/178 | 0/15 | 0/4 |
| `io.nekohasekai.sagernet.fmt.naive` | 0/94 | 0/664 | 0/76 | 0/13 | 0/3 |
| `io.nekohasekai.sagernet.fmt.shadowsocksr` | 0/87 | 0/614 | 0/20 | 0/13 | 0/3 |
| `moe.matsuri.nb4a.net` | 0/83 | 0/346 | 0/28 | 0/11 | 0/4 |
| `io.nekohasekai.sagernet.fmt.juicity` | 0/81 | 0/561 | 0/70 | 0/14 | 0/3 |
| `io.nekohasekai.sagernet.fmt.socks` | 0/80 | 0/529 | 0/66 | 0/17 | 0/3 |
| `moe.matsuri.nb4a.proxy` | 0/62 | 0/376 | 0/30 | 0/27 | 0/2 |
| `io.nekohasekai.sagernet.fmt.wireguard` | 0/59 | 0/263 | 0/20 | 0/12 | 0/3 |
| `moe.matsuri.nb4a.proxy.shadowtls` | 0/56 | 0/339 | 0/4 | 0/15 | 0/4 |
| `io.nekohasekai.sagernet.fmt.mieru` | 0/54 | 0/309 | 0/12 | 0/10 | 0/3 |
| `moe.matsuri.nb4a.plugin` | 0/53 | 0/376 | 0/50 | 0/10 | 0/1 |
| `moe.matsuri.nb4a.proxy.config` | 0/51 | 0/252 | 0/30 | 0/18 | 0/3 |
| `io.nekohasekai.sagernet.fmt.ssh` | 0/51 | 0/222 | 0/30 | 0/10 | 0/3 |
| `io.nekohasekai.sagernet.plugin` | 0/46 | 0/283 | 0/32 | 0/12 | 0/3 |
| `moe.matsuri.nb4a.hevtun` | 0/45 | 0/368 | 0/10 | 0/6 | 0/2 |
| `io.nekohasekai.sagernet.fmt.http` | 0/42 | 0/282 | 0/32 | 0/11 | 0/3 |
| `io.nekohasekai.sagernet.fmt.internal` | 0/33 | 0/132 | 0/12 | 0/15 | 0/3 |
| `io.nekohasekai.sagernet.fmt.trojan` | 0/33 | 0/194 | 0/24 | 0/10 | 0/3 |
| `moe.matsuri.nb4a.proxy.neko` | 0/32 | 0/110 | 0/4 | 0/14 | 0/2 |
| `com.github.shadowsocks.plugin.fragment` | 0/21 | 0/181 | 0/6 | 0/14 | 0/2 |
| `io.nekohasekai.sagernet.aidl` | 0/19 | 0/253 | 0/2 | 0/31 | 0/3 |
| `io.nekohasekai.sagernet.fmt.gson` | 0/8 | 0/42 | 0/8 | 0/4 | 0/1 |
| `com.github.shadowsocks.plugin` | 0/2 | 0/12 | 0/0 | 0/3 | 0/1 |

## 未覆盖最多的源文件

全部文件覆盖率都是 0%。下表按 JaCoCo 未覆盖行数排序，表示缺口面积。

| 源文件 | 未覆盖行 |
| --- | ---: |
| `io.nekohasekai.sagernet.ui.ConfigurationFragment.kt` | 1587 |
| `io.nekohasekai.sagernet.fmt.ConfigBuilder.kt` | 698 |
| `io.nekohasekai.sagernet.group.RawUpdater.kt` | 646 |
| `io.nekohasekai.sagernet.fmt.v2ray.V2RayFmt.kt` | 569 |
| `io.nekohasekai.sagernet.ui.BackupFragment.kt` | 506 |
| `io.nekohasekai.sagernet.database.ProxyEntity.kt` | 365 |
| `io.nekohasekai.sagernet.ui.MainActivity.kt` | 323 |
| `io.nekohasekai.sagernet.ui.GroupFragment.kt` | 322 |
| `io.nekohasekai.sagernet.ui.GroupSettingsActivity.kt` | 316 |
| `io.nekohasekai.sagernet.ui.SettingsPreferenceFragment.kt` | 267 |
| `io.nekohasekai.sagernet.bg.BaseService.kt` | 246 |
| `io.nekohasekai.sagernet.ui.AssetsActivity.kt` | 245 |
| `io.nekohasekai.sagernet.fmt.hysteria.HysteriaFmt.kt` | 243 |
| `io.nekohasekai.sagernet.ui.profile.ProfileSettingsActivity.kt` | 233 |
| `io.nekohasekai.sagernet.ui.RouteSettingsActivity.kt` | 228 |

`ui` 与 `ui.profile` 合计 6455 行，是最大的包，但它们绑在 Fragment / Activity 上。先补协议解析，同样的测试投入能先把分支稠密的纯函数盖住。

## 建议的后续单测顺序

1. **分享链接与出站 JSON（JVM，优先）**。这些 `*Fmt.kt` 多数不直接 import Android，分支集中：
   - `fmt/v2ray/V2RayFmt.kt`（569 行，包内分支 817）
   - `fmt/v2ray/XhttpExtraConverter.kt`、`StandardV2RayBean.java`
   - `fmt/hysteria/HysteriaFmt.kt`（243 行）
   - `fmt/trojan_go/TrojanGoFmt.kt`、`fmt/shadowsocks/ShadowsocksFmt.kt`、`fmt/tuic/TuicFmt.kt`、`fmt/snell/SnellFmt.kt`
   - `moe.matsuri.nb4a.proxy.anytls.AnyTLSFmt.kt`、`shadowtls/ShadowTLSFmt.kt`
   - 其余：naive、shadowsocksr、juicity、socks、wireguard、http、trojan、ssh、mieru 的 `parse*` / `toUri` / `buildSingBoxOutbound*`
2. **纯函数工具**。`ktx/Formats.kt`（176 行）、`utils/Subnet.kt`、`utils/Commandline.kt`，以及 `moe.matsuri.nb4a.utils.Util` 里不触碰 `libcore` 的字符串与压缩函数。
3. **`SingBoxOptions` JSON 往返**。类很多（`moe.matsuri.nb4a` 有 152 个类），可执行行只有 374。用少量序列化样例覆盖字段模型，避免按内部类逐个写测试。
4. **订阅与配置构建**。`ConfigBuilder.kt`（698）、`RawUpdater.kt`（646，先测 `parseRaw`）、`GroupUpdater.kt`、`ProxyEntity.kt`（365）。它们碰到 `DataStore`、Android 和 `libcore` HTTP。把解析函数留在 JVM 测试里；Android 调用留到 Robolectric 或仪器测试。
5. **数据库与后台**。`database`（961 行，方法多）、`bg` / `bg.proto`。Room 可用内存库；`BaseService`、`VpnService` 需要 Android 运行时。
6. **UI**。`ui`、`ui.profile`、`moe.matsuri.nb4a.ui`、`widget`。用仪器测试或 Robolectric。当前 `testOptions.unitTests.isReturnDefaultValues = true` 只让 Android stub 返回默认值，不能代替界面测试。
7. **`libcore` Go**。见下一节。补上兄弟仓库后再跑 `go test -cover`。子包 `device`、`procfs` 已经能单独编译，适合先写测试。

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

- 没有升级依赖，没有改 release 签名。
- 没有跑仪器测试或打包 APK。
- 没有把 `libcore.aar` 或 JaCoCo HTML 提交进 git（`build/`、`app/libs/` 仍被忽略）。
