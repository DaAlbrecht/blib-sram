pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // karoo-ext from Github Packages
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
credentials {
    username = System.getenv("GITHUB_USERNAME")
    password = System.getenv("GITHUB_TOKEN")
}
        }
    }
}

rootProject.name = "Karoo Extension Template"
include("app")
