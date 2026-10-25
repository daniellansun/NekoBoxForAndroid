# 从 `opt` 构建可核验的 Release APK

本文对应当前 `opt`（与 `main`、`starifly/NekoBoxForAndroid@main` 相同）：

- 提交：`a4c5072040329a68cc139d8823d016f8aeba6094`
- 版本名：`1.4.2-rev-24`（`nb4a.properties` 的 `VERSION_NAME`）
- `VERSION_CODE` 为 46，Gradle 再乘 5，因此 APK `versionCode` 为 **230**（`buildSrc/src/main/kotlin/Helpers.kt`）
- `applicationId`：`com.nb4a`（debug 构建为 `com.nb4a.debug`）
- 显示名：NekoBoxF

目标是打出 **release 变体、R8 压缩开启、按 ABI 拆分、由你自己的密钥签名** 的 APK。不要把仓库里的 `release.keystore` 当作你的发布身份。原因见 `docs/SECURITY_AUDIT_opt.md` 的 H-1。

本指南不修改应用源码。签名步骤在 Gradle 之外完成。

## 1. 你会得到什么

产品风味（`Helpers.kt` 的 `flavorDimensions += "vendor"`）：

| Flavor | 与 `oss` 的代码差异 | 用途 |
| --- | --- | --- |
| `oss` | 基线 | CI 与旁加载发布用这个 |
| `fdroid` | 无独立 source set | 与 `oss` 同一套代码 |
| `play` | 无独立 source set | 仓库已不再在 CI 里打 AAB |
| `preview` | 仅把 `BuildConfig.PRE_VERSION_NAME` 设为 `pre-1.4.2-20260214-1` | 预览版 |

四个 flavor 的 Java/Kotlin 源都是 `app/src/main`。没有“官方 flavor 少一段恶意代码”这种分叉。

Release 构建还会：

- `minifyEnabled` 与 `shrinkResources` 为 true（可用环境变量 `nkmr_minify=0` 关掉，发布不要关）
- ABI split：`armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`，**不**生成 universal APK
- `minSdk` 21，`compileSdk` / `targetSdk` 35，`buildToolsVersion` 35.0.1
- Java/Kotlin 目标字节码 1.8
- JNI 使用 legacy packaging（`.so` 会解压到原生库目录，Hev 与 gomobile 需要）

当前手机用 **`arm64-v8a`**。`armeabi-v7a` 是 32 位 ARM。`x86` / `x86_64` 给模拟器。

## 2. 前置条件

在 Linux 上从仓库根目录操作。

| 工具 | 版本 | 原因 |
| --- | --- | --- |
| Git | 含 submodule | `hev-socks5-tunnel` |
| JDK | Temurin **17** | CI 的 `ci.yml` 使用 Temurin 17。AGP 8.8.1 需要 JDK 17。不要用 JDK 24 去跑 Gradle 8.10.2 |
| Android SDK | Platform **35**、Build-Tools **35.0.1** | `Helpers.kt` |
| Android NDK | **25.0.8775105**（r25） | `buildScript/init/env_ndk.sh` 与 CI 把该路径写死。目录必须含 `source.properties` |
| Go | **1.24.9** 或更新的 1.24/1.25 | `libcore/go.mod` 为 `go 1.24.7`、`toolchain go1.24.9`。`build.yml` 写的是 `^1.25`，`ci.yml` 写的是 `^1.24`，以 go.mod 的 toolchain 为准 |
| 其他 | `make`、`gcc`、`xz-utils`、`unzip`、`python3` 不必需 | NDK 编译 Hev；`assets.sh` 用 `xz`；`curl` 拉地理库 |

安装 SDK 组件示例（`sdkmanager` 的路径按本机安装调整）：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platforms;android-35" \
  "build-tools;35.0.1" \
  "ndk;25.0.8775105"
```

`env_ndk.sh` 的查找顺序：`$ANDROID_HOME/ndk/25.0.8775105`，然后才是已有的 `ANDROID_NDK_HOME` / `NDK` / `ndk-bundle`。只要 r25 那份目录存在，就会用它。

磁盘与网络：首次 `./run lib core` 会把 `sing-box` 和 `libneko` 克隆到**仓库的上一级目录**，并下载 Go 模块和 MatsuriDayo/gomobile。预留数 GB。

## 3. 取出源码

```bash
git clone --recurse-submodules https://github.com/daniellansun/NekoBoxForAndroid.git
cd NekoBoxForAndroid
git checkout a4c5072040329a68cc139d8823d016f8aeba6094
git submodule update --init --recursive
```

`opt` 与 `main` 都指向该提交，`git checkout opt` 等价。核对：

```bash
git rev-parse HEAD
git ls-tree HEAD hev-socks5-tunnel
# hev gitlink 应为 9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a（上游 tag 2.17.1）
```

子模块地址是 `https://github.com/heiher/hev-socks5-tunnel`（`.gitmodules`）。若子模块没初始化，`buildScript/compile-hevtun.sh` 会自己 `submodule update`，失败时再按 tag `2.17.1` 浅克隆。

## 4. 编译原生库

所有命令在仓库根目录执行。`./run lib core` 展开为 `buildScript/lib/core.sh`，它会：

1. `buildScript/lib/core/get_source.sh`  
   `pushd ..` 后克隆并检出：
   - `../sing-box` ← `https://github.com/starifly/sing-box.git` @ `0b7a2fc2f2c33f754e5fe5a1bb3e0a4f610b6aa9`
   - `../libneko` ← `https://github.com/starifly/libneko.git` @ `6a85c185d62435a5293ef70ac3b638ae3ee1efa7`  
   这与 `libcore/go.mod` 的 `replace ... => ../../sing-box`（相对 `libcore/`）一致。不要把这两份源码夹到仓库里面，replace 会找不到。
2. `libcore/init.sh` 安装 `gomobile-matsuri`（见审计 M-1，分支 `master2` 不钉 SHA）。
3. `libcore/build.sh` 执行：

```text
gomobile-matsuri bind -androidapi 21 -trimpath -ldflags='-s -w' \
  -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api'
```

产物复制到 `app/libs/libcore.aar`（该目录被 gitignore）。

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$(go env GOPATH)/bin"
./run lib core
test -f app/libs/libcore.aar && echo "libcore.aar ok"
```

构建结束后记录钉扎，便于以后对照：

```bash
git -C ../sing-box rev-parse HEAD
git -C ../libneko rev-parse HEAD
sha256sum app/libs/libcore.aar
```

Hev 的 `.so` 不必单独先编。`app/build.gradle.kts` 的 `preBuild` 依赖 `buildHevTun`：四个 ABI 的 `app/src/main/jniLibs/<abi>/libhev-socks5-tunnel.so` 缺任何一个就会跑 `buildScript/compile-hevtun.sh`。强制重编：`FORCE_HEV=1 ./gradlew :app:preBuild`。

地理库（审计 M-2）在 `./run init action gradle` 时下载，Gradle 任务本身不会下载。发布构建请显式跑一次，并记下版本文件：

```bash
bash buildScript/lib/assets.sh
cat app/src/main/assets/sing-box/geoip.version.txt
cat app/src/main/assets/sing-box/geosite.version.txt
sha256sum app/src/main/assets/sing-box/*.db.xz
```

这些文件在 `app/src/main/assets/sing-box/`，被 gitignore。不跑脚本时，APK 里就没有内置 geo 库，用户仍可在应用里按设置 URL 下载。

## 5. 签名：不要用仓库里的 keystore

`Helpers.kt` 只有在 `KEYSTORE_PASS`（`local.properties` 或环境变量）非空时才创建 signingConfig，并且 **store 文件写死为仓库根的 `release.keystore`**。因此：

- 不设置密码 → release APK **未签名**（这是我们要的中间产物）
- 设置了密码 → 用已公开的那份 keystore 签名

可信发布用未签名包 + 你自己的密钥。密钥放在仓库外，例如 `$HOME/keystores/nekoboxf-release.jks`。不要提交，不要放进 `app/`。

```bash
keytool -genkeypair -v \
  -keystore "$HOME/keystores/nekoboxf-release.jks" \
  -alias nekoboxf \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12
```

口令只放在密码管理器里。若用 `local.properties`，只写 SDK 路径，不要写口令：

```properties
sdk.dir=/home/you/Android/Sdk
ndk.dir=/home/you/Android/Sdk/ndk/25.0.8775105
```

`local.properties` 已在 `.gitignore`。CI 还支持把整份 properties 做 Base64 放进 secret `LOCAL_PROPERTIES`；本地不需要。

Gradle 属性名（仅当你明知要签仓库内 keystore 时才设置，发布不要这样做）：

| 来源 | 键 |
| --- | --- |
| `local.properties` 或环境变量 | `KEYSTORE_PASS` |
| 同上 | `ALIAS_NAME` |
| 同上 | `ALIAS_PASS` |

没有 `storeFile` 属性。要换密钥文件就必须改 `Helpers.kt`，或按下一节在 Gradle 之后用 `apksigner`。

## 6. 精确的 Gradle 任务

先确认没有签名环境变量：

```bash
unset KEYSTORE_PASS ALIAS_NAME ALIAS_PASS
./gradlew :app:assembleOssRelease
```

等价任务名是 `app:assembleOssRelease`。不要用 `assembleRelease` 做日常发布：它会编 **oss + fdroid + play + preview** 四个 flavor，每个再拆 4 个 ABI，共 16 个 APK，代码相同、耗时数倍。

AAB（Play 不建议用于本 fork，见第 8 节）：

```bash
./gradlew :app:bundleOssRelease
```

输出目录：

```text
app/build/outputs/apk/oss/release/
app/build/outputs/bundle/ossRelease/
```

文件名会被改两次（`Helpers.kt` 的 `setupCommon` 与 `setupApp`）。`app-oss-arm64-v8a-release.apk` 这类默认名会先变成带 `app-<versionName>` 的名字，再把前缀 `app` 换成 `NekoBoxF-<versionName>`，结果通常是：

```text
NekoBoxF-1.4.2-rev-24-1.4.2-rev-24-arm64-v8a.apk
```

以 `find` 为准，不要手写死文件名：

```bash
find app/build/outputs/apk/oss/release -name '*.apk' -print
```

只发布 arm64：

```bash
APK=$(find app/build/outputs/apk/oss/release -name '*arm64-v8a*.apk' | head -n 1)
echo "$APK"
```

调试包（applicationId 带 `.debug`，且可调试）不是发布物：

```bash
./gradlew :app:assembleOssDebug
```

`lint` 配置了 `warningsAsErrors = true`（`Helpers.kt`）。release 可能因为 lint 失败。文本报告在 `app/build/lint.txt`。先修报告里的问题，不要用 `-x lint` 把发布检查拿掉，除非你记录了每一条忽略理由。

## 7. 用你自己的密钥签名并校验

Build-Tools 35 的 `apksigner` 会对齐并签名（v2/v3）。对每个要发布的 ABI 做一次：

```bash
BT="$ANDROID_HOME/build-tools/35.0.1"
UNSIGNED=$(find app/build/outputs/apk/oss/release -name '*arm64-v8a*.apk' | head -n 1)
SIGNED="${UNSIGNED%.apk}-signed.apk"

"$BT/zipalign" -c -p -v 4 "$UNSIGNED"
"$BT/apksigner" sign \
  --ks "$HOME/keystores/nekoboxf-release.jks" \
  --ks-type PKCS12 \
  --ks-key-alias nekoboxf \
  --out "$SIGNED" \
  "$UNSIGNED"
"$BT/apksigner" verify --verbose --print-certs "$SIGNED"
sha256sum "$SIGNED" | tee "${SIGNED}.sha256"
```

核对证书时看 `Signer #1 certificate SHA-256 digest`。以后每次发布都对比这个摘要。它不会等于用仓库 `release.keystore` 签出来的包，这是预期结果。

装到设备：

```bash
adb install -r "$SIGNED"
adb shell pm path com.nb4a
```

`com.nb4a` 与上游 `moe.nb4a` 是两个应用，可以同时安装，也不会继承上游的签名。升级只能覆盖**同一 applicationId 且同一签名**的旧包。`47c088d` 换过仓库 keystore 之后，即使用那份密钥签的新旧包也可能无法覆盖安装。

### Play App Signing 与旁加载

README 写明：Google Play 上的 NekoBox 自 2024 年 5 月起由第三方控制，且非开源，不要从那个商品页安装。本树的 `play` flavor 没有单独源码，CI 里的 Play bundle job 也已删除。

旁加载不经过 Play App Signing。用户信任的是你公布的证书 SHA-256 和 APK SHA-256。若将来新建一个 Play 商品（新包名 `com.nb4a`），由 Play 再签一层，设备上的证书会变成 Play 的分发证书；上传前仍用你的 upload key。不要把 `release.keystore` 交给 Play。四个 ABI 目前共用 versionCode 230，Play 多 APK 要求每个 ABI 的 versionCode 不同，现有 `splits.abi` 没有 `versionCodeOverride`。旁加载不受此限制。

## 8. R8、ABI、SDK

| 项 | 当前值 | 发布时 |
| --- | --- | --- |
| R8 minify / shrink | release 开启 | 保持开启。`proguard-rules.pro` 含 `-dontobfuscate`，类名不会变。这不影响正确性，只降低逆向成本 |
| ProGuard 文件 | `proguard-android-optimize.txt` + `app/proguard-rules.pro` | 已由 `setupApp()` 挂上 |
| ABI | 四套拆分，无 fat APK | 给用户 arm64，同时公布其余 ABI 的哈希 |
| minSdk / targetSdk | 21 / 35 | 2026 年新 Play 目标 API 可能已高于 35。旁加载可以维持 35；上架前核对当年 Play 政策 |
| 版本号 | versionName `1.4.2-rev-24`，versionCode 230 | 下次发布要同时改 `nb4a.properties` 的 `VERSION_NAME` 与 `VERSION_CODE`。只改名字不会让 Android 认为这是升级 |

## 9. 可选 CI 提纲

现有工作流可以当骨架，但不要原样当作可信发布：

| 文件 | 触发 | 问题 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | 所有分支的 push / PR，忽略 `v*` tag | 只跑 `assembleOssDebug`。Go 为 `^1.24`。有 `setup-java` 17 |
| `.github/workflows/build.yml` | 仅 `main`，且路径含 `nb4a.properties` | `assembleOssRelease`，并把 keystore 密码注入 Gradle。**没有** `setup-java`。Go 为 `^1.25`。`opt` 这个分支名不会触发它（今天 `opt`==`main` 的内容，推送到 `main` 才会） |
| `.github/workflows/release.yml` | `workflow_dispatch` | 同上，外加未校验的 `ghr`。`publish=y` 表示不发布 |
| `.github/workflows/preview.yml` | `workflow_dispatch` | `assemblePreviewRelease`，不签名 |

建议的发布流水线：

1. `actions/setup-java@v5`，Temurin 17。不要依赖 runner 预装 JDK。
2. 显式安装 NDK `25.0.8775105` 与 platform 35、build-tools 35.0.1。只写 `local.properties` 不会安装 NDK；GitHub 镜像若已去掉 r25，现有 workflow 会在 `env_ndk.sh` 失败。
3. `actions/setup-go` 使用 `1.24.9`（或 `^1.24.9`），不要在 `ci.yml` 与 `build.yml` 之间分叉。
4. `actions/checkout` 带 `submodules: recursive`。
5. `./run lib core`，把 `git -C ../sing-box rev-parse HEAD` 和 `libcore.aar` 的 SHA-256 打进构建日志。
6. `bash buildScript/lib/assets.sh`，保存 `geoip.version.txt` / `geosite.version.txt` 和 `sha256sum`。
7. `unset` 三个 keystore 变量后 `./gradlew :app:assembleOssRelease`。
8. 在独立、受保护的 job 里用上传密钥 `apksigner`。构建脚本碰不到口令。
9. 上传每个 ABI 的 APK 和 `.sha256`。发布说明写上 `apksigner` 的证书 SHA-256。
10. 不要 `wget` 后直接执行 `ghr`。用带 checksum 的 release action。

`BUILD_PLUGIN=none` 在四个 workflow 里都有，源码中没有任何读取处，设不设不影响产物。

缓存键只哈希 `*.gradle.kts` 与 libcore 源码摘要（`.github/workflows/build.yml`）。改 `nb4a.properties` 以外的资源文件不一定会让 Gradle 缓存失效。发布 job 使用干净缓存更稳妥。

## 10. 本仓库构建文档里的坑

1. **README 没有构建步骤**，只链到 `matsuridayo.github.io` 和插件页。按 README 无法知道 NDK 版本、flavor 和 keystore 行为。
2. **`release.keystore` 被跟踪**，Gradle 只签这一份。可信发布应走未签名 APK + 外部 `apksigner`。
3. **核心源码不在树里**，克隆到仓库的父目录。在错误的工作目录执行 `./run lib core` 会把 `sing-box` 克隆到别处，随后 `replace` 失败。
4. **NDK 路径写死**为 `25.0.8775105`。只设置 `ANDROID_NDK_HOME` 指向 r26+ 仍可能被忽略（当 r25 目录存在时）。r25 不存在且环境变量也没有时，脚本直接退出。
5. **`build.yml` / `release.yml` / `preview.yml` 不安装 JDK**，`ci.yml` 才安装。runner 默认 JDK 变化会导致同一 YAML 有时能过、有时不能。
6. **Go 版本在 workflow 之间不一致**（`^1.24` 与 `^1.25`）。以 `libcore/go.mod` 的 `toolchain go1.24.9` 为准。
7. **地理库不钉版本**。同一次提交在不同日期构建，内置 `geoip.db` / `geosite.db` 可以不同。
8. **gomobile 不钉提交**（`libcore/init.sh` 的 `origin/master2`）。
9. **APK 文件名可能带两段版本号**，CI 用 `find ... *arm64-v8a*.apk` 再取目录上传，能工作，但发布说明里的文件名要以实际产物为准。
10. **没有 universal APK**。只拷走一个 ABI 时，别的手机装不上（`INSTALL_FAILED_NO_MATCHING_ABIS`）。
11. **四个 flavor 共用 versionCode**。不能把四个 ABI 当作同一 versionCode 的 Play 多 APK 上传。
12. **`warningsAsErrors = true`**。本地 Android Studio 能安装的 debug 包，不代表 `assembleOssRelease` 能过 lint。
13. **`./run` 的参数是路径拼接**（`run` 脚本）：`./run lib core` → `buildScript/lib/core.sh`，`./run init action gradle` → `buildScript/init/action/gradle.sh`。多写一段路径会去执行不存在的脚本。
14. **Hev 与 libcore 的缓存**在 CI 里按源码哈希。本地若改了 `compile-hevtun.sh` 但 `.so` 已存在，Gradle 不会重编，除非 `FORCE_HEV=1` 或删掉 `app/src/main/jniLibs`。
15. **Wrapper jar 与 Gradle 8.10.2 标签里的 jar 哈希不同**，但与 MatsuriDayo `1.4.2` 相同，且 `distributionUrl` 仍是 `services.gradle.org` 的 8.10.2。第一次运行 `./gradlew` 时核对下载的发行包哈希（Gradle 会校验发行包自身的 checksum）。不要替换 `gradle-wrapper.jar`，除非你从官方 Gradle 重新生成并对比。

## 11. 发布前核对清单

```text
[ ] HEAD 是预期提交，hev 子模块是 9a06bc6
[ ] ../sing-box 与 ../libneko 的 SHA 等于 get_source_env.sh
[ ] 记录 libcore.aar、geoip、geosite 的 SHA-256
[ ] 未设置 KEYSTORE_PASS / ALIAS_NAME / ALIAS_PASS
[ ] ./gradlew :app:assembleOssRelease 成功，且没有 -x lint
[ ] apksigner verify --print-certs 的证书 SHA-256 是你自己的密钥
[ ] 对将要分发的每个 ABI 做 sha256sum
[ ] 确认包名是 com.nb4a，不会覆盖 moe.nb4a
[ ] 没有把 .jks、口令、local.properties 提交回去
```
