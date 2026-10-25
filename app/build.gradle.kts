@file:Suppress("UnstableApiUsage")

import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.12"
}

setupApp()

android {
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    ksp {
        arg("room.incremental", "true")
        arg("room.schemaLocation", "$projectDir/schemas")
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        aidl = true
    }
    namespace = "io.nekohasekai.sagernet"
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    androidResources {
        generateLocaleConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Resources stay on the unit-test classpath so Robolectric can inflate
        // layouts. This flag does not enter the release APK.
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            it.extensions.configure(JacocoTaskExtension::class.java) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
}

// JVM unit tests load libcore classes. The gomobile AAR's JNI initializer throws
// UnsatisfiedLinkError on a plain JVM and can hang ProfileManagerJvmTest. Packaging
// tasks still consume the AAR. A unit-test-only invocation renames the known copies
// out of the way for this build and puts them back when the build ends, including
// after a failure. Backups stay next to the original so :app:clean cannot delete
// them, and fileTree excludes the backup suffix.
val libcoreCoverageAsideSuffix = ".coverage-aside"
val libcoreAarsSetAside = mutableListOf<Pair<File, File>>()

fun libcoreAarBackup(source: File) = File(source.parentFile, source.name + libcoreCoverageAsideSuffix)

fun restoreLibcoreAar(source: File) {
    val backup = libcoreAarBackup(source)
    if (source.exists() || !backup.isFile) return
    if (!backup.renameTo(source)) {
        backup.copyTo(source, overwrite = false)
        backup.delete()
    }
    logger.lifecycle("Restored ${source.path}")
}

fun knownLibcoreAars(): List<File> {
    val found = mutableListOf(file("libs/libcore.aar"), rootProject.file("libcore/libcore.aar"))
    val outputDir = rootProject.file("libcore/build/outputs/aar")
    if (outputDir.isDirectory) {
        outputDir.listFiles()?.forEach { child ->
            if (!child.isFile) return@forEach
            when {
                child.name.endsWith(".aar") -> found += child
                child.name.endsWith(".aar$libcoreCoverageAsideSuffix") ->
                    found += File(outputDir, child.name.removeSuffix(libcoreCoverageAsideSuffix))
            }
        }
    }
    return found.distinctBy { it.absolutePath }
}

val requestedTaskLeaves = gradle.startParameter.taskNames.map { it.substringAfterLast(':').lowercase() }
fun isJvmCoverageTask(name: String): Boolean {
    return name.contains("jacoco") || name.contains("unittest") || name == "test" || name == "check"
}
fun packagesAndroidArtifact(name: String): Boolean {
    return name == "build" ||
        name.startsWith("assemble") ||
        name.startsWith("bundle") ||
        name.startsWith("install") ||
        name.startsWith("package")
}
val jvmCoverageRequested = requestedTaskLeaves.any { isJvmCoverageTask(it) }
val androidArtifactRequested = requestedTaskLeaves.any { packagesAndroidArtifact(it) }

knownLibcoreAars().forEach { restoreLibcoreAar(it) }
if (jvmCoverageRequested && !androidArtifactRequested && System.getenv("SKIP_LIBCORE_ASIDE") != "1") {
    knownLibcoreAars().forEach { source ->
        if (!source.isFile) return@forEach
        val backup = libcoreAarBackup(source)
        if (backup.exists()) {
            logger.warn("Leaving ${source.path} in place; ${backup.name} already exists")
            return@forEach
        }
        val moved = if (source.renameTo(backup)) {
            true
        } else {
            source.copyTo(backup, overwrite = false)
            val deleted = source.delete()
            if (!deleted) backup.delete()
            deleted
        }
        if (moved && backup.isFile && !source.exists()) {
            libcoreAarsSetAside += source to backup
            logger.lifecycle("Set aside ${source.path} so JVM tests use the libcore stub")
        } else {
            logger.warn("Could not set aside ${source.path}")
        }
    }
    if (libcoreAarsSetAside.isNotEmpty()) {
        gradle.buildFinished {
            libcoreAarsSetAside.forEach { (source, _) -> restoreLibcoreAar(source) }
        }
    }
} else if (jvmCoverageRequested && androidArtifactRequested) {
    logger.warn(
        "libcore.aar was left on the classpath because this invocation also packages an Android " +
            "artifact. JVM unit tests can throw UnsatisfiedLinkError or hang. Run jacocoFirstPartyReport " +
            "on its own, or move app/libs/libcore.aar and libcore/libcore.aar aside yourself."
    )
}

dependencies {

    implementation(fileTree("libs") { exclude("*$libcoreCoverageAsideSuffix") })

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.5.6")
    implementation("androidx.browser:browser:1.5.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.5.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.3")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.work:work-runtime-ktx:2.8.1")
    implementation("androidx.work:work-multiprocess:2.8.1")

    implementation("com.google.android.material:material:1.8.0")
    implementation("com.google.code.gson:gson:2.9.0")

    implementation("com.github.jenly1314:zxing-lite:2.1.1")
    implementation("com.blacksquircle.ui:editorkit:2.6.0")
    implementation("com.blacksquircle.ui:language-base:2.6.0")
    implementation("com.blacksquircle.ui:language-json:2.6.0")

    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.3")
    implementation("org.yaml:snakeyaml:1.30")
    implementation("com.github.daniel-stoneuk:material-about-library:3.2.0-rc01")
    implementation("com.jakewharton:process-phoenix:2.1.2")
    implementation("com.esotericsoftware:kryo:5.2.1")
    implementation("com.google.guava:guava:31.0.1-android")
    implementation("org.ini4j:ini4j:0.5.4")

    implementation("com.simplecityapps:recyclerview-fastscroll:2.0.1") {
        exclude(group = "androidx.recyclerview")
        exclude(group = "androidx.appcompat")
    }

    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("com.github.MatrixDev.Roomigrant:RoomigrantLib:0.3.4")
    ksp("com.github.MatrixDev.Roomigrant:RoomigrantCompiler:0.3.4")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")

    testImplementation("junit:junit:4.13.2")
    // JVM UI/service tests only. Not an app dependency, so release builds
    // do not package Robolectric.
    testImplementation("org.robolectric:robolectric:4.14.1")
}

// gomobile output (app/libs/libcore.aar) is gitignored. When it is missing — including
// when a JVM unit-test invocation has set it aside — compile against a no-op stub.
// The stub is compileOnly / testImplementation, so it is not packaged into the APK,
// and JaCoCo ignores it. assemble/bundle still see the real AAR when it is present.
val libcoreAar = file("libs/libcore.aar")
val libcoreStubClasses = layout.buildDirectory.dir("libcore-stub/classes")
val compileLibcoreStub = tasks.register<JavaCompile>("compileLibcoreStub") {
    source = fileTree("src/libcoreStub/java") { include("**/*.java") }
    classpath = files()
    destinationDirectory.set(libcoreStubClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}
val libcoreStubJar = tasks.register<Jar>("libcoreStubJar") {
    dependsOn(compileLibcoreStub)
    from(libcoreStubClasses)
    archiveFileName.set("libcore-stub.jar")
    destinationDirectory.set(layout.buildDirectory.dir("libcore-stub"))
}
if (!libcoreAar.exists()) {
    dependencies {
        val stub = files(libcoreStubJar.flatMap { it.archiveFile })
        compileOnly(stub)
        testImplementation(stub)
    }
    tasks.matching { it.name.startsWith("compile") && (it.name.contains("Kotlin") || it.name.contains("Java")) }
        .configureEach {
            dependsOn(libcoreStubJar)
        }
}

val buildHevTun by tasks.registering {
    val hevAbis = listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    doLast {
        val missing = hevAbis.any { !file("src/main/jniLibs/$it/libhev-socks5-tunnel.so").exists() }
        if (missing || System.getenv("FORCE_HEV") == "1") {
            exec {
                commandLine("bash", rootProject.file("buildScript/compile-hevtun.sh").absolutePath)
            }
        }
    }
}

tasks.named("preBuild") {
    // JNI is not required to compile or run JVM unit tests. SKIP_HEV=1 keeps
    // coverage runs from invoking the NDK build of libhev-socks5-tunnel.so.
    if (System.getenv("SKIP_HEV") != "1") {
        dependsOn(buildHevTun)
    }
}

// First-party class-file filters. Third-party Maven/JitPack jars are not in
// the app compile output. Generated Android/Room/AIDL/ViewBinding classes are.
val firstPartyClassIncludes = listOf(
    "io/nekohasekai/sagernet/**/*.class",
    "moe/matsuri/nb4a/**/*.class",
    "com/github/shadowsocks/**/*.class",
)
val firstPartyClassExcludes = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.class",
    "**/BR.class",
    "**/Manifest.class",
    "**/Manifest\$*.class",
    "**/databinding/**",
    "**/*_Impl.class",
    "**/*_Impl\$*.class",
    "**/ISagerNetService.class",
    "**/ISagerNetService\$*.class",
    "**/ISagerNetServiceCallback.class",
    "**/ISagerNetServiceCallback\$*.class",
)

tasks.withType(Test::class.java).configureEach {
    maxHeapSize = "2g"
    extensions.configure(JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val firstPartyClassesDir = layout.buildDirectory.dir("jacoco/firstPartyClasses")

// Copy one class file per relative path so JaCoCo never sees duplicates from
// Kotlin and Java output directories, and so generated classes stay excluded.
val syncFirstPartyClasses = tasks.register("syncFirstPartyClasses") {
    group = "verification"
    description = "Collect ossDebug class files for the first-party JaCoCo report."
    dependsOn("testOssDebugUnitTest")
    outputs.dir(firstPartyClassesDir)
    outputs.upToDateWhen { false }
    doLast {
        val dest = firstPartyClassesDir.get().asFile
        if (dest.exists()) dest.deleteRecursively()
        dest.mkdirs()

        val buildRoot = layout.buildDirectory.get().asFile
        val discovered = mutableListOf<File>()
        // Kotlin output is tmp/kotlin-classes/<variant> (the directory is not named
        // "classes"). Java output is a directory literally named "classes".
        listOf(File(buildRoot, "tmp"), File(buildRoot, "intermediates")).forEach baseLoop@{ base ->
            if (!base.isDirectory) return@baseLoop
            base.walkTopDown().maxDepth(8).forEach candidateLoop@{ candidate ->
                if (!candidate.isDirectory) return@candidateLoop
                val path = candidate.invariantSeparatorsPath
                if (path.contains("UnitTest") || path.contains("AndroidTest") || path.contains("androidTest")) return@candidateLoop
                if (path.contains("/jacoco/") || path.contains("transforms") || path.contains("libcore-stub")) return@candidateLoop
                val owned = File(candidate, "io/nekohasekai/sagernet").isDirectory ||
                    File(candidate, "moe/matsuri/nb4a").isDirectory ||
                    File(candidate, "com/github/shadowsocks").isDirectory
                if (owned) discovered += candidate
            }
        }
        val ordered = discovered.distinctBy { it.canonicalPath }.sortedBy { file ->
            val path = file.invariantSeparatorsPath
            when {
                path.contains("tmp/kotlin-classes/ossDebug") -> 0
                path.contains("compileOssDebugJavaWithJavac/classes") -> 1
                path.contains("compileOssDebugKotlin/classes") -> 2
                else -> 5
            }
        }
        val seen = mutableSetOf<String>()
        var copied = 0
        ordered.forEach rootLoop@{ classesRoot ->
            fileTree(classesRoot) {
                include(firstPartyClassIncludes)
                exclude(firstPartyClassExcludes)
            }.files.forEach classLoop@{ classFile ->
                val rel = classFile.relativeTo(classesRoot).invariantSeparatorsPath
                if (!seen.add(rel)) return@classLoop
                val out = File(dest, rel)
                out.parentFile.mkdirs()
                classFile.copyTo(out, overwrite = false)
                copied++
            }
        }
        logger.lifecycle("syncFirstPartyClasses copied $copied class files from ${ordered.size} roots")
        ordered.forEach { logger.lifecycle("syncFirstPartyClasses root: ${it.absolutePath}") }
        if (copied == 0) {
            throw GradleException(
                "No first-party class files matched io.nekohasekai.sagernet / " +
                    "moe.matsuri.nb4a / com.github.shadowsocks."
            )
        }
    }
}

// android.jar stubs android.util.Base64 and org.json so they return null. Share-link
// parsers call both, so unit tests load real implementations ahead of android.jar.
// These jars are test-only: they are not app dependencies and are not in JaCoCo.
val unitTestOrgJson = configurations.create("unitTestOrgJson") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
}
dependencies.add("unitTestOrgJson", "org.json:json:20240303")
val compileAndroidBase64 = tasks.register<JavaCompile>("compileAndroidBase64") {
    source = fileTree("src/testAndroidBase64/java") { include("**/*.java") }
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("android-base64/classes"))
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}
val androidBase64Jar = tasks.register<Jar>("androidBase64Jar") {
    dependsOn(compileAndroidBase64)
    from(compileAndroidBase64.flatMap { it.destinationDirectory })
    archiveFileName.set("android-base64.jar")
    destinationDirectory.set(layout.buildDirectory.dir("android-base64"))
    manifest {
        attributes("Premain-Class" to "io.nekohasekai.sagernet.testutil.AndroidBase64Agent")
    }
}
afterEvaluate {
    tasks.withType<Test>().configureEach {
        dependsOn(androidBase64Jar)
        val agent = androidBase64Jar.flatMap { it.archiveFile }
        classpath = files(agent) + unitTestOrgJson + classpath
        val agentPath = agent.get().asFile.absolutePath
        jvmArgs("-javaagent:$agentPath")
    }
}

tasks.register<JacocoReport>("jacocoFirstPartyReport") {
    group = "verification"
    description = "ossDebug JVM unit-test coverage for first-party app classes only."
    dependsOn(syncFirstPartyClasses)

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/firstParty/html"))
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/firstParty/jacocoFirstParty.xml"))
        csv.outputLocation.set(layout.buildDirectory.file("reports/jacoco/firstParty/jacocoFirstParty.csv"))
    }

    val execFile = layout.buildDirectory.file("jacoco/testOssDebugUnitTest.exec")
    executionData.setFrom(execFile)
    sourceDirectories.setFrom(files("src/main/java"))
    classDirectories.setFrom(firstPartyClassesDir)

    doFirst {
        if (!execFile.get().asFile.exists()) {
            throw GradleException(
                "Missing JaCoCo execution data at ${execFile.get().asFile}. Run testOssDebugUnitTest first."
            )
        }
    }
}
