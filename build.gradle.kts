@file:Suppress("UNUSED_VARIABLE")

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.*

repositories {
    mavenCentral()
}

plugins {
    kotlin("multiplatform")
    id("maven-publish")
}

val vulkanVersion: String by project

val group: String by project
val bintrayOrg: String by project
val bintrayRepo: String by project

project.group = group
project.version = vulkanVersion

val nativeLibsDir = buildDir.resolve("nativeLibs")
val downloadsDir  = buildDir.resolve("tmp")

val vulkanDir = nativeLibsDir.resolve("vulkan-$vulkanVersion")

publishing {
    repositories {
        maven {
            name = "Bintray"
            url = uri("https://api.bintray.com/maven/$bintrayOrg/$bintrayRepo/${project.name}/;publish=1;override=1")
            credentials {
                username = System.getenv("BINTRAY_USER")
                password = System.getenv("BINTRAY_API_KEY")
            }
        }
    }
}

tasks {
    val setupVulkan by registering {
        downloadNativeLibFromGithubAsset(
            url = "https://github.com/KhronosGroup/Vulkan-Headers/archive",
            asset = "v$vulkanVersion.zip",
            dest = vulkanDir
        )
    }

    val buildFromMacos by registering {
        tasksFiltering("compile", "", false, "ios", "macos").forEach {
            dependsOn(this@tasks.getByName(it))
        }
    }

    val publishFromMacos by registering {
        tasksFiltering("publish", "BintrayRepository", false, "ios", "macos").forEach {
            dependsOn(this@tasks.getByName(it))
        }
    }

    val buildFromLinux by registering {
        (tasksFiltering("compile", "", false, "android", "linux")).forEach {
            dependsOn(this@tasks.getByName(it))
        }
    }

    val publishFromLinux by registering {
        tasksFiltering("publish", "BintrayRepository", false, "android", "linux").forEach {
            dependsOn(this@tasks.getByName(it))
        }
    }

    val buildFromWindows by registering {
        tasksFiltering("compile", "", false, "mingw").forEach {
            dependsOn(this@tasks.getByName(it))
        }
    }

    val publishFromWindows by registering {
        tasksFiltering("publish", "BintrayRepository", false, "mingw").forEach {
            dependsOn(this@tasks.getByName(it))
        }
    }
}

kotlin {
    macosX64()
    mingwX64()
    linuxX64()

    androidNativeArm64()
    androidNativeX64()
    androidNativeX86()

    iosArm32()
    iosArm64()
    iosX64()

    targets.withType<KotlinNativeTarget>().forEach {
        it.compilations.named("main") {
            cinterops.create("vulkan") {
                tasks.named(interopProcessingTaskName) {
                    dependsOn(tasks.named("setupVulkan"))
                }

                includeDirs(vulkanDir.resolve("include"))
            }
        }
    }
}

fun downloadNativeLibFromGithubAsset(url: String, asset: String, dest: File, runAfter: (() -> Unit)? = null) {
    if (dest.exists()) return

    nativeLibsDir.mkdirs()
    if (!downloadsDir.exists()) downloadsDir.mkdirs()

    println("Downloading Vulkan $asset ...")
    val archive = downloadsDir.resolve(asset)
    download("$url/$asset", archive)

    println("Expanding Vulkan $asset ...")
    copy {
        from(zipTree(archive)) {
            includeEmptyDirs = false
            eachFile {
                relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
            }
        }
        into(dest)
    }

    delete(archive)
    runAfter?.invoke()
}

fun download(url : String, dest: File) {
    ant.invokeMethod("get", mapOf("src" to url, "dest" to dest))
}

fun tasksFiltering(prefix: String, suffix: String, test: Boolean, vararg platforms: String) = tasks.names
        .asSequence()
        .filter { it.startsWith(prefix, ignoreCase = true) }
        .filter { it.endsWith(suffix, ignoreCase = true) }
        .filter { it.endsWith("test", ignoreCase = true) == test }
        .filter { it.contains("test", ignoreCase = true) == test }
        .filter { task -> platforms.any { task.contains(it, ignoreCase = true) } }
        .toMutableList()

fun String.runCommand(workingDir: File = file("./")): String {
    val parts = this.split("\\s".toRegex())
    val proc = ProcessBuilder(*parts.toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

    proc.waitFor(1, TimeUnit.MINUTES)
    return proc.inputStream.bufferedReader().readText().trim()
}