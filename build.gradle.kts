/*
 * Adapt is Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import io.freefair.gradle.plugins.lombok.LombokPlugin
import io.github.slimjar.func.slimjarHelper
import io.github.slimjar.resolver.data.Mirror
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import xyz.jpenilla.runpaper.task.RunServer
import kotlin.system.exitProcess

plugins {
    `java-library`
    alias(libs.plugins.lombok)
    alias(libs.plugins.shadow)
    alias(libs.plugins.runPaper)
    alias(libs.plugins.runVelocity)
    alias(libs.plugins.idea)
    alias(libs.plugins.slimjar)
    alias(libs.plugins.kotlin.jvm)
}

version = "1.18.0-1.20.2-1.21.11"
val apiVersion = "1.20"
val main = "art.arcane.adapt.Adapt"

// ADD YOURSELF AS A NEW LINE IF YOU WANT YOUR OWN BUILD TASK GENERATED
// ======================== WINDOWS =============================
registerCustomOutputTask("Cyberpwn", "C://Users/cyberpwn/Documents/development/server/plugins")
registerCustomOutputTask("Psycho", "C://Dan/MinecraftDevelopment/Server/plugins")
registerCustomOutputTask("ArcaneArts", "C://Users/arcane/Documents/development/server/plugins")
registerCustomOutputTask("Vatuu", "D://Minecraft/Servers/1.20/plugins")
registerCustomOutputTask("Nowhere", "E://Desktop/server/plugins")
registerCustomOutputTask("CrazyDev22", "C://Users/Julian/Desktop/server/plugins")
registerCustomOutputTask("Pixel", "D://Iris Dimension Engine//1.20.4 - Development//plugins")
// ========================== UNIX ==============================
registerCustomOutputTaskUnix("CyberpwnLT", "/Users/danielmills/development/server/plugins")
registerCustomOutputTaskUnix("PsychoLT", "/Users/brianfopiano/Developer/RemoteGit/[Minecraft Server]/plugins")
registerCustomOutputTaskUnix("the456gamer", "/home/the456gamer/projects/minecraft/adapt-testserver/plugins/update/", false)
// ==============================================================

val supported = listOf("1.20.2", "1.20.4", "1.20.6", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11")
val jdk = listOf("1.20.2", "1.20.4")

val MIN_HEAP_SIZE = "2G"
val MAX_HEAP_SIZE = "8G"
//Valid values are: none, truecolor, indexed256, indexed16, indexed8
val COLOR = "truecolor"

supported.forEach { version ->
    tasks.register<RunServer>("runServer-$version") {
        group = "servers"
        minecraftVersion(version)
        minHeapSize = MIN_HEAP_SIZE
        maxHeapSize = MAX_HEAP_SIZE
        systemProperty("disable.watchdog", "")
        systemProperty("net.kyori.ansi.colorLevel", COLOR)
        systemProperty("com.mojang.eula.agree", true)
        pluginJars(tasks.shadowJar.flatMap { it.archiveFile} )
        runDirectory.convention(layout.buildDirectory.dir("run/$version"))

        if (!jdk.contains(version)) {
            javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21)}
        }
    }
}

tasks.runVelocity {
    group = "servers"
    velocityVersion(libs.versions.velocity.get())
    runDirectory.convention(layout.buildDirectory.dir("run/velocity"))
}

/**
 * Expand properties into plugin yml
 */
tasks.processResources {
    inputs.properties(
        "name" to rootProject.name,
        "version" to version,
        "main" to main,
        "apiVersion" to apiVersion,
    )

    filesMatching("**/plugin.yml") {
        expand(inputs.properties)
    }
}

allprojects {
    apply<JavaPlugin>()
    apply<LombokPlugin>()

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.org/repository/maven-public")
        maven("https://mvn.lumine.io/repository/maven-public/")
        maven("https://nexus.frengor.com/repository/public/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
        maven("https://repo.glaremasters.me/repository/bloodshot/")
        maven("https://maven.enginehub.org/repo/")
        maven("https://repo.oraxen.com/releases")
        maven("https://repo.alessiodp.com/releases")
        maven("https://jitpack.io")
    }

    /**
     * We need parameter meta for the decree command system
     */
    tasks.compileJava {
        options.compilerArgs.add("-parameters")
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}

sourceSets {
    main {
        java.srcDir("../VolmLib/shared/src/main/java")
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir("../VolmLib/shared/src/main/kotlin")
}

dependencies {
    implementation(project(":velocity"))
    implementation(slimjarHelper("spigot"))
    implementation(slimjarHelper("velocity"))
    implementation(libs.platformUtils) {
        isTransitive = false
    }

    compileOnly(libs.spigot)

    // Cancer
    slimApi(libs.fukkit)
    slimApi(libs.amulet)
    slimApi(libs.chrono)
    slimApi(libs.spatial)
    implementation(libs.kotlin.coroutines)

    // Dynamically Loaded
    slimApi(libs.adventure.minimessage)
    slimApi(libs.adventure.platform)
    slimApi(libs.adventure.gson)
    slimApi(libs.adventure.legacy)
    slimApi(libs.lettuce)
    slimApi(libs.particle)
    slimApi(libs.ultimateAdvancementApi)
    slimApi(libs.customBlockData)
    slimApi(libs.lur)
    slimApi(libs.lang3)
    slimApi(libs.effectLib)
    slimApi(libs.gson)
    slimApi(libs.toml4j)
    slimApi(libs.fastutil)
    slimApi(libs.glowingentities)
    slimApi(libs.caffeine)

    //Random Api's
    compileOnlyApi("me.clip:placeholderapi:2.11.6")
    compileOnlyApi("com.github.DeadSilenceIV:AdvancedChestsAPI:2.9-BETA")
    compileOnlyApi("com.sk89q.worldguard:worldguard-bukkit:7.0.8")
    compileOnlyApi("com.github.FrancoBM12:API-MagicCosmetics:2.2.8")
    compileOnlyApi("com.massivecraft:Factions:1.6.9.5-U0.6.21")
    compileOnlyApi("com.github.angeschossen:ChestProtectAPI:3.9.1")
    compileOnlyApi("com.github.TechFortress:GriefPrevention:16.18.1")
    compileOnlyApi("com.griefdefender:api:2.1.0-SNAPSHOT")
    compileOnlyApi(fileTree("libs") { include("*.jar") })
}

val lib = "art.arcane.adapt.util"
slimJar {
    mirrors = listOf(Mirror(
        uri("https://maven-central.storage-download.googleapis.com/maven2").toURL(),
        uri("https://repo.maven.apache.org/maven2/").toURL()
    ))

    relocate("manifold", "$lib.manifold")
    relocate("art.arcane", "$lib.arcane")
    relocate("Fukkit.extensions", "$lib.extensions")
    relocate("Amulet.extensions", "$lib.extensions")
    relocate("com.fren_gor.ultimateAdvancementAPI", "$lib.advancements")
    relocate("net.byteflux.libby", "$lib.libby")
    relocate("com.jeff_media.customblockdata", "$lib.customblocks")
}

/**
 * Configure Adapt for shading
 */
tasks.shadowJar {
//    minimize()
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(60, "minutes")
    resolutionStrategy.cacheDynamicVersionsFor(60, "minutes")
}

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    System.err.println()
    System.err.println("=========================================================================================================")
    System.err.println("You must run gradle on Java 21 or newer. You are using " + JavaVersion.current())
    System.err.println()
    System.err.println("=== For IDEs ===")
    System.err.println("1. Configure the project for Java 21")
    System.err.println("2. Configure the bundled gradle to use Java 21 in settings")
    System.err.println()
    System.err.println("=== For Command Line (gradlew) ===")
    System.err.println("1. Install JDK 21 from https://www.oracle.com/java/technologies/downloads/#java21")
    System.err.println("2. Set JAVA_HOME environment variable to the new jdk installation folder such as C:/Program Files/Java/jdk-21")
    System.err.println("3. Open a new command prompt window to get the new environment variables if need be.")
    System.err.println("=========================================================================================================")
    System.err.println()
    exitProcess(69)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks {
    build { dependsOn(shadowJar) }

    register<Copy>("adapt") {
        from(shadowJar.flatMap { it.archiveFile })
        into(layout.buildDirectory)
        rename { "Adapt-$version.jar" }
    }
}

fun registerCustomOutputTask(name: String, path: String, doRename: Boolean = true) {
    if (!System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }
    createOutputTask(name, path, doRename)
}

fun registerCustomOutputTaskUnix(name: String, path: String, doRename: Boolean = true) {
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        return
    }

    createOutputTask(name, path, doRename)
}

fun createOutputTask(name: String, path: String, doRename: Boolean = true) {
    tasks.register<Copy>("build$name") {
        group = "development"
        outputs.upToDateWhen { false }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        dependsOn(tasks.shadowJar)
        from(tasks.shadowJar.flatMap { it.archiveFile })
        into(file(path))
        if (doRename) rename { "Adapt.jar" }
    }
}

idea.project.settings.taskTriggers {
    afterSync(":velocity:generateTemplates")
}
