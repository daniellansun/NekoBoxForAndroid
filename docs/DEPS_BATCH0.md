# Dependency batch 0: process and pinning

Batch 0 changes no library versions. It pins the inputs that used to float
and records what still has to happen in a build step.

## Pinned in this batch

| Input | Before | After |
|---|---|---|
| Go toolchain (CI) | `^1.24` (ci.yml), `^1.25` (build/preview/release) | `1.24.9`, `check-latest: false`. Matches `libcore/go.mod` `toolchain go1.24.9` and starifly/sing-box `0b7a2fc2` `toolchain go1.24.9`. |
| gomobile-matsuri (`libcore/init.sh`) | `origin/master2` | MatsuriDayo/gomobile `17d6af34f6bd6d7e1e428e0c652c8b54a46bda4f` (master2 head when pinned), checked after checkout |
| sing-geoip (`buildScript/lib/assets.sh`) | GitHub `latest` | `20261012`, geoip.db sha256 `f24050f4ce11783cd632fe3982f24895d959fa01afc8fab6ca8a6a3615b5de56` |
| sing-geosite (`buildScript/lib/assets.sh`) | GitHub `latest` | `20261025014458`, geosite.db sha256 `abbb33e5948fd396c9e4ace3442ae3cc483f31ee4400e02cfc40dc1dfae4e008` |
| Lint remote version checks (`lint.xml`) | errors via `warningsAsErrors` | `informational`: NewerVersionAvailable, GradleDependency, AndroidGradlePluginVersion, GradleDynamicVersion, PlaySdkIndexNonCompliant, PlaySdkIndexGenericIssues, PlaySdkIndexVulnerability, RiskyLibrary, OutdatedLibrary |
| libgojni.so page size (`libcore/build.sh`) | 4 KB LOAD alignment | `-extldflags=-Wl,-z,max-page-size=16384` (LOAD align 0x4000) |
| APK signing (`buildSrc/.../Helpers.kt`) | v1+v2, AGP dependency-info block | `enableV3Signing = true`; `dependenciesInfo { includeInApk = false; includeInBundle = false }` |

To bump the geo assets, change the version and sha256 together in
`buildScript/lib/assets.sh`.

## gomobile constraints

- **gomobile bind cannot build with `-mod=vendor`.** gobind needs
  `golang.org/x/mobile/bind` and `bind/seq`, which `go mod vendor` does not
  include. gomobile also compiles inside a synthesized module under
  `$WORK/src-android-<arch>`, runs `go mod tidy` there and overrides GOFLAGS,
  so a vendor tree is never used. An offline build needs an archived,
  populated GOMODCACHE (with its sha256) instead of a vendor directory.
- **gomobile adds about 120 `/go.mod` lines to `libcore/go.sum`.** Its
  `go list -m -json all` needs the `/go.mod` hashes of the whole module graph.
  Selected versions do not change, but the tree is left dirty
  (`vcs.modified=true`). Committing those lines needs that `go list` to run
  against the real source layout, so it is deferred until a compile slot.
- gomobile writes local replace directories as absolute paths into the
  synthesized go.mod, and they end up in libgojni.so build info.
  `tools/gomobile-relative-replace.patch` from the dry run fixes this. It is
  meant to live as a commit on the daniellansun/gomobile fork and be pinned
  in `libcore/init.sh`. That fork commit is not pushed yet, so this batch still
  pins upstream 17d6af34.

## Outstanding (needs a compile or network slot)

1. Commit the gomobile `/go.mod` lines to `libcore/go.sum`: with
   `../sing-box` and `../libneko` checked out by
   `buildScript/lib/core/get_source.sh`, run
   `cd libcore && go list -m -json all >/dev/null`, then check that
   `git diff libcore/go.sum` only adds `/go.mod` lines.
2. Gradle dependency verification (`gradle/verification-metadata.xml`,
   sha256 plus PGP where published):
   `./gradlew --no-daemon --write-verification-metadata pgp,sha256 --export-keys app:assembleOssRelease app:assembleOssDebug app:assemblePreviewRelease app:lintOssRelease app:testOssDebugUnitTest`
   This resolves every configuration the CI tasks use, including buildSrc,
   and writes `gradle/verification-keyring.keys` and `.gpg`. Review the
   generated file: entries with `<ignored-keys>` or sha256-only entries for
   artifacts without signatures, and AAPT2's platform-specific artifacts
   (only the host OS is recorded).
3. Check verification: `./gradlew --no-daemon --dependency-verification strict app:assembleOssRelease`,
   then the same for `app:assembleOssDebug` and `app:lintOssRelease`, using a
   fresh `GRADLE_USER_HOME` so cached artifacts are verified too.
4. Full build with the pinned toolchain: `./run lib core` (go1.24.9,
   gomobile 17d6af34), then confirm LOAD alignment 0x4000 on libgojni.so
   (`llvm-readelf -l`), then `./run init action gradle` (pinned assets,
   sha256 checks), then assemble and `apksigner verify -v` (v1/v2/v3
   present, no dependency-info block).
