import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipEntry
import org.apache.tools.zip.ZipOutputStream
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
    id("org.jetbrains.intellij.platform") version "2.3.0"

    // https://github.com/GradleUp/shadow
    id("com.gradleup.shadow") version "9.0.0-beta9"
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
                useVersion("$lspLibraryVersion.$platformVersion")
                because("LSP platform version")
            }
        }
    }

    dependencies {
        implementation(rootProject.libs.lsp.client) {
            exclude("org.jetbrains.kotlin")
            exclude("com.google.code.gson")
        }

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

        // Bundle LSP libraries into the composed plugin JAR
        val lspLibrary by configurations.creating { isTransitive = true }
        lspLibrary(rootProject.libs.lsp.client) {
            exclude("org.jetbrains.kotlin")
            exclude("com.google.code.gson")
        }
        lspLibrary.resolvedConfiguration.firstLevelModuleDependencies
            .flatMap { it.allDependencies() }
            .forEach { if (it.moduleGroup == "dev.j-a.ide") intellijPlatformPluginModule(it.name) }
    }

    tasks {
        val shadowComposedJar by registering(ShadowJar::class) {
            dependsOn(composedJar)

            archiveBaseName = "gosupport-plugin"
            from(zipTree(composedJar.get().outputs.files.singleFile))
            transform(UpdatePluginXmlTransformer())
            // Change the target package to be inside your own plugin's package
            // For v2 descriptors, it must be inside the package specified by the 'package' attribute of <idea-plugin>
            relocate("dev.j_a.ide", "dev.j_a.gosupport.lsp_support")
        }

        prepareSandbox {
            pluginJar.set(shadowComposedJar.get().archiveFile)
        }

        prepareJarSearchableOptions.configure {
            composedJarFile.set(shadowComposedJar.get().archiveFile)
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

private class UpdatePluginXmlTransformer : com.github.jengelman.gradle.plugins.shadow.transformers.Transformer {
    private val transformedResources = mutableMapOf<String, String>()
    private val transformedPaths = setOf("META-INF/plugin-lsp-client.xml", "META-INF/plugin-dap-client.xml")

    override fun canTransformResource(element: FileTreeElement): Boolean = element.path in transformedPaths

    override fun hasTransformedResource(): Boolean = transformedResources.isNotEmpty()

    override fun transform(context: TransformerContext) {
        val initialXml = context.inputStream.readAllBytes().toString(Charsets.UTF_8)
        val patchedXml = context.relocators.fold(initialXml) { xml, relocator -> relocator.applyToSourceContent(xml) }
        transformedResources.put(context.path, patchedXml)
    }

    override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) {
        transformedResources.forEach { key, value ->
            os.putNextEntry(ZipEntry(key))
            os.write(value.toByteArray(Charsets.UTF_8))
            os.flush()
        }
    }
}

fun ResolvedDependency.allDependencies(target: MutableSet<ResolvedDependency> = mutableSetOf()): Set<ResolvedDependency> {
    target += this
    children.forEach { it.allDependencies(target) }
    return target
}
