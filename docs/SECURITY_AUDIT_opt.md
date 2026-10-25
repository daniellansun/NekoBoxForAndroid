# `opt` 分支安全审计报告

审计日期：2026-10-25  
审计对象：`daniellansun/NekoBoxForAndroid` 分支 `opt`  
提交：`a4c5072040329a68cc139d8823d016f8aeba6094`（2026-08-22，`bump version`，作者 starifly）  
方式：只读源码与供应链核对。未改应用运行时代码，未对 keystore 做离线破解，未把本树编译成 APK。

## 执行摘要

**总评：中等风险。未发现故意后门。**

`opt` 与本仓库默认分支 `main`、以及直接上游 `starifly/NekoBoxForAndroid` 的 `main` **字节级相同**（GitHub compare：`ahead_by=0`、`behind_by=0`、`status=identical`）。`opt` 没有相对默认分支的独有提交。相对再上游 `MatsuriDayo/NekoBoxForAndroid` 的 `main`（`5768494`，tag `1.4.2`）是快进：本树比它多 **179** 个提交、**0** 个落后，GitHub 对 starifly 与 MatsuriDayo 的 compare 为 **295** 个提交、**173** 个文件（计数差来自 GitHub 把合并提交摊开；本地 `git rev-list --count matsuri-main..HEAD` 为 179）。这些差异是 starifly 维护线的协议与界面改动，不是 `daniellansun` 另写的一套代码。

审查了应用源码、`libcore`、构建脚本、CI、清单与权限、仓库内二进制，并核对了构建期才会拉下的 `starifly/sing-box` 固定提交。没有发现硬编码的 C2、把用户流量或订阅凭据偷偷上传到第三方、或在 sing-box 配置里插入攻击者出站的逻辑。

不能把它当成“已签名、可盲信的官方包”：

- 仓库里提交了 **`release.keystore`（私钥材料）**，且与 MatsuriDayo 的 keystore 不是同一份。密码不在树里，但文件本身已公开。
- 核心 `sing-box` / `libneko` 在编译时从 **starifly 的 fork** 按固定 SHA 克隆，不在本树内；`gomobile` 跟踪浮动分支；地理数据库跟踪 GitHub “latest”。
- 这是一个会接管 VPN、DNS 和本机代理端口的客户端。订阅内容、自定义 sing-box JSON、以及用户打开的“允许不安全 TLS”都会决定流量走向。这些是产品能力，不是本树里藏的第二通道。

| 级别 | 数量 | 含义 |
| --- | --- | --- |
| Critical | 0 | 没有已证实、必须在运行时代码里拆除的后门 |
| High | 1 | 发布用私钥进了 Git |
| Medium | 6 | 供应链浮动钉扎、备份/崩溃日志带出密钥、CI 把签名密码交给仓库脚本 |
| Low | 8 | 明文流量、本机 Clash API、过宽权限与深链、Hev 路由范围、自定义规则 URL 默认值对调等 |
| Info | 若干 | 与上游的身份差异、已核对但无恶意的行为 |

## 1. 范围与方法

对照：

| 参照 | 结果 |
| --- | --- |
| `origin/main`（默认分支） | 与 `opt` 同一提交 `a4c5072` |
| `starifly/NekoBoxForAndroid@main` | identical |
| `MatsuriDayo/NekoBoxForAndroid@main` `5768494` | 祖先；`opt` 仅超前 |
| `origin/dev` | `opt` 的祖先（`opt` 比 `dev` 多 277 个提交）。`dev` 不是更新的秘密分支 |

仓库关系：`daniellansun/NekoBoxForAndroid` 是 `starifly/NekoBoxForAndroid` 的 fork（创建于 2026-10-25）；starifly 又是 `MatsuriDayo/NekoBoxForAndroid` 的 fork。本树 278 个可达提交的作者以 starifly（177）和 armv9（81）为主，没有 `daniellansun` 的独有提交。

查过的类别：硬编码 URL、更新检查、订阅拉取、WebDAV、崩溃日志、DNS/TUN/Hev、TLS 校验、JNI 与 `.so`、Gradle/CI、权限与导出组件、混淆与打包资源、密钥样式（`AKIA`、私钥 PEM、`ghp_`、Slack token 等）。密钥样式搜索无命中。

未做：动态运行 APK、对 `sing-box` 111 个提交做逐行形式化审计、破解 keystore。`sing-box` 的结论来自 compare 文件列表、提交说明、补丁中的 URL，以及对固定提交 `1.12.x` 的克隆后检索。

## 2. `opt` 相对上游实际改了什么

相对 **本仓库默认分支：没有差异。**

相对 **MatsuriDayo `1.4.2`**，功能向的变化（不是后门特征）包括：

- 包名 `moe.nb4a` → `com.nb4a`（`nb4a.properties`，自 `fa5df9c` 起）。显示名 `NekoBox` → `NekoBoxF`（`0c50981`）。APK 名前缀同样改为 `NekoBoxF-`（`buildSrc/src/main/kotlin/Helpers.kt`）。
- 协议与导入：Juicity、Snell、ShadowsocksR、XHTTP extra、VLESS encryption、AnyTLS/Reality 相关 UI、KCP、订阅过滤、按订阅的服务器 DNS、DNS hosts 重写、Hev TUN。
- 更新检查从 `api.github.com/repos/MatsuriDayo/NekoBoxForAndroid/...` 改为 `starifly/NekoBoxForAndroid`（`AboutFragment.kt`）。关于页里的 GitHub / Telegram 链接仍指向 MatsuriDayo。
- 核心来源从 `MatsuriDayo/sing-box` @ `aed32ee`、`MatsuriDayo/libneko` @ `1c47a3a` 换成 `starifly/sing-box` @ `0b7a2fc`、`starifly/libneko` @ `6a85c18`（`buildScript/lib/core/get_source.sh`、`get_source_env.sh`）。
- `release.keystore` 被替换（`47c088d`，`update key`，2724 → 4484 字节）。与 MatsuriDayo 的 blob 不同（git blob `7b58d27` vs `91dcf36`）。
- CI 改为给 `assembleOssRelease` 传入签名 secret，并缓存 Hev 的 `.so`；Play AAB job 被删掉（`.github/workflows/release.yml`）。

`starifly/sing-box` 的 `0b7a2fc`（分支 `1.12.x`，2026-08-21，`concurrent dial improvements`）相对 MatsuriDayo 当时钉住的 `aed32ee`：**超前 111 个提交、131 个文件、0 个落后**。提交说明是 xhttp、vless encryption、snell、ssr、kcp、TLS fragment、并发拨号等。补丁里新增的 URL 只有 `https://sing-box.sagernet.org/`、`https://github.com/KaringX/sing-box` 注释和 Go heap 文档。克隆该提交后，`metacubex/mihomo` 只被 `protocol/shadowsocksr/outbound.go` 用于 SSR 传输，不是独立的上报客户端。非测试代码里没有额外的外连主机。

`starifly/libneko` 相对 MatsuriDayo 钉扎只多 1 个文件：`speedtest/speedtest.go` 给 URL 测试加上 context 值 `nb4a_no_concurrent_dial`，避免测速走并发拨号。

## 3. 发现

### H-1 发布密钥库提交在仓库中

- **级别：** High
- **路径：** `release.keystore`；签名读取在 `buildSrc/src/main/kotlin/Helpers.kt`（`storeFile = rootProject.file("release.keystore")`）；CI 在 `.github/workflows/build.yml`、`release.yml` 把 `KEYSTORE_PASS`、`ALIAS_NAME`、`ALIAS_PASS` 注入 Gradle。
- **行为：** JKS/PKCS12 密钥库随 Git 分发。`strings` 看不到别名或证书主题（内容是加密的）。密码不在树里，也不在已搜索的 PEM/token 模式中。对 `android`、`sagernet`、`neko` 三次 `keytool -list` 均因密码错误失败，**没有继续爆破，也没有恢复出密码**。`47c088d` 更换了这份文件，因此旧提交里的 keystore 与现在这份不同，历史里两份都还在。
- **为何重要：** 私钥材料一旦进 Git 就无法收回。签名身份只剩“密码是否保密”。拿到密码的人可以打出与 CI 发布包相同签名的 APK。它也不是 MatsuriDayo 官方 `moe.nb4a` 的密钥，不能用来证明“这是上游原版”。
- **建议：** 停止把这份 keystore 当作信任根。新的发布密钥放在仓库外，用 `apksigner` 签未签名包（见构建指南）。轮换后，旧的 `opt`/`main` 签名应视为可伪造（一旦密码泄露）。不要把密码写进 `local.properties` 再提交。`.gitignore` 已忽略 `local.properties`，但 **没有** 忽略 `release.keystore`。

### M-1 `gomobile` 跟踪浮动分支

- **级别：** Medium（继承自 MatsuriDayo；`libcore/init.sh` 相对 `5768494` 无差异）
- **路径：** `libcore/init.sh`
- **行为：** 若本机没有 `gomobile-matsuri`，脚本 `git clone https://github.com/MatsuriDayo/gomobile.git` 然后 `git checkout origin/master2`，再 `go install`。没有提交 SHA。
- **为何重要：** `libcore.aar` 由这个工具链生成。`master2` 被改写后，下一次干净构建会编进不同的绑定代码，而应用仓库的 Git SHA 不变。
- **建议：** 把 gomobile 钉到已审核的提交，或把生成的 `libcore.aar` 与其 SHA-256 一起作为发布物存档。

### M-2 地理数据库构建时拉取 “latest”

- **级别：** Medium（`buildScript/lib/assets.sh` 相对 MatsuriDayo 无差异）
- **路径：** `buildScript/lib/assets.sh`
- **行为：** CI 的 `./run init action gradle` 会执行该脚本，请求 `api.github.com/repos/SagerNet/sing-geoip/releases/latest` 与 `SagerNet/sing-geosite` 的 latest，再下载对应的 `geoip.db` / `geosite.db` 并 `xz`。仓库名和文件名是配对的。没有校验和，也没有版本钉扎。
- **为何重要：** 分流库可以决定哪些域名走代理。构建机或 GitHub Release 被替换时，APK 里的内置库会变，源码提交不变。
- **建议：** 钉住 tag，校验 SHA-256，并把 `*.version.txt` 与压缩包哈希写进发布说明。

应用内非自定义规则源不走这两个 URL，而走 `AssetsActivity.kt` 的 `rulesProviders`（`SagerNet/sing-geoip` 配 `geoip.db`，`SagerNet/sing-geosite` 配 `geosite.db`，另有 soffchen、Chocolate4U、L11R）。自定义源的默认值问题见 L-8。

### M-3 发布工作流下载并执行未校验的 `ghr`

- **级别：** Medium
- **路径：** `.github/workflows/release.yml` 的 `publish` job
- **行为：** `wget` `https://github.com/tcnksm/ghr/releases/download/v0.13.0/ghr_v0.13.0_linux_amd64.tar.gz`，解压后立即 `./ghr`。没有 checksum 或签名。`publish` 输入的语义是反的：`if: github.event.inputs.publish != 'y'`，填写 `y` 才会 **跳过** 发布。
- **为何重要：** 发布 job 持有 `github.token`。二进制被调包时，令牌和待发布的 APK 都在同一台 runner 上。
- **建议：** 钉 SHA-256 或改用官方 `softprops/action-gh-release`。把“是否发布”改成正向条件。

### M-4 云备份包含节点库与配置库

- **级别：** Medium
- **路径：** `app/src/main/AndroidManifest.xml`（`android:allowBackup="true"`、`fullBackupContent`、`dataExtractionRules`）；`app/src/main/res/xml/backup_descriptor.xml`；`app/src/main/res/xml/backup_rules.xml`
- **行为：** 云备份和设备迁移显式包含 `sager_net.db` 与 `configuration.db`。其中有节点、订阅 URL、WebDAV 账号（`BackupFragment.kt` / `WebDAVSettingsActivity.kt` 把口令交给用户填写的 WebDAV，不会另发一份）。
- **为何重要：** 开启系统备份时，代理凭据会进入 Google 账号备份。这不是隐藏上传，但是过宽。
- **建议：** 备份规则排除含密钥的库，或默认关闭 `allowBackup`，只保留应用内加密导出。

### M-5 崩溃日志含全部设置并自动弹出分享

- **级别：** Medium
- **路径：** `app/src/main/java/io/nekohasekai/sagernet/utils/CrashHandler.kt`；`moe/matsuri/nb4a/utils/SendLog.kt`；`ui/BlankActivity.kt`
- **行为：** 未捕获异常会重启到 `BlankActivity`，并立刻 `SendLog.sendLog`。报告头遍历 `PublicDatabase.kvPairDao.all()`，再附加 `logcat -d` 和 `cache/neko.log`。分享走 `ACTION_SEND` 选择器，**不会**自己 POST 到固定服务器。
- **为何重要：** 选择器里的目标应用能看到订阅 URL、混合入站随机口令（`DataStore.mixedSecret`）、WebDAV 口令等。崩溃时选择器会自动出现，用户容易点错。
- **建议：** 崩溃时只记本地文件；分享前去掉密钥字段，并改成用户明确点击。

### M-6 CI 在执行仓库脚本的同时注入签名密码

- **级别：** Medium
- **路径：** `.github/workflows/build.yml`、`release.yml`
- **行为：** `main` 上 `nb4a.properties` 变更会跑 `build.yml`。Gradle 命令带有 keystore secret，而 `./run`、`buildSrc` 和 Gradle 脚本都来自同一次 checkout。`pull_request` 的 `ci.yml` 只打 debug、不注入这些 secret。
- **为何重要：** 能推 `main` 的人可以在构建脚本里把 secret 打出来。这是 GitHub Actions 的常见模型，但和 H-1 叠在一起时，密码泄露就等于签名身份泄露。
- **建议：** 签名放到受保护环境，使用 GitHub 的 artifact attestation；构建脚本与签名步骤分离。不要在日志里打印 `LOCAL_PROPERTIES`（该 secret 会被 Base64 解码进 Gradle 的 `local.properties`，见 `Helpers.kt` 的 `requireLocalProperties()`）。

### L-1 全局允许明文 HTTP

- **级别：** Low
- **路径：** `app/src/main/res/xml/network_security_config.xml`（`cleartextTrafficPermitted="true"`，无域名限制）
- **行为：** 进程内的明文 HTTP 不被网络配置拒绝。默认连通性测试 URL 是 `http://www.gstatic.com/generate_204`（`Constants.kt`）。订阅 URL 由用户提供。
- **建议：** 保持用户订阅可用的同时，不要把应用自己的更新检查降到 HTTP。当前更新检查已是 HTTPS。

### L-2 可选的本机 Clash API 没有 secret

- **级别：** Low
- **路径：** `ConfigBuilder.kt` 中 `clash_api.external_controller = "127.0.0.1:9090"`；`DataStore.enableClashAPI` 默认 false
- **行为：** 用户打开后，本机任意应用可访问无密钥的 Clash API 和 YACD。WebView（`WebviewFragment.kt`）对 `DataStore.yacdURL`（默认 `http://127.0.0.1:9090/ui`）启用 JavaScript，没有 `addJavascriptInterface`。
- **建议：** 绑定 secret，或仅在调试构建启用。不要把 `yacdURL` 指到不受信任的远程页。

### L-3 权限与导出组件偏宽，但是客户端的常规形态

- **级别：** Low
- **路径：** `app/src/main/AndroidManifest.xml`
- **行为：**
  - `QUERY_ALL_PACKAGES` 与 `com.android.permission.GET_INSTALLED_APPS`：分应用代理需要枚举包名。`play` flavor 没有单独拿掉该权限。
  - `MainActivity` `exported=true`，并接收 `sn`、`ss`、`ssr`、`vmess`、`trojan`、`clash://install-config` 等 scheme。没有 host 限制的那些 scheme 可被任意网页或应用唤起，用来导入节点。这是导入功能，不是静默连出。
  - `QuickToggleShortcut` 等快捷方式 `exported=true`。VPN 已授权时，其他应用可以请求切换。
  - `BootReceiver` `exported=true` 但 `android:enabled="false"`，仅在用户打开“开机启动”后才会启用。
  - `VpnService` `exported=false`，且要求 `BIND_VPN_SERVICE`。
  - 前台服务类型为 `systemExempted`。普通应用在较新的 Android 上可能拿不到该豁免，这是兼容性风险，不是额外采集。
- **建议：** 导入深链保持用户确认。若上架，为 `QUERY_ALL_PACKAGES` 准备声明，并核对前台服务类型。

### L-4 混合入站在“允许局域网”时监听 `0.0.0.0`

- **级别：** Low
- **路径：** `ConfigBuilder.kt`：`bind = if (DataStore.allowAccess) "0.0.0.0" else "127.0.0.1"`，端口默认基于 2080。
- **行为：** 默认只绑回环。用户打开允许访问后，局域网可连到 mixed 端口。VPN 模式下会给该入站加上随机用户名/密码（`DataStore.mixedSecret`，首次运行生成）。例外：Android 10+ 且打开“附加 HTTP 代理”时，`mixedInboundNeedsAuth` 为 false，因为系统 HTTP 代理不能带认证（`02d0061` 的注释意图是防止无认证泄漏，但附加 HTTP 代理这条路径仍无认证）。
- **建议：** 打开 `allowAccess` 时在界面上明确这是局域网开放代理。附加 HTTP 代理与 `allowAccess` 不要同时使用。

### L-5 Hev TUN 在绕过局域网时额外路由 `100.64.0.0/10`

- **级别：** Low
- **路径：** `VpnService.kt`（`HEV_MAPDNS_VLAN4 = "100.64.0.0"`，前缀 10）；`moe/matsuri/nb4a/hevtun/HevTunRuntime.kt`
- **行为：** 仅当 VPN 模式、用户启用 Hev TUN、且启用 FakeDNS、且“绕过局域网”打开时，把 CGNAT 段送进 TUN。Hev 配置把 mapdns 的地址池设成这段，DNS 仍指向本机 `172.19.0.2:53`。FakeIP 本身仍是 `198.18.0.0/15`。`.so` 不在 Git 里，由 `buildScript/compile-hevtun.sh` 从子模块编译。子模块 gitlink `9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a` 等于上游 tag `2.17.1`（作者 hev）。
- **为何重要：** 不是把 DNS 指到外部解析器，但会把运营商级 NAT 地址吸进隧道。
- **建议：** 在设置说明里写上这段路由。不需要 Hev 时保持 `enableHevTun` 关闭（默认 false）。

### L-6 有签名配置时，debug 与 release 共用同一密钥

- **级别：** Low
- **路径：** `Helpers.kt` `setupAppCommon()`：release 与 debug 都 `signingConfig = key`
- **行为：** 环境里一旦有 `KEYSTORE_PASS`，debug 包（applicationId `com.nb4a.debug`）也用发布密钥签名。
- **建议：** debug 使用 debug keystore。发布密钥只签 release。

### L-8 自定义规则 URL 的代码默认值与界面默认值相反

- **级别：** Low
- **路径：** `DataStore.kt` 第 136–137 行；`app/src/main/res/xml/global_preferences.xml` 的 `rulesGeositeUrl` / `rulesGeoipUrl`；`AssetsActivity.updateCustomAsset`（仅 `rulesProvider == 4`）
- **行为：** 偏好 XML 的默认值是对的：`rulesGeositeUrl` → `sing-geosite/.../geosite.db`，`rulesGeoipUrl` → `sing-geoip/.../geoip.db`。Kotlin 委托在键尚未写入数据库时的回退值把两者对调。`updateCustomAsset` 读的是 DataStore，不是 XML。用户只把规则提供方改成“自定义”、却从未改过 URL 时，界面上仍显示正确地址，实际下载可能把 geosite 文件存成 `geoip.db`，反过来亦然。两个地址仍都在 `SagerNet` 的 Release 上，不是外部主机。MatsuriDayo `1.4.2` 还没有这对 DataStore 默认值。
- **为何重要：** 用错库会让分流规则匹配失败，流量可能该代理时直连，或相反。这是默认值写错，不是把查询送到攻击者的解析器。
- **建议：** 让 DataStore 回退值与 XML `defaultValue` 一致，并在发布说明里提醒已保存了旧值的用户核对这两个字段。

### L-7 R8 打开了压缩，但规则禁止混淆

- **级别：** Low（构建质量，不是隐藏恶意逻辑）
- **路径：** `Helpers.kt`（release `isMinifyEnabled = true`，`isShrinkResources = true`）；`app/proguard-rules.pro` 含 `-dontobfuscate`，并 `-keep` 整个 `io.nekohasekai.sagernet.**` 与 `moe.matsuri.nb4a.**`
- **行为：** 没有用加密字符串或壳来藏行为。反编译后类名仍在。`nkmr_minify=0` 会把 minify 和 shrink 都关掉。
- **建议：** 若希望提高逆向成本，去掉 `-dontobfuscate` 并收窄 keep。不要把它当成安全边界。

### Info（核对过、不构成后门）

- **没有** 仓库内的 `.so`、`.aar`、`.dex`。`app/libs/` 与 `jniLibs/` 被 gitignore。原生代码在构建时产生。
- **没有** `curl | bash`。依赖仓库只有 `google()`、`mavenCentral()`、`gradlePluginPortal()`、`https://jitpack.io`（`repositories.gradle.kts`）。JitPack 上的直接依赖是 `zxing-lite`、`material-about-library`、`Roomigrant`。JitPack 按 Git 构建，属于可接受但应锁版本的供应链面；当前版本号已写死在 `app/build.gradle.kts`。
- Gradle Wrapper 分发地址是 `https://services.gradle.org/distributions/gradle-8.10.2-bin.zip`。`gradle/wrapper/gradle-wrapper.jar` 的 SHA-256 为 `33ad4583fd7ee156f533778736fa1b4940bd83b433934d1cc4e9f608e99a6a89`，与 MatsuriDayo `1.4.2` **相同**，与 `gradle/gradle` tag `v8.10.2` 里的 wrapper jar（`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`）**不同**。jar 内类名是标准 Wrapper，`strings` 未见替换后的下载地址。差异来自 Gradle 后来把 wrapper jar 做瘦了，不是本 fork 换进去的加载器。
- 启动路径（`SagerNet.kt`）不发 HTTP。出站 HTTP 只出现在：用户触发的更新检查、用户触发的规则下载（`AssetsActivity.kt`）、用户配置的订阅（`RawUpdater.kt`）、用户配置的 WebDAV、连通性测试。订阅 User-Agent 为 `NekoBox/Android/<version> (Prefer ClashMeta Format)`（`ktx/Nets.kt`）。更新检查只读 GitHub Release JSON，再由用户确认后用浏览器打开 `html_url`，不静默下载 APK。
- DNS：`hijack-dns` 只匹配 `protocol=dns` 和端口 53，把查询交给本机 sing-box DNS，这是 VPN 客户端的常规做法。默认远程 DNS `https://dns.google/dns-query`，直连 DNS `https://223.5.5.5/dns-query`，均可在设置里改。FakeIP 段 `198.18.0.0/15`。按订阅的 `serverDnsResolver` 只解析该订阅节点域名，且 `detour` 为 `direct`。`dnsHosts` 来自用户设置，不是写死的映射。
- 订阅 JSON 若含 `outbounds`，只导入非 `dns`/`block`/`direct`/`selector`/`urltest` 的出站，类型为 sing-box outbound（`ConfigBean.type = 1`）。`type == 0` 的整份配置会原样交给核心（`ConfigBuilder.kt`），那是用户在“sing-box 配置”编辑器里保存的内容，订阅解析器不会把节点标成 type 0。`globalCustomConfig` 会 `mergeJSON` 进最终配置，同样是用户设置。
- `allowInsecure` 只在用户或节点链接打开时跳过 TLS 校验。Juicity 证书钉扎（`libcore/protocol/juicity/outbound.go`）在比对 SHA-256 时把 `InsecureSkipVerify` 设为 true，这是“不信系统 CA、只信钉扎哈希”的写法；哈希不匹配会失败，不会改道到别的主机。
- 插件通过已安装应用的 `ContentProvider` 取可执行文件（`PluginManager.kt`），或查找随包的 `libhysteria.so` / `libhysteria2.so`。没有下载后 `exec` 的路径。下载链接是文档页 `matsuridayo.github.io` 与 MatsuriDayo/plugins 的 Release 列表（`PluginEntry.kt`），由用户在浏览器打开。
- `app/src/main/assets/yacd.zip` 与 MatsuriDayo 同一 git blob，内容是 2023-06-14 的 Yacd-meta 静态页，不是原生载荷。
- `go.mod` 的 replace：`sing-box` 与 `libneko` 指向仓库**上一级**目录；`sing-vmess` → `github.com/starifly/sing-vmess` `v0.2.8-mod.1`（相对 `SagerNet/sing-vmess` 的 `dev`：12 提交 / 7 文件，主题是 vision 与早期连接兼容）；`sing-snell` → `github.com/reF1nd/sing-snell` `c120d40`（相对 `SagerNet/sing-snell` 的 `main`：6 提交 / 43 文件，Snell v4–v6 与测试）。`sing-box` 的 `go.mod` 还有 `replace` 到 `Restia-Ashbell/sing-shadowsocks2`（fork 自 `MetaCubeX/sing-shadowsocks2`）。`github.com/dyhkwong/sing-juicity` `v0.0.3` 被 `libcore/protocol/juicity` 使用；GitHub 上该路径现在解析到 `ExclaveNetwork/sing-juicity`。这些都是协议实现依赖，本次没有在它们的元数据里看到第二套 C2，但也没有逐行审完 Snell/SSR 的全部实现。
- 直接 DNS 使用阿里公共 DNS、更新检查指向 starifly、关于页仍链到 MatsuriDayo，会让“这个 APK 的维护者是谁”不够一目了然。今天代码与 starifly 相同，所以更新检查与源码一致；若以后 `daniellansun` 的 `opt` 分叉而 URL 不改，用户会被带到另一个仓库的 Release。

## 4. 明确结论

在上述范围内，**没有证据表明 `opt` 含有故意后门**（没有隐藏的上报端点、没有把 VPN 流量改写到固定攻击者服务器、没有运行时下载并执行未知二进制、没有用混淆藏起来的第二套逻辑）。

风险来自：公开的发布密钥、编译期才进入二进制的大型 fork（尤其是未钉死的 gomobile 与 geo 库）、以及任何代理客户端都有的“订阅和自定义配置被信任”模型。在 H-1 处理之前，不要把“由仓库里这份 keystore 签出的 APK”当作不可伪造的发布身份。

## 5. 复核命令

```bash
git rev-parse opt main origin/opt origin/main
# 期望四个 SHA 都是 a4c5072040329a68cc139d8823d016f8aeba6094

git rev-list --count 5768494d8ae3c74a057bb6d46c0f8dc071b0d821..HEAD
git diff --stat 5768494d8ae3c74a057bb6d46c0f8dc071b0d821..HEAD -- \
  buildScript/lib/core/get_source.sh buildScript/lib/core/get_source_env.sh \
  nb4a.properties release.keystore

# sing-box 钉扎（需要网络）
git ls-remote https://github.com/starifly/sing-box.git refs/heads/1.12.x
# 期望 0b7a2fc2f2c33f754e5fe5a1bb3e0a4f610b6aa9

git ls-tree HEAD hev-socks5-tunnel
# 期望 9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a
```
