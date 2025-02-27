import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.konan.properties.loadProperties

loadPlatformProperties()

val isCI = System.getenv("CI") != null
val platformVersion = prop("platformVersion").toInt()
val ideVersion = prop("ideVersion")
val junitVersion = prop("junitVersion")
val lspLibraryVersion = rootProject.libs.versions.lsp.library.get()

// https://plugins.jetbrains.com/docs/intellij/setting-up-theme-environment.html#add-jdk-and-intellij-platform-plugin-sdk
val javaPlatformVersion = JavaVersion.VERSION_21

plugins {
    java
    idea
    kotlin("jvm")

    // https://github.com/JetBrains/intellij-platform-gradle-plugin/releases
    id("org.jetbrains.intellij.platform") version "2.3.1-SNAPSHOT"

    // Plugin from buildSrc to relocate the LSP package
    // fixme Until the plugin is approved on the Gradle Plugin Portal,
    //    follow this URL to install a snapshot locally:
    //    https://github.com/jansorg/lsp-gradle-plugin?tab=readme-ov-file#publish-locally
    id("dev.j-a.ide.lsp") version "0.3.0-SNAPSHOT"
}

allprojects {
    group = "dev.j-a.ide.gosupport"

    apply {
        plugin("java")
        plugin("idea")
        plugin("kotlin")

        when {
            project == rootProject -> plugin("org.jetbrains.intellij.platform")
            else -> plugin("org.jetbrains.intellij.platform.module")
        }
    }

    repositories {
        mavenLocal()
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }

    // Replace version "default" of LSP and DAP libraries with the version for the current platform
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "dev.j-a.ide" && requested.version == "default") {
                val suffix = if (lspLibraryVersion.endsWith("-SNAPSHOT")) "-SNAPSHOT" else ""
                useVersion(lspLibraryVersion.removeSuffix(suffix) + "." + platformVersion + suffix)
                because("LSP platform version")
            }
        }
    }

    dependencies {
        implementation(rootProject.libs.lsp.client)

        // https://mvnrepository.com/artifact/org.junit.jupiter
        testImplementation(platform("org.junit:junit-bom:$junitVersion"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.junit.jupiter:junit-jupiter-params")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        // unfortunately, we still need JUnit4
        // https://mvnrepository.com/artifact/junit/junit
        testImplementation("junit:junit:4.13.2")
        // Jupiter vintage tests
        testRuntimeOnly("org.junit.vintage:junit-vintage-engine")

        intellijPlatform {
            create(IntelliJPlatformType.IntellijIdeaCommunity, ideVersion)

            testFramework(TestFrameworkType.Bundled)
            testFramework(TestFrameworkType.Platform)
            testFramework(TestFrameworkType.JUnit5)
        }
    }

    java {
        toolchain {
            sourceCompatibility = javaPlatformVersion
            targetCompatibility = javaPlatformVersion
        }
    }

    kotlin {
        jvmToolchain(javaPlatformVersion.majorVersion.toInt())
    }

    idea {
        module {
            isDownloadSources = !isCI
            isDownloadJavadoc = !isCI

            excludeDirs.add(file(project.layout.buildDirectory.get().asFile))
        }
    }

    intellijPlatform {
        buildSearchableOptions = false
        instrumentCode = false
    }

    tasks {
        withType<Zip>().configureEach {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        withType<JavaCompile>().configureEach {
            options.isDeprecation = true
            options.isDebug = true
        }
    }
}

project(":") {
    shadowLSP {
        packagePrefix = "dev.j_a.gosupport.lsp_support"
    }

    intellijPlatform {
        pluginVerification {
            ides {
                recommended()
            }
        }
    }

    dependencies {
        intellijPlatform {
            pluginVerifier()
        }

        // Bundle JARs of subprojects into the composed plugin JAR
        implementation(project(":core")) {
            intellijPlatformPluginModule(this)
        }
    }
}

fun prop(name: String): String {
    return extra.properties[name] as? String ?: error("Property `$name` is not defined in gradle.properties")
}

fun loadPlatformProperties() {
    val platformVersion = prop("platformVersion").toInt()
    val platformPropertiesPath = rootDir.resolve("gradle-$platformVersion.properties")
    loadProperties(platformPropertiesPath.toString()).forEach { (key, value) ->
        rootProject.extra.set(key.toString(), value)
    }
}