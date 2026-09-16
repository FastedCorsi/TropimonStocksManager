import java.security.MessageDigest

plugins {
    id("fabric-loom") version "1.15.5"
    id("maven-publish")
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

// Build-only privacy tooling: Java 21 is the only additional runtime needed.
// This source set is never included in the game or sources JAR.
val privacy by sourceSets.creating {
    java.setSrcDirs(listOf("tools/privacy"))
}
sourceSets.test {
    compileClasspath += privacy.output
    runtimeClasspath += privacy.output
}
val privacyCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Inspect shareable sources and tracked inventory; findings never print private values."
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.stocksmanager.build.PrivacyCheck")
    args("--sources")
    workingDir(projectDir)
}
val privacyArtifacts by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Inspect final and development JARs, including nested archives and compiled constants."
    dependsOn(tasks.remapJar, tasks.named("remapSourcesJar"))
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.stocksmanager.build.PrivacyCheck")
    args("--archives",
            "build/libs/TropimonStocksManager-${project.version}.jar",
            "build/libs/TropimonStocksManager-${project.version}-sources.jar",
            "build/devlibs/TropimonStocksManager-${project.version}-dev.jar",
            "build/devlibs/TropimonStocksManager-${project.version}-sources.jar")
    workingDir(projectDir)
}
tasks.check { dependsOn(privacyCheck, privacyArtifacts) }
tasks.jar { dependsOn(privacyCheck) }
tasks.named("sourcesJar") { dependsOn(privacyCheck) }
tasks.remapJar { finalizedBy(privacyArtifacts) }
tasks.named("remapSourcesJar") { finalizedBy(privacyArtifacts) }
tasks.withType<AbstractArchiveTask>().configureEach {
    // Defense in depth; preserve originals and third-party licenses.
    exclude("**/.git/**", "**/.gradle/**", "**/.idea/**", "**/.env*", "**/*.log",
            "**/*.bak", "**/*.backup", "**/*.private", "**/*.iml", "**/promo/**",
            "**/config/**", "**/logs/**", "**/screenshots/**", "**/saves/**", "**/backups/**",
            "**/mod-archive/**", "**/town-chests.json", "**/options.txt",
            "**/launcher_accounts.json", "**/launcher_profiles.json")
}

// Opt-in real-client smoke mod. It is never included in the distributable JAR.
val smoke by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath
}
val smokeJar by tasks.registering(Jar::class) {
    dependsOn(privacyCheck)
    from(smoke.output)
    archiveClassifier.set("smoke-dev")
    destinationDirectory.set(layout.buildDirectory.dir("smoke"))
}
tasks.register<net.fabricmc.loom.task.RemapJarTask>("remapSmokeJar") {
    dependsOn(smokeJar)
    inputFile.set(smokeJar.flatMap { it.archiveFile })
    archiveClassifier.set("smoke")
    destinationDirectory.set(layout.buildDirectory.dir("smoke"))
    addNestedDependencies.set(false)
    finalizedBy("privacySmoke")
}
tasks.register<JavaExec>("privacySmoke") {
    group = "verification"
    dependsOn("remapSmokeJar")
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.stocksmanager.build.PrivacyCheck")
    args("--archives",
            "build/smoke/TropimonStocksManager-${project.version}-smoke.jar",
            "build/smoke/TropimonStocksManager-${project.version}-smoke-dev.jar")
    workingDir(projectDir)
}

val shareJar by tasks.registering(Sync::class) {
    dependsOn(privacyArtifacts)
    from(tasks.remapJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("deliverables/share"))
    rename { "TropimonStocksManager-${project.version}-SHARE.jar" }
}
val localJar by tasks.registering(Sync::class) {
    dependsOn(privacyArtifacts)
    from(tasks.remapJar.flatMap { it.archiveFile })
    from("tools/install-local-deferred.ps1")
    into(layout.buildDirectory.dir("deliverables/local"))
    rename { name -> if (name.endsWith(".jar"))
        "TropimonStocksManager-${project.version}-LOCAL.jar" else name }
}
val writeLocalHash by tasks.registering {
    dependsOn(localJar)
    doLast {
        val jar = layout.buildDirectory.file(
                "deliverables/local/TropimonStocksManager-${project.version}-LOCAL.jar").get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = jar.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02X".format(it) }
        }
        jar.resolveSibling(jar.name + ".sha256").writeText(hash + System.lineSeparator())
    }
}
val prepareDeliverables by tasks.registering {
    group = "distribution"
    dependsOn(shareJar, writeLocalHash)
}
val privacyDeliverables by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(prepareDeliverables)
    classpath = privacy.runtimeClasspath
    mainClass.set("fr.tropimon.stocksmanager.build.PrivacyCheck")
    args("--archives", "build/deliverables/share", "build/deliverables/local")
    workingDir(projectDir)
}
tasks.register("deliver") {
    group = "distribution"
    description = "Build, test and privacy-check separate local/share JARs of the same version."
    dependsOn(tasks.build, "remapSmokeJar", privacyDeliverables)
}

val cobblemonMinimumVersion = property("cobblemon_min_version") as String
val verifyCobblemonCompatibility = tasks.register("verifyCobblemonCompatibility") {
    group = "verification"
    description = "Refuse les anciennes bornes Cobblemon avant de fabriquer un JAR."
    inputs.property("cobblemonMinimumVersion", cobblemonMinimumVersion)
    inputs.file("src/main/resources/fabric.mod.json")
    doLast {
        val expected = "\"cobblemon\": \">=$cobblemonMinimumVersion\""
        check(file("src/main/resources/fabric.mod.json").readText().contains(expected)) {
            "fabric.mod.json doit déclarer Cobblemon >=$cobblemonMinimumVersion sans borne maximale artificielle."
        }
    }
}

tasks.processResources {
    dependsOn(verifyCobblemonCompatibility)
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.register("installTropimonLocal") {
    group = "distribution"
    description = "Arm the verified deferred installer; it never stops Minecraft or the launcher."
    dependsOn(privacyDeliverables)
    doLast {
        val script = layout.buildDirectory.file(
                "deliverables/local/install-local-deferred.ps1").get().asFile
        ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", script.absolutePath)
                .directory(script.parentFile).start()
    }
}


val prepareReleaseDelivery = tasks.register("prepareReleaseDelivery") {
    group = "distribution"
    description = "Produit les JAR local et partageable vérifiés de la même version."
    dependsOn(tasks.build)
    doLast {
        val source = tasks.remapJar.get().archiveFile.get().asFile
        val deliveryRoot = layout.buildDirectory.dir("release").get().asFile
        val shareDirectory = deliveryRoot.resolve("shareable")
        val localDirectory = deliveryRoot.resolve("local")
        shareDirectory.deleteRecursively()
        localDirectory.deleteRecursively()
        shareDirectory.mkdirs()
        localDirectory.mkdirs()

        fun copyAndHash(target: File) {
            source.copyTo(target, overwrite = true)
            val digest = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            target.resolveSibling(target.name + ".sha256").writeText(hash + System.lineSeparator())
        }

        copyAndHash(shareDirectory.resolve("TropimonStocksManager-${project.version}+1.21.1.jar"))
        copyAndHash(localDirectory.resolve("TropimonStocksManager-${project.version}+1.21.1-LOCAL.jar"))
        file("tools/install-local-deferred.ps1")
            .copyTo(localDirectory.resolve("install-local-deferred.ps1"), overwrite = true)
    }
}

tasks.register("armReleaseLocal") {
    group = "distribution"
    description = "Arme l'installation locale différée sans arrêter Minecraft ni le launcher."
    dependsOn(prepareReleaseDelivery)
    doLast {
        val script = layout.buildDirectory.file("release/local/install-local-deferred.ps1").get().asFile
        ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
            "-ExecutionPolicy", "Bypass", "-File", script.absolutePath)
            .directory(script.parentFile)
            .start()
    }
}


