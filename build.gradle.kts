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

import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers
import xyz.jpenilla.runpaper.task.RunServer
import kotlin.system.exitProcess

plugins {
    id("java")
    id("java-library")
    id("io.freefair.lombok") version "6.3.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.9"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

version = "1.16.10-1.19.2-1.21.5"
val apiVersion = "1.19"
val main = "com.volmit.adapt.Adapt"

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
registerCustomOutputTaskUnix("PsychoLT", "/Users/brianfopiano/Developer/RemoteGit/Server/plugins")
registerCustomOutputTaskUnix("the456gamer", "/home/the456gamer/projects/minecraft/adapt-testserver/plugins/update/", false)
// ==============================================================

val versions = mapOf(
    "v1_21_2" to "1.21.2-R0.1-SNAPSHOT", 
    "v1_21" to "1.21-R0.1-SNAPSHOT", 
    "v1_20_5" to "1.20.5-R0.1-SNAPSHOT", 
    "v1_20_4" to "1.20.4-R0.1-SNAPSHOT", 
    "v1_19_2" to "1.19.2-R0.1-SNAPSHOT"
)
val supported = listOf("1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20.1", "1.20.2", "1.20.4", "1.20.6", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.7")
val jdk = listOf("1.20.6", "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.7")

val MIN_HEAP_SIZE = "2G"
val MAX_HEAP_SIZE = "8G"
//Valid values are: none, truecolor, indexed256, indexed16, indexed8
val COLOR = "truecolor"

versions.forEach { (key, value) ->
    project(":version:${key}") {
        apply(plugin = "java")
        apply(plugin = "java-library")

        dependencies {
            implementation(rootProject)
            compileOnly("org.spigotmc:spigot-api:${value}")
        }
    }
}

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

        if (jdk.contains(version)) {
            javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21)}
        }
    }
}

/**
 * Expand properties into plugin yml
 */
tasks.processResources {
    filesMatching("**/plugin.yml") {
        inputs.properties(
            "name" to rootProject.name,
            "version" to version,
            "main" to main,
            "apiVersion" to apiVersion,
        )
        
        expand(inputs.properties)
    }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
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
    }

    dependencies {
        // Provided or Classpath
        compileOnly("org.projectlombok:lombok:1.18.24")
        annotationProcessor("org.projectlombok:lombok:1.18.24")

        // Cancer
        implementation("com.github.VolmitDev:Fukkit:23.6.1")
        implementation("com.github.VolmitDev:Amulet:23.5.1")
        implementation("com.github.VolmitDev:Chrono:22.9.10")
        implementation("com.github.VolmitDev:Spatial:22.11.1")
        implementation("net.byteflux:libby-velocity:1.3.1")

        implementation("io.papermc:paperlib:1.0.7")
        compileOnly("io.lettuce:lettuce-core:6.5.1.RELEASE")

        //Random Api"s
        compileOnly("com.github.DeadSilenceIV:AdvancedChestsAPI:2.9-BETA")
        compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.8")
        compileOnly("com.github.FrancoBM12:API-MagicCosmetics:2.2.8")
        compileOnly("me.clip:placeholderapi:2.11.6")
        compileOnly("com.github.LoneDev6:api-itemsadder:3.2.5")
        compileOnly("io.th0rgal:oraxen:1.182.0")
        compileOnly("com.massivecraft:Factions:1.6.9.5-U0.6.21")
        compileOnly("com.github.angeschossen:ChestProtectAPI:3.9.1")
        compileOnly("com.github.TechFortress:GriefPrevention:16.18.1")
        implementation("xyz.xenondevs:particle:1.8.4")
        implementation("com.frengor:ultimateadvancementapi-shadeable:2.6.0")
        implementation("com.jeff-media:custom-block-data:2.2.3")
        compileOnly("com.griefdefender:api:2.1.0-SNAPSHOT")
        compileOnly("io.netty:netty-all:4.1.68.Final")

        // Dynamically Loaded
        compileOnly("com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2")
        compileOnly("org.apache.commons:commons-lang3:3.12.0")
        compileOnly("com.google.code.gson:gson:2.10")
        compileOnly("com.elmakers.mine.bukkit:EffectLib:10.4")
        compileOnly("net.kyori:adventure-text-minimessage:4.18.0")
        compileOnly("net.kyori:adventure-platform-bukkit:4.3.4")
        compileOnly("it.unimi.dsi:fastutil:8.5.13")
        compileOnly("fr.skytasul:glowingentities:1.4.6")
        compileOnly("com.google.guava:guava:30.1-jre")
        compileOnly("com.github.ben-manes.caffeine:caffeine:3.0.6")
        compileOnly(fileTree("libs") { include("*.jar") })
    }
}

dependencies {
    compileOnly(project(":velocity")) {
        isTransitive = false
    }
}

/**
 * Configure Adapt for shading
 */
tasks.shadowJar {
//    minimize()
    versions.forEach {
        from(project(":version:${it.key}").tasks.jar.flatMap { archiveFile })
    }
    from(project(":velocity").tasks.jar.flatMap { archiveFile })

    append("plugin.yml")
    val lib = "com.volmit.adapt.util"
    relocate("manifold", "$lib.manifold")
    relocate("art.arcane", "$lib.arcane")
    relocate("Fukkit.extensions", "$lib.extensions")
    relocate("Amulet.extensions", "$lib.extensions")
    relocate("com.fren_gor.ultimateAdvancementAPI", "$lib.advancements")
    relocate("net.byteflux.libby", "$lib.libby")
    relocate("com.jeff_media.customblockdata", "$lib.customblocks")
    relocate("io.papermc.lib", "$lib.paperlib")
    dependencies {
        include(dependency("systems.manifold:"))
        include(dependency("xyz.xenondevs:"))
        include(dependency("com.github.VolmitDev:"))
        include(dependency("com.frengor:ultimateadvancementapi-shadeable:"))
        include(dependency("net.byteflux:"))
        include(dependency("com.jeff-media:custom-block-data:"))
        include(dependency("io.papermc:paperlib:"))
    }
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(60, "minutes")
    resolutionStrategy.cacheDynamicVersionsFor(60, "minutes")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")
}

if (JavaVersion.current().toString() != "17") {
    System.err.println()
    System.err.println("=========================================================================================================")
    System.err.println("You must run gradle on Java 17. You are using " + JavaVersion.current())
    System.err.println()
    System.err.println("=== For IDEs ===")
    System.err.println("1. Configure the project for Java 17")
    System.err.println("2. Configure the bundled gradle to use Java 17 in settings")
    System.err.println()
    System.err.println("=== For Command Line (gradlew) ===")
    System.err.println("1. Install JDK 17 from https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html")
    System.err.println("2. Set JAVA_HOME environment variable to the new jdk installation folder such as C:/Program Files/Java/jdk-17.0.1")
    System.err.println("3. Open a new command prompt window to get the new environment variables if need be.")
    System.err.println("=========================================================================================================")
    System.err.println()
    exitProcess(69)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
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
        dependsOn("adapt")
        from(tasks.named<Copy>("adapt").map { outputs.files.singleFile })
        into(file(path))
        if (doRename) rename { "Adapt.jar" }
    }
}

idea.project.settings.taskTriggers {
    afterSync(":velocity:generateTemplates")
}
