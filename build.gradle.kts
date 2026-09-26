plugins {
    `java-library`
    `maven-publish`
    id("io.github.goooler.shadow").version("8.1.7")
}

repositories {
    mavenLocal()
    //CrypticLib
    maven("https://repo2.crypticlib.com/releases/")
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation(project(":hook"))
    implementation("com.crypticlib:bukkit:${rootProject.findProperty("crypticlibVer")}")
}

version = "${rootProject.findProperty("pluginVer")}"
group = "pers.yufiria.landguard"
val gitHash: String by lazy {
    runCatching {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(project.rootDir)
            .start()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
        } else {
            null
        }
    }.getOrNull() ?: "unknown"
}

java.sourceCompatibility = JavaVersion.VERSION_21
java.targetCompatibility = JavaVersion.VERSION_21

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

val crypticlibRelocate = "pers.yufiria.landguard.crypticlib"

tasks {
    val props = HashMap<String, String>()
    props["version"] = "$version-$gitHash"
    processResources {
        outputs.upToDateWhen { false }
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
    build {
        dependsOn(shadowJar)
    }
    compileJava {
        options.encoding = "UTF-8"
    }
    shadowJar {
        archiveFileName.set("LandGuard-$version.jar")
        relocate("crypticlib", crypticlibRelocate)
        relocate("org.bstats", "pers.yufiria.landguard.bstats")
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "io.github.goooler.shadow")
    version = rootProject.version
    java.sourceCompatibility = JavaVersion.VERSION_21
    java.targetCompatibility = JavaVersion.VERSION_21
    repositories {
        maven("https://repo.papermc.io/repository/maven-public/")
        //CrypticLib
        maven("https://repo2.crypticlib.com/releases/")
    }
    dependencies {
        compileOnly("org.jetbrains:annotations:${rootProject.findProperty("jetbrainsAnnotationsVer")}")
        compileOnly("com.crypticlib:bukkit:${rootProject.findProperty("crypticlibVer")}")
    }
    tasks {
        build {
            dependsOn(shadowJar)
        }
        compileJava {
            options.encoding = "UTF-8"
        }
        shadowJar {
            relocate("crypticlib", crypticlibRelocate)
            relocate("org.bstats", "pers.yufiria.landguard.bstats")
        }
    }
}
