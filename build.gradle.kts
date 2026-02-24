plugins {
    java
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "com.blockforge"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://maven.enginehub.org/repo/")
    maven { url = uri("file://${rootDir}/../GriefPrevetionFlagsReborn/build/libs") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly(files("../GriefPrevetionFlagsReborn/build/libs/GriefPreventionFlagsReborn-1.0.0.jar"))
    compileOnly("com.github.TechFortress:GriefPrevention:16.18.4")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")
    implementation("org.bstats:bstats-bukkit:3.1.0")
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.bstats", "com.blockforge.horizonutilities.libs.bstats")
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    enabled = false
}
