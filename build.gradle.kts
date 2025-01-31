import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.konan.properties.loadProperties

loadPlatformProperties()

val isCI = System.getenv("CI") != null
val platformVersion = prop("platformVersion").toInt()

val ideVersion = prop("ideVersion")

// https://plugins.jetbrains.com/docs/intellij/setting-up-theme-environment.html#add-jdk-and-intellij-platform-plugin-sdk
val javaPlatformVersion = JavaVersion.VERSION_21

val junitVersion by properties

val lspLibraryVersion = prop("lspLibraryVersion").let {
    when {
        it.contains("-SNAPSHOT") -> it.removeSuffix("-SNAPSHOT") + ".$platformVersion-SNAPSHOT"
        else -> "$it.$platformVersion"
    }
}

plugins {
    java
    idea
    kotlin("jvm")

    // https://github.com/JetBrains/intellij-platform-gradle-plugin/releases
    id("org.jetbrains.intellij.platform") version "2.2.2-SNAPSHOT"
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
        // LSP4J snapshots
        maven("https://oss.sonatype.org/content/repositories/snapshots/")

        intellijPlatform {
            defaultRepositories()
        }
    }

    dependencies {
        // LSP
        implementation("dev.j-a.ide:lsp-client:$lspLibraryVersion") {
            exclude("org.jetbrains.kotlin")
        }

        // DAP
        implementation("dev.j-a.ide:dap-client:$lspLibraryVersion") {
            exclude("org.jetbrains.kotlin")
        }

        // https://mvnrepository.com/artifact/org.junit.jupiter
        testImplementation(platform("org.junit:junit-bom:$junitVersion"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.junit.jupiter:junit-jupiter-params")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        // unfortunately, we still need JUnit4
        // https://mvnrepository.com/artifact/junit/junit
        testImplementation("junit:junit:4.13.2")

        // vintage tests
        testImplementation("junit:junit:4.13.2")
        testRuntimeOnly("org.junit.vintage:junit-vintage-engine")

        sourceSets {
            main {
                val resourceDirs = mutableListOf<File>()
                collectKotlinSourceDirs(resourceDirs = resourceDirs)
                resources.srcDirs(resourceDirs)
            }
        }

        kotlin {
            val kotlinSourceDirs = mutableListOf<File>()
            val kotlinTestSourceDirs = mutableListOf<File>()
            collectKotlinSourceDirs(kotlinSourceDirs, kotlinTestSourceDirs)

            sourceSets["main"].kotlin.srcDirs(kotlinSourceDirs)
            sourceSets["test"].kotlin.srcDirs(kotlinTestSourceDirs)
        }

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
    apply {
        plugin("org.jetbrains.intellij.platform")
    }

    dependencies {
        implementation(project(":core"))
    }
}


fun prop(name: String): String {
    return extra.properties[name] as? String ?: error("Property `$name` is not defined in gradle.properties")
}

fun loadPlatformProperties() {
    val platformVersion = prop("platformVersion").toInt()
    val platformFilePath = rootDir.resolve("gradle-$platformVersion.properties")
    loadProperties(platformFilePath.toString()).forEach { (key, value) ->
        rootProject.extra.set(key.toString(), value)
    }
}

fun Project.collectKotlinSourceDirs(
    sourceDirs: MutableList<File>? = null,
    testSourceDirs: MutableList<File>? = null,
    resourceDirs: MutableList<File>? = null
) {
    val dirPattern = Regex("\\d+[+]?|\\d+-\\d+")
    projectDir.resolve("src")
        .list { file, name -> file.isDirectory && name.matches(dirPattern) }
        ?.sorted()
        ?.forEach {
            val (min, max) = when {
                it.contains('-') -> it.split('-').map(String::toInt)
                it.endsWith('+') -> it.removeSuffix("+").toInt().let { n -> listOf(n, 999) }
                else -> it.toInt().let { n -> listOf(n, n) }
            }

            if (platformVersion in min..max) {
                if (sourceDirs != null) {
                    projectDir
                        .resolve("src/$it/main/kotlin")
                        .takeIf(File::isDirectory)
                        ?.also(sourceDirs::plusAssign)
                }
                if (testSourceDirs != null) {
                    projectDir.resolve("src/$it/test/kotlin")
                        .takeIf(File::isDirectory)
                        ?.also(testSourceDirs::plusAssign)
                }
                if (resourceDirs != null) {
                    projectDir.resolve("src/$it/main/resources")
                        .takeIf(File::isDirectory)
                        ?.also(resourceDirs::plusAssign)
                }
            }
        }
}
