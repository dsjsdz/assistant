pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
  }
  resolutionStrategy {
    eachPlugin {
      when (requested.id.id) {
        "com.android.library" -> useVersion("8.0.2")
        "org.jetbrains.kotlin.android" -> useVersion("2.0.0")
      }
    }
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenCentral()
    google()
    maven(url = "https://jitpack.io")
  }
}

rootProject.name = "Awish Assistant"
include(":app")
