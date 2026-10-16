pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor는 Maven Central에 배포되지 않고 JitPack으로만 제공됩니다.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Barley's MusicPlayer"
include(":app")
