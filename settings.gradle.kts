rootProject.name = "go-support-lsp"

include("core")

pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        gradlePluginPortal()
    }

    val platformVersion = (extra["platformVersion"] as String).toInt()
    plugins {
        kotlin("jvm") version when {
            platformVersion >= 251 -> "2.1.0"
            else -> "2.0.21"
        }
    }
}
