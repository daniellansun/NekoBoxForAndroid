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
        unitTests.all {
            it.extensions.configure(JacocoTaskExtension::class.java) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
}

dependencies {

    implementation(fileTree("libs"))

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
}

// gomobile output (app/libs/libcore.aar) is gitignored. When it is missing, compile
// against a no-op stub so JVM unit tests can type-check. The stub is compileOnly /
// testImplementation, so it is not packaged into the APK, and JaCoCo ignores it.
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
